package roj.asm.type;

import roj.io.IOUtil;
import roj.text.CharList;

import java.util.function.UnaryOperator;

/**
 * 泛型类型
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Generic extends IGeneric {
	public static final byte EX_NONE = 0, EX_SUPERS = 1, EX_EXTENDS = 2;

	public byte extendType;
	private byte array;

	public Generic() {}

	public Generic(String owner, int array, byte extendType) {
		this.extendType = extendType;
		this.owner = owner;
		setArrayDim(array);
	}

	public boolean isRealGeneric() {
		return !children.isEmpty() || "*".equals(owner) || extendType != 0 || sub != null;
	}

	public void toDesc(CharList sb) {
		if (extendType != 0) sb.append(extendType == EX_SUPERS ? '-' : '+');
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

	public void rename(UnaryOperator<String> fn) {
		if (sub != null) {
			recursionSubs(fn, sub, new CharList().append(owner), 1);
		}
		owner = fn.apply(owner);

		for (int i = 0; i < children.size(); i++) {
			children.get(i).rename(fn);
		}
	}

	private void recursionSubs(UnaryOperator<String> fn, GenericSub sub, CharList t, int dollar) {
		t.append('$').append(sub.owner);

		if (sub.sub != null) {
			int len = t.length();
			recursionSubs(fn, sub.sub, t, dollar+1);
			t.setLength(len);
		}

		String rpl = fn.apply(t.toString());
		if (t.equals(rpl)) return;

		int i = 0;
		while (dollar-- > 0) {
			i = rpl.indexOf('$', i);
			if (i < 0) throw new IllegalStateException("Non-static sub class renaming error: " + t + " => " + rpl);
		}
		sub.owner = rpl.substring(i+1);
	}

	public void toString(CharList sb) {
		switch (extendType) {
			case EX_SUPERS: sb.append("? super "); break;
			case EX_EXTENDS: sb.append("? extends "); break;
		}

		int start = owner.lastIndexOf('/') + 1;
		sb.append(owner, start, owner.length());

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

	@Override
	public void checkPosition(int env, int pos) {}

	@Override
	public byte genericType() {
		return GENERIC_TYPE;
	}

	@Override
	public Type rawType() {
		return new Type(owner, array&0xFF);
	}

	@Override
	public String owner() {
		return owner;
	}

	@Override
	public void owner(String owner) {
		this.owner = owner;
	}

	@Override
	public int array() {
		return array&0xFF;
	}

	@Override
	public void setArrayDim(int array) {
		if (array > 255 || array < 0)
			throw new ArrayIndexOutOfBoundsException(array);
		this.array = (byte) array;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Generic generic = (Generic) o;

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

	public String toString() {
		CharList cl = IOUtil.getSharedCharBuf();
		toString(cl);
		return cl.toString();
	}
}