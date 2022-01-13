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
import roj.kscript.func.KFunction;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/28 23:40
 */
public final class KJavaObject<T> extends KBase {
    private T val;

    public KJavaObject(T val) {
        this.val = val;
    }

    @Override
    public Type getType() {
        return Type.JAVA_OBJECT;
    }

    public void setObject(T object) {
        this.val = object;
    }

    public T getObject() {
        return this.val;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <O> KJavaObject<O> asJavaObject(Class<O> clazz) {
        if (this.val == null)
            return (KJavaObject<O>) this;
        else {
            if (clazz.isInstance(val)) {
                return (KJavaObject<O>) this;
            }
            throw new ClassCastException(val.getClass() + " to " + clazz.getName());
        }
    }

    public T getOr(T def) {
        return this.val == null ? def : this.val;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append('{').append(val == null ? "" : val.getClass()).append(':').append(val).append('}');
    }

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case STRING:
                return val instanceof CharSequence;
            case INT:
            case DOUBLE:
                return val instanceof Number;
            case BOOL:
                return val instanceof Boolean;
            case FUNCTION:
                return val instanceof KFunction;
            case JAVA_OBJECT:
                return true;
            case OBJECT:
                return val instanceof IObject;
            case ARRAY:
                return val instanceof roj.kscript.api.IArray;
        }
        return false;
    }

    @Override
    public boolean isString() {
        return val instanceof CharSequence;
    }

    @Override
    public boolean isInt() {
        return !(val instanceof Double || val instanceof Float);
    }

    @Override
    public boolean asBool() {
        return asJavaObject(Boolean.class).getOr(Boolean.FALSE);
    }

    @Override
    public double asDouble() {
        return asJavaObject(Number.class).getOr(0.0D).doubleValue();
    }

    @Override
    public int asInt() {
        return asJavaObject(Number.class).getOr(0).intValue();
    }

    @Nonnull
    @Override
    public String asString() {
        return asJavaObject(CharSequence.class).getOr("").toString();
    }

    @Nonnull
    @Override
    public IObject asObject() {
        return asJavaObject(IObject.class).getObject();
    }

    @Nonnull
    @Override
    public roj.kscript.api.IArray asArray() {
        return asJavaObject(IArray.class).getObject();
    }

    @Override
    public KFunction asFunction() {
        KFunction func = asJavaObject(KFunction.class).getObject();
        return func == null ? super.asFunction() : func;
    }
}
