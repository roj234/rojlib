package roj.misc;

import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.io.buf.NativeArray;
import roj.net.ch.ChannelCtx;
import roj.net.ch.CtxEmbedded;
import roj.net.http.Headers;
import roj.net.http.srv.MultipartFormHandler;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * @author Roj234
 * @since 2023/2/2 0002 7:21
 */
public class MHTParser extends MultipartFormHandler {
	public static void main(String[] args) throws Exception {
		ByteList buf = new ByteList(IOUtil.read(new File(args[0])));
		Headers h = new Headers();
		h.parseHead(buf);
		buf.rIndex += 2;

		String type = h.get("Content-Type");
		MHTParser parser = new MHTParser();
		parser.init(type);

		CtxEmbedded ch = new CtxEmbedded();
		ch.addLast("_", parser);
		ch.fireChannelRead(buf);
		parser.onSuccess();
		parser.onComplete();
		ch.close();
	}

	FileChannel out;
	File file;
	int enc;

	@Override
	protected String getName(Headers hdr) throws IOException {
		String enc = hdr.get("content-transfer-encoding");
		if (enc.equalsIgnoreCase("quoted-printable")) {
			this.enc = 0;
		} else if (enc.equalsIgnoreCase("base64")) {
			this.enc = 1;
		} else {
			throw new IOException("Unknown enc " + enc);
		}

		String url = hdr.get("content-location");
		try {
			URI url1 = new URI(url);
			url = url1.getHost() + url1.getPath();
			url = IOUtil.safePath(url);
			if (url.endsWith("/")) url += "index.html";
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		file = new File(url);
		File p = file.getParentFile();
		if (p != null) p.mkdirs();
		else System.out.println(url);
		out = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		return url;
	}

	@Override
	protected void onValue(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		ByteList buf1 = IOUtil.getSharedByteBuf();
		if (enc == 1) {
			ByteList tmp = new ByteList();
			NativeArray range = buf.byteRangeR(buf.readableBytes());
			for (int i = 0; i < range.length(); i++) {
				byte b = range.get(i);
				if ((b&0xFF) >= 32) tmp.put(b);
			}
			Base64.decode(tmp, buf1);
		}
		else if (enc == 0) QuotedPrintable.decode(buf, buf1);
		out.write(buf1.nioBuffer());
	}

	@Override
	protected void onValueEnd(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		onValue(ctx, buf);
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

				out.put((byte) '=').put((byte) TextUtil.b2h(v>>>4)).put((byte) TextUtil.b2h(v&0xF));
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
						in.rIndex -= 3;
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
