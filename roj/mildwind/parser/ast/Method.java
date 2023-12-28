package roj.mildwind.parser.ast;

import roj.mildwind.JsContext;
import roj.mildwind.api.ArgListInternal;
import roj.mildwind.api.MethodsAPI;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;

import javax.annotation.Nonnull;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Method implements Expression {
	Expression func;
	List<Expression> args;

	int flag;
	static final byte NEW = 1, DYNAMIC = 2, SPREAD = 4;

	public Method(Expression line, List<Expression> args, boolean isNew) {
		this.func = line;
		this.args = args;
		this.flag = isNew ? NEW : 0;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (left == null || getClass() != left.getClass()) return false;
		Method method = (Method) left;
		return method.func.isEqual(func) && (method.flag&1) == (flag&1) && ArrayDef.arrayEq(args, method.args);
	}

	@Override
	public void write(JsMethodWriter tree, boolean noRet) {
		if ((flag&1) == 0) { // call
			if (func instanceof Variable) {
				func.write(tree, false);
				tree.one(ALOAD_1); // this (global)
			} else if (func instanceof ArrayGet) {
				ArrayGet get = (ArrayGet) func;

				get.array.write(tree, false);
				int id = tree.getTmpVar();
				tree.vars(ASTORE, id);
				tree.vars(ALOAD, id);

				get.index.write(tree, false);
				get.writeExecute(tree, false);

				tree.vars(ALOAD, id); // this (array)
				tree.delTmpVar(id);
			} else {
				Field get = (Field) func;

				get.writeLoad(tree);
				int id = tree.getTmpVar();
				tree.vars(ASTORE, id);
				tree.vars(ALOAD, id);

				get.writeExecute(tree, false);

				tree.vars(ALOAD, id); // this (object ref)
				tree.delTmpVar(id);
			}
		} else { // new
			func.write(tree, false);
		}

		if (args.isEmpty()) {
			tree.field(GETSTATIC, "roj/mildwind/api/Arguments", "EMPTY", "Lroj/mildwind/api/Arguments;");
		} else {
			tree.ldc(args.size());
			tree.invokeS("roj/mildwind/JsContext", "getArguments", "(I)Lroj/mildwind/api/ArgListInternal;");

			for (int i = 0; i < args.size(); i++) {
				Expression arg = args.get(i);
				arg.write(tree, false);
				tree.invokeV("roj/mildwind/api/ArgListInternal", arg instanceof Spread ? "pushAll" : "push", "(Lroj/mildwind/type/JsObject;)Lroj/mildwind/api/ArgListInternal;");
			}
		}

		// todo: closure may be finished in ldc expression?
		if ((flag & 1) != 0) tree.invokeV("roj/mildwind/type/JsObject", "_new", "(Lroj/mildwind/api/Arguments;)Lroj/mildwind/type/JsObject;");
		else tree.invokeV("roj/mildwind/type/JsObject", "_invoke", "(Lroj/mildwind/type/JsObject;Lroj/mildwind/api/Arguments;)Lroj/mildwind/type/JsObject;");

		if (noRet) tree.one(POP);
	}

	@Nonnull
	@Override
	public Expression compress() {
		func = func.compress();

		List<Expression> args = this.args;
		for (int i = 0; i < args.size(); i++) {
			Expression cp = args.get(i).compress();
			args.set(i, cp);

			if (!cp.isConstant()) flag |= DYNAMIC;
			if (cp instanceof Spread) flag |= SPREAD; // dynamic
		}

		if ((flag & DYNAMIC) == 0) {
			if (func.isConstant() && MethodsAPI.isPureFunction((JsFunction) func.constVal())) {
				return Constant.valueOf(compute(null));
			}
		}

		return this;
	}

	@Override
	public JsObject compute(JsContext ctx) {
		ArgListInternal vals = JsContext.getArguments(args.size());
		for (int i = 0; i < args.size(); i++) {
			Expression arg = args.get(i);

			if (arg instanceof Spread) vals.pushAll(arg.compute(ctx));
			else vals.push(arg.compute(ctx));
		}

		return func.compute(ctx)._invoke(null, vals);
	}

	//public Type type() { return Type.OBJECT; }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if ((flag & 1) != 0) sb.append("new ");

		sb.append(func.toString()).append('(');
		if (!args.isEmpty()) {
			int i = 0;
			for (;;) {
				Expression expr = args.get(i);
				sb.append(expr);
				if (++i == args.size()) break;
				sb.append(',');
			}
		}
		return sb.append(')').toString();
	}
}