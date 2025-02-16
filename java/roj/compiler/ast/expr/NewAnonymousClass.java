package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.IClass;
import roj.asm.Opcodes;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.LPSignature;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.config.ParseException;
import roj.text.TextUtil;

import java.util.Arrays;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/6/3 0003 2:27
 */
final class NewAnonymousClass extends ExprNode {
	private final Invoke init;
	private final CompileUnit type;
	private CodeWriter initMethod;

	NewAnonymousClass(Invoke cur, CompileUnit type) {
		this.init = cur;
		this.type = type;
	}

	@Override
	public String toString() {return "<newAnonymousClass "+type.name()+">: "+init;}

	@Override
	public IType type() {return init.type();}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		List<ExprNode> args = init.args;
		List<IType> argTypes = Arrays.asList(new IType[args.size()]);
		for (int i = 0; i < args.size(); i++) {
			ExprNode node = args.get(i).resolve(ctx);
			args.set(i, node);
			argTypes.set(i, node.type());
		}

		IType parentType = ctx.resolveType((IType) init.fn);

		// 实际上可以处理它，for assign only
		if (Inferrer.hasUndefined(parentType)) ctx.report(Kind.ERROR, "invoke.noExact");

		String parent = parentType.owner();
		IClass info = ctx.classes.getClassInfo(parent);
		if (info == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchClass", parent);
			return NaE.RESOLVE_FAILED;
		}

		if (parentType.genericType() != 0) {
			var sign = new LPSignature(Signature.CLASS);
			if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) sign._impl(parentType);
			else sign._add(parentType);
			type.signature = sign;
			type.putAttr(sign);
		}

		if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) {
			type.addInterface(parent);

			if (!args.isEmpty()) {
				// anonymousClass.interfaceArg
				ctx.report(Kind.ERROR, "invoke.incompatible.single", info, "<init>", "  \1invoke.except\0 \1invoke.no_param\0\n  \1invoke.found\0 "+TextUtil.join(argTypes, ", "));
				return NaE.RESOLVE_FAILED;
			}

			CodeWriter c = initMethod = type.newMethod(ACC_PUBLIC, "<init>", "()V");
			c.visitSize(1, 1);
			c.one(ALOAD_0);
			c.invoke(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		} else {
			type.parent(parent);

			var list = ctx.getMethodList(info, "<init>");
			if (list == ComponentList.NOT_FOUND) {
				ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "invoke.method", "<init>"+"("+TextUtil.join(argTypes, ",")+")", "\1symbol.type\0 "+parent);
				return NaE.RESOLVE_FAILED;
			}

			var r = list.findMethod(ctx, argTypes, ComponentList.THIS_ONLY);
			if (r == null) return NaE.RESOLVE_FAILED;

			r.addExceptions(ctx, false);
			type.j11PrivateConstructor(r.method);
			initMethod = type.createDelegation(Opcodes.ACC_SYNTHETIC, r.method, r.method, false, false);
		}
		ctx.classes.addGeneratedClass(type);

		if (parentType instanceof Generic g && g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric) {
			return this;// write on demand
		}
		return resolveNow(ctx);
	}

	private ExprNode resolveNow(LocalContext ctx) {
		ctx.enclosing.add(NestContext.anonymousClass(ctx, type, initMethod, init.args = new SimpleList<>(init.args)));
		try {
			LocalContext.next();
			type.S2_ResolveSelf();
			type.S2_ResolveRef();
			type.S3_Annotation();
			type.S4_Code();
			type.S5_noStore();
			type.appendGlobalInit(initMethod, null);
		} catch (ParseException e) {
			throw new ResolveException("newAnonymousClass failed", e);
		} finally {
			ctx.enclosing.pop();
			LocalContext.prev();
		}

		initMethod.one(RETURN);
		initMethod.finish();

		init.fn = Type.klass(type.name());
		return init.resolve(ctx);
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {write(cw, null);}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		if (returnType == null || !(returnType.getType1() instanceof Generic g)) {
			LocalContext.get().report(Kind.ERROR, "anonymousClass.inferFailed");
			return;
		}

		Generic parentType = (Generic) init.fn;
		parentType.children.clear();
		parentType.children.addAll(g.children);

		resolveNow(LocalContext.get()).write(cw, false);
	}
}