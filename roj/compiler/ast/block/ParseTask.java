package roj.compiler.ast.block;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValArray;
import roj.asm.tree.anno.AnnValClass;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodParamAnno;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.context.CompileContext;
import roj.compiler.context.CompileUnit;
import roj.config.ParseException;
import roj.util.Helpers;

import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.List;

public interface ParseTask {
	static ParseTask Field(CompileUnit ctx, FieldNode f) throws ParseException {
		CompileContext ccttxx = CompileContext.get();

		ExprNode expr = ccttxx.ep.parse(ctx, ExprParser.STOP_COMMA|ExprParser.STOP_SEMICOLON);
		if (expr == null) return null;

		return () -> {
			ExprNode resolve = expr.resolve(ccttxx);

			if ((f.modifier & Opcodes.ACC_STATIC) != 0) {
				CodeWriter m = ctx.getClinit();
				MethodWriter mp = ctx.ctx().createMethodPoet(ctx, m.mn);

				resolve.write(mp, false);
				mp.field(Opcodes.PUTSTATIC, ctx.name, f.name(), f.rawDesc());
			} else {
				// TODO get current methodWriter instance
				CodeWriter m = ctx.getClinit();
				MethodWriter mp = ctx.ctx().createMethodPoet(ctx, m.mn);

				mp.one(Opcodes.ALOAD_0);
				resolve.write(mp, false);
				mp.field(Opcodes.PUTFIELD, ctx.name, f.name(), f.rawDesc());
			}
		};
	}
	static Object Annotation(CompileUnit ctx, AnnotationPrimer annotation, String name) throws ParseException {
		CompileContext ccttxx = CompileContext.get();

		// TODO 检测值是否合法，以及进行数字的转换

		// _ENV_TYPED_ARRAY允许直接使用数组生成式而不用给定类型
		ExprNode expr = ccttxx.ep.parse(ctx, ExprParser.STOP_COMMA|ExprParser.STOP_RSB|ExprParser._ENV_TYPED_ARRAY);
		if (expr == null) {
			ctx.fireDiagnostic(Diagnostic.Kind.ERROR, "empty annotation value");
			return null;
		}

		if (expr.isConstant()) return convert(expr);

		return (ParseTask) () -> {
			ccttxx.annotationEnv = true;
			try {
				ExprNode node = expr.resolve(ccttxx);
				if (!node.isConstant()) {
					ctx.fireDiagnostic(Diagnostic.Kind.ERROR, "non-constant annotation value");
					return;
				}

				annotation.newEntry(name, convert(expr));
			} finally {
				ccttxx.annotationEnv = false;
			}
		};
	}
	private static Object convert(ExprNode expr) {
		Object o = expr.constVal();
		if (o instanceof Object[] arr) {
			for (int i = 0; i < arr.length; i++) {
				arr[i] = convert(arr[i]);
			}
			return new AnnValArray(Helpers.cast(Arrays.asList(arr)));
		}
		return convert(o);
	}
	private static Object convert(Object o) {
		if (o instanceof AnnVal) return o;
		if (o instanceof String) return AnnVal.valueOf(o.toString());
		if (o instanceof IType type) return new AnnValClass(type.rawType().toDesc());
		throw new UnsupportedOperationException("未预料的常量类型:"+o);
	}


	static ParseTask Enum(CompileUnit ctx, int ordinal, FieldNode f) throws ParseException {
		CompileContext ccttxx = CompileContext.get();

		// TODO 延迟解析
		ExprNode expr;
		Type type = new Type(ctx.name);
		if (ctx.lex().next().type() == JavaLexer.left_s_br) {
			expr = ccttxx.ep.enumHelper(ctx, type);
		} else {
			expr = ccttxx.ep.newInvoke(type, Arrays.asList(new Constant(Type.std(Type.INT), AnnVal.valueOf(ordinal)), new Constant(new Type("java/lang/String"), f.name())));
		}

		return () -> {
			CodeWriter m = ctx.getClinit();
			MethodWriter mp = ctx.ctx().createMethodPoet(ctx, m.mn);

			expr.resolve(ccttxx).write(mp, false);
			mp.field(Opcodes.PUTSTATIC, ctx.name, f.name(), f.rawDesc());
		};
	}

	// 对于格式良好的类文件，end用不到
	static ParseTask Method(CompileUnit ctx, MethodNode mn, List<String> names) throws ParseException {
		return () -> {
			BlockParser bp = CompileContext.get().bp;
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
		BlockParser bp = CompileContext.get().bp;
		bp.init(ctx,0,null);
		bp.type = 1;
		bp.parse0();

		return () -> {
		};
	}

	static ParseTask InstanceInitBlock(CompileUnit ctx) throws ParseException {
		return () -> {
			BlockParser bp = CompileContext.get().bp;
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

	// TODO priority (for static initializator)
	void parse() throws ParseException;
}