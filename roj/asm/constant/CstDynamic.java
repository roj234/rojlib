/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantInvokeDynamic.java
 */
package roj.asm.constant;

import roj.util.ByteWriter;

public final class CstDynamic extends Constant {
    public int bootstrapTableIndex;
    private int descIndex;

    private CstNameAndType desc;

    public CstDynamic(boolean method, int tableIndex, int descIndex) {
        super(method ? CstType.INVOKE_DYNAMIC : CstType.DYNAMIC);
        this.bootstrapTableIndex = tableIndex;
        this.descIndex = descIndex;
    }

    public final void setDesc(CstNameAndType desc) {
        if (desc == null)
            throw new NullPointerException("desc");
        this.desc = desc;
        this.descIndex = desc.getIndex();
    }

    @Override
    protected final void write0(ByteWriter w) {
        w.writeShort(bootstrapTableIndex);
        w.writeShort(getDescIndex());
    }

    public final String toString() {
        return super.toString() + "BTableIdx: #" + bootstrapTableIndex + ", //" + desc + "]";
    }

    public final CstNameAndType getDesc() {
        return desc;
    }

    public final int getDescIndex() {
        return desc == null ? descIndex : desc.getIndex();
    }

    public final int hashCode() {
        return (desc.hashCode() << 16 ^ bootstrapTableIndex) + type;
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null || o.getClass() != getClass())
            return false;
        CstDynamic ref = (CstDynamic) o;
        return ref.type == this.type && ref.bootstrapTableIndex == this.bootstrapTableIndex && ref.getDescIndex() == this.getDescIndex();
    }
}