package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.collect.MyBitSet;
import roj.compiler.asm.LPSignature;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.text.TextUtil;

import java.util.List;

import static roj.compiler.ast.GeneratorUtil.RETURNSTACK_TYPE;

/**
 * @author Roj234
 * @since 2024/6/9 1:42
 */
final class MultiReturn extends Expr {
	private final List<Expr> values;
	private MyBitSet unsafeCall;
	private Generic exactType;

	public MultiReturn(List<Expr> values) {this.values = values;}

	@Override public String toString() {return "{"+TextUtil.join(values, ", ")+"}";}

	@Override
	public Expr resolve(LocalContext ctx) throws ResolveException {
		if (values.size() == 0 || values.size() > 255) ctx.report(this, Kind.ERROR, "泛型过长："+values.size());

		for (int i = 0; i < values.size(); i++) {
			Expr node = values.get(i).resolve(ctx);
			values.set(i, node);
			if (!(node instanceof LocalVariable) && !node.isConstant()) {
				if (unsafeCall == null)
					unsafeCall = new MyBitSet(values.size());
				unsafeCall.add(i);
				ctx.report(this, Kind.WARNING, "multiReturn.sideEffect");
			}

			if (RETURNSTACK_TYPE.equals(node.type().owner())) {
				ctx.report(this, Kind.ERROR, "multiReturn.russianToy");
			}
		}

		LPSignature node = ctx.file.currentNode;
		if (node != null) {
			IType type1 = node.values.get(node.values.size() - 1);
			if (type1.owner().equals(RETURNSTACK_TYPE) && type1 instanceof Generic g) {
				exactType = g;
				return this;
			}
		}

		ctx.report(this, Kind.ERROR, "multiReturn.incompatible");
		return NaE.RESOLVE_FAILED;
	}

	@Override public IType type() {return exactType;}

	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		var ctx = LocalContext.get();

		if (unsafeCall != null) for (var itr = unsafeCall.iterator(); itr.hasNext(); ) {
			int i = itr.nextInt();

			Expr expr = values.get(i);
			Variable variable = ctx.bp.tempVar(expr.type());
			expr.write(cw);
			cw.store(variable);

			values.set(i, new LocalVariable(variable));
		}

		cw.ldc(exactType.children.hashCode());
		cw.invokeS(RETURNSTACK_TYPE, "get", "(I)L"+RETURNSTACK_TYPE+";");
		for (int i = 0; i < values.size(); i++) {
			var node = values.get(i);
			var type = exactType.children.get(i);
			node.write(cw, ctx.castTo(node.type(), type, 0));
			cw.invokeV(RETURNSTACK_TYPE, "put", "("+(type.isPrimitive() ? (char) type.rawType().type : "Ljava/lang/Object;")+")L"+RETURNSTACK_TYPE+";");
		}
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {throw new ResolveException("未预料的情况");}
}