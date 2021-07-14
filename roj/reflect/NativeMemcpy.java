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
package roj.reflect;

/**
 * Native array copy util
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/17 19:54
 */
public abstract class NativeMemcpy {
    public static final NativeMemcpy instance;

    static {
        NativeMemcpy i;
        try {
            i = DirectMethodAccess.getStatic(NativeMemcpy.class, new String[] {
                    "copyFromArray", "copyToArray"
            }, Class.forName("java.nio.Bits"), new String[] {
                    "copyFromArray", "copyToArray"
            });
        } catch (ClassNotFoundException e) {
            i = null;
        }
        instance = i;
    }

    /**
     * Copy from given source array to destination address.
     *
     * @param   src
     *          source array
     * @param   srcBaseOffset
     *          offset of first element of storage in source array
     * @param   srcPos
     *          offset within source array of the first element to read
     * @param   dstAddr
     *          destination address
     * @param   length
     *          number of bytes to copy
     */
    public abstract void copyFromArray(Object src, long srcBaseOffset, long srcPos, long dstAddr, long length);

    /**
     * Copy from source address into given destination array.
     *
     * @param   srcAddr
     *          source address
     * @param   dst
     *          destination array
     * @param   dstBaseOffset
     *          offset of first element of storage in destination array
     * @param   dstPos
     *          offset within destination array of the first element to write
     * @param   length
     *          number of bytes to copy
     */
    public abstract void copyToArray(long srcAddr, Object dst, long dstBaseOffset, long dstPos, long length);
}
