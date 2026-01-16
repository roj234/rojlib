package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.type.IType;
import roj.asm.type.ParameterizedType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.WildcardType;
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
	private ParameterizedType type;
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

		type = new ParameterizedType("java/util/List", 0, ParameterizedType.NO_WILDCARD);
		if (vType == null) {
			type.addChild(WildcardType.anyType);
		} else {
			var wrapper = Type.getWrapper(vType);
			type.addChild(wrapper == null ? vType : wrapper);
		}
		return this;
	}

	@Override public IType type() {return type;}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);
		cw.ldc(elements.size());
		cw.clazz(ANEWARRAY, type.typeParameters.get(0).rawType().owner);

		var vType = type.typeParameters.get(0);
		var ctx = cw.ctx;
		for (int i = 0; i < elements.size(); i++) {
			cw.insn(DUP);
			cw.ldc(i);
			var node = elements.get(i);
			node.write(cw, ctx.castTo(node.type(), vType, 0));
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