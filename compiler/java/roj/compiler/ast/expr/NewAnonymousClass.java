package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.type.*;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.IText;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.compiler.types.CompoundType;
import roj.compiler.types.SignatureBuilder;
import roj.compiler.types.VirtualType;
import roj.text.ParseException;
import roj.text.TextUtil;
import roj.util.OperationDone;

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
	private IType parentType;

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
		if (!Inferrer.isFullyResolved(parentType)) ctx.report(this, Kind.ERROR, "invoke.noExact");

		String parent = parentType.owner();
		ClassNode info = ctx.compiler.resolve(parent);
		if (info == null) {
			ctx.reportNoSuchType(this, Kind.ERROR, parentType);
			return NaE.resolveFailed(this);
		}

		if (parentType.kind() != IType.SIMPLE_TYPE) {
			var sign = new SignatureBuilder(Signature.CLASS);
			if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) sign.set(1, parentType);
			else sign.set(0, parentType);
			sign.applyTypeParam(klass);

			klass.classSignature = sign;
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

		klass.S2p1resolveInheritance();
		klass.S2p2resolveMembers();
		klass.NAC_SetGlobalInit(constructor);
		klass.S2p3resolveMethod();
		CompileContext.pop();

		newExpr.expr = Type.klass(klass.name());
		nestContext = NestContext.anonymous(ctx, klass, constructor, args);

		this.parentType = parentType;
		return parentType instanceof ParameterizedType g && g.typeParameters.size() == 1 && g.typeParameters.get(0) == Types.anyGeneric
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
		if (!(cast.getTarget() instanceof ParameterizedType target)) {
			cw.ctx.report(this, Kind.ERROR, "type.cannotInfer", IText.translatable("type.anonymousClass"));
			return;
		}

		var sourceType = (ParameterizedType) this.parentType;
		int typeVariableCount = cw.ctx.resolve(sourceType).getSignature().typeVariables.size();

		List<IType> dummies = Arrays.asList(new IType[typeVariableCount]);
		for (int i = 0; i < dummies.size(); i++) dummies.set(i, VirtualType.anyType("dummy"));
		sourceType.typeParameters = dummies;

		List<IType> inferred = cw.ctx.compiler.inferGeneric(sourceType, target.owner());
		for (int i = 0; i < inferred.size(); i++) {
			substituteVirtualTypes(inferred.get(i), target.typeParameters.get(i), dummies);
		}

		for (IType type : dummies) {
			if (type instanceof VirtualType) {
				cw.ctx.report(Kind.ERROR, "newAnonymousClass.unsubstitutedType", dummies);
				return;
			}
		}

		resolveNow(cw.ctx).write(cw, cast);
	}

	private static void substituteVirtualTypes(IType inferred, IType provided, List<IType> type2) {
		if (inferred.kind() == IType.CAPTURED_WILDCARD) {
			inferred = ((CompoundType) inferred).getBound();
		}
		if (provided.kind() == IType.CAPTURED_WILDCARD) {
			provided = ((CompoundType) provided).getBound();
		}
		if (provided.kind() == IType.TYPE_VARIABLE) {
			provided = ((TypeVariable) provided).decl.get(0);
		}

		switch (inferred.kind()) {
			case IType.ANY_TYPE -> {
				int i = type2.indexOf(inferred);
				type2.set(i, provided);
			}
			case IType.PARAMETERIZED_TYPE, IType.PARAMETERIZED_CHILD -> {
				IGeneric t1 = (IGeneric) inferred;
				IGeneric t2 = (IGeneric) provided;

				List<IType> t1p = t1.typeParameters;
				List<IType> t2p = t2.typeParameters;
				for (int i = 0; i < t1p.size(); i++) {
					substituteVirtualTypes(t1p.get(i), t2p.get(i), type2);
				}

				if (t1.sub != null) {
					substituteVirtualTypes(t1.sub, t2.sub, type2);
				}
			}
		}
	}
}