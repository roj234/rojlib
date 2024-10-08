package roj.compiler.plugins.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.collect.MyHashMap;
import roj.compiler.api.Evaluable;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.*;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.api.LavaApi;
import roj.compiler.resolve.ResolveException;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/4 22:15
 */
public class AsmHook extends Evaluable {
	public final MyHashMap<String, ExprNode> injectedProperties = new MyHashMap<>();

	public AsmHook() {}

	@Override
	public ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args) {
		switch (owner.name()) {
			default: throw new ResolveException("未预料的错误");
			case "i2z", "z2i": return args.get(0);
			case "cast": return new GenericCat(args.get(0));
			case "__asm":
				var lambda = (Lambda) args.get(0);
				throw new IllegalArgumentException("未实现");
			case "inject":
				if (!args.get(0).isConstant()) throw new IllegalArgumentException("__inject()的参数必须是常量字符串");

				var replace = injectedProperties.get(args.get(0).constVal().toString());
				return replace != null ? replace : args.get(1);
		}
	}

	@Override
	public String toString() {return "__asm hook";}

	public static AsmHook init(GlobalContext ctx) {
		IClass ci = ctx.getClassInfo("roj/compiler/plugins/asm/ASM");
		List<MethodNode> methods = Helpers.cast(ci.methods()); methods.clear();

		MethodNode mn;
		AsmHook cf = new AsmHook();

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "i2z", "(I)Z");
		mn.putAttr(cf);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "z2i", "(Z)I");
		mn.putAttr(cf);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "inject", "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;");
		mn.putAttr(cf);
		methods.add(mn);

		mn = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, ci.name(), "cast", "(Ljava/lang/Object;)Ljava/lang/Object;");
		mn.putAttr(cf);
		methods.add(mn);

		var expr = ((LavaApi) ctx).getExprApi();

		var typeDecl = new MethodNode(Opcodes.ACC_STATIC|Opcodes.ACC_PUBLIC, "roj/asm/type/Signature", "parseGeneric", "(Ljava/lang/CharSequence;)Lroj/asm/type/IType;");
		expr.addExprGen("__TypeDecl", (lexer, lc) -> {
			IType type = lc.resolveType(lc.file.readType(CompileUnit.TYPE_PRIMITIVE | CompileUnit.TYPE_GENERIC | CompileUnit.TYPE_ALLOW_VOID));

			var node = Invoke.staticMethod(typeDecl, Constant.valueOf(type.toDesc()));
			node.setGenericReturnType(new Generic("roj/asm/type/IType", Collections.singletonList(type)));
			return node;
		});
		expr.addUnaryPre("__TypeOf", (lexer, node) -> new UnaryPreNode() {
			@Override public String setRight(ExprNode node) {return "[\n  表达式="+node+"\n  解析="+(node = node.resolve(LocalContext.get()))+"\n  返回类型="+node.type()+"\n]";}
			@Override public String toString() {return null;}
			@Override public IType type() {return null;}
			@Override public void write(MethodWriter cw, boolean noRet) {}
		});

		return cf;
	}
}