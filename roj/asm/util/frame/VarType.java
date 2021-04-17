package roj.asm.util.frame;

import roj.asm.util.type.NativeType;
import roj.asm.util.type.Type;
import roj.concurrent.OperationDone;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: VarType.java.java
 */
public final class VarType {
    public static final byte TOP = 0,
            INT = 1,
            FLOAT = 2,
            DOUBLE = 3,
            LONG = 4,
            NULL = 5,
            UNINITIAL_THIS = 6,
            REFERENCE = 7,
            UNINITIAL = 8;

    public static int ofType(Type type) {
        if(type.array > 0)
            return -3;
        switch (type.type) {
            case NativeType.VOID:
                return -1;
            case NativeType.BOOLEAN:
            case NativeType.BYTE:
            case NativeType.CHAR:
            case NativeType.SHORT:
            case NativeType.INT:
                return 1;
            case NativeType.FLOAT:
                return 2;
            case NativeType.DOUBLE:
                return 3;
            case NativeType.LONG:
                return 4;
            case NativeType.CLASS:
                return -2;
            case NativeType.ARRAY:
                return -3;
        }
        throw OperationDone.NEVER;
    }

    static final String[] toString = {
            "top", "int", "float", "double", "long", "null", "uninitial_this", "object", "uninitial"
    };

    public static String toString(byte type) {
        return toString[type];
    }

    public static int validate(byte b) {
        if ((b & 0xFF) > 8) {
            throw new IllegalArgumentException("Unsupported verification type " + b);
        }
        return (b & 0xFF);
    }
}
