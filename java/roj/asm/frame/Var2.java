package roj.asm.frame;

import roj.asm.cp.Constant;
import roj.asm.insn.Label;
import roj.asm.type.Type;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Var2 {
	public static final byte T_TOP = 0, T_INT = 1, T_FLOAT = 2, T_DOUBLE = 3, T_LONG = 4, T_NULL = 5, T_UNINITIAL_THIS = 6, T_REFERENCE = 7, T_UNINITIAL = 8, T_ANY = 114, T_ANY2 = 115;

	public static final String BYTE_ARRAY = "[B";

	public byte type;
	public String owner;
	public int bci;
	public Label monitor_bci;
	public Constant value;

	public static final Var2
		TOP = new Var2(T_TOP),
		INT = new Var2(T_INT),
		FLOAT = new Var2(T_FLOAT),
		DOUBLE = new Var2(T_DOUBLE),
		LONG = new Var2(T_LONG);

	public static Var2 of(Type type) {
		return switch (type.getActualType()) {
			case Type.VOID -> null;
			case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> INT;
			case Type.FLOAT -> FLOAT;
			case Type.DOUBLE -> DOUBLE;
			case Type.LONG -> LONG;
			case Type.CLASS -> new Var2(T_REFERENCE, type.getActualClass());
			default -> throw new IllegalStateException("Unexpected type: "+type);
		};
	}
	public static Var2 of(byte type, CharSequence klass) {
		return switch (type) {
			case T_TOP -> TOP;
			case T_INT -> INT;
			case T_FLOAT -> FLOAT;
			case T_DOUBLE -> DOUBLE;
			case T_LONG -> LONG;
			case T_NULL -> new Var2(T_NULL);
			case T_REFERENCE, T_UNINITIAL, T_UNINITIAL_THIS -> new Var2(type, klass.toString());
			default -> throw new IllegalStateException("Unexpected type: "+type);
		};
	}
	public static Var2 any() {return new Var2(T_ANY);}

	public Var2(byte type) {this.type = type;}
	public Var2(byte type, String owner) {
		this.type = type;
		this.owner = Objects.requireNonNull(owner, "owner");
	}
	public Var2(Label init_bci) {
		this.type = T_UNINITIAL;
		this.monitor_bci = init_bci;
	}

	public int bci() {return monitor_bci == null ? bci : monitor_bci.getValue();}

	public boolean verify(Var2 o) {
		if (o == this) return false;
		if (o.type >= T_ANY) return false;
		if (type >= T_ANY) {
			copy(o);
			return true;
		}

		if (o.type < 5 || type < 5) {
			if (type != o.type) throw new IllegalStateException("Could not merge " + this + " and " + o);
			return false;
		}

		// NULL = 5, UNINITIAL_THIS = 6, REFERENCE = 7, UNINITIAL = 8
		if (o.type == T_NULL) return false;
		if (type == T_NULL) {
			copy(o);
			return true;
		}

		if (type != T_REFERENCE) {
			// uninitial / uninitial_this
			if (o.bci >= 0) {
				type = T_REFERENCE;
				bci = o.bci;
			} else {
				if (owner.equals("java/lang/Object")) throw new RuntimeException("new Object() breakpoint: in >1 basic blocks");
			}
		}

		if (o.owner.equals("java/lang/Object")) return false;
		if (owner.equals(o.owner)) return false;
		if (owner.equals("java/lang/Object")) {
			copy(o);
			return true;
		}

		if (owner.startsWith("[") != o.owner.startsWith("[")) {
			throw new IllegalStateException("Could not merge " + this + " and " + o);
		}
		if (o.owner.equals("[")) return false;
		if (owner.equals("[")) {
			copy(o);
			return true;
		}
		if (owner.equals(BYTE_ARRAY) || o.owner.equals(BYTE_ARRAY)) {
			if (o.owner.equals("[Z") || owner.equals("[Z")) return false;
		}

		if (owner.equals("[Ljava/lang/Object;")) {
			copy(o);
			return true;
		}

		String newOwner = FrameVisitor.getCommonUnsuperClass(owner, o.owner);
		boolean changed = !newOwner.equals(owner);
		owner = newOwner;
		return changed;
	}
	public Var2 uncombine(Var2 o) {
		if (o == this) return null;
		if (o.type >= T_ANY) return null;
		if (type >= T_ANY) {
			copy(o);
			return this;
		}

		if (o.type < 5 || type < 5) {
			if (type != o.type && type != T_TOP) return TOP;
			return null;
		}

		if (o.type == T_NULL) return null;
		if (type == T_NULL) {
			copy(o);
			return this;
		}

		if (type != T_REFERENCE) {
			// uninitial / uninitial_this
			if (o.bci >= 0) {
				type = T_REFERENCE;
				bci = o.bci;
			} else {
				if (owner.equals("java/lang/Object")) throw new RuntimeException("new Object() breakpoint: in >1 basic blocks");
			}
		}

		if (o.owner.equals("java/lang/Object")) return null;
		if (owner.equals(o.owner)) return null;
		if (owner.equals("java/lang/Object")) {
			copy(o);
			return this;
		}

		if (owner.startsWith("[") != o.owner.startsWith("[")) {
			return TOP;
		}

		// Only for ArrayLength
		if (o.owner.equals("[")) return null;
		if (owner.equals("[")) {
			copy(o);
			return this;
		}
		if (owner.equals(BYTE_ARRAY) || o.owner.equals(BYTE_ARRAY)) {
			if (o.owner.equals("[Z") || owner.equals("[Z")) return null;
		}

		if (owner.startsWith("[")) {
			if (owner.charAt(1) != 'L') return TOP;
		}

		String commonSuperClass = FrameVisitor.getCommonSuperClass(owner, o.owner);
		boolean changed = !commonSuperClass.equals(owner);
		owner = commonSuperClass;
		return changed ? this : null;
	}

	private void copy(Var2 o) {
		type = o.type;
		bci = o.bci;
		owner = o.owner;
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

	private static final String[] toString = {"top", "int", "float", "double", "long", "{null}", "uninitial_this", "object", "uninitial"};
	public String toString() {
		if (type == T_UNINITIAL) {
			return "【在 " + bci + " | " + monitor_bci + " 初始化】";
		} else if (type == T_REFERENCE) {
			return owner;
		} else {
			return type > 100 ? "Any" : toString[type];
		}
	}

	public Var2 copy() {
		if (type <= 5) return this;

		Var2 c = new Var2(type);
		c.bci = bci;
		c.monitor_bci = monitor_bci;
		c.owner = owner;
		return c;
	}

	public Type asmType() {
		return switch (type) {
			case T_INT -> Type.primitive(Type.INT);
			case T_FLOAT -> Type.primitive(Type.FLOAT);
			case T_DOUBLE -> Type.primitive(Type.DOUBLE);
			case T_LONG -> Type.primitive(Type.LONG);
			case T_NULL -> Type.klass("java/lang/Object");
			case T_REFERENCE -> owner.charAt(0) == '[' ? Type.fieldDesc(owner) : Type.klass(owner);
			default -> throw new UnsupportedOperationException(String.valueOf(type));
		};
	}
}