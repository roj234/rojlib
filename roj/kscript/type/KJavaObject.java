package roj.kscript.type;

import roj.kscript.api.IGettable;
import roj.kscript.func.KFunction;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: CObject.java
 */
public final class KJavaObject<T> extends KBase {
    private T val;

    public KJavaObject(T val) {
        super(Type.JAVA_OBJECT);
        this.val = val;
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
    public KType copy() {
        return this;
    }

    @Override
    public boolean canCastTo(Type type) {
        switch (type) {
            case STRING:
                return val instanceof CharSequence;
            case NUMBER:
            case DOUBLE:
                return val instanceof Number;
            case BOOL:
                return val instanceof Boolean;
            case FUNCTION:
                return val instanceof KFunction;
            case JAVA_OBJECT:
                return true;
            case OBJECT:
                return val instanceof IGettable;
            case ARRAY:
                return val instanceof IArray;
        }
        return false;
    }

    @Override
    public boolean isString() {
        return val instanceof CharSequence;
    }

    @Override
    public boolean isInteger() {
        return !(val instanceof Double || val instanceof Float);
    }

    @Override
    public boolean asBoolean() {
        return asJavaObject(Boolean.class).getOr(Boolean.FALSE);
    }

    @Override
    public double asDouble() {
        return asJavaObject(Number.class).getOr(0.0D).doubleValue();
    }

    @Override
    public int asInteger() {
        return asJavaObject(Number.class).getOr(0).intValue();
    }

    @Nonnull
    @Override
    public String asString() {
        return asJavaObject(CharSequence.class).getOr("").toString();
    }

    @Override
    public KDouble asKDouble() {
        return KDouble.valueOfF(asDouble());
    }

    @Override
    public KInteger asKInteger() {
        return KInteger.valueOf(asInteger());
    }

    @Nonnull
    @Override
    public IGettable asObject() {
        return asJavaObject(IGettable.class).getObject();
    }

    @Nonnull
    @Override
    public IArray asArray() {
        return asJavaObject(IArray.class).getObject();
    }

    @Override
    public KFunction asFunction() {
        KFunction func = asJavaObject(KFunction.class).getObject();
        return func == null ? super.asFunction() : func;
    }
}
