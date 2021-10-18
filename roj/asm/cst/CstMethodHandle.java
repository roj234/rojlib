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

package roj.asm.cst;

import roj.util.ByteWriter;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class CstMethodHandle extends Constant {
    public byte kind;

    private int refIndex;
    private CstRef ref;

    public CstMethodHandle(byte kind, int refIndex) {
        this.kind = kind;
        this.refIndex = refIndex;
    }

    @Override
    public byte type() {
        return CstType.METHOD_HANDLE;
    }

    @Override
    public final void write(ByteWriter w) {
        w.writeByte(CstType.METHOD_HANDLE)
         .writeByte(kind)
         .writeShort(getRefIndex());
    }

    public final String toString() {
        return super.toString() + " Type: " + kind + ", Method: " + ref;
    }

    public final int hashCode() {
        return (ref.hashCode() << 3) | kind;
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof CstMethodHandle))
            return false;
        CstMethodHandle ref = (CstMethodHandle) o;
        return ref.kind == this.kind && ref.getRefIndex() == this.getRefIndex();
    }

    public final CstRef getRef() {
        return ref;
    }

    public void setRef(CstRef ref) {
        if (ref == null)
            throw new NullPointerException("ref");
        this.ref = ref;
        this.refIndex = ref.getIndex();
    }

    public int getRefIndex() {
        return ref == null ? refIndex : ref.getIndex();
    }

    @Override
    public final CstMethodHandle clone() {
        CstMethodHandle slf = (CstMethodHandle) super.clone();
        if (ref != null)
            slf.ref = ref.clone();
        return slf;
    }
}