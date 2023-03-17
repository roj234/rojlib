package roj.misc;

import roj.io.IOUtil;
import roj.net.http.Headers;
import roj.net.http.IllegalRequestException;
import roj.net.http.srv.MultipartFormHandler;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.FileInputStream;

/**
 * @author Roj234
 * @since 2023/2/2 0002 7:21
 */
public class MHTParser extends MultipartFormHandler {
	public static void main(String[] args) throws Exception {
		ByteList buf = IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(args[0]));
		int len = buf.wIndex();
		Headers h = new Headers();
		h.parseHead(buf);
		buf.wIndex(len);

		String type = h.get("Content-Type");
		MHTParser parser = new MHTParser();
		parser.init(type);
		parser.onData(buf);
	}

	protected void onKey(CharSequence key) {}

	@Override
	protected void onMPKey(Headers hdr) throws IllegalRequestException {
		System.out.println("header="+hdr);
	}

	@Override
	protected void onValue(DynByteBuf buf) throws IllegalRequestException {
		System.out.println("data="+buf);
		//Base64.decode(buf)
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
				if (v > 32 && v < 127) {
					if (v != 61) {
						out.put((byte) v);
						if (76 == ++len) {
							out.putAscii("=\r\n");
							len = 0;
						}
						continue;
					}
				}

				if (out.writableBytes() < 3) {
					in.rIndex--;
					this.len = len;
					return -1;
				}

				if (len > 73) {
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

				byte v = in.readByte();
				if (v != '=') {
					out.put(v);
				} else {
					int ch = in.readUnsignedShort();
					if (ch == 0x0D0A) continue;

					if (in.readableBytes() < 2) {
						in.rIndex -= 3;
						return 1;
					}
					ch = (TextUtil.h2b((char) (ch>>>8))<<4)|TextUtil.h2b((char) (ch&0xFF));
					out.put((byte) ch);
				}
			}
			return 0;
		}
	}
}
