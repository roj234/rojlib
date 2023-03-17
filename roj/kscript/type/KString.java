package roj.kscript.type;

import roj.config.word.ITokenizer;
import roj.math.MathUtils;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/28 23:38
 */
public final class KString extends KBase {
	private final String value;
	private byte vt = -2;

	public KString(String string) {
		this.value = string;
	}

	@Override
	public Type getType() {
		return Type.STRING;
	}

	public static final KString EMPTY = new KString("");

	public static KString valueOf(String name) {
		return name.length() == 0 ? EMPTY : new KString(name);
	}

	@Nonnull
	@Override
	public String asString() {
		return value;
	}

	@Override
	public double asDouble() {
		if (checkType() >= 0) {
			try {
				return Double.parseDouble(value);
			} catch (NumberFormatException ignored) {}
		}
		return super.asDouble();
	}

	@Override
	public int asInt() {
		if (checkType() >= 0) {
			try {
				return MathUtils.parseInt(value);
			} catch (NumberFormatException ignored) {}
		}
		return super.asInt();
	}

	@Override
	public boolean isString() {
		return checkType() == -1;
	}

	@Override
	public boolean asBool() {
		return !value.equalsIgnoreCase("false") && !value.isEmpty();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		KString that = (KString) o;
		return Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return value == null ? 0 : value.hashCode();
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		return ITokenizer.addSlashes(value, sb.append('"')).append('"');
	}

	@Override
	public boolean equalsTo(KType b) {
		return b.canCastTo(Type.STRING) && b.asString().equals(value);
	}

	@Override
	public boolean isInt() {
		return checkType() == 0;
	}

	@Override
	public boolean canCastTo(Type type) {
		switch (type) {
			case BOOL:
			case STRING:
				return true;
			case DOUBLE:
			case INT:
				return checkType() >= 0;
		}
		return false;
	}

	private int checkType() {
		if (vt == -2) {
			vt = (byte) TextUtil.isNumber(this.value);
			if (vt == -1) {
				switch (value) {
					case "NaN":
					case "Infinity":
						vt = 1;
				}
			}
		}
		return vt;
	}

}
