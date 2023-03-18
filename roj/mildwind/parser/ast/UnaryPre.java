package roj.mildwind.parser.ast;

import roj.asm.Opcodes;
import roj.asm.visitor.Label;
import roj.config.word.NotStatementException;
import roj.mildwind.JsContext;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.JSLexer;
import roj.mildwind.type.JsBool;
import roj.mildwind.type.JsObject;
import roj.mildwind.type.Type;

import javax.annotation.Nonnull;

import static roj.asm.Opcodes.*;
import static roj.mildwind.parser.JSLexer.*;

/**
 * 一元运算符 ++i --i !i ~i
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class UnaryPre implements Expression {
	private final short op;
	private Expression right;

	public UnaryPre(short op) {
		switch (op) {
			default: throw new IllegalArgumentException("Unsupported operator " + op);
			case logic_not:
			case rev:
			case inc: case dec:
			case add: case sub:
		}
		this.op = op;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Type type() {
		switch (op) {
			case logic_not: default: return Type.BOOL;
			case rev: return Type.INT;
			case add:
				// +"" === 0
				if (right.isConstant() && right.constVal().klassType() == Type.STRING && right.constVal().toString().isEmpty())
					return Type.INT;
			case sub:
			case inc: case dec: return right.type() == Type.INT ? Type.INT : Type.DOUBLE;
		}
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		switch (op) {
			case inc: case dec: Assign.writeIncrement((LoadExpression) right, tree, noRet, false, op == JSLexer.inc ? 1 : -1, false); return;
		}

		if (noRet) throw new NotStatementException();

		right.write(tree, false);

		switch (op) {
			case logic_not: default:
				tree.invokeV("roj/mildwind/type/JsObject", "asBool", "()I");
				Label ifFalse = new Label(), end = new Label();
				tree.jump(IFEQ, ifFalse);
				tree.field(GETSTATIC, "roj/mildwind/type/JsBool", "TRUE", "Lroj/mildwind/type/JsBool;");
				tree.jump(Opcodes.GOTO, end);
				tree.label(ifFalse);
				tree.field(GETSTATIC, "roj/mildwind/type/JsBool", "FALSE", "Lroj/mildwind/type/JsBool;");
				tree.label(end);
				tree.one(IXOR);
			break;
			case rev: tree.invokeS("roj/mildwind/type/JsObject", "op_rev", "(Lroj/mildwind/type/JsObject;)Lroj/mildwind/type/JsObject;"); break;
			case add:
				tree.ldc(0);
				tree.invokeV("roj/mildwind/type/JsObject", "op_inc", "(I)Lroj/mildwind/type/JsObject;");
			break;
			case sub: tree.invokeV("roj/mildwind/type/JsObject", "op_neg", "()Lroj/mildwind/type/JsObject;"); break;
		}
	}

	@Override
	public boolean isConstant() { return op != inc && op != dec && right.isConstant(); }
	@Override
	public JsObject constVal() {
		Expression v = compress();
		return v == this ? Expression.super.constVal() : v.constVal(); }

	@Nonnull
	@Override
	public Expression compress() {
		if (right == null) throw new IllegalArgumentException("Missing right");

		if (op == sub && right instanceof UnaryPre) {
			UnaryPre p = (UnaryPre) right;
			if (p.op == sub) { // 双重否定
				Expression expr = p.right.compress();
				return expr.isConstant()
					? expr.constVal().type().num()
						? expr
						: Constant.valueOf(expr.constVal().op_inc(0))
					: new AsNumber(expr);
			}
		}

		// inc/dec不能对常量使用, 所以不用管了
		if (!(right = right.compress()).isConstant()) return this;

		JsObject val = right.constVal();
		switch (op) {
			default: return this;

			case logic_not: return Constant.valueOf(val.asBool() == 0);
			case rev: return Constant.valueOf(JsObject.op_rev(val));
			case sub: return Constant.valueOf(val.type() == Type.INT ? JsContext.getInt(-val.asInt()) : JsContext.getDouble(-val.asDouble()));
			case add: return Constant.valueOf(val.op_inc(0));
		}
	}

	@Override
	public JsObject compute(JsObject ctx) {
		JsObject v;
		switch (op) {
			case logic_not: return JsBool.valueOf(right.compute(ctx).asBool() == 0 ? 1 : 0);
			case rev: return JsObject.op_rev(right.compute(ctx));
			case add:
				v = right.compute(ctx);
				if (v.klassType().num()) return v;
				return v.type() == Type.INT ? JsContext.getInt(v.asInt()) : JsContext.getDouble(v.asDouble());
			case sub: return right.compute(ctx).op_neg();
		}

		int dt = op == JSLexer.inc ? 1 : -1;
		v = right.compute(ctx).op_inc(dt);
		((LoadExpression) right).computeAssign(ctx, v);

		return v;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof UnaryPre)) return false;
		UnaryPre left1 = (UnaryPre) left;
		return left1.right.isEqual(right) && left1.op == op;
	}

	@Override
	public String toString() { return byId(op) + ' ' + right; }

	public String setRight(Expression right) {
		if (right == null) return "upf: right is null";

		if (!(right instanceof LoadExpression)) {
			switch (op) {
				case inc: case dec: return "unary.expecting_variable";
			}
		}

		this.right = right;
		return null;
	}
}
