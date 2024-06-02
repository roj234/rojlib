package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
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
 * TODO TEST
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

		String parent = ((Type) init.fn).owner;

		type.parent(parent);

		IClass info = ctx.classes.getClassInfo(parent);
		if (info == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchClass", parent);
			return this;
		}

		//will be done via S2 parent check
		//ctx.assertAccessible(info);

		ComponentList list = ctx.methodListOrReport(info, "<init>");
		if (list == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "invoke.method", "<init>"+"("+ TextUtil.join(argTypes, ",")+")", "{symbol.type} "+parent);
			return this;
		}

		MethodResult r = list.findMethod(ctx, argTypes, ComponentList.THIS_ONLY);
		if (r == null) return this;

		r.addExceptions(ctx, info, 0);
		type.createDelegation(Opcodes.ACC_SYNTHETIC, r.method);
		ctx.classes.addGeneratedClass(type);

		try {
			type.S2_Parse();
			type.S3_Annotation();
			type.S4_Code();
		} catch (ParseException e) {
			throw new ResolveException("newAnonymousClass failed", e);
		}

		init.fn = new Type(type.name);
		return init.resolve(ctx);
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {throw new ResolveException("resolve failed");}
}