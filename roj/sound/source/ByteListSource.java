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
package roj.sound.source;

import roj.util.ByteList;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/18 13:36
 */
public class ByteListSource implements Source {
    private final byte[] array;
    private final int offset, length;
    private int index;

    public ByteListSource(byte[] arr) {
        this.array = arr;
        this.offset = 0;
        this.length = arr.length;
    }

    public ByteListSource(ByteList bytes) {
        this.array = bytes.list;
        this.offset = bytes.offset();
        this.length = bytes.pos();
    }

    @Override
    public void seek(long source) {
        this.index = (int) source;
    }

    @Override
    public int read(byte[] array, int offset, int length) {
        System.arraycopy(array, index + this.offset, array, offset, length = Math.min(length, this.length - index - this.offset));
        index += length;
        return length;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public void close() {
        //array = null;
    }
}
