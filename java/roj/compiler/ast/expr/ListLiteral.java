package roj.compiler.ast.expr;

import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/11/28 22:45
 */
final class ListLiteral extends Expr {
	private final List<Expr> elements;
	private Generic type;
	public ListLiteral(List<Expr> elements) {this.elements = elements;}

	@Override public String toString() {return elements.toString();}

	@Override public Expr resolve(CompileContext ctx) throws ResolveException {
		IType vType = null;
		boolean allIsConstant = true;

		for (int i = 0; i < elements.size(); i++) {
			var node = elements.get(i).resolve(ctx);
			elements.set(i, node);

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
		cw.ldc(elements.size());
		cw.clazz(ANEWARRAY, type.children.get(0).rawType().owner);

		var vType = type.children.get(0);
		var lc = CompileContext.get();
		for (int i = 0; i < elements.size(); i++) {
			cw.insn(DUP);
			cw.ldc(i);
			var node = elements.get(i);
			node.write(cw, lc.castTo(node.type(), vType, 0));
			cw.insn(AASTORE);
		}

		cw.invokeS("java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ListLiteral that = (ListLiteral) o;

		return elements.equals(that.elements);
	}

	@Override
	public int hashCode() {
		return elements.hashCode();
	}
}