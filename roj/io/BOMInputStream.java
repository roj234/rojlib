/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.io;

import javax.annotation.Nonnull;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 支持各种BOM的输入流
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/3/7 11:58
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