package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.LPSignature;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.util.OperationDone;
import roj.text.ParseException;
import roj.text.TextUtil;

import java.util.Arrays;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/6/3 2:27
 */
final class NewAnonymousClass extends Expr {
	// 创建该匿名类的表达式 new XXX<>(1,2,3)
	private final Invoke newExpr;
	// 匿名类的实体 { ... }
	private final CompileUnit klass;
	// 匿名类的上下文
	private NestContext nestContext;

	NewAnonymousClass(Invoke cur, CompileUnit klass) {
		this.newExpr = cur;
		this.klass = klass;
	}

	@Override public String toString() {return newExpr+"{"+klass+"}";}
	@Override public IType type() {return newExpr.type();}

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		List<Expr> args = newExpr.args;
		// writable
		if (!(args instanceof ArrayList<Expr>))
			newExpr.args = args = new ArrayList<>(args);

		List<IType> argTypes = Arrays.asList(new IType[args.size()]);
		for (int i = 0; i < args.size(); i++) {
			Expr node = args.get(i).resolve(ctx);
			args.set(i, node);
			argTypes.set(i, node.type());
		}

		IType parentType = ctx.resolveType((IType) newExpr.expr);

		// 实际上可以处理它，for assign only
		if (Inferrer.hasUndefined(parentType)) ctx.report(this, Kind.ERROR, "invoke.noExact");

		String parent = parentType.owner();
		ClassNode info = ctx.compiler.resolve(parent);
		if (info == null) {
			ctx.reportNoSuchType(this, Kind.ERROR, parentType);
			return NaE.resolveFailed(this);
		}

		if (parentType.genericType() != 0) {
			var sign = new LPSignature(Signature.CLASS);
			if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) sign._impl(parentType);
			else sign._add(parentType);
			klass.signature = sign;
			klass.addAttribute(sign);
		}

		ctx.compiler.addGeneratedClass(klass);
		var next = CompileContext.push();
		next.setClass(klass);

		MethodWriter constructor;
		if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) {
			klass.addInterface(parent);

			// 接口是不存在构造器的，只能这么写
			if (!args.isEmpty()) {
				next.report(this, Kind.ERROR, "invoke.incompatible.single", info, "invoke.constructor",
						"[\"  \",invoke.except,\" \",invoke.no_param,\"\n"+
						"  \",invoke.found\" "+TextUtil.join(argTypes, ", ")+"\"]");
				return NaE.resolveFailed(this);
			}

			constructor = klass.newWritableMethod(ACC_PUBLIC, "<init>", "()V");
			constructor.visitSize(1, 1);
			constructor.insn(ALOAD_0);
			constructor.invoke(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		} else {
			klass.parent(parent);

			var r = next.getMethodListOrReport(info, "<init>", newExpr).findMethod(next, argTypes, ComponentList.THIS_ONLY);
			if (r == null) return NaE.resolveFailed(this);

			r.addExceptions(next, false);
			klass.j11PrivateConstructor(r.method);
			constructor = klass.createDelegation(Opcodes.ACC_SYNTHETIC, r.method, r.method, false, false);
		}

		klass.S2p1resolveName();
		klass.S2p2resolveType();
		klass.NAC_SetGlobalInit(constructor);
		klass.S2p3resolveMethod();
		CompileContext.pop();

		newExpr.expr = Type.klass(klass.name());
		nestContext = NestContext.anonymous(ctx, klass, constructor, args);

		return parentType instanceof Generic g && g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric
				? this /* new XXX<>() {}的泛型参数到赋值时才能确定 */
				: resolveNow(ctx);
	}

	private Expr resolveNow(CompileContext ctx) {
		CompileContext.push();
		ctx.pushNestContext(nestContext);
		try {
			klass.S3processAnnotation();
			klass.S4parseCode();
			klass.S5serialize();
		} catch (ParseException e) {
			throw new ResolveException("匿名类解析失败", e);
		} finally {
			ctx.popNestContext();
			CompileContext.pop();
		}

		return newExpr.resolve(ctx);
	}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {throw OperationDone.NEVER;}
	@Override
	public void write(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		if (!(cast.getType1() instanceof Generic g)) {
			CompileContext.get().report(this, Kind.ERROR, "lambda.untyped");
			return;
		}

		((Generic) newExpr.expr).children = g.children;
		resolveNow(CompileContext.get()).write(cw, cast);
	}
}