package roj.asm.attr;

import org.jetbrains.annotations.Nullable;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstNameAndType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class EnclosingMethod extends Attribute {
	public static final String PREDEFINED = null;
	public EnclosingMethod() {}
	public EnclosingMethod(CstClass clazz, CstNameAndType method) {
		owner = clazz.value().str();
		// 当并非被代码文本意义上的'函数'创建时(etc {}, static{}, x = ?), 需为Null
		if (method == null) {
			name = PREDEFINED;
		} else {
			name = method.name().str();
			rawDesc = method.rawDesc().str();
		}
	}

	public String owner;
	@Nullable public String name;
	private String rawDesc;

	public String rawDesc() {return rawDesc;}
	public void rawDesc(String desc) {this.rawDesc = desc;}


	@Override
	public String name() { return "EnclosingMethod"; }

	@Override
	public void toByteArrayNoHeader(DynByteBuf w, ConstantPool pool) {
		w.putShort(pool.getClassId(owner));
		w.putShort(PREDEFINED == name ? 0 : pool.getDescId(name, rawDesc));
	}

	public String toString() {
		if (PREDEFINED == name) return "EnclosingMethod: Immediately";
		ArrayList<Type> argumentTypes = new ArrayList<>();
		var returnType = Type.getArgumentTypes(rawDesc, argumentTypes);

		var sb = new StringBuilder().append("EnclosingMethod: ").append(returnType).append(' ').append(owner).append('.').append(name).append('(');
		for (Type par : argumentTypes) sb.append(par).append(", ");
		if (!argumentTypes.isEmpty()) sb.delete(sb.length() - 2, sb.length());
		return sb.append(')').toString();
	}
}