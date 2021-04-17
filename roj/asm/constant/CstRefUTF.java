/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantString.java
 */
package roj.asm.constant;

import roj.util.ByteWriter;

public abstract class CstRefUTF extends Constant {
    public static final boolean NULL_CHECK = true;

    private int valueIndex;
    private CstUTF value;

    public CstRefUTF(byte type, int valueIndex) {
        super(type);
        this.valueIndex = valueIndex;
    }

    public CstRefUTF(byte type) {
        super(type);
    }

    public CstUTF getValue() {
        return value;
    }

    public final void setValue(CstUTF value) {
        if (value == null) {
            if (NULL_CHECK)
                throw new NullPointerException("value");
            else {
                System.err.println("Warning: Value is null and ignored!");
                this.value = null;
                this.valueIndex = 0;
                return;
            }
        }
        this.value = value;
        this.valueIndex = value.getIndex();
    }

    @Override
    protected final void write0(ByteWriter w) {
        w.writeShort(getValueIndex());
    }

    public final String toString() {
        return super.toString() + " : " + (value == null ? valueIndex : value.getString());
    }

    public final int hashCode() {
        return (value == null ? 0 : value.hashCode()) ^ type;
    }

    public final boolean equals(Object o) {
        return o instanceof CstRefUTF && equals0((CstRefUTF) o);
    }

    public final boolean equals0(CstRefUTF o) {
        if (o == this)
            return true;
        if (o.getClass() != getClass())
            return false;
        return getValueIndex() == o.getValueIndex();
    }

    public final int getValueIndex() {
        return value == null ? valueIndex : value.getIndex();
    }
}