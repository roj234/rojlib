/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantNameAndType.java
 */
package roj.asm.cst;

import roj.util.ByteWriter;

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
    protected final void write0(ByteWriter w) {
        w.writeShort(getNameIndex());
        w.writeShort(getTypeIndex());
    }

    public final String toString() {
        return super.toString() + " " + (name == null ? nameIndex : name.getString()) + ':' + (type == null ? typeIndex : type.getString());
    }


    public final int hashCode() {
        return name.hashCode() << 16 ^ type.hashCode();
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
}