package roj.asm.attr;

import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstNameAndType;
import roj.asm.type.Type;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class EnclosingMethod extends Attribute {
	public static final String PREDEFINED = null;
	public EnclosingMethod() {}
	public EnclosingMethod(CstClass clazz, CstNameAndType method) {
		super();
		// 当并非被代码文本意义上的'函数'创建时(etc {}, static{}, x = ?), 需为Null
		owner = clazz.name().str();
		if (method == null) {
			name = PREDEFINED;
		} else {
			name = method.name().str();
			parameters = Type.methodDesc(method.rawDesc().str());
			returnType = parameters.remove(parameters.size() - 1);
		}
	}

	public String owner, name;
	public List<Type> parameters;
	public Type returnType;

	@Override
	public String name() { return "EnclosingMethod"; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getClassId(owner));
		if (PREDEFINED == name) {
			w.putShort(0);
		} else {
			parameters.add(returnType);
			w.putShort(pool.getDescId(name, Type.toMethodDesc(parameters)));
			parameters.remove(parameters.size() - 1);
		}
	}

	public String toString() {
		if (PREDEFINED == name) return "EnclosingMethod: Immediately";
		final StringBuilder sb = new StringBuilder().append("EnclosingMethod: ").append(returnType).append(' ').append(owner).append('.').append(name).append('(');
		for (Type par : parameters) {
			sb.append(par).append(", ");
		}
		if (!parameters.isEmpty()) sb.delete(sb.length() - 2, sb.length());
		return sb.append(')').toString();
	}
}