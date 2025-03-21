package roj.asm.type;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Range;
import roj.text.CharList;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * 泛型类型
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Generic extends IGeneric {
	public static final int EX_NONE = 0, EX_SUPER = 1, EX_EXTENDS = 2;
	@MagicConstant(intValues = {EX_NONE, EX_SUPER, EX_EXTENDS}) public byte extendType;
	private byte array;

	public Generic() {}
	public Generic(String owner) { this.owner = owner; }
	public Generic(String owner, List<? extends IType> children) {
		this.owner = owner;
		this.children = Helpers.cast(children);
	}
	public Generic(String owner, @MagicConstant(intValues = {EX_NONE, EX_SUPER, EX_EXTENDS}) int extendType) {
		this.extendType = (byte) extendType;
		this.owner = owner;
	}
	public Generic(String owner, @Range(from = 0, to = 255) int array, @MagicConstant(intValues = {EX_NONE, EX_SUPER, EX_EXTENDS}) int extendType) {
		this.extendType = (byte) extendType;
		this.owner = owner;
		setArrayDim(array);
	}

	@Deprecated
	public static Type klass(String type) {return Type.klass(type);}
	public static Generic generic(String type, IType...rest) {return new Generic(type, Arrays.asList(rest));}

	public boolean canBeAny() { return extendType == EX_EXTENDS && owner.equals("java/lang/Object") && children.isEmpty() && sub == null; }

	public void toDesc(CharList sb) {
		if (extendType != EX_NONE) sb.append(extendType == EX_SUPER ? '-' : '+');
		for (int i = array&0xFF; i > 0; i--) sb.append('[');
		sb.append('L').append(owner);

		if (!children.isEmpty()) {
			sb.append('<');
			for (int i = 0; i < children.size(); i++) {
				children.get(i).toDesc(sb);
			}
			sb.append('>');
		}

		if (sub != null) sub.toDesc(sb);
		else sb.append(';');
	}

	public void toString(CharList sb) {
		switch (extendType) {
			case EX_SUPER: sb.append("? super "); break;
			case EX_EXTENDS: sb.append("? extends "); break;
		}

		TypeHelper.toStringOptionalPackage(sb, owner);

		if (!children.isEmpty()) {
			sb.append('<');
			int i = 0;
			while (true) {
				children.get(i++).toString(sb);
				if (i == children.size()) break;
				sb.append(", ");
			}
			sb.append('>');
		}

		if (sub != null) sub.toString(sb.append('.'));

		for (int i = array&0xFF; i > 0; i--) sb.append("[]");
	}

	@Override public byte genericType() {return GENERIC_TYPE;}

	@Override public Type rawType() {return Type.klass(owner, array&0xFF);}
	@Override public int array() {return array&0xFF;}
	@Override public void setArrayDim(int array) {
		if (array > 255 || array < 0)
			throw new ArrayIndexOutOfBoundsException(array);
		this.array = (byte) array;
	}
	@Override public String owner() {return owner;}
	@Override public void owner(String owner) {this.owner = owner;}

	public void rename(UnaryOperator<String> fn) {
		b: if (sub != null) {
			CharList name = new CharList().append(owner);

			IGeneric x = sub;
			while (x != null) {
				name.append('$').append(x.owner);
				x = x.sub;
			}

			String t = name.toStringAndFree();
			String newName = fn.apply(t);
			if (t.equals(newName)) break b;

			x = this;
			int prevI = 0, i = 0;
			while ((i = newName.indexOf('$', i)) > 0) {
				if (x == null) throw new IllegalArgumentException("[GenericSub]: "+t+"("+this+") => "+newName+" has too may '$'");

				x.owner = newName.substring(prevI, i);
				x = x.sub;

				prevI = ++i;
			}
			if (x != null) Logger.getLogger("GenericSub").warn("{}({}) => {} has too less '$'", t, this, newName);
		} else {
			owner = fn.apply(owner);
		}

		for (int i = 0; i < children.size(); i++) {
			children.get(i).rename(fn);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Generic generic)) return false;

		if (extendType != generic.extendType) return false;
		if (array != generic.array) return false;
		if (!owner.equals(generic.owner)) return false;
		if (sub != null ? !sub.equals(generic.sub) : generic.sub != null) return false;
		return children.equals(generic.children);
	}

	@Override
	public int hashCode() {
		int h = extendType;
		h = 31 * h + owner.hashCode();
		h = 31 * h + (sub != null ? sub.hashCode() : 0);
		h = 31 * h + array;
		h = 31 * h + children.hashCode();
		return h;
	}
}