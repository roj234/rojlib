package roj.compiler.ast.expr;

import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/11/28 22:45
 */
final class EasyList extends ExprNode {
	private final List<ExprNode> exprList;
	private Generic type;
	public EasyList(List<ExprNode> exprList) {this.exprList = exprList;}

	@Override public String toString() {return exprList.toString();}

	@Override public ExprNode resolve(LocalContext ctx) throws ResolveException {
		IType vType = null;
		boolean allIsConstant = true;

		for (int i = 0; i < exprList.size(); i++) {
			var node = exprList.get(i).resolve(ctx);
			exprList.set(i, node);

			if (!node.isConstant()) allIsConstant = false;

			if (vType == null) vType = node.type();
			else vType = ctx.getCommonParent(vType, node.type());
		}

		type = new Generic("java/util/List", 0, Generic.EX_NONE);
		if (vType == null) {
			type.addChild(Asterisk.anyType);
		} else {
			var wrapper = TypeCast.getWrapper(vType);
			type.addChild(wrapper == null ? vType : wrapper);
		}
		return this;
	}

	@Override public IType type() {return type;}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		cw.ldc(exprList.size());
		cw.clazz(ANEWARRAY, type.children.get(0).rawType().owner);

		var vType = type.children.get(0);
		var lc = LocalContext.get();
		for (int i = 0; i < exprList.size(); i++) {
			cw.one(DUP);
			cw.ldc(i);
			var node = exprList.get(i);
			node.write(cw, lc.castTo(node.type(), vType, 0));
			cw.one(AASTORE);
		}

		cw.invokeS("java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;");
	}
}