package roj.compiler.ast.block;

import roj.asm.frame.Var2;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.compiler.ast.expr.ExprNode;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2023/11/4 0004 17:33
 */
public class VarDefNode extends BlockNode {
	Type defineType;
	Var2 mergeType;
	int flags;
	String name;
	ExprNode directDefine;

	@Override
	public void toString(CharList sb, int depth) {
		AccessFlag.toString(flags, AccessFlag.TS_FIELD, sb);
		sb.append(defineType).append(' ').append(name);
		if (directDefine != null) sb.append(" = ").append(directDefine);
	}
}
