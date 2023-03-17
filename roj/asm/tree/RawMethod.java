package roj.asm.tree;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.Parser;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;

import java.util.List;

/**
 * {@link roj.asm.tree.ConstantData}中的简单方法, 不解析{@link Attribute}
 *
 * @author Roj234
 * @version 2.0
 * @since 2021/6/18 9:51
 */
public final class RawMethod extends RawNode implements MethodNode {
	public RawMethod(int accesses, CstUTF name, CstUTF typeName) {
		super(accesses, name, typeName);
	}

	String owner;
	List<Type> params;

	@Override
	public String ownerClass() {
		return owner;
	}

	@Override
	public List<Type> parameters() {
		if (params == null) {
			params = TypeHelper.parseMethod(this.type.getString());
			params.remove(params.size() - 1);
		}
		return params;
	}

	@Override
	public Type getReturnType() {
		return TypeHelper.parseReturn(type.getString());
	}

	@Internal
	public void cn(String owner) {
		this.owner = owner;
	}

	@Override
	public int type() {
		return Parser.MTYPE_SIMPLE;
	}

	@Override
	public String toString() {
		return AccessFlag.toString(accesses, AccessFlag.TS_METHOD) + TypeHelper.humanize(TypeHelper.parseMethod(type.getString()), name.getString(), false);
	}
}