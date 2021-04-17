package roj.asm.util.frame;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: ArrayType.java
 */
public enum ArrayType {
    T_BOOLEAN(4, 'Z'), T_CHAR(5, 'C'), T_FLOAT(6, 'F'), T_DOUBLE(7, 'D'), T_BYTE(8, 'B'), T_SHORT(9, 'S'), T_INT(10, 'I'), T_LONG(11, 'J');

    private static final ArrayType[] byId = new ArrayType[8];

    public static byte byId(byte id) {
        return byId[id - 4].desc;
    }

    static {
        for (ArrayType type : values()) {
            byId[type.id - 4] = type;
        }
    }

    public final byte id, desc;

    ArrayType(int id, int desc) {
        this.id = (byte) id;
        this.desc = (byte) desc;
    }
}
