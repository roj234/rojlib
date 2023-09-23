package roj.asm.cst;

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
	final CstTop set(int i) {
		intVal = i;
		type = INT;
		hash = ~i;
		return this;
	}
	final CstTop set(float data) {
		int i = Float.floatToRawIntBits(data);
		intVal = i;
		type = FLOAT;
		hash = i;
		return this;
	}
	final CstTop set(long l) {
		longVal = l;
		type = LONG;
		hash = (int) ~(l ^ (l >>> 32));
		return this;
	}
	final CstTop set(double d) {
		long l = Double.doubleToRawLongBits(d);
		longVal = l;
		type = DOUBLE;
		hash = (int) (l ^ (l >>> 32));
		return this;
	}
	final CstTop set(CstUTF name, CstUTF type) {
		this.type = NAME_AND_TYPE;

		strVal = name.str();
		strVal2 = type.str();
		hash = 31 * name.hashCode() + type.hashCode();
		return this;
	}
	final CstTop set(byte cat, CstUTF value) {
		type = cat;

		strVal = value.str();
		hash = 31 * value.hashCode() + cat;
		return this;
	}

	final CstTop set(byte cat, CstClass clazz, CstNameAndType desc) {
		type = cat;

		strVal = clazz.name().str();
		strVal2 = desc.name().str();
		strVal3 = desc.getType().str();
		hash = 31 * (31 * desc.hashCode() + clazz.name().hashCode()) + cat;
		return this;
	}
	final CstTop set(int kind, CstRef ref) {
		type = METHOD_HANDLE;

		intVal = kind;
		strVal = ref.className();
		strVal2 = ref.desc().name().str();
		strVal3 = ref.desc().getType().str();
		hash = ref.hashCode() << 3 | kind;
		return this;
	}
	final CstTop set(byte cat, int table, CstNameAndType desc) {
		type = cat;

		intVal = table;
		strVal = desc.name().str();
		strVal2 = desc.getType().str();
		hash = (desc.hashCode() * 31 + table) * 31 * cat;
		return this;
	}

	@Override
	public final boolean equals(Object o) {
		if (o == this) return true;
		if (!(o instanceof Constant)) return false;

		Constant c = (Constant) o;
		if (c.type() != type) return false;
		switch (type) {
			case UTF: return strVal.equals(((CstUTF) c).str());
			case INT: return intVal == ((CstInt) c).value;
			case FLOAT: return intVal == Float.floatToRawIntBits(((CstFloat) c).value);
			case LONG: return longVal == ((CstLong) c).value;
			case DOUBLE: return longVal == Double.doubleToRawLongBits(((CstDouble) c).value);
			case CLASS:
			case MODULE:
			case PACKAGE:
			case STRING:
			case METHOD_TYPE:
				return strVal.equals(((CstRefUTF) c).name().str());
			case NAME_AND_TYPE: {
				CstNameAndType r = (CstNameAndType) c;
				return strVal.equals(r.name().str()) && strVal2.equals(r.getType().str());
			}
			case FIELD:
			case METHOD:
			case INTERFACE: {
				CstRef r = (CstRef) c;
				return strVal.equals(r.className()) && strVal2.equals(r.desc().name().str()) && strVal3.equals(r.desc().getType().str());
			}
			case METHOD_HANDLE: {
				CstMethodHandle r = (CstMethodHandle) c;
				return intVal == r.kind && strVal.equals(r.getRef().className()) && strVal2.equals(r.getRef().desc().name().str()) && strVal3.equals(
					r.getRef().desc().getType().str());
			}
			case DYNAMIC:
			case INVOKE_DYNAMIC: {
				CstDynamic r = (CstDynamic) c;
				CstNameAndType t = r.desc();
				return intVal == r.tableIdx && strVal.equals(t.name().str()) && strVal2.equals(t.getType().str());
			}
		}
		return false;
	}

	@Override
	public final int hashCode() { return hash; }
	@Override
	public final byte type() { return Constant._TOP_; }
	@Override
	public final Constant clone() { return this == TOP ? this : super.clone();  }
}
