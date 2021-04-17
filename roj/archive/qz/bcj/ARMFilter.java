package roj.archive.qz.bcj;

/**
 * BCJ filter for little endian ARM instructions.
 * @author Lasse Collin <lasse.collin@tukaani.org>, Roj234
 * @since 2023/3/16 0016 2:22
 */
public class ARMFilter extends Filter {
    public ARMFilter(boolean isEncoder, int startPos) {
        super(isEncoder);
        pos = checkStartOffset(startPos, 4) + 8;
    }

    public int filter(byte[] buf, int off, int len) {
        int end = off + len - 4;
        int i;

        for (i = off; i <= end; i += 4) {
            if ((buf[i + 3] & 0xFF) == 0xEB) {
                int src = ((buf[i + 2] & 0xFF) << 16)
                    | ((buf[i + 1] & 0xFF) << 8)
                    | (buf[i] & 0xFF);
                src <<= 2;

                int addr;
                if (encode) addr = src + (pos + i - off);
                else addr = src - (pos + i - off);

                addr >>>= 2;
                buf[i + 2] = (byte)(addr >>> 16);
                buf[i + 1] = (byte)(addr >>> 8);
                buf[i] = (byte)addr;
            }
        }

        i -= off;
        pos += i;
        return i;
    }
}
