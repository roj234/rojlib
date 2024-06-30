package roj.compiler.asm;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/27 0001 11:04
 */
public final class Asterisk implements IType {
	public static final Asterisk anyType = new Asterisk();
	public static final Asterisk anyGeneric = new Asterisk();

	private IType bound;
	private List<IType> bounds = Collections.emptyList();
	private boolean limited;

	private Asterisk() {}
	/**
	 * "泛型返回值"类型
	 * @param visualType 表现类型 以这个类型去测试能否转换
	 * @param rawType 真实类型 以这个类型的转换写入二进制
	 */
	public Asterisk(IType visualType, IType rawType) {
		this.bound = visualType;
		this.bounds = Collections.singletonList(rawType);
		this.limited = true;
	}
	public Asterisk(List<IType> bound) {
		this.bound = bound.get(0);
		this.bounds = bound;
		this.limited = false;
	}

	public IType getBound() { return bound; }
	public List<IType> getBounds() { return bounds; }

	@Override
	public byte genericType() { return limited ? CONCRETE_ASTERISK_TYPE : ASTERISK_TYPE; }
	@Override
	public void toDesc(CharList sb) { throw new UnsupportedOperationException("Asterisk仅用于类型转换的比较"); }
	@Override
	public void checkPosition(int env, int pos) {throw new UnsupportedOperationException("Asterisk仅用于类型转换的比较");}

	@Override
	public boolean isPrimitive() { return bound != null && bound.isPrimitive(); }
	@Override
	public Type rawType() { return bound != null ? bound.rawType() : IType.super.rawType(); }
	@Override
	public int array() { return bound != null ? bound.array() : 0; }
	@Override
	public void setArrayDim(int array) {
		if (bound != null) bound.setArrayDim(array);
		else IType.super.setArrayDim(array);
	}
	@Override
	public String owner() { return bound != null ? bound.owner() : IType.super.owner(); }
	@Override
	public void owner(String owner) {
		if (bound != null) bound.owner(owner);
		else IType.super.owner(owner);
	}

	@Override
	public Asterisk clone() {
		if (bound == null) return this;
		try {
			Asterisk clone = (Asterisk) super.clone();
			clone.bound = clone.bound.clone();
			clone.bounds = new SimpleList<>(clone.bounds);
			return clone;
		} catch (CloneNotSupportedException e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}

	@Override
	public void toString(CharList sb) {
		if (this == anyType) sb.append("<AnyType>");
		if (this == anyGeneric) sb.append("<AnyGeneric>");
		if (limited) {
			bound.toString(sb.append('*'));
		} else {
			sb.append("* extends ");
			var itr = bounds.iterator();
			while (true) {
				itr.next().toString(sb);
				if (!itr.hasNext()) break;
				sb.append('&');
			}
		}
	}
	@Override
	public String toString() {
		if (this == anyType) return "<AnyType>";
		if (this == anyGeneric) return "<AnyGeneric>";
		return limited ? "*"+bound : "* extends "+TextUtil.join(bounds, "&");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Asterisk asterisk)) return false;
		return bound == null ? asterisk.bound == null : bound.equals(asterisk.bound);
	}

	@Override
	public int hashCode() { return bound == null ? 1 : bound.hashCode(); }
}