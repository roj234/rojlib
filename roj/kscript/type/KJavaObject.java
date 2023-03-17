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
		if (this.val == null) {return (KJavaObject<O>) this;} else {
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
