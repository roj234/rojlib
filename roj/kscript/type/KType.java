/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.kscript.type;

import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.asm.Frame;
import roj.kscript.func.KFunction;
import roj.kscript.parser.ast.Expression;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since  2020/10/27 13:13
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
     * ?????????{@link Expression#compress() Expression?????????}??????{@link roj.kscript.asm.LoadDataNode#exec(Frame) ??????KType}??? <BR>
     *     ????????????, ??????????????????, ???
     */
    default KType copy() {
        return this;
    }

    /**
     * ?????????{@link roj.kscript.asm.LoadDataNode#exec(Frame) ??????KType}??? <BR>
     *     ??????????????????????????????
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
     * kind =-1: ????????????????????? <br>
     * kind = 0: ?????????????????? <br>
     * kind = 1: ?????????????????? <br>
     * kind = 2: ?????? <br>
     * kind = 3: ??????????????? <br>
     * kind = 4: ?????????????????? <br>
     * kind = 5: ????????????+1 <br>
     * kind = 6: ????????????-1
     */
    default KType memory(int kind) {
        return this;
    }
}
