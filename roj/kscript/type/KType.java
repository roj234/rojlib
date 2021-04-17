package roj.kscript.type;

import roj.kscript.api.IGettable;
import roj.kscript.func.KFunction;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/27 13:13
 */
public interface KType {
    default <T> KJavaObject<T> asJavaObject(Class<T> clazz) {
        throw new ClassCastException(getType() + " cannot cast to " + Type.JAVA_OBJECT);
    }

    Type getType();

    default KInteger asKInteger() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.NUMBER);
    }

    default KDouble asKDouble() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.DOUBLE);
    }

    default int asInteger() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.NUMBER);
    }

    default double asDouble() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.DOUBLE);
    }

    @Nonnull
    default String asString() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.STRING);
    }

    @Nonnull
    default IGettable asObject() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.OBJECT);
    }

    @Nonnull
    default KObject asKObject() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.OBJECT);
    }

    @Nonnull
    default IArray asArray() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.ARRAY);
    }

    default KFunction asFunction() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.FUNCTION);
    }

    default boolean asBoolean() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.BOOL);
    }

    default KError asKError() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.ERROR);
    }

    StringBuilder toString0(StringBuilder sb, int depth);

    KType copy();

    boolean canCastTo(Type type);

    default boolean equalsTo(KType b) {
        return b == this;
    }

    default boolean isInteger() {
        return false;
    }

    default boolean isString() {
        return false;
    }

    default boolean isImmutable() {
        return false;
    }
}
