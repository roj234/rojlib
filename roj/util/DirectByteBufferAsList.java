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
package roj.util;

import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/11/29 22:07
 */
public class DirectByteBufferAsList extends ByteList {
    ByteBuffer buffer;
    public DirectByteBufferAsList(ByteBuffer buffer) {
        this.buffer = buffer;
        this.list = null;
    }

    @Override
    public void ensureCapacity(int required) {
        throw new IllegalStateException();
    }

    @Override
    public byte get(int index) {
        return buffer.get(index);
    }

    @Override
    public int getU(int index) {
        return buffer.get(index) & 0xFF;
    }

    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public int pos() {
        return buffer.position();
    }

    @Override
    public void pos(int id) {
        buffer.position(id);
    }

    @Override
    public void clear() {
        buffer.position(0);
        writePtr = 0;
    }

    @Override
    public void putInto(ByteBuffer target, int max) {
        int v = Math.min(buffer.position() - writePtr, max);
        if (v <= 0)
            return;
        ByteBuffer copy = buffer.duplicate();
        copy.position(0).limit(v);
        target.put(copy);
        writePtr += v;
    }

    @Override
    public void set(int index, byte e) {
        buffer.put(index, e);
    }

    @Override
    public String toString() {
        return buffer.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ByteList)) return false;

        ByteList list = (ByteList) o;
        if (list.pos() != pos())
            return false;
        for (int i = 0; i < pos(); i++) {
            if (list.get(i) != get(i)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return buffer.duplicate().flip().hashCode();
    }

    @Override
    public void add(byte e) {
        buffer.put(e);
    }

    @Override
    public void addAll(byte[] array, int start, int length) {
        buffer.put(array, start, length);
    }
}
