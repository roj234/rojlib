package roj.compiler.plugins;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.config.ParseException;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/12/1 0001 8:35
 */
@LavaPlugin(name = "typedecl", desc = "加入了支持编译期泛型推断的__Type( type )表达式")
public final class TypeDeclPlugin implements LavaApi.StartOp {
	public void pluginInit(LavaApi api) {api.newExprOp("__Type", this);}

	private final MethodNode typeDecl = new MethodNode(Opcodes.ACC_STATIC|Opcodes.ACC_PUBLIC, "roj/asm/type/Signature", "parseGeneric", "(Ljava/lang/CharSequence;)Lroj/asm/type/IType;");
	@Override
	public ExprNode parse(LocalContext ctx) throws ParseException {
		IType type = ctx.resolveType(ctx.file.readType(CompileUnit.TYPE_PRIMITIVE | CompileUnit.TYPE_GENERIC | CompileUnit.TYPE_ALLOW_VOID));

		var node = Invoke.staticMethod(typeDecl, ExprNode.valueOf(type.toDesc()));
		node.setGenericReturnType(new Generic("roj/asm/type/IType", Collections.singletonList(type)));
		return node;
	}
}
