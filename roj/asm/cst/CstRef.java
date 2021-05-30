/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantReference.java
 */
package roj.asm.cst;

import roj.util.ByteWriter;

public abstract class CstRef extends Constant {
    private short classIndex, descIndex;
    private final byte type;

    private CstClass clazz;
    private CstNameAndType desc;

    CstRef(byte type, int classIndex, int descIndex) {
        this(type);
        this.classIndex = (short) classIndex;
        this.descIndex = (short) descIndex;
    }

    CstRef(byte type) {
        this.type = type;
    }

    @Override
    public final byte type() {
        return type;
    }

    @Override
    protected final void write0(ByteWriter writer) {
        writer.writeShort(getClassIndex())
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
        this.classIndex = (short) clazz.getIndex();
    }

    public final void desc(CstNameAndType desc) {
        if (desc == null) {
            throw new NullPointerException("desc");
        }
        this.desc = desc;
        this.descIndex = (short) desc.getIndex();
    }

    public final int hashCode() {
        return clazz.hashCode() << 16 ^ desc.hashCode() ^ getClass().hashCode();
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
        return clazz == null ? classIndex & 0xFFFF : clazz.getIndex();
    }

    public final int getDescIndex() {
        return desc == null ? descIndex & 0xFFFF : desc.getIndex();
    }

    public CstClass getClazz() {
        return clazz;
    }

    public CstNameAndType desc() {
        return desc;
    }
}