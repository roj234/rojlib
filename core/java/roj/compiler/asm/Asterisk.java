package roj.compiler.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.api.SwitchableType;
import roj.compiler.api.Types;
import roj.util.OperationDone;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/27 11:04
 */
public final class Asterisk implements IType, SwitchableType {
	// 尽管AnyGeneric有专门的检测，AnyType是没有的
	public static final Asterisk anyType = new Asterisk("<AnyType>");
	public static final Asterisk anyGeneric = new Asterisk("<AnyGeneric>");

	private String typename;
	private IType bound;
	private List<IType> traits = Collections.emptyList();
	private byte type = ASTERISK_TYPE;

	public Asterisk(String typename) {this.typename = typename;}
	public Asterisk(String typename, List<IType> traits) {this.typename = typename;this.traits = traits;}
	/**
	 * "泛型返回值"类型
	 * @param visualType 表现类型 以这个类型去测试能否转换
	 * @param rawType 真实类型 以这个类型的转换写入二进制
	 */
	public static IType genericReturn(IType visualType, IType rawType) {
		if (visualType instanceof Asterisk as) {
			if (as.traits.size() == 1 && as.traits.get(0).equals(rawType)) return visualType;
		}
		return new Asterisk(visualType, rawType);
	}
	public static IType oneOf(List<IType> rawTypes) {return new Asterisk(Types.OBJECT_TYPE, rawTypes);}
	private Asterisk(IType visualType, IType rawType) {
		this.bound = visualType;
		this.traits = Collections.singletonList(rawType);
		this.type = CONCRETE_ASTERISK_TYPE;
	}
	public Asterisk(List<IType> bound) {
		this.bound = bound.get(0);
		this.traits = bound;
	}
	private Asterisk(IType visualType, List<IType> rawTypes) {
		this.bound = visualType;
		this.traits = rawTypes;
		this.type = OR_TYPE;
	}

	public IType getBound() { return bound; }
	public List<IType> getTraits() { return traits; }

	@Override
	public byte genericType() { return type; }
	@Override
	public void toDesc(CharList sb) { if (bound != null) bound.toDesc(sb); else throw new UnsupportedOperationException("Asterisk<"+this+">仅用于类型转换的比较"); }

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
			clone.traits = new ArrayList<>(clone.traits);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw OperationDone.NEVER;
		}
	}

	@Override
	public void toString(CharList sb) {
		if (typename != null) sb.append(typename);
		else if (type == CONCRETE_ASTERISK_TYPE) {
			bound.toString(sb.append('*'));
		} else {
			sb.append("* extends ");
			var itr = traits.iterator();
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
		return type == CONCRETE_ASTERISK_TYPE ? "*"+bound : "* extends "+TextUtil.join(traits, "&");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Asterisk asterisk)) return false;

		if (typename != null) return asterisk.typename != null;
		if (asterisk.typename != null) return false;

		if (type != asterisk.type) return false;
		if (!bound.equals(asterisk.bound)) return false;
		return traits.equals(asterisk.traits);
	}

	@Override
	public int hashCode() {
		if (typename != null) return 42;

		int hash = bound.hashCode();
		hash = 31 * hash + traits.hashCode();
		hash = 31 * hash + type;
		return hash;
	}

	@Override
	public @Nullable String getFieldOwner() {
		return bound == null ? null : bound.owner();
	}
}