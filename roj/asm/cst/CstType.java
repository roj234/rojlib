/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantType.java
 */
package roj.asm.cst;

public class CstType {
    public static final byte
            UTF = 1,
            INT = 3,
            FLOAT = 4,
            LONG = 5,
            DOUBLE = 6,
            CLASS = 7,
            STRING = 8,
            FIELD = 9,
            METHOD = 10,
            INTERFACE = 11,
            NAME_AND_TYPE = 12,
            METHOD_HANDLE = 15,
            METHOD_TYPE = 16,
            DYNAMIC = 17,
            INVOKE_DYNAMIC = 18,
            MODULE = 19,
            PACKAGE = 20;
    public static final byte DOUBLE_LONG_HOLDER = 127;

    public static final String[] indexes = {
            "UTF", null, "INT", "FLOAT", "LONG", "DOUBLE", "CLASS", "STRING",
            "FIELD", "METHOD", "INTERFACE", "NAME_AND_TYPE", null, null,
            "METHOD_HANDLE", "METHOD_TYPE", "DYNAMIC", "INVOKE_DYNAMIC",
            "MODULE", "PACKAGE"
    };

    public static String byId(int id) {
        return id > 20 ? null : indexes[id - 1];
    }
}