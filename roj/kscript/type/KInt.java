package roj.kscript.type;

import roj.kscript.vm.KScriptVM;
import roj.math.MathUtils;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/16 23:19
 */
public class KInt extends KBase {
	public int value;

	protected KInt(int number) {
		this.value = number;
	}

	@Override
	public Type getType() {
		return Type.INT;
	}

	public static KInt valueOf(int nv) {
		return new KInt(nv);
	}

	public static KInt valueOf(String d) {
		return valueOf(MathUtils.parseInt(d));
	}

	@Override
	public boolean isInt() {
		return true;
	}

	@Override
	public double asDouble() {
		return value;
	}

	@Override
	public int asInt() {
		return value;
	}

	@Override
	public void setIntValue(int v) {
		value = v;
	}

	@Override
	public void setDoubleValue(double v) {
		value = (int) v;
	}

	@Nonnull
	@Override
	public String asString() {
		return String.valueOf(value);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		KInt that = (KInt) o;
		return that.value == value;
	}

	@Override
	public void copyFrom(KType type) {
		value = type.asInt();
	}

	@Override
	public boolean canCastTo(Type type) {
		switch (type) {
			case BOOL:
			case DOUBLE:
			case INT:
			case STRING:
				return true;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return value;
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return sb.append(value);
	}

	@Override
	public boolean equalsTo(KType b) {
		return b.canCastTo(Type.INT) && b.asInt() == value;
	}

	@Override
	public boolean asBool() {
		return value != 0;
	}

	@Override
	public KType copy() {
		return new KInt(value);
	}

	@Override
	public KType memory(int kind) {
		return kind < 5 ? KScriptVM.get().allocI(value, kind) : this;
	}
}
