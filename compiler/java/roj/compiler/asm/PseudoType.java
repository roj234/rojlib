package roj.compiler.asm;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.resolve.TypeCast;
import roj.text.CharList;
import roj.util.OperationDone;

/**
 * @author Roj234
 * @since 2025/10/26 3:25
 */
public class PseudoType implements IType {
	public static final int PSEUDO_TYPE = 9;

	private String typename;
	private Type bound;

	public PseudoType(String typename, Type bound) {
		this.typename = typename;
		this.bound = bound;
	}

	public TypeCast.Cast castTo(IType type) {return TypeCast.ERROR(TypeCast.IMPOSSIBLE);}
	public TypeCast.Cast castFrom(IType type) {return TypeCast.ERROR(TypeCast.IMPOSSIBLE);}

	@Override public byte kind() { return PSEUDO_TYPE; }
	@Override public void toDesc(CharList sb) { bound.toDesc(sb); }

	@Override public boolean isPrimitive() { return bound.isPrimitive(); }
	@Override public Type rawType() { return bound.rawType(); }
	@Override public int array() { return bound.array(); }
	@Override public void setArrayDim(int array) {
		bound = bound.clone();
		bound.setArrayDim(array);
	}
	@Override public String owner() { return bound.owner(); }
	@Override public void owner(String owner) {bound.owner(owner);}

	@Override public PseudoType clone() {
		if (bound == null) return this;
		try {
			PseudoType clone = (PseudoType) super.clone();
			clone.bound = clone.bound.clone();
			return clone;
		} catch (CloneNotSupportedException e) {
			throw OperationDone.NEVER;
		}
	}
	@Override public void toString(CharList sb) {bound.toString(sb.append('?'));}
	@Override public String toString() {return "?"+bound;}

	@Override public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof PseudoType pt)) return false;

		if (!typename.equals(pt.typename)) return false;

		return bound.equals(pt.bound);
	}
	@Override public int hashCode() {
		int hash = typename.hashCode();
		hash = 31 * hash + bound.hashCode();
		return hash;
	}
}