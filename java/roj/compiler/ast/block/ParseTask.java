package roj.compiler.ast.block;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValArray;
import roj.asm.tree.anno.AnnValClass;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.UnresolvedExprNode;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.config.ParseException;
import roj.config.Word;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.List;

import static roj.compiler.JavaLexer.lBrace;
import static roj.compiler.JavaLexer.rBrace;
import static roj.config.Word.EOF;

public interface ParseTask {
	static ParseTask Field(CompileUnit ctx, FieldNode f) throws ParseException {
		LocalContext lc = LocalContext.get();

		UnresolvedExprNode expr = lc.ep.parse(ctx, ExprParser.STOP_COMMA|ExprParser.STOP_SEMICOLON);
		if (expr == null) return null;

		return () -> {
			ExprNode resolve = expr.resolve(lc);

			System.out.println("Field init "+f);
			if ((f.modifier & Opcodes.ACC_STATIC) != 0) {
				ctx.finalStaticField.remove(f);

				MethodWriter mp = ctx.getStaticInit();
				resolve.write(mp, false);
				mp.field(Opcodes.PUTSTATIC, ctx.name, f.name(), f.rawDesc());
			} else {
				ctx.finalObjectField.remove(f);

				MethodWriter mp = ctx.getGlobalInit();
				mp.one(Opcodes.ALOAD_0);
				resolve.write(mp, false);
				mp.field(Opcodes.PUTFIELD, ctx.name, f.name(), f.rawDesc());
			}
		};
	}
	static Object Annotation(CompileUnit ctx, AnnotationPrimer annotation, String name) throws ParseException {
		LocalContext ccttxx = LocalContext.get();

		// TODO 检测值是否合法，以及进行数字的转换

		// _ENV_TYPED_ARRAY允许直接使用数组生成式而不用给定类型
		UnresolvedExprNode expr = ccttxx.ep.parse(ctx, ExprParser.STOP_COMMA|ExprParser.STOP_RSB|ExprParser._ENV_TYPED_ARRAY);
		if (expr == null) {
			ctx.fireDiagnostic(Kind.ERROR, "empty annotation value");
			return null;
		}

		if (expr.isConstant()) return convert((ExprNode) expr);

		return (ParseTask) () -> {
			ccttxx.annotationEnv = true;
			try {
				ExprNode node = expr.resolve(ccttxx);
				if (!node.isConstant()) {
					ctx.fireDiagnostic(Kind.ERROR, "non-constant annotation value");
					return;
				}

				annotation.newEntry(name, convert((ExprNode) expr));
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
		LocalContext ccttxx = LocalContext.get();

		// TODO 延迟解析
		ExprNode expr;
		Type type = new Type(ctx.name);
		if (ctx.getLexer().next().type() == JavaLexer.lParen) {
			expr = ccttxx.ep.enumHelper(ctx, type);
		} else {
			expr = ccttxx.ep.newInvoke(type, Arrays.asList(new Constant(Type.std(Type.INT), AnnVal.valueOf(ordinal)), new Constant(new Type("java/lang/String"), f.name())));
		}

		return () -> {
			CodeWriter m = ctx.getStaticInit();
			MethodWriter mp = ctx.ctx().createMethodPoet(ctx, m.mn);

			expr.resolve(ccttxx).write(mp, false);
			mp.field(Opcodes.PUTSTATIC, ctx.name, f.name(), f.rawDesc());
		};
	}

	static ParseTask Method(CompileUnit ctx, MethodNode mn, List<String> argNames) throws ParseException {
		int pos = ctx.getLexer().index;
		skipMethodBody(ctx.getLexer());

		return () -> {
			LocalContext lc = LocalContext.get();
			lc.setMethod(mn);

			MethodWriter cw = lc.bp.parseMethod(ctx, mn, argNames, pos);

			autoConstructor:
			if (lc.not_invoke_constructor) {
				IClass pInfo = lc.classes.getClassInfo(ctx.parent);
				int superInit = pInfo.getMethod("<init>", "()V");
				if (superInit < 0) {
					lc.report(Kind.ERROR, "cu.noDefaultConstructor");
					break autoConstructor;
				}
				if (!lc.checkAccessible(pInfo, pInfo.methods().get(superInit), false, true))
					break autoConstructor;

				MethodWriter ac = lc.classes.createMethodPoet(ctx, mn);
				ac.one(Opcodes.ALOAD_0);
				ac.invokeD(ctx.parent, "<init>", "()V");

				cw.writeTo(ac);

				cw = ac;
			}

			cw.visitSizeMax(10, 10);
			cw.finish();

			mn.putAttr(new AttrUnknown("Code", cw.bw.toByteArray()));
		};
	}

	//排序 GlobalInit和NonStaticField按写顺序处理, 继承到Init(Method)
	static ParseTask StaticInitBlock(CompileUnit ctx) throws ParseException {
		int pos = ctx.getLexer().index;
		skipMethodBody(ctx.getLexer());

		return () -> {
			System.out.println("Static init block (return hook)");
			BlockParser bp = LocalContext.get().bp;
			bp.parseStaticInit(ctx, null, pos);
		};
	}

	static ParseTask InstanceInitBlock(CompileUnit ctx) throws ParseException {
		int pos = ctx.getLexer().index;
		skipMethodBody(ctx.getLexer());

		return () -> {
			System.out.println("Instance init block (return hook)");
			BlockParser bp = LocalContext.get().bp;
			bp.parseGlobalInit(ctx, null, pos);
		};
	}


	static ParseTask MethodDefault(CompileUnit ctx, MethodNode mn, int id) throws ParseException {
		LocalContext ccttxx = LocalContext.get();

		UnresolvedExprNode expr = ccttxx.ep.parse(ctx, ExprParser.STOP_RSB|ExprParser.STOP_SEMICOLON);
		if (expr == null) return null;
		if (!expr.isConstant()) ccttxx.report(Kind.WARNING, "ps.method.paramDef");

		// IClass+MethodNode => IntMap<ExprNode>
		return () -> {
			// SYNC TO GCtx

		};
	}

	static ParseTask AnnotationDefault(CompileUnit unit, MethodNode mn) throws ParseException {
		return null;
	}

	// TODO priority (for static initializator)
	void parse() throws ParseException;

	private static List<Word> skipMethodBody(JavaLexer wr) throws ParseException {
		List<Word> collect = new SimpleList<>();
		int L = 0;
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case lBrace: L++; break;
				case rBrace: if (--L < 0) return collect; break;
				case EOF: throw wr.err("unclosed_bracket");
			}

			collect.add(w.copy());
		}
	}
}