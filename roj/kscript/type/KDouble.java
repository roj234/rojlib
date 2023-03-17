package roj.kscript.type;

import roj.kscript.vm.KScriptVM;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/16 23:19
 */
public class KDouble extends KBase {
	public double value;

	public KDouble(double number) {
		this.value = number;
	}

	@Override
	public Type getType() {
		return Type.DOUBLE;
	}

	public static KType valueOf(double d) {
		return (((int) d) == d) ? new KInt((int) d) : new KDouble(d);
	}

	public static KType valueOf(String d) {
		return valueOf(Double.parseDouble(d));
	}

	@Override
	public final boolean isInt() {
		return ((int) value) == value;
	}

	@Override
	public final boolean asBool() {
		return value == value && value != 0;
	}

	@Override
	public final double asDouble() {
		return value;
	}

	@Override
	public final int asInt() {
		return (int) value;
	}

	@Override
	public final void setDoubleValue(double v) {
		value = v;
	}

	@Override
	public final void setIntValue(int v) {
		value = v;
	}

	@Nonnull
	@Override
	public final String asString() {
		return String.valueOf(value);
	}

	@Override
	public final boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		KDouble that = (KDouble) o;
		return Double.compare(that.value, value) == 0;
	}

	@Override
	public final int hashCode() {
		return Float.floatToIntBits((float) value);
	}

	@Override
	public final StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append(value);
	}

	@Override
	public final boolean equalsTo(KType b) {
		return b.canCastTo(Type.DOUBLE) && b.asDouble() == value;
	}

	@Override
	public final void copyFrom(KType type) {
		value = type.asDouble();
	}

	@Override
	public KType copy() {
		return new KDouble(value);
	}

	@Override
	public KType memory(int kind) {
		return kind < 5 ? KScriptVM.get().allocD(value, kind) : this;
	}

	@Override
	public final boolean canCastTo(Type type) {
		switch (type) {
			case BOOL:
			case DOUBLE:
			case INT:
			case STRING:
				return true;
		}
		return false;
	}
}
