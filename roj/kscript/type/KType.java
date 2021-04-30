package roj.kscript.type;

import roj.kscript.api.IObject;
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

    default KInt asKInt() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.INT);
    }

    default KDouble asKDouble() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.DOUBLE);
    }

    default int asInt() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.INT);
    }

    default double asDouble() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.DOUBLE);
    }

    @Nonnull
    default String asString() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.STRING);
    }

    @Nonnull
    default IObject asObject() {
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

    default boolean asBool() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.BOOL);
    }

    default KError asKError() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.ERROR);
    }

    StringBuilder toString0(StringBuilder sb, int depth);

    KType copy();

    boolean canCastTo(Type type);

    default boolean equalsTo(KType b) {
        return equals(b);
    }

    default boolean isInt() {
        return false;
    }

    default boolean isString() {
        return false;
    }

    default boolean isImmutable() {
        return false;
    }
}
