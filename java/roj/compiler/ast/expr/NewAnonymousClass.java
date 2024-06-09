package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.NaE;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.ResolveException;
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

		String parent = (ctx.resolveType((Type) init.fn)).owner();
		IClass info = ctx.classes.getClassInfo(parent);
		if (info == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchClass", parent);
			return NaE.RESOLVE_FAILED;
		}

		if ((info.modifier() & Opcodes.ACC_INTERFACE) != 0) {
			type.addInterface(parent);
			type.createDelegation(Opcodes.ACC_SYNTHETIC, new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/Object", "<init>", "()V"));
		} else {
			type.parent(parent);

			ComponentList list = ctx.methodListOrReport(info, "<init>");
			if (list == null) {
				ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "invoke.method", "<init>"+"("+TextUtil.join(argTypes, ",")+")", "\1symbol.type\0 "+parent);
				return NaE.RESOLVE_FAILED;
			}

			MethodResult r = list.findMethod(ctx, argTypes, ComponentList.THIS_ONLY);
			if (r == null) return NaE.RESOLVE_FAILED;

			r.addExceptions(ctx, info, false);
			type.createDelegation(Opcodes.ACC_SYNTHETIC, r.method);
		}
		ctx.classes.addGeneratedClass(type);

		var lexer = type.getLexer();
		var table = lexer.table;
		var gen = lexer.labelGen;
		int pos;

		LocalContext.depth(1);
		try {
			pos = lexer.next().pos();
			lexer.table = null;
			lexer.labelGen = null;

			type.S2_Resolve();
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
	public void write(MethodWriter cw, boolean noRet) {throw new ResolveException("resolve failed");}
}