package roj.asm.cp;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/5/24 22:50
 */
public final class CstTop extends Constant {
	private byte type;
	private int hash;

	private CharSequence strVal, strVal2, strVal3;
	private int intVal;
	private long longVal;

	public static final Constant TOP = new CstTop(false);

	public CstTop() {}

	private CstTop(boolean unused) {
		hash = 0x61c88647;
		type = _TOP_;
	}

	@Override
	void write(DynByteBuf w) {
		// no-op so needn't 'check&skip' in ConstantPool#write
		if (type != _TOP_) throw new IllegalStateException();
	}

	final CstTop set(CharSequence s) {
		strVal = s;
		type = UTF;
		hash = 1 + s.hashCode();
		return this;
	}
	final CstTop set(int value) {
		intVal = value;
		type = INT;
		hash = value * 0xBF58476D;
		return this;
	}
	final CstTop set(float value) {
		int val = Float.floatToRawIntBits(value);
		intVal = val;
		type = FLOAT;
		hash = val * 0x94D049BB;
		return this;
	}
	final CstTop set(long value) {
		longVal = value;
		type = LONG;
		hash = Long.hashCode(value) * 0x1CE4E5B9;
		return this;
	}
	final CstTop set(double value) {
		long val = Double.doubleToRawLongBits(value);
		longVal = val;
		type = DOUBLE;
		hash = Long.hashCode(val) * 0x133111EB;
		return this;
	}
	final CstTop set(CstUTF name, CstUTF desc) {
		type = NAME_AND_TYPE;

		strVal = name.str();
		strVal2 = desc.str();
		hash = 31 * name.hashCode() + desc.hashCode();
		return this;
	}
	// ref
	final CstTop set(byte cat, CstUTF value) {
		type = cat;

		strVal = value.str();
		hash = 31 * value.hashCode() + cat;
		return this;
	}
	final CstTop set(byte cat, CstClass clazz, CstNameAndType desc) {
		type = cat;

		strVal = clazz.value().str();
		strVal2 = desc.name().str();
		strVal3 = desc.rawDesc().str();
		hash = 31 * (31 * desc.hashCode() + clazz.value().hashCode()) + cat;
		return this;
	}
	final CstTop set(int kind, CstRef ref) {
		type = METHOD_HANDLE;

		intVal = kind;
		strVal = ref.owner();
		strVal2 = ref.nameAndType().name().str();
		strVal3 = ref.nameAndType().rawDesc().str();
		hash = ref.hashCode() << 3 | kind;
		return this;
	}
	//dynamic / invokedynamic
	final CstTop set(byte cat, int table, CstNameAndType desc) {
		type = cat;

		intVal = table;
		strVal = desc.name().str();
		strVal2 = desc.rawDesc().str();
		hash = (desc.hashCode() * 31 + table) * 31 * cat;
		return this;
	}

	@Override public final boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof Constant c) || c.type() != type) return false;
		return switch (type) {
			case UTF -> strVal.equals(((CstUTF) c).str());
			case INT -> intVal == ((CstInt) c).value;
			case FLOAT -> intVal == Float.floatToRawIntBits(((CstFloat) c).value);
			case LONG -> longVal == ((CstLong) c).value;
			case DOUBLE -> longVal == Double.doubleToRawLongBits(((CstDouble) c).value);
			case CLASS, MODULE, PACKAGE, STRING, METHOD_TYPE -> strVal.equals(((CstRefUTF) c).value().str());
			case NAME_AND_TYPE -> {
				CstNameAndType r = (CstNameAndType) c;
				yield strVal.equals(r.name().str()) && strVal2.equals(r.rawDesc().str());
			}
			case FIELD, METHOD, INTERFACE -> {
				CstRef r = (CstRef) c;
				yield strVal.equals(r.owner()) && strVal2.equals(r.nameAndType().name().str()) && strVal3.equals(r.nameAndType().rawDesc().str());
			}
			case METHOD_HANDLE -> {
				CstMethodHandle r = (CstMethodHandle) c;
				yield intVal == r.kind && strVal.equals(r.getTarget().owner()) && strVal2.equals(r.getTarget().nameAndType().name().str()) && strVal3.equals(
						r.getTarget().nameAndType().rawDesc().str());
			}
			case DYNAMIC, INVOKE_DYNAMIC -> {
				CstDynamic r = (CstDynamic) c;
				CstNameAndType t = r.desc();
				yield intVal == r.tableIdx && strVal.equals(t.name().str()) && strVal2.equals(t.rawDesc().str());
			}
			default -> false;
		};
	}

	@Override public final int hashCode() { return hash; }
	@Override public final byte type() { return Constant._TOP_; }
	@Override public final Constant clone() { return this == TOP ? this : super.clone();  }
}