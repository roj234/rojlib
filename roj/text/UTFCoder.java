package roj.text;

import roj.crypt.Base64;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;

/**
 * @author solo6975
 * @since 2022/1/15 16:01
 */
public class UTFCoder {
    public final CharList charBuf = new CharList();
    public final ByteList byteBuf = new ByteList();
    private final ByteList shell = new ByteList();

    public boolean keep;

    public byte[] baseChars, baseCharsRev;

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
        ByteList.writeUTF(b, cs, -1);
        return b.toByteArray();
    }

    public byte[] encode() {
        ByteList b = this.byteBuf;
        if (!keep) b.clear();
        ByteList.writeUTF(b, charBuf, -1);
        charBuf.clear();
        return b.toByteArray();
    }

    public ByteList encodeR() {
        ByteList b = this.byteBuf;
        if (!keep) b.clear();
        ByteList.writeUTF(b, charBuf, -1);
        charBuf.clear();
        return b;
    }

    public ByteList encodeR(CharSequence cs) {
        ByteList b = this.byteBuf;
        if (!keep) b.clear();
        ByteList.writeUTF(b, cs, -1);
        return b;
    }

    public String decode() {
        if (!keep) charBuf.clear();
        try {
            ByteList.decodeUTF(byteBuf.wIndex(), charBuf, byteBuf);
        } catch (UTFDataFormatException ignored) {}
        byteBuf.clear();
        return charBuf.toString();
    }

    public String decode(ByteList b) {
        if (!keep) charBuf.clear();
        try {
            ByteList.decodeUTF(b.wIndex(), charBuf, b);
        } catch (UTFDataFormatException ignored) {}
        return charBuf.toString();
    }

    public String decode(byte[] b) {
        return decode(shell.setArray(b));
    }

    public String decode(ByteBuffer buf, boolean eatData) {
        readBuf(buf, eatData);
        return decode();
    }

    public CharList decodeR() {
        if (!keep) charBuf.clear();
        try {
            ByteList.decodeUTF(byteBuf.wIndex(), charBuf, byteBuf);
        } catch (UTFDataFormatException ignored) {}
        byteBuf.clear();
        return charBuf;
    }

    public CharList decodeR(ByteList b) {
        if (!keep) charBuf.clear();
        try {
            ByteList.decodeUTF(b.wIndex(), charBuf, b);
        } catch (UTFDataFormatException ignored) {}
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
        return encodeHex(shell.setArray(b));
    }

    public CharList encodeHexR() {
        if (!keep) charBuf.clear();
        TextUtil.bytes2hex(byteBuf.list, 0, byteBuf.wIndex(), charBuf);
        byteBuf.clear();
        return charBuf;
    }

    public CharList encodeHexR(ByteList b) {
        if (!keep) charBuf.clear();
        TextUtil.bytes2hex(b.list, 0, b.wIndex(), charBuf);
        return charBuf;
    }

    public byte[] decodeBase64() {
        ByteList b = byteBuf;
        if (!keep) b.clear();
        if (baseCharsRev == null) baseCharsRev = Base64.B64_CHAR_REV;
        byte[] bb = Base64.decode(charBuf, 0, charBuf.length(), b, baseCharsRev).toByteArray();
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
        if (baseCharsRev == null) baseCharsRev = Base64.B64_CHAR_REV;
        Base64.decode(charBuf, 0, charBuf.length(), b, baseCharsRev);
        charBuf.clear();
        return b;
    }

    public ByteList decodeBase64R(CharSequence c) {
        ByteList b = byteBuf;
        if (!keep) b.clear();
        if (baseCharsRev == null) baseCharsRev = Base64.B64_CHAR_REV;
        Base64.decode(c, 0, c.length(), b, baseCharsRev);
        return b;
    }

    public String encodeBase64() {
        if (!keep) charBuf.clear();
        if (baseChars == null) baseChars = Base64.B64_CHAR;
        Base64.encode(byteBuf, charBuf, baseChars);
        byteBuf.clear();
        return charBuf.toString();
    }

    public String encodeBase64(ByteList b) {
        if (!keep) charBuf.clear();
        if (baseChars == null) baseChars = Base64.B64_CHAR;
        Base64.encode(b, charBuf, baseChars);
        return charBuf.toString();
    }

    public String encodeBase64(byte[] b) {
        return encodeBase64(shell.setArray(b));
    }

    public CharList encodeBase64R() {
        if (!keep) charBuf.clear();
        if (baseChars == null) baseChars = Base64.B64_CHAR;
        Base64.encode(byteBuf, charBuf, baseChars);
        byteBuf.clear();
        return charBuf;
    }

    public CharList encodeBase64R(ByteList b) {
        if (!keep) charBuf.clear();
        if (baseChars == null) baseChars = Base64.B64_CHAR;
        Base64.encode(b, charBuf, baseChars);
        return charBuf;
    }

    public int encodeTo(OutputStream out) throws IOException {
        int i = encodeTo(charBuf, out);
        charBuf.clear();
        return i;
    }

    public int encodeTo(CharSequence str, OutputStream out) throws IOException {
        final int ONCE = Math.max(4096, byteBuf.list.length >> 1);
        ByteList ob = byteBuf; ob.clear();
        int i = 0, wrote = 0;
        while (i < str.length()) {
            int len = i + Math.min(ONCE, str.length() - i);
            while (i < len) {
                int c = str.charAt(i++);
                if ((c >= 0x0001) && (c <= 0x007F)) {
                    ob.put((byte) c);
                } else if (c > 0x07FF) {
                    ob.put((byte) (0xE0 | ((c >> 12) & 0x0F)));
                    ob.put((byte) (0x80 | ((c >> 6) & 0x3F)));
                    ob.put((byte) (0x80 | (c & 0x3F)));
                } else {
                    ob.put((byte) (0xC0 | ((c >> 6) & 0x1F)));
                    ob.put((byte) (0x80 | (c & 0x3F)));
                }
            }
            wrote += ob.wIndex();
            ob.writeToStream(out);
            ob.clear();
        }
        return wrote;
    }

    public int decodeFrom(InputStream in) throws IOException {
        charBuf.clear();
        return decodeFrom(in, charBuf);
    }

    public int decodeFrom(InputStream in, CharList to) throws IOException {
        ByteList ob = byteBuf;
        ob.ensureCapacity(4096);
        byte[] arr = ob.list;

        int i = 0, read = 0;
        do {
            i = in.read(arr, i, arr.length - i);
            if (i < 0) break;
            read += i;

            ob.wIndex(i);
            int i1 = ByteList.decodeUTFPartial(0, i, to, ob);
            if (i == arr.length) {
                in.close();
                if (i1 < i) throw new UTFDataFormatException();
                break;
            }
            System.arraycopy(arr, i1, arr, 0, i - i1);
            i = i1;
        } while (true);

        in.close();
        return read;
    }

    public ByteList wrap(byte[] b) {
        return shell.setArray(b);
    }
}
