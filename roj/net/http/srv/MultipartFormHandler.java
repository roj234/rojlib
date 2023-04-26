package roj.net.http.srv;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.text.FastMatcher;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public class MultipartFormHandler extends HPostHandler {
	private CharSequence boundary;
	private FastMatcher matcher;

	private int state;

	private final Headers header = new Headers();
	private MultipartFormHandler child;

	protected String name;
	private long totalSize;

	public MultipartFormHandler() {}
	public MultipartFormHandler(Request req) {
		init(req.getField("content-type"));
	}

	public void init(String contentType) {
		if (contentType.startsWith("multipart/")) {
			String strBound = Headers.getOneValue(contentType, "boundary");
			if (strBound == null) throw new IllegalArgumentException("Not found boundary in Content-Type header: " + contentType);
			matcher = new FastMatcher(boundary = "--".concat(strBound));
			state = 0;
		} else {
			throw new IllegalArgumentException(contentType);
		}
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		while (true) {
			switch (state) {
				// find boundary | data
				case 0:
					int next = matcher.match(buf, 0);
					if (next < 0) { // full data block
						int len = Math.min(buf.readableBytes(), boundary.length());
						int i = buf.wIndex()-len;
						while (len > 0) {
							if (buf.getU(i) == boundary.charAt(0)) break;
							i++;
							len--;
						}
						if (i == 0) return;

						buf.wIndex(buf.wIndex()-len);
						onValue(ctx, buf);
						buf.wIndex(buf.wIndex()+len);
						return;
					}

					int rend = buf.rIndex + next + boundary.length();
					int lim = buf.wIndex();
					buf.wIndex(buf.rIndex+next);

					if (name != null) onValueEnd(ctx, buf);

					buf.rIndex = rend;
					buf.wIndex(lim);
					state = 1;
				case 1:
					if (buf.readableBytes() < 4) return;

					char kind = buf.readChar();
					// \r\n
					if (kind != 0x0D0A) {
						state = 3;
						// EOF
						if (kind == 0x2D2D && buf.readChar() == 0x0D0A) return;
						throw new IllegalArgumentException("Invalid multipart format");
					}

					header.clear();
					state = 2;
				// then, read header
				case 2:
					if (!header.parseHead(buf, IOUtil.getSharedByteBuf())) return;
					name = getName(header);

					String type = header.getOrDefault("content-type", "");
					if (type.startsWith("multipart")) {
						child = new MultipartFormHandler();
						child.init(type);
						child.channelOpened(ctx);
						state = 4;
					} else {
						onKey(ctx, name);
						state = 0;
					}

					break;
				// eof
				case 3: throw new EOFException();
				// multipart/mixed
				case 4:
					child.channelRead(ctx, buf);
					if (child.state != 3) return;
					state = 0;
					terracottaEnd(name, child);
				break;
			}
		}
	}

	@Override
	public void onSuccess() throws IOException {
		if (state != 3) throw new EOFException("Invalid multipart format");
	}

	public static final class FormData {
		public String name;
		// T: Map<String, T> | DynByteBuf | File
		public Object data;

		public FormData(String name, Object data) {
			this.name = name;
			this.data = data;
		}

		public boolean inMemory() {
			return !(data instanceof FileChannel);
		}

		public boolean isMulti() {
			return data instanceof Map;
		}
	}

	protected String getName(Headers hdr) throws IOException {
		String name = hdr.getFieldValue("content-disposition", "name");
		if (name == null) throw new NullPointerException("不支持的MP格式");
		return name;
	}

	protected MultipartFormHandler terracottaBegin() { return new MultipartFormHandler(); }
	protected void terracottaEnd(String name, MultipartFormHandler child) { map.put(name, new FormData(name, child.map)); }

	protected void onKey(ChannelCtx ctx, String name) throws IOException { map.put(name, ctx.allocate(true, 0xFFFF)); }
	protected void onValue(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		Object o = map.get(name);
		if (o instanceof DirectByteList) {
			DirectByteList myBuf = ((DirectByteList) o);
			if (myBuf.readableBytes() + buf.readableBytes() <= 0xFFFF) {
				myBuf.put(buf);
				return;
			}
			ctx.reserve(myBuf);
			map.put(name, o = createTmpFile());
		}

		((FileChannel) o).write(buf.nioBuffer());
	}
	protected void onValueEnd(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		onValue(ctx, buf);
		Object data = map.get(name);
		if (data instanceof DirectByteList) {
			DirectByteList v = ((DirectByteList) data);
			data = new ByteList(v.toByteArray());
			ctx.reserve(v);
		}
		map.put(name, new FormData(name, data));
	}

	protected FileChannel createTmpFile() throws IOException { throw new IOException("No file cache available"); }
}
