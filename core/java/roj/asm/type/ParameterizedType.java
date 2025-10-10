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
public class ParameterizedType extends IGeneric {
	public static final int NO_WILDCARD = 0, SUPER_WILDCARD = 1, EXTENDS_WILDCARD = 2;
	@MagicConstant(intValues = {NO_WILDCARD, SUPER_WILDCARD, EXTENDS_WILDCARD})
	public byte wildcard;
	private byte array;

	public ParameterizedType() {}
	public ParameterizedType(String owner) { this.owner = owner; }
	public ParameterizedType(String owner, List<? extends IType> children) {
		this.owner = owner;
		this.typeParameters = Helpers.cast(children);
	}
	public ParameterizedType(String owner, @MagicConstant(intValues = {NO_WILDCARD, SUPER_WILDCARD, EXTENDS_WILDCARD}) int wildcard) {
		this.wildcard = (byte) wildcard;
		this.owner = owner;
	}
	public ParameterizedType(String owner, @Range(from = 0, to = 255) int array, @MagicConstant(intValues = {NO_WILDCARD, SUPER_WILDCARD, EXTENDS_WILDCARD}) int wildcard) {
		this.wildcard = (byte) wildcard;
		this.owner = owner;
		setArrayDim(array);
	}

	public static ParameterizedType parameterized(String type, IType...rest) {return new ParameterizedType(type, Arrays.asList(rest));}

	public boolean isUnboundedWildcard() { return wildcard == EXTENDS_WILDCARD && owner.equals("java/lang/Object") && typeParameters.isEmpty(); }

	public void toDesc(CharList sb) {
		if (wildcard != NO_WILDCARD) sb.append(wildcard == SUPER_WILDCARD ? '-' : '+');
		for (int i = array&0xFF; i > 0; i--) sb.append('[');
		sb.append('L').append(owner);

		if (!typeParameters.isEmpty()) {
			sb.append('<');
			for (int i = 0; i < typeParameters.size(); i++) {
				typeParameters.get(i).toDesc(sb);
			}
			sb.append('>');
		}

		if (sub != null) sub.toDesc(sb);
		else sb.append(';');
	}

	public void toString(CharList sb) {
		switch (wildcard) {
			case SUPER_WILDCARD: sb.append("? super "); break;
			case EXTENDS_WILDCARD: sb.append("? extends "); break;
		}

		TypeHelper.toStringOptionalPackage(sb, owner);

		if (!typeParameters.isEmpty()) {
			sb.append('<');
			int i = 0;
			while (true) {
				typeParameters.get(i++).toString(sb);
				if (i == typeParameters.size()) break;
				sb.append(", ");
			}
			sb.append('>');
		}

		if (sub != null) sub.toString(sb.append('.'));

		for (int i = array&0xFF; i > 0; i--) sb.append("[]");
	}

	@Override public byte kind() {return PARAMETERIZED_TYPE;}

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
			if (x != null) Logger.FALLBACK.warn("{}({}) => {} has too less '$'", t, this, newName);
		} else {
			owner = fn.apply(owner);
		}

		for (int i = 0; i < typeParameters.size(); i++) {
			typeParameters.get(i).rename(fn);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ParameterizedType parameterizedType)) return false;

		if (wildcard != parameterizedType.wildcard) return false;
		if (array != parameterizedType.array) return false;
		if (!owner.equals(parameterizedType.owner)) return false;
		if (sub != null ? !sub.equals(parameterizedType.sub) : parameterizedType.sub != null) return false;
		return typeParameters.equals(parameterizedType.typeParameters);
	}

	@Override
	public int hashCode() {
		int h = wildcard;
		h = 31 * h + owner.hashCode();
		h = 31 * h + (sub != null ? sub.hashCode() : 0);
		h = 31 * h + array;
		h = 31 * h + typeParameters.hashCode();
		return h;
	}
}