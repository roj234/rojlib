package roj.asm.cst;

import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * Double / Long holder (Top)
 *
 * @author Roj233
 * @since 2021/5/24 22:50
 */
public final class CstDoLHolder extends Constant {
    public static final Constant HOLDER = new CstDoLHolder();

    private CstDoLHolder() {}

    @Override
    protected void write0(ByteWriter writer) {
        throw new IllegalArgumentException("CstDoLHolder is a placeholder class.");
    }

    @Override
    public boolean equals(Object o) {
        return o == HOLDER;
    }

    @Override
    public int hashCode() {
        return 88888;
    }

    @Override
    public byte type() {
        return CstType.DOUBLE_LONG_HOLDER;
    }
}
