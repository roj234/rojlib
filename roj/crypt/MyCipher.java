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

import roj.text.TextUtil;

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

    @SuppressWarnings("fallthrough")
    public Object backup() {
        switch (type) {
            default:
            case MODE_ECB:
            case MODE_CBC:
                return null;
            case MODE_PCBC:
                return iv.array().clone();
            case MODE_OFB:
                return new Object[] { iv.array().clone(), iv.position() };
            case MODE_CFB:
            case MODE_CTR:
                byte[] b0 = iv.array().clone();
                byte[] b1 = t0.array().clone();
                return new Object[] { b0, b1, ((((long) iv.position()) << 32) | t0.position()) };
        }
    }

    @SuppressWarnings("fallthrough")
    public void restore(Object data) {
        switch (type) {
            default:
            case MODE_ECB:
            case MODE_CBC:
                break;
            case MODE_PCBC:
                System.arraycopy(data, 0, iv.array(), 0, iv.capacity());
                break;
            case MODE_OFB:
                Object[] arr = (Object[]) data;
                System.arraycopy(arr[0], 0, iv.array(), 0, iv.capacity());
                iv.position((Integer) arr[1]);
                break;
            case MODE_CFB:
            case MODE_CTR:
                arr = (Object[]) data;
                System.arraycopy(arr[0], 0, iv.array(), 0, iv.capacity());
                System.arraycopy(arr[1], 0, t0.array(), 0, t0.capacity());
                long aLong = (Long) arr[2];
                iv.position((int) (aLong >> 32));
                t0.position((int) aLong);
        }
    }

    @Override
    public int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
        if (out.remaining() < in.remaining()) return BUFFER_OVERFLOW;

        int blockSize = iv.capacity();
        if ((mode & (DECRYPT | PKCS5_PADDING)) == PKCS5_PADDING)
            PKCS5(in, false, blockSize);

        switch (type) {
            case MODE_ECB: // Electronic Cipher Book
            case MODE_CBC: // Cipher Block Chaining
            case MODE_PCBC: // Plain Cipher Block Chaining
                blockCipher(in, out);
                break;
            default:
                if((blockSize & 3) != 0 || !try4(in, out))
                    try1(in, out, in.remaining());
        }

        if ((mode & (DECRYPT | PKCS5_PADDING)) == (DECRYPT | PKCS5_PADDING))
            PKCS5(out, true, blockSize);

        return OK;
    }

    private void blockCipher(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
        ByteBuffer b0 = this.iv;
        ByteBuffer b1 = this.t0;
        int blockSize = b0.capacity();

        int cyl = in.remaining() / blockSize;

        switch (type) {
            case MODE_ECB:
                while (cyl-- > 0) cip.crypt(in, out);
                break;
            case MODE_CBC: {
                byte[] bb0 = b0.array();
                byte[] bb1 = b1.array();
                while (cyl-- > 0) {
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
            case MODE_PCBC: {
                byte[] bb0 = b0.array();
                if ((mode & DECRYPT) == 0) {
                    while (cyl-- > 0) {
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
                    while (cyl-- > 0) {
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
        }
    }

    private void try1(ByteBuffer in, ByteBuffer out, int cyl) throws GeneralSecurityException {
        ByteBuffer b0 = this.iv;
        ByteBuffer b1 = this.t0;

        switch (type) {
            case MODE_OFB: { // Output Feedback
                while (cyl-- > 0) {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        cip.crypt(b0, b1);

                        b0.position(0);
                        b1.position(0);

                        System.arraycopy(b1.array(), 0, b0.array(), 0, b0.capacity());
                    }
                    out.put((byte) (in.get() ^ b0.get()));
                }
            }
            break;
            case MODE_CFB: { // Cipher Feedback
                boolean ENC = (mode & DECRYPT) == 0;
                while (cyl-- > 0) {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        b1.position(0);
                        cip.crypt(b1, b0);
                        b1.position(0);
                        b0.position(0);
                    }
                    byte b = in.get();
                    if (ENC) b1.put(b);
                    b ^= b0.get();
                    if (!ENC) b1.put(b);
                    out.put(b);
                }
            }
            break;
            case MODE_CTR: {// Counter
                byte[] ctr = b1.array();
                while (cyl-- > 0) {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        b1.position(0);
                        cip.crypt(b1, b0);
                        for (int i = 0; i < ctr.length; i++) {
                            if (ctr[i]++ != 0) break;
                        }
                        b0.position(0);
                    }
                    out.put((byte) (in.get() ^ b0.get()));
                }
            }
            break;
        }
    }

    private boolean try4(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException {
        ByteBuffer b0 = this.iv;
        ByteBuffer b1 = this.t0;

        if (in.remaining() < 4) return false;
        if ((b0.position() & 3) != 0) {
            try1(in, out, 4 - (b0.position() & 3));
            // integer align
        }
        int cyl = in.remaining() >> 2;
        if (cyl == 0) return false;

        switch (type) {
            case MODE_OFB:
                do {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        cip.crypt(b0, b1);

                        b0.position(0);
                        b1.position(0);

                        System.arraycopy(b1.array(), 0, b0.array(), 0, b0.capacity());
                    }
                    out.putInt(in.getInt() ^ b0.getInt());
                } while (--cyl > 0);
            break;
            case MODE_CFB: {
                boolean ENC = (mode & DECRYPT) == 0;
                if (ENC) {
                    do {
                        if (!b0.hasRemaining()) {
                            b0.position(0);
                            b1.position(0);
                            cip.crypt(b1, b0);
                            b1.position(0);
                            b0.position(0);
                        }
                        int b = in.getInt();
                        b1.putInt(b);
                        out.putInt(b ^ b0.getInt());
                    } while (--cyl > 0);
                } else {
                    do {
                        if (!b0.hasRemaining()) {
                            b0.position(0);
                            b1.position(0);
                            cip.crypt(b1, b0);
                            b1.position(0);
                            b0.position(0);
                        }
                        int b = in.getInt() ^ b0.getInt();
                        b1.putInt(b);
                        out.putInt(b);
                    } while (--cyl > 0);
                }
            }
            break;
            case MODE_CTR: {
                byte[] counter = b1.array();
                do {
                    if (!b0.hasRemaining()) {
                        b0.position(0);
                        b1.position(0);
                        cip.crypt(b1, b0);
                        for (int i = 0; i < counter.length; i++) {
                            if (counter[i]++ != 0) break;
                        }
                        b0.position(0);
                    }
                    out.putInt(in.getInt() ^ b0.getInt());
                } while (--cyl > 0);
            }
            break;
        }

        return !in.hasRemaining();
    }

    @Override
    public String toString() {
        return "MyCipher{" +
                "cip=" + cip +
                ", iv=" + TextUtil.dumpBytes(iv.array()) +
                ", t0=" + TextUtil.dumpBytes(t0.array()) +
                ", type=" + type +
                ", mode=" + mode + '}';
    }
}
