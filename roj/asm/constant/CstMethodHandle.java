/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantMethodHandle.java
 */
package roj.asm.constant;

import roj.util.ByteWriter;

public final class CstMethodHandle extends Constant {
    public byte kind;

    private int refIndex;
    private CstRef ref;

    public CstMethodHandle(byte kind, int refIndex) {
        super(CstType.METHOD_HANDLE);
        this.kind = kind;
        this.refIndex = refIndex;
    }

    @Override
    protected final void write0(ByteWriter w) {
        w.writeByte(kind);
        w.writeShort(getRefIndex());
    }

    public final String toString() {
        return super.toString() + " Type: " + kind + ", Method: " + ref;
    }

    public final int hashCode() {
        return ref.hashCode() << 4 ^ kind;
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
}