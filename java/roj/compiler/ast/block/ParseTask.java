package roj.compiler.ast.block;

import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.*;
import roj.asm.tree.attr.AnnotationDefault;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.api_rt.MethodDefault;
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

/**
 * Stage 3:
 *   annotation, methodDefault
 * Stage 4:
 *   static {}, static field, enum
 *   {}, field
 *   method
 */
public interface ParseTask {
	static Object Annotation(CompileUnit ctx, AnnotationPrimer annotation, String name) throws ParseException {
		var lc = LocalContext.get();

		// TODO 检测值是否合法，以及进行数字的转换

		// _ENV_TYPED_ARRAY允许直接使用数组生成式而不用给定类型
		var expr = lc.ep.parse(ctx, ExprParser.STOP_COMMA|ExprParser.STOP_RSB|ExprParser._ENV_TYPED_ARRAY);
		if (expr == null) {
			ctx.report(Kind.ERROR, "empty annotation value");
			return null;
		}

		if (expr.isConstant()) return convert((ExprNode) expr);

		return (ParseTask) () -> {
			ExprNode node = expr.resolve(lc);
			if (!node.isConstant() && !node.isKind(ExprNode.ExprKind.ENUM_REFERENCE)) {
				ctx.report(Kind.ERROR, "non-constant annotation value");
				return;
			}

			annotation.newEntry(name, convert(node));
		};
	}
	private static AnnVal convert(ExprNode expr) {
		Object o = expr.constVal();
		if (o instanceof Object[] arr) {
			for (int i = 0; i < arr.length; i++) {
				arr[i] = convert(arr[i]);
			}
			return new AnnValArray(Helpers.cast(Arrays.asList(arr)));
		}
		return convert(o);
	}
	private static AnnVal convert(Object o) {
		if (o instanceof AnnVal a) return a;
		if (o instanceof String) return AnnVal.valueOf(o.toString());
		if (o instanceof IType type) return new AnnValClass(type.rawType().toDesc());
		throw new UnsupportedOperationException("未预料的常量类型:"+o);
	}


	static void MethodDefault(CompileUnit file, MethodNode mn, int id) throws ParseException {
		LocalContext lc1 = LocalContext.get();

		UnresolvedExprNode expr = lc1.ep.parse(file, ExprParser.STOP_RSB|ExprParser.STOP_COMMA|ExprParser.NAE);
		if (!expr.isConstant()) lc1.report(Kind.WARNING, "ps.method.paramDef");

		String jsonString = ExprParser.serialize((ExprNode) expr);

		var attr = mn.parsedAttr(null, MethodDefault.METHOD_DEFAULT);
		if (attr == null) mn.putAttr(attr = new MethodDefault());

		attr.defaultValue.putInt(id-1, jsonString);
	}

	static ParseTask AnnotationDefault(CompileUnit file, MethodNode mn) throws ParseException {
		var expr = LocalContext.get().ep.parse(file, ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE);

		var attr = new AnnotationDefault(null);
		mn.putAttr(attr);

		return () -> {
			var lc = LocalContext.get();
			lc.setMethod(file.getStaticInit().mn);

			ExprNode node = expr.resolve(lc);
			if (!node.isConstant() && !node.isKind(ExprNode.ExprKind.ENUM_REFERENCE)) {
				lc.report(Kind.ERROR, "ps.annoDef.notConst");
			} else {
				attr.val = convert(node);
			}
		};
	}


	static ParseTask Enum(CompileUnit file, int ordinal, FieldNode f) throws ParseException {
		ExprParser ep = LocalContext.get().ep;

		ExprNode expr;
		Type type = new Type(file.name);
		if (file.getLexer().next().type() == JavaLexer.lParen) {
			expr = ep.enumHelper(file, type);
		} else {
			// comma or semicolon will be checked
			expr = ep.newInvoke(type, Arrays.asList(new Constant(Type.std(Type.INT), AnnVal.valueOf(ordinal)), Constant.valueOf(f.name())));
		}

		return () -> {
			var lc = LocalContext.get();

			MethodWriter mp = file.getStaticInit();
			lc.setMethod(mp.mn);

			expr.resolve(lc).write(mp, false);
			mp.field(Opcodes.PUTSTATIC, file.name, f.name(), f.rawDesc());
		};
	}

	static ParseTask StaticInitBlock(CompileUnit file) throws ParseException {
		JavaLexer wr = file.getLexer();
		int pos = wr.index;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		skipMethodBody(wr);

		return () -> {
			wr.index = pos;
			wr.LN = linePos;
			wr.LNIndex = lineIdx;
			LocalContext.get().bp.parseBlockMethod(file, file.getStaticInit());
		};
	}

	static ParseTask InstanceInitBlock(CompileUnit file) throws ParseException {
		JavaLexer wr = file.getLexer();
		int pos = wr.index;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		skipMethodBody(wr);

		return new ParseTask() {
			@Override
			public int priority() {return 1;}
			@Override
			public void parse() throws ParseException {
				wr.index = pos;
				wr.LN = linePos;
				wr.LNIndex = lineIdx;
				LocalContext.get().bp.parseBlockMethod(file, file.getGlobalInit());
			}
		};
	}
	static ParseTask Field(CompileUnit file, FieldNode f) throws ParseException {
		var expr = LocalContext.get().ep.parse(file, ExprParser.STOP_COMMA|ExprParser.STOP_SEMICOLON|ExprParser.NAE);

		return new ParseTask() {
			@Override
			public int priority() {return (f.modifier&Opcodes.ACC_STATIC) != 0 ? 0 : 1;}
			@Override
			public void parse() {
				var lc = LocalContext.get();
				var node = expr.resolve(lc);

				if ((f.modifier & Opcodes.ACC_STATIC) != 0) {
					if ((f.modifier & Opcodes.ACC_FINAL) != 0) {
						if (!file.finalFields.remove(f)) {
							lc.report(Kind.ERROR, "cu.finalField.assigned", f);
						}

						if (node.isConstant() || node.isKind(ExprNode.ExprKind.LDC_CLASS)) {
							f.putAttr(new ConstantValue(ParseTask.toConstant(node.constVal())));
							return;
						}
					}

					MethodWriter mp = file.getStaticInit();
					lc.setMethod(mp.mn);

					node.write(mp, false);
					mp.field(Opcodes.PUTSTATIC, file.name, f.name(), f.rawDesc());
				} else {
					if ((f.modifier & Opcodes.ACC_FINAL) != 0) {
						if (!file.finalFields.remove(f)) {
							lc.report(Kind.ERROR, "ps.field.assigned", f);
						}
					}

					MethodWriter mp = file.getGlobalInit();
					lc.setMethod(mp.mn);

					mp.one(Opcodes.ALOAD_0);
					node.write(mp, false);
					mp.field(Opcodes.PUTFIELD, file.name, f.name(), f.rawDesc());
				}
			}
		};
	}
	private static roj.asm.cp.Constant toConstant(Object o) {
		if (o instanceof AnnValInt a) return new CstInt(a.value);
		if (o instanceof AnnValFloat a) return new CstFloat(a.value);
		if (o instanceof AnnValLong a) return new CstLong(a.value);
		if (o instanceof AnnValDouble a) return new CstDouble(a.value);
		if (o instanceof String) return new CstString(o.toString());
		if (o instanceof IType type) return new CstClass(type.rawType().toDesc());
		throw new UnsupportedOperationException("未预料的常量类型:"+o);
	}

	static ParseTask Method(CompileUnit file, MethodNode mn, List<String> argNames) throws ParseException {
		JavaLexer wr = file.getLexer();
		int pos = wr.index;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		skipMethodBody(wr);

		return new ParseTask() {
			@Override
			public int priority() {return 2;}
			@Override
			public void parse() throws ParseException {
				wr.index = pos;
				wr.LN = linePos;
				wr.LNIndex = lineIdx;

				var lc = LocalContext.get();
				MethodWriter cw = lc.bp.parseMethod(file, mn, argNames);

				autoConstructor:
				if (lc.not_invoke_constructor) {
					IClass pInfo = lc.classes.getClassInfo(file.parent);
					int superInit = pInfo.getMethod("<init>", "()V");
					if (superInit < 0) {
						lc.report(Kind.ERROR, "cu.noDefaultConstructor");
						break autoConstructor;
					}
					if (!lc.checkAccessible(pInfo, pInfo.methods().get(superInit), false, true)) break autoConstructor;

					MethodWriter ac = lc.classes.createMethodPoet(file, mn);
					ac.one(Opcodes.ALOAD_0);
					ac.invokeD(file.parent, "<init>", "()V");

					cw.writeTo(ac);

					cw = ac;
				}

				cw.visitSizeMax(10, 10);
				cw.finish();

				mn.putAttr(new AttrUnknown("Code", cw.bw.toByteArray()));
			}
		};
	}

	default int priority() {return 0;}
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