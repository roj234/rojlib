package roj.compiler.plugins;

import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.api.Compiler;
import roj.compiler.api.CompilerPlugin;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.text.ParseException;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/12/1 8:35
 */
@CompilerPlugin(name = "typedecl", desc = """
		加入支持编译和运行期泛型推断的__Type( type )表达式

		例如 methodCall (__TypeOf ( List<String> ))
		其中，methodCall应当接收roj.asm.IType<T>
		注意：IType实际没有泛型，但它能表示泛型，你可通过IType的各种函数在运行时拿到泛型类型""")
public final class TypeDeclPlugin implements Compiler.StartOp {
	public void pluginInit(Compiler api) {api.newExprOp("__Type", this);}

	private final MethodNode typeDecl = new MethodNode(Opcodes.ACC_STATIC|Opcodes.ACC_PUBLIC, "roj/asm/type/Signature", "parseGeneric", "(Ljava/lang/CharSequence;)Lroj/asm/type/IType;");
	@Override
	public Expr parse(CompileContext ctx) throws ParseException {
		IType type = ctx.resolveType(ctx.file.readType(CompileUnit.TYPE_PRIMITIVE | CompileUnit.TYPE_GENERIC | CompileUnit.TYPE_ALLOW_VOID));

		var node = Invoke.staticMethod(typeDecl, Expr.valueOf(type.toDesc()));
		node.setGenericReturnType(new Generic("roj/asm/type/IType", Collections.singletonList(type)));
		return node;
	}
}
