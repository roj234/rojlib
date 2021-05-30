package roj.kscript.type;

import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.ast.Frame;
import roj.kscript.func.KFunction;
import roj.kscript.parser.expr.Expression;

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

    default int asInt() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.INT);
    }

    default void setIntValue(int intValue) {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.INT);
    }

    default double asDouble() {
        throw new ClassCastException(getType().name() + ' ' + toString() + " cannot cast to " + Type.DOUBLE);
    }

    default void setDoubleValue(double doubleValue) {
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


    /**
     * 仅用于{@link Expression#compress() Expression的缩减}以及{@link roj.kscript.ast.LoadDataNode#execute(Frame) 加载KType}中 <BR>
     *     拷贝自身, 用于加载对象, 嗯
     */
    default KType copy() {
        return this;
    }

    /**
     * 仅用于{@link roj.kscript.ast.LoadDataNode#execute(Frame) 加载KType}中 <BR>
     *     从同类对象中拷贝数据
     */
    default void copyFrom(KType type) {}

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

    /**
     * kind = 0: 作为本地变量 <br>
     *     kind = 1: 作为外部变量 <br>
     *     kind = -1: 返回栈
     */
    default KType setFlag(int kind) {
        return this;
    }
}
