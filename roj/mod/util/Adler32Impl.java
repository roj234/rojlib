package roj.mod.util;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
 */
public class Adler32Impl {
    static final int MOD_ADLER = 65521;
    short a = 1, b = 0;

    public Adler32Impl read(byte[] arr) {
        for (int i = 0, arrLength = arr.length; i < arrLength;) {
            short bb;
            if(i == arrLength - 1) {
                bb = arr[i++];
            } else {
                bb = (short) ((arr[i++] & 0XFF) << 8 | (arr[i++] & 0xFF));
            }
            a = (short) ((a + bb) % MOD_ADLER);
            b = (short) ((b + a) % MOD_ADLER);
        }
        return this;
    }

    public Adler32Impl read(short[] arr) {
        for (short bb : arr) {
            a = (short) ((a + bb) % MOD_ADLER);
            b = (short) ((b + a) % MOD_ADLER);
        }
        return this;
    }

    @Deprecated
    public Adler32Impl read(short bb) {
        a = (short) ((a + bb) % MOD_ADLER);
        b = (short) ((b + a) % MOD_ADLER);
        return this;
    }

    public int result() {
        return (b & 65535) << 16 | (a & 65535);
    }

    public Adler32Impl reset() {
        this.a = 1;
        this.b = 0;
        return this;
    }
}
