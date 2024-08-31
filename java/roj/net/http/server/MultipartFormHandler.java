package roj.net.http.server;

import org.jetbrains.annotations.Nullable;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.http.Headers;
import roj.text.FastMatcher;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Map;

/**
 * <a href="https://datatracker.ietf.org/doc/html/rfc2046#section-5.1.1">RFC2046</a>
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public class MultipartFormHandler extends UrlEncodedHandler {
	private String boundary;
	private FastMatcher matcher;

	private int state;

	private final Headers header = new Headers();
	private MultipartFormHandler child;

	public MultipartFormHandler() {}
	public MultipartFormHandler(Request req) { init(req.getField("content-type")); }

	public void init(String contentType) {
		if (contentType.startsWith("multipart/")) {
			String strBound = Headers.getOneValue(contentType, "boundary");
			if (strBound == null) throw new IllegalArgumentException("Not found boundary in Content-Type header: " + contentType);
			matcher = new FastMatcher(boundary = "--".concat(strBound));
			state = 0;
		} else {
			throw new IllegalArgumentException("contentType='"+contentType+"'");
		}
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		while (true) {
			switch (state) {
				case 0: // find boundary | data
					int next = matcher.match(buf, 0);
					if (next < 0) {
						// 从前往后检查至多[boundary.length-1]个字节
						// 对每个字节，尝试和boundary[0 -> i]匹配，
						int keep = Math.min(buf.readableBytes(), boundary.length()-1);

						int i = buf.wIndex()-keep;
						while (keep > 0) {
							findPartialMatch: {
								for (int j = 0; j < keep; j++) {
									if (buf.get(i+j) != boundary.charAt(j))
										break findPartialMatch;
								}
								break;
							}

							i++;
							keep--;
						}

						if (i == 0) return;

						buf.wIndex(i);
						onValue(ctx, buf);
						//buf.rIndex = buf.wIndex();
						buf.wIndex(buf.wIndex()+keep);
						return;
					}

					int rend = buf.rIndex + next + boundary.length();
					int lim = buf.wIndex();
					buf.wIndex(buf.rIndex+next);

					if (name != null) onValueEnd(ctx, buf);

					buf.rIndex = rend;
					buf.wIndex(lim);
					state = 1;
				case 1: // \r\n
					if (buf.readableBytes() < 4) return;
					if (boundary.startsWith("--")) {
						boundary = "\r\n"+boundary;
						matcher.setPattern(boundary);
					}

					char kind = buf.readChar();
					// \r\n
					if (kind != 0x0D0A) {
						state = 3;
						// EOF
						if (kind == 0x2D2D && buf.readChar() == 0x0D0A) return;
						throw new IllegalArgumentException("Invalid multipart format");
					}

					// 如果下一个 part 没有头部信息，边界之后就应该跟两个 CRLF
					if (buf.readChar(buf.rIndex) == 0x0D0A) {
						buf.rIndex += 2;
						name = getPlainName(null);
						onKey(ctx, name);
						state = 0;
						break;
					}

					header.clear();
					state = 2;
				case 2:// header
					if (!header.parseHead(buf, IOUtil.getSharedByteBuf())) return;
					name = getName(header);

					String type = header.getOrDefault("content-type", "");
					if (type.startsWith("multipart")) {
						child = terracottaBegin();
						child.init(type);
						child.channelOpened(ctx);
						state = 4;
					} else {
						onKey(ctx, name);
						state = 0;
					}

					break;
				case 3:// 根据RFC,忽略其后的任何内容
					buf.rIndex = buf.wIndex();
					break;
				case 4:// multipart/mixed
					child.channelRead(ctx, buf);
					if (child.state != 3) return;
					state = 0;
					terracottaEnd(name, child);
				break;
			}
		}
	}

	protected String getPlainName(@Nullable Headers hdr) {
		//return "!!implicit_"+map.size();
		throw new IllegalStateException("没有content-disposition.name,如果需要处理隐式的text/plain或特殊的头，请覆盖此方法");
	}
	protected String getName(Headers hdr) throws IOException {
		String name = hdr.getFieldValue("content-disposition", "name");
		if (name == null) return getPlainName(hdr);
		return name;
	}

	public static final class FormData implements Closeable {
		public String name;
		// T: Map<String, FormData> | DynByteBuf | File
		public Object data;
		File file;

		public FormData(String name, Object data) {
			this.name = name;
			this.data = data;
		}

		public int type() {
			if (data instanceof Map) return 2;
			if (data instanceof DynByteBuf) return 0;
			return 1;
		}

		public boolean append(DynByteBuf buf) throws IOException {
			if (data instanceof DynByteBuf b) {
				if (b.writableBytes() >= buf.readableBytes()) {
					b.put(buf);
					return true;
				}
			} else if (data instanceof FileChannel) {
				((FileChannel) data).write(buf.nioBuffer());
				return true;
			}
			return false;
		}

		public void _setExternalBuffer(DynByteBuf buf) throws IOException {
			if (!(data instanceof DynByteBuf)) throw new IllegalStateException("unsupported case");
			else buf.put((DynByteBuf) data);
			close();
			data = buf;
		}

		public void _setExternalFile(File file) throws IOException {
			if (!(data instanceof DynByteBuf)) throw new IllegalStateException("unsupported case");

			FileChannel fc = FileChannel.open(file.toPath(),
				StandardOpenOption.CREATE, StandardOpenOption.READ,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);

			try {
				fc.write(((DynByteBuf) data).nioBuffer());
				close();
			} catch (Exception e) {
				fc.close();
				throw e;
			}
			data = fc;
			this.file = file;
		}

		public InputStream getInputStream() throws IOException {
			if (data instanceof FileChannel) {
				((FileChannel) data).position(0);
				return Channels.newInputStream((ReadableByteChannel) data);
			}
			return ((DynByteBuf) data).asInputStream();
		}

		public void moveTo(File file) throws IOException {
			if (this.file != null) {
				if (this.file.renameTo(file)) {
					this.file = null;
					return;
				}
			}

			try (FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				fc.position(0);
				if (data instanceof DynByteBuf) {
					fc.write(((DynByteBuf) data).nioBuffer());
				} else if (data instanceof FileChannel fr) {
					fc.transferFrom(fr, 0, fr.size());
				} else {
					throw new IOException("is " + data.getClass().getName());
				}
			}
		}

		public Map<String, FormData> asMap() { return Helpers.cast(data); }

		@Override
		public void close() throws IOException {
			try {
				if (data instanceof Closeable) ((Closeable) data).close();
			} finally {
				if (file != null && !file.delete()) {
					Files.deleteIfExists(file.toPath());
				}
			}
		}
	}

	public FormData file(String name) { return (FormData) data.get(name); }

	protected MultipartFormHandler terracottaBegin() { return new MultipartFormHandler(); }
	protected void terracottaEnd(String name, MultipartFormHandler child) { data.put(name, new FormData(name, child.data)); }

	protected void onKey(ChannelCtx ctx, String name) throws IOException { data.put(name, new FormData(name, ctx.allocate(true, 0xFFFF))); }
	protected void onValue(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		FormData fd = (FormData) data.get(name);
		if (!fd.append(buf)) {
			throw new IOException("数据太大，请覆盖该函数提供临时文件缓存");
		}
	}
	protected void onValueEnd(ChannelCtx ctx, DynByteBuf buf) throws IOException { onValue(ctx, buf); }

	@Override
	public void onSuccess() throws IOException {
		if (state != 3) throw new EOFException("Invalid multipart format");
	}

	@Override
	public void onComplete() throws IOException {
		Collection<Object> values = Helpers.cast(data.values());
		for (Object fd : values) {
			if (fd instanceof Closeable c)
				IOUtil.closeSilently(c);
		}
	}
}