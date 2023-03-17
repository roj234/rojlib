package roj.asm.tree;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.collect.SimpleList;
import roj.util.TypedName;

import java.util.List;

/**
 * {@link roj.asm.tree.ConstantData}中的简单方法, 不解析{@link Attribute}
 *
 * @author Roj234
 * @version 2.1
 * @since 2021/6/18 9:51
 */
public final class RawMethod extends RawNode implements MethodNode {
	public RawMethod(int accesses, CstUTF name, CstUTF typeName) {
		super(accesses, name, typeName);
	}

	String owner;
	SimpleList<Type> params;

	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) { return Parser.parseAttribute(this, cp, type, attributes,Signature.METHOD); }

	@Internal
	public void cn(String cn) { this.owner = cn; }
	@Override
	public String ownerClass() { return owner; }

	public List<Type> parameters() {
		if (params == null) {
			params = new SimpleList<>();
			TypeHelper.parseMethod(type.str(),params);
			params.i_setSize(params.size()-1);
		}
		return params;
	}
	public Type returnType() {
		if (params != null) return (Type) params.getRawArray()[params.size()];
		return TypeHelper.parseReturn(type.str());
	}

	public int type() { return Parser.MTYPE_SIMPLE; }
	public String toString() {
		return AccessFlag.toString(access, AccessFlag.TS_METHOD) + TypeHelper.humanize(TypeHelper.parseMethod(type.str()), name.str(), true);
	}
}