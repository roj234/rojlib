package roj.text;

import roj.util.ByteList;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UTFDataFormatException;

/**
 * @author solo6975
 * @since 2022/1/15 16:01
 */
public class UTFCoder {
    public final CharList charBuf = new CharList();
    public final ByteList byteBuf = new ByteList();

    public byte[] encode(CharSequence cs) {
        byteBuf.clear();
        ByteList.writeUTF(byteBuf, cs, -1);
        return byteBuf.toByteArray();
    }

    public byte[] encode() {
        byteBuf.clear();
        ByteList.writeUTF(byteBuf, charBuf, -1);
        charBuf.clear();
        return byteBuf.toByteArray();
    }

    public ByteList encodeR() {
        byteBuf.clear();
        ByteList.writeUTF(byteBuf, charBuf, -1);
        charBuf.clear();
        return byteBuf;
    }

    public ByteList encodeR(CharSequence cs) {
        byteBuf.clear();
        ByteList.writeUTF(byteBuf, cs, -1);
        return byteBuf;
    }

    public String decode(ByteList b) {
        charBuf.clear();
        try {
            ByteList.decodeUTF(b.wIndex(), charBuf, b);
        } catch (UTFDataFormatException ignored) {}
        return charBuf.toString();
    }

    public String decode() {
        charBuf.clear();
        try {
            ByteList.decodeUTF(byteBuf.wIndex(), charBuf, byteBuf);
        } catch (UTFDataFormatException ignored) {}
        byteBuf.clear();
        return charBuf.toString();
    }

    public String decode(byte[] b) {
        charBuf.clear();
        try {
            ByteList.decodeUTF(b.length, charBuf, new ByteList(b));
        } catch (UTFDataFormatException e) {
            return "";
        }
        return charBuf.toString();
    }

    public CharList decodeR() {
        charBuf.clear();
        try {
            ByteList.decodeUTF(byteBuf.wIndex(), charBuf, byteBuf);
        } catch (UTFDataFormatException ignored) {}
        byteBuf.clear();
        return charBuf;
    }

    public CharList decodeR(ByteList b) {
        charBuf.clear();
        try {
            ByteList.decodeUTF(b.wIndex(), charBuf, b);
        } catch (UTFDataFormatException ignored) {}
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
            for (int j = 0; j < len; j++) {
                int c = str.charAt(j);
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
            i += len;
        }
        return wrote;
    }
}
