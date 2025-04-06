package roj.plugins.unpacker;

import roj.collect.TrieTree;
import roj.crypt.Base64;
import roj.http.Headers;
import roj.http.server.MultipartParser;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.EmbeddedChannel;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NativeArray;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * @author Roj234
 * @since 2023/2/2 0002 7:21
 */
class MHtml extends MultipartParser implements Unpacker {
	FileInputStream in;
	ByteList buf = new ByteList();
	Headers h;

	@Override
	public TrieTree<?> load(File file) throws IOException {
		in = new FileInputStream(file);
		Headers h = new Headers();
		for (;;) {
			buf.compact();
			if (buf.readStream(in, 1024) == 0)
				throw new EOFException();

			if (Headers.parseHeader(h, buf)) {
				buf.readShort(); // CRLF
				break;
			}
		}

		this.h = h;

		TrieTree<String> unsupported = new TrieTree<>();
		unsupported.put("", h.get("Content-Type"));
		return unsupported;
	}

	@Override
	public void export(File path, String prefix) throws IOException {
		if (!prefix.isEmpty()) throw new UnsupportedOperationException("不支持选择性导出");
		basePath = path;

		init(h.get("Content-Type"));

		try (var ch = EmbeddedChannel.createReadonly()) {
			ch.addLast("_", this);
			for (;;) {
				buf.compact();
				if (buf.readStream(in, 1024) == 0) break;

				ch.fireChannelRead(buf);
			}

			onSuccess(buf);
			onComplete();
		} finally {
			IOUtil.closeSilently(in);
		}
	}

	@Override
	public void close() throws IOException {
		IOUtil.closeSilently(in);
		IOUtil.closeSilently(out);
		in = null;
		out = null;
	}

	private File basePath;
	private FileChannel out;
	private int enc;

	@Override
	protected Object begin(ChannelCtx ctx, Headers header) throws IOException {
		String enc = header.get("content-transfer-encoding");
		if (enc.equalsIgnoreCase("quoted-printable")) {
			this.enc = 0;
		} else if (enc.equalsIgnoreCase("base64")) {
			this.enc = 1;
		} else {
			throw new IOException("Unknown encoding "+enc);
		}

		String url = header.get("content-location");
		try {
			URI url1 = new URI(url);
			url = url1.getHost() + url1.getPath();
			url = IOUtil.safePath(url);
			if (url.endsWith("/")) url += "index.html";
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		var file = new File(basePath, url);
		file.getParentFile().mkdirs();

		IOUtil.closeSilently(out);
		out = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);

		data.put(url, out);
		return out;
	}

	private final ByteList encBuf = new ByteList();
	@Override
	protected void data(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		ByteList buf1 = IOUtil.getSharedByteBuf();
		if (enc == 1) {
			ByteList tmp = encBuf;
			NativeArray range = buf.byteRangeR(buf.readableBytes());
			for (int i = 0; i < range.length; i++) {
				byte b = range.get(i);
				if ((b&0xFF) >= 32) tmp.put(b);
			}

			int off = tmp.length() & ~3;
			if (off > 0) Base64.decode(tmp, 0, off, buf1, Base64.B64_CHAR_REV);
			tmp.rIndex = off;
			tmp.compact();
		}
		else if (enc == 0) QuotedPrintable.decode(buf, buf1);
		out.write(buf1.nioBuffer());
	}

	public static class QuotedPrintable {
		int len;

		public int encode(DynByteBuf in, DynByteBuf out) {
			int len = this.len;
			while (in.isReadable()) {
				if (!out.isWritable()) {
					this.len = len;
					return -1;
				}

				int v = in.readUnsignedByte();
				if ((v > 32 && v < 127 && v != 61) || v == ' ') {
					if (len >= 75) {
						out.putAscii("=\r\n");
						len = 0;
					}
					out.put((byte) v);
					len++;
					continue;
				}

				if (out.writableBytes() < 3) {
					in.rIndex--;
					this.len = len;
					return -1;
				}

				if (len+3 > 75) {
					out.putAscii("=\r\n");
					len = 0;
				}

				out.put('=').put(TextUtil.b2h(v>>>4)).put(TextUtil.b2h(v&0xF));
				len += 3;
			}
			this.len = len;
			return 0;
		}

		public static int decode(DynByteBuf in, DynByteBuf out) {
			while (in.isReadable()) {
				if (!out.isWritable()) return -1;

				int v = in.readUnsignedByte();
				if (v != '=') {
					out.put(v);
				} else {
					if (in.readableBytes() < 2) {
						in.rIndex --;
						return 1;
					}

					int ch = in.readUnsignedShort();
					if (ch == 0x0D0A) continue;

					ch = (TextUtil.h2b((char) (ch>>>8))<<4)|TextUtil.h2b((char) (ch&0xFF));
					out.put((byte) ch);
				}
			}
			return 0;
		}
	}
}