package roj.kscript.type;

import roj.concurrent.OperationDone;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: Type.java
 */
public enum Type {
    ARRAY, OBJECT, STRING, NUMBER, NULL, BOOL, DOUBLE, FUNCTION, UNDEFINED, INSTANCE, ERROR, JAVA_OBJECT;

    public String typeof() {
        switch (this) {
            case UNDEFINED:
                return "undefined";
            case BOOL:
                return "boolean";
            case DOUBLE:
            case NUMBER:
                return "number";
            case OBJECT:
            case INSTANCE:
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
