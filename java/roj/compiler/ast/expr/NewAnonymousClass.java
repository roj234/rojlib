package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.SignaturePrimer;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.config.ParseException;
import roj.text.TextUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/3 0003 2:27
 */
final class NewAnonymousClass extends ExprNode {
	private final Invoke init;
	private final CompileUnit type;

	NewAnonymousClass(Invoke cur, CompileUnit type) {
		this.init = cur;
		this.type = type;
	}

	@Override
	public String toString() {return "<newAnonymousClass "+type.name+">: "+init;}

	@Override
	public IType type() {return init.type();}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		List<ExprNode> args = init.args;
		List<IType> argTypes = Arrays.asList(new IType[args.size()+1]);
		for (int i = 0; i < args.size(); i++) {
			ExprNode node = args.get(i).resolve(ctx);
			args.set(i, node);
			argTypes.set(i, node.type());
		}

		argTypes.set(args.size(), Type.std(Type.VOID));

		IType parentType = ctx.resolveType((IType) init.fn);

		// 实际上可以处理它，for assign only
		if (Inferrer.hasUndefined(parentType)) ctx.report(Kind.ERROR, "invoke.noExact");

		String parent = parentType.owner();
		IClass info = ctx.classes.getClassInfo(parent);
		if (info == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchClass", parent);
			return NaE.RESOLVE_FAILED;
		}

		if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) {
			if (parentType.genericType() != 0) {
				type.signature = new SignaturePrimer(Signature.CLASS);
				type.signature.parent = ctx.file.signature;
				type.signature._impl(parentType);
				type.putAttr(type.signature);
			}
			type.addInterface(parent);
			// TODO this ref

			if (!args.isEmpty()) {
				// anonymousClass.interfaceArg
				ctx.report(Kind.ERROR, "invoke.incompatible.single", info, "<init>", "  \1invoke.except\0 \1invoke.no_param\0\n  \1invoke.found\0 "+TextUtil.join(argTypes, ", "));
				return NaE.RESOLVE_FAILED;
			}
		} else {
			if (parentType.genericType() != 0) {
				type.signature = new SignaturePrimer(Signature.CLASS);
				type.signature.parent = ctx.file.signature;
				type.signature._add(parentType);
				type.putAttr(type.signature);
			}
			type.parent(parent);

			ComponentList list = ctx.methodListOrReport(info, "<init>");
			if (list == null) {
				ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "invoke.method", "<init>"+"("+TextUtil.join(argTypes, ",")+")", "\1symbol.type\0 "+parent);
				return NaE.RESOLVE_FAILED;
			}

			MethodResult r = list.findMethod(ctx, argTypes, ComponentList.THIS_ONLY);
			if (r == null) return NaE.RESOLVE_FAILED;

			r.addExceptions(ctx, info, false);
			type.createDelegation(Opcodes.ACC_SYNTHETIC, r.method, r.method);
		}
		ctx.classes.addGeneratedClass(type);

		if (parentType instanceof Generic g && g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric) {
			return this;// write on demand
		}
		return resolveNow(ctx);
	}

	private ExprNode resolveNow(LocalContext ctx) {
		var lexer = type.getLexer();
		var table = lexer.table;
		var gen = lexer.labelGen;
		int pos;

		LocalContext.depth(1);
		try {
			pos = lexer.next().pos();
			lexer.table = null;
			lexer.labelGen = null;

			type.S2_ResolveSelf();
			type.S2_ResolveRef();
			type.S3_Annotation();
			type.S4_Code();
		} catch (ParseException e) {
			throw new ResolveException("newAnonymousClass failed", e);
		}

		LocalContext.depth(-1);

		lexer.index = pos;
		lexer.table = table;
		lexer.labelGen = gen;

		init.fn = new Type(type.name);
		return init.resolve(ctx);
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {writeDyn(cw, null);}
	@Override
	public void writeDyn(MethodWriter cw, TypeCast.@Nullable Cast cast) {
		if (cast == null || !(cast.getType1() instanceof Generic g)) {
			LocalContext.get().report(Kind.ERROR, "anonymousClass.inferFailed");
			return;
		}

		Generic parentType = (Generic) init.fn;
		parentType.children.clear();
		parentType.children.addAll(g.children);

		resolveNow(LocalContext.get()).write(cw, false);
	}

}