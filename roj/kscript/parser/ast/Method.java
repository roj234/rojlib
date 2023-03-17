package roj.kscript.parser.ast;

import roj.asm.Opcodes;
import roj.asm.tree.insn.*;
import roj.asm.util.InsnHelper;
import roj.asm.util.InsnList;
import roj.kscript.Arguments;
import roj.kscript.api.MethodsAPI;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.func.KFunction;
import roj.kscript.type.KNull;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public class Method implements Expression {
	Expression func;
	public List<Expression> args;
	public byte flag;

	public Method(Expression line, List<Expression> args, boolean isNew) {
		this.func = line;
		this.args = args;
		this.flag = (byte) (isNew ? 1 : 0);
	}

	Method() {}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (left == null || getClass() != left.getClass()) return false;
		Method method = (Method) left;
		return method.func.isEqual(func) && (method.flag & 1) == (flag & 1) && ArrayDef.arrayEq(args, method.args);
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		this.func.write(tree, false);
		compressArg();
		for (int i = 0; i < args.size(); i++) {
			Expression expr = args.get(i);
			expr.write(tree, false);
		}

		if ((flag & 1) != 0) {
			tree.New(args.size(), noRet);
		} else {
			tree.Invoke(args.size(), noRet);
		}
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		this.func.toVMCode(ctx, false);

		InsnList list = ctx.list;
		if (args.isEmpty()) {
			list.add(NPInsnNode.of(Opcodes.ACONST_NULL));
		} else {
			compressArg();
			list.add(InsnHelper.loadInt(args.size()));
			list.add(NPInsnNode.of(Opcodes.ICONST_0));
			list.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/vm/KScriptVM", "retainArgHolder", "(IZ)Ljava/util/List;"));
			for (int i = 0; i < args.size(); i++) {
				Expression expr = args.get(i);
				list.add(NPInsnNode.of(Opcodes.DUP));
				list.add(InsnHelper.loadInt(i));
				expr.toVMCode(ctx, false);
				list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "java/util/List", "set", "(ILjava/lang/Object;)Ljava/lang/Object;"));
				list.add(NPInsnNode.of(Opcodes.POP));
			}
		}

		list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asFunction", "()Lroj/kscript/func/KFunction;"));
		list.add(NPInsnNode.of(Opcodes.DUP));

		int i = ctx.createTmpVar("fn");
		InsnHelper.compress(list, Opcodes.ASTORE, i);

		list.add(NPInsnNode.of(Opcodes.SWAP));
		list.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/vm/KScriptVM", "retainJITArgList", "(Lroj/kscript/func/KFunction;Ljava/util/List;)Lroj/kscript/api/ArgList;"));

		int j = ctx.createTmpVar("args");
		InsnHelper.compress(list, Opcodes.ASTORE, j);

		InsnHelper.compress(list, Opcodes.ALOAD, i);
		ctx.endTmpVar(i);
		if ((flag & 1) == 0) {
			// static
			ctx.loadThis();
			InsnHelper.compress(list, Opcodes.ALOAD, j);

			list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/func/KFunction", "invoke", "(Lroj/kscript/api/IObject;Lroj/kscript/api/ArgList;)Lroj/kscript/type/KType;"));
		} else {
			list.add(NPInsnNode.of(Opcodes.DUP));
			list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/func/KFunction", "createInstance", "()Lroj/kscript/type/KType;"));
			list.add(NPInsnNode.of(Opcodes.DUP));
			list.add(new ClassInsnNode(Opcodes.INSTANCEOF, "roj/kscript/api/IObject"));

			LabelInsnNode label = new LabelInsnNode();

			InvokeInsnNode invoke = new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/func/KFunction", "invoke", "(Lroj/kscript/api/IObject;Lroj/kscript/api/ArgList;)Lroj/kscript/type/KType;");

			list.add(new JumpInsnNode(Opcodes.IFNE, invoke)); // instanceof

			list.add(new JumpInsnNode(label));

			InsnHelper.compress(list, Opcodes.ALOAD, j);
			list.add(invoke);

			list.add(label);
		}
		if (noRet) list.add(NPInsnNode.of(Opcodes.POP));

		InsnHelper.compress(list, Opcodes.ALOAD, j);
		list.add(new InvokeInsnNode(Opcodes.INVOKESTATIC, "roj/kscript/vm/KScriptVM", "releaseArgList", "(Lroj/kscript/api/ArgList;)V"));
		ctx.endTmpVar(j);
	}

	private void compressArg() {
		if ((flag & 2) == 0) {
			List<Expression> args = this.args;
			for (int i = 0; i < args.size(); i++) {
				Expression cp = args.get(i).compress();
				if (!cp.isConstant()) flag |= 4;
				if (cp instanceof Spread) flag |= 8; // dynamic
				args.set(i, cp);
			}
			flag |= 2;
		}
	}

	@Nonnull
	@Override
	public Expression compress() {
		func = func.compress();
		compressArg();

		if ((flag & 8) != 0) {
			return new MethodSpreaded(this);
		}

		CharSequence sb = getFuncPath(func);
		if (sb == null) return this;
		if ((flag & 4) == 0) {
			KType pc = MethodsAPI.preCompute(sb, getCst());
			if (pc != null) {
				return Constant.valueOf(pc);
			}
		}

		Expression expr = MethodsAPI.getDedicated(sb, this);
		return expr == null ? this : expr;
	}

	private static CharSequence getFuncPath(Expression func) {
		if (!(func instanceof Field)) {
			return null;
		}
		Field f = (Field) func;

		List<String> fieldDot = new ArrayList<>();
		fieldDot.add(f.name);
		while (f.parent instanceof Field) {
			f = (Field) f.parent;
			fieldDot.add(f.name);
		}

		if (!(f instanceof Variable)) {
			return null;
		}

		Collections.reverse(fieldDot);
		StringBuilder sb = new StringBuilder(fieldDot.size() * 5);
		for (String s : fieldDot) {
			sb.append(s).append('.');
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb;
	}

	@Override
	public KType compute(Map<String, KType> param) {
		List<KType> vals = new ArrayList<>(args.size());
		for (int i = 0; i < args.size(); i++) {
			vals.add(args.get(i).compute(param));
		}

		CharSequence sb = getFuncPath(func);
		KType nf = MethodsAPI.preCompute(sb, vals);
		if (nf != null) return nf;

		try {
			KFunction func = this.func.compute(param).asFunction();
			return func.invoke(KNull.NULL, new Arguments(vals));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Not a simple expression");
		}
	}

	@Override
	public byte type() {
		if ((flag & 12) != 0) return -1;

		List<KType> exprs = getCst();

		KType res = MethodsAPI.preCompute(getFuncPath(func), getCst());

		return res == null ? -1 : Constant.typeOf(res);
	}

	private List<KType> getCst() {
		List<KType> exprs = new ArrayList<>(args.size());
		for (int i = 0; i < args.size(); i++) {
			exprs.add(args.get(i).asCst().val());
		}
		return exprs;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if ((flag & 1) != 0) {
			sb.append("new ");
		}
		sb.append(func.toString()).append('(');
		for (Expression expr : args) {
			sb.append(expr).append(',');
		}
		if (!args.isEmpty()) {
			sb.deleteCharAt(sb.length() - 1);
		}

		return sb.append(')').toString();
	}
}
