package roj.kscript.type;

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


    /**
     * 仅用于{@link Expression#compress() Expression的缩减}, {@link roj.kscript.util.opm.GlobalVarMap#reset 全局变量重置的比对}以及{@link roj.kscript.ast.LoadDataNode#execute(Frame) 加载KType}中 <BR>
     *     拷贝自身, 用于加载对象, 嗯
     */
    default KType copy() {
        return this;
    }

    /**
     * 仅用于{@link roj.kscript.util.opm.GlobalVarMap#reset 全局变量重置的比对}以及{@link roj.kscript.ast.LoadDataNode#execute(Frame) 加载KType}中 <BR>
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
     * 内部对象标记, 勿覆盖 <BR>
     *     1: default <BR>
     *     2: int <BR>
     *     4: double <BR>
     *     8: internal <BR>
     *    16: 变量
     */
    default int spec() {
        return 1;
    }

    /**
     * 使其不可变, object无效, 因为它不是用来加载对象的
     */
    default KType markImmutable(boolean kind) {
        return this;
    }
}
