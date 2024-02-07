package roj.compiler.ast.block;

import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.compiler.ast.expr.ExprNode;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2023/11/4 0004 17:33
 */
public class VarDefNode extends BlockNode {
	Type defineType;
	int flags;
	String name;
	ExprNode directDefine;

	@Override
	public void toString(CharList sb, int depth) {
		Opcodes.showModifiers(flags, Opcodes.ACC_SHOW_FIELD, sb);
		sb.append(defineType).append(' ').append(name);
		if (directDefine != null) sb.append(" = ").append(directDefine);
	}
}