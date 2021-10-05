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
public abstract class CstRef extends Constant {
    private char classIndex, descIndex;

    private CstClass clazz;
    private CstNameAndType desc;

    CstRef(int classIndex, int descIndex) {
        this.classIndex = (char) classIndex;
        this.descIndex = (char) descIndex;
    }

    CstRef() {}

    @Override
    public final void write(ByteWriter w) {
        w.writeByte(type())
         .writeShort(getClassIndex())
         .writeShort(getDescIndex());
    }

    public final String toString() {
        return super.toString() + ' ' + (clazz == null ? classIndex : clazz.getValue().getString()) + '.' + (desc == null ? descIndex : (desc.getName().getString() + ':' + desc.getType().getString()));
    }

    public final String getClassName() {
        return clazz.getValue().getString();
    }

    public final void setClazz(CstClass clazz) {
        if (clazz == null) {
            throw new NullPointerException("clazz");
        }
        this.clazz = clazz;
        this.classIndex = (char) clazz.getIndex();
    }

    public final void desc(CstNameAndType desc) {
        if (desc == null) {
            throw new NullPointerException("desc");
        }
        this.desc = desc;
        this.descIndex = (char) desc.getIndex();
    }

    public final int hashCode() {
        return (clazz.hashCode() << 16) ^ desc.hashCode() ^ getClass().hashCode();
    }

    public final boolean equals(Object o) {
        return o instanceof CstRef && equals0((CstRef) o);
    }

    public final boolean equals0(CstRef ref) {
        if (ref == this)
            return true;
        if (ref.getClass() != getClass())
            return false;
        return ref.getClassIndex() == getClassIndex() && ref.getDescIndex() == getDescIndex();
    }

    public final int getClassIndex() {
        return clazz == null ? classIndex : clazz.getIndex();
    }

    public final int getDescIndex() {
        return desc == null ? descIndex : desc.getIndex();
    }

    public CstClass getClazz() {
        return clazz;
    }

    public CstNameAndType desc() {
        return desc;
    }
}