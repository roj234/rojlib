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

import roj.util.ByteList;

import javax.crypto.IllegalBlockSizeException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * @author Roj233
 * @since 2021/12/28 0:05
 */
public interface CipheR {
    int OK = 0, BUFFER_OVERFLOW = -1;
    int ENCRYPT = 0, DECRYPT = 1;

    String name();
    void setKey(byte[] key, int flags);
    default void setOption(String key, Object value) {}

    default boolean isBaseCipher() { return false; }
    /**
     * Zero: stream cipher
     */
    default int getBlockSize() { return 0; }
    default int getCryptSize(int data) { return data; }

    int crypt(ByteBuffer in, ByteBuffer out) throws GeneralSecurityException;
    default void crypt(ByteList in, ByteList out) throws GeneralSecurityException {
        int len = getCryptSize(in.wIndex());
        out.wIndex(out.wIndex() + len);
        len = crypt(ByteBuffer.wrap(in.list, in.arrayOffset(), in.wIndex()),
              ByteBuffer.wrap(out.list, out.wIndex(), len));
        if (len != OK) throw new IllegalBlockSizeException("Precondition Violation");
    }
}
