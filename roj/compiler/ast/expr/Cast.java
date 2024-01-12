package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.TypeCast;
import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;

import javax.tools.Diagnostic;

/**
 * 强制类型转换
 *
 * @author Roj234
 * @since 2022/2/24 19:48
 */
final class Cast extends UnaryPre {
	TypeCast.Cast cast;

	public Cast(IType type) {
		super((short) 0);
		this.type = type;
	}

	@Override
	public String toString() { return "("+type+") "+right; }

	@NotNull
	@Override
	public ExprNode resolve(CompileContext ctx) {
		IType rType = (right = right.resolve(ctx)).type();
		ctx.resolveType(type);
		cast = ctx.castTo(rType, type, TypeCast.E_DOWNCAST);

		// 这里还要多搞搞
		if (cast.type >= 0) {
			// 可能会影响方法的选择, 加一个cast original type什么的做检测吧
			ctx.report(Diagnostic.Kind.WARNING, "cast.warn.redundant:"+type);
			//return right;
		}

		if (type.isPrimitive() && rType.isPrimitive() && right.isConstant()) {
			AnnVal o = (AnnVal) right.constVal();
			return new Constant(type, switch (TypeCast.getDataCap(type.getActualType())) {
				default -> throw OperationDone.NEVER;
				case 1 -> AnnVal.valueOf((byte)o.asInt());
				case 2 -> AnnVal.valueOf((char)o.asInt());
				case 3 -> AnnVal.valueOf((short)o.asInt());
				case 4 -> AnnVal.valueOf(o.asInt());
				case 5 -> AnnVal.valueOf(o.asLong());
				case 6 -> AnnVal.valueOf(o.asFloat());
				case 7 -> AnnVal.valueOf(o.asDouble());
			});
		}
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		mustBeStatement(noRet);
		right.writeDyn(cw, cast);
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Cast cast = (Cast) o;

		if (!type.equals(cast.type)) return false;
		return right.equalTo(cast.right);
	}

	@Override
	public String setRight(ExprNode right) {
		if (right == null) return "unexpected_2:EOF:type.expr";
		this.right = right;
		return null;
	}
}