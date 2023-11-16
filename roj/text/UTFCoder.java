package roj.text;

import roj.crypt.Base64;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author solo6975
 * @since 2022/1/15 16:01
 */
public class UTFCoder {
	public final CharList charBuf = new CharList();
	public final ByteList byteBuf = new ByteList();

	private final ByteList.Slice shell = new ByteList.Slice();
	public final ByteList.Slice shellB = new ByteList.Slice();

	public final StringBuilder numberHelper = new StringBuilder(32);

	public byte[] encode(CharSequence str) {
		ByteList b = byteBuf; b.clear();
		return b.putUTFData(str).toByteArray();
	}

	public String decode(byte[] b) { return decodeR(b).toString(); }
	public CharList decodeR(byte[] b) { return decodeR(shell.setR(b,0,b.length)); }
	public CharList decodeR(DynByteBuf b) {
		charBuf.clear();
		UTF8MB4.CODER.decodeFixedIn(b, b.readableBytes(), charBuf);
		return charBuf;
	}

	public String encodeHex(byte[] b) { return encodeHex(shell.setR(b,0,b.length)); }
	public String encodeHex(ByteList b) {
		charBuf.clear();
		TextUtil.bytes2hex(b.list, 0, b.wIndex(), charBuf);
		return charBuf.toString();
	}

	public byte[] decodeHex(CharSequence c) {
		ByteList b = byteBuf; b.clear();
		return TextUtil.hex2bytes(c, b).toByteArray();
	}

	public String encodeBase64(byte[] b) { return encodeBase64(shell.setR(b,0,b.length)); }
	public String encodeBase64(DynByteBuf b) { return encodeBase64(b, Base64.B64_CHAR); }
	public String encodeBase64(DynByteBuf b, byte[] chars) {
		charBuf.clear();
		Base64.encode(b, charBuf, chars);
		return charBuf.toString();
	}

	public byte[] decodeBase64(CharSequence c) { return decodeBase64R(c).toByteArray(); }
	public ByteList decodeBase64R(CharSequence c) { return decodeBase64R(c, Base64.B64_CHAR_REV); }
	public ByteList decodeBase64R(CharSequence c, byte[] chars) {
		ByteList b = byteBuf; b.clear();
		Base64.decode(c, 0, c.length(), b, chars);
		return b;
	}

	public int encodeTo(OutputStream out) throws IOException {
		int i = encodeTo(charBuf, out);
		charBuf.clear();
		return i;
	}

	public int encodeTo(CharSequence str, OutputStream out) throws IOException {
		if (out instanceof DynByteBuf) {
			int len = DynByteBuf.byteCountUTF8(str);
			((DynByteBuf) out).putUTFData0(str, len);
			return len;
		}

		ByteList ob = byteBuf; ob.clear(); ob.ensureCapacity(3072);

		int wrote = 0;
		int off = 0;
		while (off < str.length()) {
			off = UTF8MB4.CODER.encodeLoop(str, off, str.length(), ob, ob.capacity(), 0);

			ob.writeToStream(out);
			wrote += ob.wIndex();
			ob.clear();
		}

		return wrote;
	}

	public int decodeFrom(InputStream in) throws IOException {
		charBuf.clear();
		return decodeUpto(in, charBuf, -1);
	}
	public int decodeUpto(InputStream in, Appendable to, int len) throws IOException {
		ByteList ob = byteBuf; ob.clear(); ob.ensureCapacity(4096);

		if (len < 0) len = Integer.MAX_VALUE;
		int read = 0;
		while (len > 0) {
			int d = ob.readStream(in, Math.min(ob.unsafeWritableBytes(), len));
			if (d == 0) break; // EOF

			read += d;
			len -= d;

			UTF8MB4.CODER.decodeLoop(ob, ob.readableBytes(), to, Integer.MAX_VALUE, true);
			ob.compact();
		}

		UTF8MB4.CODER.decodeLoop(ob, ob.readableBytes(), to, Integer.MAX_VALUE, false);

		return read;
	}

	public ByteList wrap(byte[] b) { return shell.setR(b,0,b.length); }
	public ByteList wrap(byte[] b, int off, int len) { return shell.setR(b,off,len); }
}
