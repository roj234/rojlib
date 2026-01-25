package roj.compiler.types;

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
 * 复合类型：包含泛型返回值、交集和并集类型
 * @author Roj234
 * @since 2024/1/27 11:04
 */
public final class CompoundType implements IType, SwitchableType {
	private final byte kind;
	private IType bound;
	private List<IType> traits;

	/**
	 * "泛型返回值"类型
	 * @param visualType 表现类型 以这个类型去测试能否转换
	 * @param rawType 真实类型 以这个类型的转换写入二进制
	 */
	public static IType genericReturn(IType visualType, IType rawType) {
		if (visualType instanceof CompoundType as) {
			// flatten
			if (as.traits.size() == 1 && as.traits.get(0).equals(rawType)) return visualType;
		}
		return new CompoundType(visualType, rawType);
	}
	private CompoundType(IType visualType, IType rawType) {
		this.bound = visualType;
		this.traits = Collections.singletonList(rawType);
		this.kind = CAPTURED_WILDCARD;
	}

	/**
	 * "交集"类型 （只具备输入中任意一个的类型，且需要显式转换）
	 * @param candidates 所有类型的集合
	 * @apiNote 文件中类型是Object
	 */
	public static IType union(List<IType> candidates) {return new CompoundType(Types.OBJECT_TYPE, candidates);}
	private CompoundType(IType visualType, List<IType> rawTypes) {
		this.bound = visualType;
		this.traits = rawTypes;
		this.kind = UNION_TYPE;
	}

	/**
	 * "并集"类型 （同时具备所有输入的类型）
	 * @param bounds 所有类型的集合
	 * @apiNote bounds中至多出现一个非接口类型，若出现必须是bounds[0]
	 * @apiNote 文件中类型是bounds[0]
	 */
	public static IType intersection(List<IType> bounds) {return new CompoundType(bounds);}
	private CompoundType(List<IType> bounds) {
		this.bound = bounds.get(0);
		this.traits = bounds;
		this.kind = BOUNDED_WILDCARD;
	}

	// FIXME 现在有个问题就是在大部分需要 instanceof ParameterizedType 的地方，都需要检测该类型并getBound
	public IType getBound() { return bound; }
	public List<IType> getTraits() { return traits; }

	@Override
	public byte kind() { return kind; }
	@Override
	public void toDesc(CharList sb) {
		if (kind == BOUNDED_WILDCARD) {
			sb.append('W');
			for (IType trait : traits) {
				trait.toDesc(sb);
			}
			sb.append('M');
			return;
		}

 		bound.toDesc(sb);
	}

	@Override public boolean isPrimitive() { return bound.isPrimitive(); }
	@Override public Type rawType() { return bound.rawType(); }
	@Override public int array() { return bound.array(); }
	@Override public void setArrayDim(int array) {bound.setArrayDim(array);}
	@Override public String owner() { return bound.owner(); }
	@Override public void owner(String owner) {bound.owner(owner);}

	@Override
	public CompoundType clone() {
		try {
			CompoundType clone = (CompoundType) super.clone();
			clone.traits = new ArrayList<>(clone.traits);
			if (kind == CAPTURED_WILDCARD) {
				clone.bound = bound.clone();
			} else {
				List<IType> copy = clone.traits;
				for (int i = 0; i < copy.size(); i++) {
					copy.set(i, copy.get(i).clone());
				}
				clone.bound = clone.traits.get(0);
			}
			return clone;
		} catch (CloneNotSupportedException e) {
			throw OperationDone.NEVER;
		}
	}

	@Override
	public void toString(CharList sb) {
		if (kind == CAPTURED_WILDCARD) {
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
		return kind == CAPTURED_WILDCARD ? "*"+bound : "* extends "+TextUtil.join(traits, "&");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CompoundType wildcard)) return false;

		if (kind != wildcard.kind) return false;
		if (!bound.equals(wildcard.bound)) return false;
		return traits.equals(wildcard.traits);
	}

	@Override
	public int hashCode() {
		int hash = bound.hashCode();
		hash = 31 * hash + traits.hashCode();
		hash = 31 * hash + kind;
		return hash;
	}

	@Override
	public @Nullable String getFieldOwner() {return bound.owner();}
}