package roj.config.data;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: Type.java
 */
public enum Type {
    LIST, MAP, STRING, NUMBER, NULL, BOOL, DOUBLE, LIST_MAP, SERIALIZED_OBJECT, JVAV_OBJECT, JVAV_FUNCTION;

    public boolean isNumber() {
        return this == NUMBER || this == DOUBLE;
    }

    public boolean canFit(Type type) {
        switch (this) {
            case DOUBLE:
            case NUMBER:
            case BOOL:
            case STRING:
                return type == DOUBLE || type == NUMBER || type == BOOL || type == STRING;
            case NULL:
                return false;
            default:
                return type == this;
        }
    }
}
