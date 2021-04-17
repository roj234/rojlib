/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Constant.java
 */
package roj.asm.constant;

import roj.annotation.Internal;
import roj.util.ByteWriter;

public abstract class Constant {
    public final byte type;
    private int index;

    Constant(byte type) {
        this.type = type;
        if (CstType.byId(type) == null)
            throw new IllegalArgumentException("Illegal id " + type);
    }

    public final void write(ByteWriter writer) {
        write0(writer.writeByte(type));
    }

    protected abstract void write0(ByteWriter writer);

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    public String toString() {
        return type + "#" + index;
    }

    @Internal
    public void setIndex(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}