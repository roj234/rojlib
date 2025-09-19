package roj.compiler.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.api.SwitchableType;
import roj.compiler.api.Types;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.OperationDone;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/1/27 11:04
 */
public final class WildcardType implements IType, SwitchableType {
	// 尽管AnyGeneric有专门的检测，AnyType是没有的
	public static final WildcardType anyType = new WildcardType("<AnyType>");
	public static final WildcardType anyGeneric = new WildcardType("<AnyGeneric>");

	private String typename;
	private IType bound;
	private List<IType> traits = Collections.emptyList();
	private byte kind = BOUNDED_WILDCARD;

	public WildcardType(String typename) {this.typename = typename;}
	public WildcardType(String typename, List<IType> traits) {this.typename = typename;this.traits = traits;}
	/**
	 * "泛型返回值"类型
	 * @param visualType 表现类型 以这个类型去测试能否转换
	 * @param rawType 真实类型 以这个类型的转换写入二进制
	 */
	public static IType genericReturn(IType visualType, IType rawType) {
		if (visualType instanceof WildcardType as) {
			if (as.traits.size() == 1 && as.traits.get(0).equals(rawType)) return visualType;
		}
		return new WildcardType(visualType, rawType);
	}
	public static IType oneOf(List<IType> rawTypes) {return new WildcardType(Types.OBJECT_TYPE, rawTypes);}
	private WildcardType(IType visualType, IType rawType) {
		this.bound = visualType;
		this.traits = Collections.singletonList(rawType);
		this.kind = CAPTURED_WILDCARD;
	}
	public WildcardType(List<IType> bound) {
		this.bound = bound.get(0);
		this.traits = bound;
	}
	private WildcardType(IType visualType, List<IType> rawTypes) {
		this.bound = visualType;
		this.traits = rawTypes;
		this.kind = UNION_TYPE;
	}

	public IType getBound() { return bound; }
	public List<IType> getTraits() { return traits; }

	@Override
	public byte kind() { return kind; }
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
	public WildcardType clone() {
		if (bound == null) return this;
		try {
			WildcardType clone = (WildcardType) super.clone();
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
		else if (kind == CAPTURED_WILDCARD) {
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
		return kind == CAPTURED_WILDCARD ? "*"+bound : "* extends "+TextUtil.join(traits, "&");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof WildcardType wildcard)) return false;

		if (typename != null) return wildcard.typename != null;
		if (wildcard.typename != null) return false;

		if (kind != wildcard.kind) return false;
		if (!bound.equals(wildcard.bound)) return false;
		return traits.equals(wildcard.traits);
	}

	@Override
	public int hashCode() {
		if (typename != null) return 42;

		int hash = bound.hashCode();
		hash = 31 * hash + traits.hashCode();
		hash = 31 * hash + kind;
		return hash;
	}

	@Override
	public @Nullable String getFieldOwner() {
		return bound == null ? null : bound.owner();
	}
}