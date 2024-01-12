package roj.asm.frame;

import roj.asm.cp.Constant;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.Label;
import roj.collect.MyHashSet;

import java.util.Objects;
import java.util.Set;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Var2 {
	public static final String BYTE_ARRAY = "[B";

	public byte type;
	public String owner;
	public int bci;
	public Label monitor_bci;
	public Set<String> limitation;
	public Constant value;

	public static final Var2
		TOP = new Var2(VarType.TOP),
		INT = new Var2(VarType.INT),
		FLOAT = new Var2(VarType.FLOAT),
		DOUBLE = new Var2(VarType.DOUBLE),
		LONG = new Var2(VarType.LONG);

	public static Var2 except(byte type, CharSequence owner) {
		switch (type) {
			case VarType.TOP: return Var2.TOP;
			case VarType.INT: return Var2.INT;
			case VarType.FLOAT: return Var2.FLOAT;
			case VarType.DOUBLE: return Var2.DOUBLE;
			case VarType.LONG: return Var2.LONG;
			case VarType.NULL: return new Var2(VarType.NULL);
			case VarType.REFERENCE:
			case VarType.UNINITIAL:
			case VarType.UNINITIAL_THIS: return new Var2(type, owner.toString());
			default: throw new IllegalStateException("Unexpected type: " + type);
		}
	}

	public static Var2 any() {
		return new Var2(VarType.ANY);
	}

	public Var2(byte type) {
		this.type = type;
	}
	public Var2(byte type, String owner) {
		this.type = type;
		this.owner = Objects.requireNonNull(owner, "owner");
	}

	public Var2(Label init_bci) {
		this.type = VarType.UNINITIAL;
		this.monitor_bci = init_bci;
	}


	public int bci() {
		return monitor_bci == null ? bci : monitor_bci.getValue();
	}

	public void merge(Var2 o) {
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

		// NULL = 5, UNINITIAL_THIS = 6, REFERENCE = 7, UNINITIAL = 8
		if (o.type == VarType.NULL) return;
		if (type == VarType.NULL) {
			copy(o);
			return;
		}

		if (type != VarType.REFERENCE) {
			// uninitial / uninitial_this
			if (o.bci >= 0) {
				type = VarType.REFERENCE;
				bci = o.bci;
			} else {
				if (owner.equals("java/lang/Object")) throw new RuntimeException("new Object() breakpoint: in >1 basic blocks");
			}
		}

		if (o.owner.equals("java/lang/Object")) return;
		if (owner.equals(o.owner)) return;
		if (owner.equals("java/lang/Object")) {
			copy(o);
			return;
		}

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
			String s = FrameVisitor.getCommonUnsuperClass(owner, o.owner);
			if (s != null && o.limitation != null) {
				for (String s1 : o.limitation) {
					s = FrameVisitor.getCommonUnsuperClass(s, s1);
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

	private void copy(Var2 o) {
		type = o.type;
		bci = o.bci;
		owner = o.owner;
	}

	public void drop() {
		bci = -114514;
		type = VarType.TOP;
	}

	public boolean isDropped() {
		return bci == -114514;
	}

	public boolean eq(Var2 v) {
		if (this == v) return true;
		if (v != null && v.type == type) {
			if (owner != null) {
				return owner.equals(v.owner);
			} else if (v.owner == null) {
				return v.bci == this.bci;
			}
		}
		return false;
	}

	public String toString() {
		if (type == VarType.UNINITIAL) {
			return "【在 " + bci + " | " + monitor_bci + " 初始化】";
		} else if (type == VarType.REFERENCE) {
			return owner;
		} else {
			return type > 100 ? "Any" : VarType.toString(type);
		}
	}

	public Var2 copy() {
		if (type <= 5) return this;

		Var2 c = new Var2(type);
		c.bci = bci;
		c.monitor_bci = monitor_bci;
		c.owner = owner;
		if (limitation != null)
			c.limitation = new MyHashSet<>(limitation);
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
			case VarType.UNINITIAL_THIS:
			case VarType.UNINITIAL:
			default: throw new UnsupportedOperationException(String.valueOf(type));
		}
	}
}