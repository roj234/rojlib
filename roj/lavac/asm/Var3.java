package roj.lavac.asm;

import roj.asm.cst.Constant;
import roj.asm.frame.VarType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.FrameVisitor;
import roj.collect.MyHashSet;
import roj.util.VarMapper;

import java.util.Objects;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Var3 extends VarMapper.VarX {
	public static final String BYTE_ARRAY = "[B";

	public byte type, smallType;
	public String owner;
	public Set<String> limitation;
	public Constant value;

	public static final Var3
		TOP = new Var3(VarType.TOP),
		INT = new Var3(VarType.INT),
		FLOAT = new Var3(VarType.FLOAT),
		DOUBLE = new Var3(VarType.DOUBLE),
		LONG = new Var3(VarType.LONG);

	public static Var3 get(byte type, String owner) {
		switch (type) {
			case VarType.TOP: return Var3.TOP;
			case VarType.INT: return Var3.INT;
			case VarType.FLOAT: return Var3.FLOAT;
			case VarType.DOUBLE: return Var3.DOUBLE;
			case VarType.LONG: return Var3.LONG;
			case VarType.NULL: return new Var3(VarType.NULL);
			case VarType.REFERENCE: return new Var3(type, owner);
			default: throw new IllegalStateException("Unexpected type: " + type);
		}
	}
	public static Var3 any() { return new Var3(VarType.ANY); }

	public Var3(byte type) {
		this.type = type;
	}
	public Var3(byte type, String owner) {
		this.type = type;
		this.owner = Objects.requireNonNull(owner, "owner");
	}

	public void merge(Var3 o) {
		if (o == this) return;
		if (o.type >= VarType.ANY) return;
		if (type >= VarType.ANY) {
			copy(o);
			return;
		}

		if (o.type < 5 || type < 5) {
			if (type != o.type) throw new IllegalStateException("Could not merge " + this + " and " + o);
			return;
		}

		if (o.type == VarType.NULL) return;
		if (type == VarType.NULL) {
			copy(o);
			return;
		}

		if (o.owner.equals("java/lang/Object")) return;
		if (owner.equals(o.owner)) return;
		if (owner.equals("java/lang/Object")) {
			copy(o);
			return;
		}

		// todo Cloneable,Serializable,Object => array
		if (owner.startsWith("[") != o.owner.startsWith("[")) {
			throw new IllegalStateException("Could not merge " + this + " and " + o);
		}
		if (o.owner.equals("[")) return;
		if (owner.equals("[")) {
			copy(o);
			return;
		}
		if (owner.equals(BYTE_ARRAY) || o.owner.equals(BYTE_ARRAY)) {
			if (o.owner.equals("[Z") || owner.equals("[Z")) return;
		}

		try {
			String s = FrameVisitor.LOCAL.get().getCommonUnsuperClass(owner, o.owner);
			if (s != null && o.limitation != null) {
				for (String s1 : o.limitation) {
					s = FrameVisitor.LOCAL.get().getCommonUnsuperClass(s, s1);
					if (s == null) break;
				}
			}

			if (s == null) {
				if (limitation == null) {
					limitation = new MyHashSet<>();
				}
				limitation.add(o.owner);
				if (o.limitation != null) {
					limitation.addAll(o.limitation);
				}
			} else {
				owner = s;
			}
		} catch (Throwable e) {
			throw new IllegalStateException("Unable determine class hierarchy for " + owner + " and " + o.owner, e);
		}
	}

	private void copy(Var3 o) {
		type = o.type;
		owner = o.owner;
	}

	public boolean eq(Var3 v) {
		if (this == v) return true;
		if (v != null && v.type == type) {
			if (owner != null) return owner.equals(v.owner);
			return v.owner == null;
		}
		return false;
	}

	public String toString() {
		if (type == VarType.REFERENCE) {
			return owner;
		} else {
			return type > 100 ? "Any" : VarType.toString(type);
		}
	}

	public Var3 copy() {
		Var3 c = new Var3(type);
		c.owner = owner;
		if (limitation != null) c.limitation = new MyHashSet<>(limitation);
		return c;
	}

	public Type type() {
		switch (type) {
			case VarType.INT: return Type.std(Type.INT);
			case VarType.FLOAT: return Type.std(Type.FLOAT);
			case VarType.DOUBLE: return Type.std(Type.DOUBLE);
			case VarType.LONG: return Type.std(Type.LONG);
			case VarType.NULL: return new Type("java/lang/Object");
			case VarType.REFERENCE: return owner.charAt(0) == '[' ? TypeHelper.parseField(owner) : new Type(owner);
			default: throw new UnsupportedOperationException(String.valueOf(type));
		}
	}
}
