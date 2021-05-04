package roj.kscript.type;

import roj.concurrent.OperationDone;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Type.java
 */
public enum Type {
    ARRAY, OBJECT, STRING, INT, NULL, BOOL, DOUBLE, FUNCTION, UNDEFINED, ERROR, JAVA_OBJECT;

    public static final Type[] VALUES = values();

    public String typeof() {
        switch (this) {
            case UNDEFINED:
                return "undefined";
            case BOOL:
                return "boolean";
            case DOUBLE:
            case INT:
                return "number";
            case JAVA_OBJECT:
            case OBJECT:
            case ARRAY:
            case NULL:
            case ERROR:
                return "object";
            case STRING:
                return "string";
            case FUNCTION:
                return "function";
        }
        throw OperationDone.NEVER;
    }

}
