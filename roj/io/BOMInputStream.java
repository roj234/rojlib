package roj.io;

import javax.annotation.Nonnull;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 http://www.unicode.org/unicode/faq/utf_bom.html

 00 00 FE FF    = UTF-32, big-endian
 FF FE 00 00    = UTF-32, little-endian
 EF BB BF       = UTF-8,
 FE FF          = UTF-16, big-endian
 FF FE          = UTF-16, little-endian
 * @author solo6975
 * @since 2021/3/7 11:58
 */

public class BOMInputStream extends FilterInputStream {
    String encoding;

    byte[] bomBuffer;
    int bomPush = -1, bomOff = 0;

    public BOMInputStream(InputStream in) {
        this(in, "UTF-8");
    }

    public BOMInputStream(InputStream in, String defaultEnc) {
        super(in);
        this.encoding = defaultEnc;
        bomBuffer = new byte[4];
    }

    public BOMInputStream(InputStream in, String defaultEnc, boolean autoInit) throws IOException {
        this(in, defaultEnc);
        if(autoInit)
            init();
    }

    public String getEncoding() throws IOException {
        init();
        return encoding;
    }

    @Override
    public int available() throws IOException {
        init();
        return super.available() + 4 - bomPush;
    }

    /**
     * Skip BOM bytes
     */
    protected void init() throws IOException {
        if (bomPush != -1) {
            return;
        }

        byte[] bom = bomBuffer;
        int n = in.read(bom, 0, bom.length);

        int rev = 0;

        switch (bom[0] & 0xFF) {
            case 0x00:
                if ((bom[1] == (byte)0x00) && (bom[2] == (byte)0xFE) && (bom[3] == (byte)0xFF)) {
                    encoding = "UTF-32BE";
                    rev = 4;
                }
            break;

            case 0xFF:
                if (bom[1] == (byte)0xFE) {
                    if((bom[2] == (byte)0x00) && (bom[3] == (byte)0x00)) {
                        encoding = "UTF-32LE";
                        rev = 4;
                    } else {
                        encoding = "UTF-16LE";
                        rev = 2;
                    }
                }
            break;

            case 0xEF:
                if ((bom[1] == (byte)0xBB) && (bom[2] == (byte)0xBF)) {
                    encoding = "UTF-8";
                    rev = 3;
                }
            break;

            case 0xFE:
                if ((bom[1] == (byte)0xFF)) {
                    encoding = "UTF-16BE";
                    rev = 2;
                }
            break;
        }

        bomPush = rev;
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) throws IOException {
        init();

        int k = bomPush;
        if(k < 4) {
            if (len < k) {
                bomPush += len;

                while (len-- > 0) {
                    b[off++] = bomBuffer[k++];
                }

                return len;
            } else {
                bomPush = 4;

                int r = 4 - k;
                len -= r;
                while (k < 4) {
                    b[off++] = bomBuffer[k++];
                }
                return super.read(b, off, len) + r;
            }
        } else {
            return super.read(b, off, len);
        }
    }

    @Override
    public int read() throws IOException {
        init();

        if(bomPush < 4) {
            return bomBuffer[bomPush++];
        } else {
            return super.read();
        }
    }
}