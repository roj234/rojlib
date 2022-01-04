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

import roj.util.ByteList;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class CstNameAndType extends Constant {
    private short nameIndex, typeIndex;

    private CstUTF name, type;

    public CstNameAndType() {}

    public CstNameAndType(int nameIndex, int typeIndex) {
        this.nameIndex = (short) nameIndex;
        this.typeIndex = (short) typeIndex;
    }

    @Override
    public byte type() {
        return CstType.NAME_AND_TYPE;
    }

    @Override
    public final void write(ByteList w) {
        w.put(CstType.NAME_AND_TYPE)
         .putShort(getNameIndex())
         .putShort(getTypeIndex());
    }

    public final String toString() {
        return super.toString() + " " + (name == null ? nameIndex : name.getString() + " (" + name.getIndex() + ")") + ':' + (type == null ? typeIndex : type.getString() + " (" + type.getIndex() + ")");
    }


    public final int hashCode() {
        return (name.hashCode() << 16) ^ type.hashCode();
    }


    public final boolean equals(Object o) {
        return o instanceof CstNameAndType && equals0((CstNameAndType) o);
    }

    public final boolean equals0(CstNameAndType ref) {
        if (ref == this)
            return true;
        return ref.getNameIndex() == getNameIndex() && ref.getTypeIndex() == getTypeIndex();
    }

    public final int getNameIndex() {
        return name == null ? nameIndex & 0xFFFF : name.getIndex();
    }

    public final int getTypeIndex() {
        return type == null ? typeIndex & 0xFFFF : type.getIndex();
    }

    public final CstUTF getName() {
        return name;
    }

    public final void setName(CstUTF name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        this.name = name;
        this.nameIndex = (short) name.getIndex();
    }

    public final CstUTF getType() {
        return type;
    }

    public final void setType(CstUTF type) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        this.type = type;
        this.typeIndex = (short) type.getIndex();
    }

    @Override
    public final CstNameAndType clone() {
        CstNameAndType slf = (CstNameAndType) super.clone();
        if (name != null)
            slf.name = (CstUTF) name.clone();
        if (type != null)
            slf.type = (CstUTF) type.clone();
        return slf;
    }
}