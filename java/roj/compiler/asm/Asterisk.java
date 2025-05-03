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
 * @since 2024/1/27 11:04
 */
public final class Asterisk implements IType {
	// 尽管AnyGeneric有专门的检测，AnyType是没有的
	public static final Asterisk anyType = new Asterisk("<AnyType>");
	public static final Asterisk anyGeneric = new Asterisk("<AnyGeneric>");

	private String typename;
	private IType bound;
	private List<IType> bounds = Collections.emptyList();
	private boolean limited;

	public Asterisk(String typename) {this.typename = typename;}
	/**
	 * "泛型返回值"类型
	 * @param visualType 表现类型 以这个类型去测试能否转换
	 * @param rawType 真实类型 以这个类型的转换写入二进制
	 */
	public static IType genericReturn(IType visualType, IType rawType) {
		if (visualType instanceof Asterisk as) {
			if (as.bounds.size() == 1 && as.bounds.get(0).equals(rawType)) return visualType;
		}
		return new Asterisk(visualType, rawType);
	}
	private Asterisk(IType visualType, IType rawType) {
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
		if (typename != null) sb.append(typename);
		else if (limited) {
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
		if (typename != null) return typename;
		return limited ? "*"+bound : "* extends "+TextUtil.join(bounds, "&");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Asterisk asterisk)) return false;

		if (typename != null) return asterisk.typename != null;
		if (asterisk.typename != null) return false;

		if (limited != asterisk.limited) return false;
		if (!bound.equals(asterisk.bound)) return false;
		return bounds.equals(asterisk.bounds);
	}

	@Override
	public int hashCode() {
		if (typename != null) return 42;

		int hash = bound.hashCode();
		hash = 31 * hash + bounds.hashCode();
		hash = 31 * hash + (limited ? 1 : 0);
		return hash;
	}
}