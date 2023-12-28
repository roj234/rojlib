package roj.lavac.block;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.visitor.CodeWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.config.ParseException;
import roj.lavac.asm.AnnotationPrimer;
import roj.lavac.asm.MethodParamAnno;
import roj.lavac.expr.ExprParser;
import roj.lavac.parser.CompileLocalCache;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.MethodWriterL;

import java.util.List;

public interface ParseTask {
	static ParseTask Field(CompileUnit ctx, FieldNode f) throws ParseException {
		ExprParser ep = CompileLocalCache.get().ep;
		ExprNode expr = ep.parse(ctx, ExprParser.STOP_COMMA | ExprParser.STOP_SEMICOLON);
		System.out.println(expr);

		return () -> {
			ExprNode resolve = expr.resolve();

			CodeWriter m = ctx.getClinit();
			MethodWriterL mp = ctx.ctx().createMethodPoet(ctx, m.mn);
			if ((f.access & Opcodes.ACC_STATIC) != 0) {

			} else {

			}
			resolve.write(mp, false);
			// todo if is not static?
			//mp.node(new FieldInsnNode(Opcodes.PUTSTATIC, ctx.name, f.name, f.fieldType()));
		};
	}
	static ParseTask Annotation(CompileUnit ctx, AnnotationPrimer annotation, String name) throws ParseException {
		ExprParser ep = CompileLocalCache.get().ep;
		ExprNode expr = ep.parse(ctx, ExprParser.STOP_COMMA | ExprParser.STOP_RSB);
		System.out.println(expr);

		return () -> {

		};
	}


	static ParseTask Enum(CompileUnit ctx, int ordinal, FieldNode f) throws ParseException {
		ExprParser ep = CompileLocalCache.get().ep;
		ExprNode expr = ep.parse(ctx, ExprParser.STOP_COMMA | ExprParser.STOP_SEMICOLON);

		return () -> {
			CodeWriter m = ctx.getClinit();
			MethodWriterL mp = ctx.ctx().createMethodPoet(ctx, m.mn);

			//mp.new1(ctx.name).dup();
			//mp.node(new InvokeInsnNode(Opcodes.INVOKESPECIAL, ctx.name, "<init>", ctx.ctx().findSuitableMethod(mp, ctx, "<init>", f)))
			//	.node(new FieldInsnNode(Opcodes.PUTSTATIC, ctx.name, f.name, f.fieldType()));
		};
	}

	// 对于格式良好的类文件，end用不到
	static ParseTask Method(CompileUnit ctx, MethodNode mn, List<String> names) throws ParseException {
		return () -> {
			BlockParser bp = CompileLocalCache.get().bp;
			//bp.init(u, start, mn);
			bp.type = 0;

			int off = (mn.modifier() & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
			for (int i = 0; i < names.size(); i++) {
				//bp.variables.put(names.get(i), bp.mw.arg(i+off));
			}

			bp.parse0();
		};
	}

	static ParseTask StaticInitBlock(CompileUnit ctx) throws ParseException {
		BlockParser bp = CompileLocalCache.get().bp;
		bp.init(ctx,0,null);
		bp.type = 1;
		bp.parse0();

		return () -> {
		};
	}

	static ParseTask InstanceInitBlock(CompileUnit ctx) throws ParseException {
		return () -> {
			BlockParser bp = CompileLocalCache.get().bp;
			bp.init(ctx,0,null);
			bp.type = 2;
			bp.parse0();
		};
	}

	static ParseTask MethodDefault(CompileUnit ctx, MethodParamAnno desc) throws ParseException {
		return () -> {

		};
	}

	static ParseTask AnnotationDefault(CompileUnit unit, MethodNode method) throws ParseException {
		return null;
	}

	void parse() throws ParseException;
}