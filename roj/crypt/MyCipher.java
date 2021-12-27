/*
 * This file is a part of MoreItems
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
package roj.crypt;

import javax.crypto.BadPaddingException;
import javax.crypto.ShortBufferException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * 让ECB模式的块密码支持流式OFB/CFB/CTR和CBC/PCBC <br>
 *     简而言之，省的以后弄<br>
 *     此外，你会发现它可以套娃
 * @author Roj233
 * @version 0.1
 * @since 2021/12/27 21:35
 */
public class MyCipher implements CipheR, DeCipher {
    public static final String MODE = "MC_MODE", IV = "IV";
    public static final int MODE_ECB = 0, MODE_CBC = 1, MODE_PCBC = 2, MODE_OFB = 3, MODE_CFB = 4, MODE_CTR = 5;

    public static final int PKCS5_PADDING = 8;

    public final DeCipher cip;

    private final ByteBuffer iv, t0;

    private byte type, mode;

    public MyCipher(DeCipher cip) {
        this.cip = cip;
        this.iv = ByteBuffer.allocate(cip.getBlockSize());
        this.t0 = ByteBuffer.allocate(cip.getBlockSize());
    }

    public MyCipher(DeCipher cip, int i) {
        this(cip);
        type = (byte) i;
    }

    @Override
    public String name() {
        return cip.name();
    }

    @Override
    public void setKey(byte[] key, int cryptFlags) {
        this.mode = (byte) cryptFlags;
        switch (type) {
            case MODE_OFB:
            case MODE_CFB:
            case MODE_CTR:
                cryptFlags &= ~ DECRYPT;
                break;
        }
        cip.setKey(key, cryptFlags);
        resetIV();
    }

    public void resetIV() {
        iv.clear();
        try {
            cip.crypt(ByteBuffer.wrap(new byte[cip.getBlockSize()]), iv);
        } catch (GeneralSecurityException ignored) {}
        iv.position(0);
        t0.position(0);
    }

    @Override
    public void setOption(String key, Object value) {
        switch (key) {
            case MODE:
                type = ((Number) value).byteValue();
                break;
            case IV:
                System.arraycopy(iv.array(), 0, value, 0, iv.capacity());
                iv.position(0);
                t0.position(0);
                break;
            default:
                cip.setOption(key, value);
                break;
        }
    }

    @Override
    public int getBlockSize() {
        return cip.getBlockSize();
    }

    public static void PKCS5(ByteBuffer buf, boolean unpad, int block) throws GeneralSecurityException {
        if (unpad) {
            byte last = buf.get(buf.position() - 1);
            int num = last & 0xff;
            if (num <= 0 || num > block)
                throw new BadPaddingException();

            int start = buf.position() - num;
            if (start < 0)
                throw new BadPaddingException();

            for (int i = buf.position() - 2; i >= start; i--) {
                if (buf.get(i) != last)
                    throw new BadPaddingException();
            }
            buf.position(buf.position() - num);
        } else {
            int len = block - buf.limit() % block;

            int lim = buf.limit();
            if (buf.capacity() < lim + len)
                throw new ShortBufferException("Unable pad, more: " + (len + lim - buf.capacity()));
            byte num = (byte) len;
            buf.limit(lim + len);
            while (len-- > 0) buf.put(lim++, num);
        }
    }

    public byte[][] backup() {
        return new byte[][] { iv.array().clone(), t0.array().clone() };
    }

    public void restore(byte[][] data) {
        iv.position(0);
        iv.put(data[0]);
        iv.position(0);
        t0.position(0);
        t0.put(data[1]);
        t0.position(0);
    }

    @Override
    public int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
        if (out.remaining() < in.remaining()) return BUFFER_OVERFLOW;

        ByteBuffer b0 = this.iv;
        ByteBuffer b1 = this.t0;
        int blockSize = b0.capacity();

        if ((mode & (DECRYPT | PKCS5_PADDING)) == PKCS5_PADDING)
            PKCS5(in, false, blockSize);

        switch (type) {
            case MODE_ECB: // Electronic Cipher Book
                while (in.hasRemaining()) cip.crypt(in, out);
                break;
            case MODE_CBC: { // Cipher Block Chaining
                int k = in.remaining() / blockSize;
                byte[] bb0 = b0.array();
                byte[] bb1 = b1.array();
                while (k-- > 0) {
                    for (int i = 0; i < blockSize; i++) {
                        bb1[i] = (byte) (in.get() ^ bb0[i]);
                    }
                    b1.position(0);
                    cip.crypt(b1, out);
                    out.position(out.position() - blockSize);
                    out.get(bb0);
                }
            }
            break;
            case MODE_PCBC: { // Plain Cipher Block Chaining
                int k = in.remaining() / blockSize;
                byte[] bb0 = b0.array();
                if ((mode & DECRYPT) == 0) {
                    while (k-- > 0) {
                        for (int i = 0; i < blockSize; i++) {
                            bb0[i] ^= in.get();
                        }

                        b0.position(0);
                        cip.crypt(b0, out);

                        out.position(out.position() - blockSize);

                        in.position(in.position() - blockSize);
                        in.get(bb0);

                        for (int i = 0; i < blockSize; i++) {
                            bb0[i] ^= out.get();
                        }
                    }
                } else {
                    while (k-- > 0) {
                        cip.crypt(in, out);
                        in.position(in.position() - blockSize);
                        int p = out.position() - blockSize;
                        for (int i = 0; i < blockSize; i++) {
                            byte b = (byte) (out.get(p + i) ^ bb0[i]);
                            out.put(p + i, b);
                            bb0[i] = (byte) (b ^ in.get());
                        }
                    }
                }
            }
            break;
            case MODE_OFB: { // Output Feedback
                while (in.hasRemaining()) {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        cip.crypt(b0, b1);

                        b0.position(0);
                        b1.position(0);

                        System.arraycopy(b0.array(), 0, b1.array(), 0, blockSize);
                    }
                    out.put((byte) (in.get() ^ b0.get()));
                }
            }
            break;
            case MODE_CFB: { // Cipher Feedback
                ByteBuffer T = (mode & DECRYPT) == 0 ? b1 : in;
                int lim = T.limit();
                while (in.hasRemaining()) {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        T.limit(T.position())
                         .position(T.position() - blockSize);
                        cip.crypt(T, b0);
                        if (T == b1) T.position(0);
                        else T.limit(lim);
                        b0.position(0);
                    }
                    byte b = (byte) (in.get() ^ b0.get());
                    if (T == b1) b1.put(b);
                    out.put(b);
                }
            }
            break;
            case MODE_CTR: {// Counter
                byte[] counter = b1.array();
                while (in.hasRemaining()) {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        b1.position(0);
                        cip.crypt(b1, b0);
                        for (int i = 0; i < counter.length; i++) {
                            if (counter[i]++ != 0) break;
                        }
                        b0.position(0);
                    }
                    out.put((byte) (in.get() ^ b0.get()));
                }
            }
            break;
            //case 6:
            //    break;
        }

        if ((mode & (DECRYPT | PKCS5_PADDING)) == (DECRYPT | PKCS5_PADDING))
            PKCS5(out, true, blockSize);

        return OK;
    }
}
