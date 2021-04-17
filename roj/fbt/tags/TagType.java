package roj.fbt.tags;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: TagType.java
 */
public enum TagType {
    BYTE(1), UBYTE(1), SHORT(2), USHORT(2), CHAR(2), INT(4), UINT(4), LONG(8), DOUBLE(8), FLOAT(4), STRING(-1), BYTE_ARRAY(-1);

    private final byte length;

    TagType(int i) {
        this.length = (byte) i;
    }

    public byte getLength() {
        return length;
    }
}
