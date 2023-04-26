package roj.text;

import roj.crypt.Base64;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * @author solo6975
 * @since 2022/1/15 16:01
 */
public class UTFCoder {
	public final CharList charBuf = new CharList();
	public final ByteList byteBuf = new ByteList();
	private final ByteList.Slice shell = new ByteList.Slice();

	public final StringBuilder numberHelper = new StringBuilder(32);

	public boolean keep;

	private void readBuf(ByteBuffer buf, boolean eatData) {
		ByteList b = byteBuf;
		b.clear();
		int rem = buf.remaining(), pos = buf.position();
		b.ensureCapacity(rem);

		buf.get(b.list, 0, rem);
		if (!eatData) buf.position(pos);
		b.wIndex(rem);
	}

	public byte[] encode(CharSequence cs) {
		ByteList b = this.byteBuf;
		if (!keep) b.clear();
		b.putUTFData(cs);
		return b.toByteArray();
	}

	public byte[] encode() {
		ByteList b = this.byteBuf;
		if (!keep) b.clear();
		b.putUTFData(charBuf);
		charBuf.clear();
		return b.toByteArray();
	}

	public ByteList encodeR() {
		ByteList b = this.byteBuf;
		if (!keep) b.clear();
		b.putUTFData(charBuf);
		charBuf.clear();
		return b;
	}

	public ByteList encodeR(CharSequence cs) {
		ByteList b = this.byteBuf;
		if (!keep) b.clear();
		b.putUTFData(cs);
		return b;
	}

	public String decode() {
		if (!keep) charBuf.clear();
		try {
			ByteList.decodeUTF(byteBuf.wIndex(), charBuf, byteBuf);
		} catch (IOException ignored) {}
		byteBuf.clear();
		return charBuf.toString();
	}

	public String decode(DynByteBuf b) {
		if (!keep) charBuf.clear();
		try {
			ByteList.decodeUTF(b.readableBytes(), charBuf, b);
		} catch (IOException ignored) {}
		return charBuf.toString();
	}

	public String decode(byte[] b) {
		return decode(shell.setR(b,0,b.length));
	}

	public String decode(ByteBuffer buf, boolean eatData) {
		readBuf(buf, eatData);
		return decode();
	}

	public CharList decodeR() {
		if (!keep) charBuf.clear();
		try {
			ByteList.decodeUTF(byteBuf.wIndex(), charBuf, byteBuf);
		} catch (IOException ignored) {}
		byteBuf.clear();
		return charBuf;
	}

	public CharList decodeR(DynByteBuf b) {
		if (!keep) charBuf.clear();
		try {
			ByteList.decodeUTF(b.wIndex(), charBuf, b);
		} catch (IOException ignored) {}
		return charBuf;
	}

	public CharList decodeR(ByteBuffer buf, boolean eatData) {
		readBuf(buf, eatData);
		return decodeR();
	}

	public byte[] decodeHex() {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		byte[] bb = TextUtil.hex2bytes(charBuf, b).toByteArray();
		charBuf.clear();
		return bb;
	}

	public byte[] decodeHex(CharSequence c) {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		return TextUtil.hex2bytes(c, b).toByteArray();
	}

	public ByteList decodeHexR() {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		TextUtil.hex2bytes(charBuf, b);
		charBuf.clear();
		return b;
	}

	public ByteList decodeHexR(CharSequence c) {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		TextUtil.hex2bytes(c, b);
		return b;
	}

	public String encodeHex() {
		if (!keep) charBuf.clear();
		TextUtil.bytes2hex(byteBuf.list, 0, byteBuf.wIndex(), charBuf);
		byteBuf.clear();
		return charBuf.toString();
	}

	public String encodeHex(ByteList b) {
		if (!keep) charBuf.clear();
		TextUtil.bytes2hex(b.list, 0, b.wIndex(), charBuf);
		return charBuf.toString();
	}

	public String encodeHex(byte[] b) {
		return encodeHex(shell.setR(b,0,b.length));
	}

	public CharList encodeHexR() {
		if (!keep) charBuf.clear();
		TextUtil.bytes2hex(byteBuf.list, 0, byteBuf.wIndex(), charBuf);
		byteBuf.clear();
		return charBuf;
	}

	public CharList encodeHexR(ByteList b) {
		if (!keep) charBuf.clear();
		TextUtil.bytes2hex(b.list, b.arrayOffset(), b.readableBytes(), charBuf);
		return charBuf;
	}

	public byte[] decodeBase64() {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		byte[] bb = Base64.decode(charBuf, 0, charBuf.length(), b, Base64.B64_CHAR_REV).toByteArray();
		charBuf.clear();
		return bb;
	}

	public byte[] decodeBase64(CharSequence c) {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		return Base64.decode(c, b).toByteArray();
	}

	public ByteList decodeBase64R() {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		Base64.decode(charBuf, 0, charBuf.length(), b, Base64.B64_CHAR_REV);
		charBuf.clear();
		return b;
	}

	public ByteList decodeBase64R(CharSequence c) {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		Base64.decode(c, 0, c.length(), b, Base64.B64_CHAR_REV);
		return b;
	}

	public ByteList decodeBase64R(CharSequence c, byte[] chars) {
		ByteList b = byteBuf;
		if (!keep) b.clear();
		Base64.decode(c, 0, c.length(), b, chars);
		return b;
	}

	public String encodeBase64() {
		if (!keep) charBuf.clear();
		Base64.encode(byteBuf, charBuf, Base64.B64_CHAR);
		byteBuf.clear();
		return charBuf.toString();
	}

	public String encodeBase64(DynByteBuf b) {
		if (!keep) charBuf.clear();
		Base64.encode(b, charBuf, Base64.B64_CHAR);
		return charBuf.toString();
	}

	public String encodeBase64(DynByteBuf b, byte[] chars) {
		if (!keep) charBuf.clear();
		Base64.encode(b, charBuf, chars);
		return charBuf.toString();
	}

	public String encodeBase64(byte[] b) {
		return encodeBase64(shell.setR(b,0,b.length));
	}

	public CharList encodeBase64R() {
		if (!keep) charBuf.clear();
		Base64.encode(byteBuf, charBuf, Base64.B64_CHAR);
		byteBuf.clear();
		return charBuf;
	}

	public CharList encodeBase64R(DynByteBuf b) {
		if (!keep) charBuf.clear();
		Base64.encode(b, charBuf, Base64.B64_CHAR);
		return charBuf;
	}

	public CharList encodeBase64R(DynByteBuf b, byte[] chars) {
		if (!keep) charBuf.clear();
		Base64.encode(b, charBuf, chars);
		return charBuf;
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

	public final ByteList.Slice shellB = new ByteList.Slice();
}
