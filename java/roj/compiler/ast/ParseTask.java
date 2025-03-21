package roj.compiler.ast;

import roj.asm.FieldNode;
import roj.asm.IClass;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.AnnotationDefault;
import roj.asm.attr.ConstantValue;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.*;
import roj.asm.type.IType;
import roj.compiler.JavaLexer;
import roj.compiler.api.MethodDefault;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.UnresolvedExprNode;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.io.IOUtil;

import java.util.List;

/**
 * Lava Compiler - 表达式或代码块懒解析<p>
 * Parser levels: <ol>
 *     <li>{@link CompileUnit Class Parser}</li>
 *     <li><b><i>Segment Parser</i></b></li>
 *     <li>{@link BlockParser Method Parser}</li>
 *     <li>{@link ExprParser Expression Parser}</li>
 * </ol>
 *
 * Stage 3:
 *   annotation, methodDefault
 * Stage 4:
 *   static {}, static field, enum
 *   {}, field
 *   method
 *
 * @author Roj234
 */
public interface ParseTask {
	static void MethodDefault(CompileUnit file, MethodNode mn, int id) throws ParseException {
		var lc = file.lc();
		var wr = lc.lexer;
		int state = wr.setState(JavaLexer.STATE_EXPR);
		UnresolvedExprNode expr = lc.ep.parse(ExprParser.STOP_RSB|ExprParser.STOP_COMMA|ExprParser.NAE);
		wr.state = state;

		if (!expr.isConstant()) lc.report(wr.current().pos(), Kind.WARNING, "ps.method.paramDef");

		String jsonString = ExprParser.serialize((ExprNode) expr);

		var attr = mn.getAttribute(null, MethodDefault.METHOD_DEFAULT);
		if (attr == null) mn.addAttribute(attr = new MethodDefault());

		attr.defaultValue.putInt(id-1, jsonString);
	}

	static ParseTask AnnotationDefault(CompileUnit file, MethodNode mn) throws ParseException {
		var wr = file.lc().lexer;
		int index = wr.index;
		int state = wr.setState(JavaLexer.STATE_EXPR);
		var expr = file.lc().ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser._ENV_TYPED_ARRAY|ExprParser.NAE);
		wr.state = state;

		var attr = new AnnotationDefault(null);
		mn.addAttribute(attr);

		return (ctx) -> {
			ctx.errorReportIndex = index;
			ctx.setMethod(file.getStaticInit().mn);
			attr.val = AnnotationPrimer.toAnnVal(ctx, (ExprNode) expr, mn.returnType());
			ctx.errorReportIndex = -1;
		};
	}


	static ParseTask StaticInitBlock(CompileUnit file) throws ParseException {
		var wr = file.lc().lexer;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		int pos = wr.skipBrace();

		return (ctx) -> {
			ctx.lexer.init(pos, linePos, lineIdx);
			ctx.bp.parseBlockMethod(file, file.getStaticInit());
		};
	}

	static ParseTask InstanceInitBlock(CompileUnit file) throws ParseException {
		var wr = file.lc().lexer;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		int pos = wr.skipBrace();

		return new ParseTask() {
			@Override
			public int priority() {return 1;}
			@Override
			public void parse(LocalContext ctx) throws ParseException {
				ctx.lexer.init(pos, linePos, lineIdx);
				ctx.bp.parseBlockMethod(file, file.getGlobalInit());
			}
		};
	}
	static ParseTask Field(CompileUnit file, FieldNode f) throws ParseException {
		var wr = file.lc().lexer;
		int index = wr.index;
		int state = wr.setState(JavaLexer.STATE_EXPR);
		var expr = file.lc().ep.parse(ExprParser.STOP_COMMA|ExprParser.STOP_SEMICOLON|ExprParser.NAE);
		wr.state = state;

		return new ParseTask() {
			@Override
			public boolean isStaticFinalField() {return (f.modifier&(Opcodes.ACC_STATIC|Opcodes.ACC_FINAL)) == (Opcodes.ACC_STATIC|Opcodes.ACC_FINAL);}
			@Override
			public int priority() {return (f.modifier&Opcodes.ACC_STATIC) != 0 ? 0 : 1;}
			@Override
			public void parse(LocalContext ctx) {
				// 已经解析
				if (f.getRawAttribute("ConstantValue") != null) return;

				ctx.errorReportIndex = index;

				var file = ctx.file;
				var node = expr.resolve(ctx);
				var cast = ctx.castTo(node.type(), f.fieldType(), 0);

				ctx.errorReportIndex = -1;

				if ((f.modifier & Opcodes.ACC_STATIC) != 0) {
					if ((f.modifier & Opcodes.ACC_FINAL) != 0) {
						if (!file.finalFields.remove(f)) {
							ctx.report(Kind.ERROR, "symbol.error.field.writeAfterWrite", file.name(), f.name());
						}

						if (node.isConstant() || node.hasFeature(ExprNode.ExprFeat.LDC_CLASS)) {
							f.addAttribute(new ConstantValue(ParseTask.toConstant(node.constVal())));
							return;
						}

						if (ctx.fieldDFS) return;
					}

					if (cast.type < 0) return;

					var mp = file.getStaticInit();
					ctx.setMethod(mp.mn);

					node.write(mp, cast);
					mp.field(Opcodes.PUTSTATIC, file.name(), f.name(), f.rawDesc());
				} else {
					if ((f.modifier & Opcodes.ACC_FINAL) != 0) {
						if (!file.finalFields.remove(f)) {
							ctx.report(Kind.ERROR, "ps.field.assigned", f);
						}
					}

					if (cast.type < 0) return;

					var mp = file.getGlobalInit();
					ctx.setMethod(mp.mn);

					mp.vars(Opcodes.ALOAD, ctx.thisSlot);
					node.write(mp, cast);
					mp.field(Opcodes.PUTFIELD, file.name(), f.name(), f.rawDesc());
					mp.visitSizeMax(1 + f.fieldType().length(), 1);
				}
			}
		};
	}
	private static roj.asm.cp.Constant toConstant(Object o) {
		if (o instanceof CEntry entry) {
			switch (entry.dataType()) {
				case 'F': return new CstFloat(entry.asFloat());
				case 'D': return new CstDouble(entry.asDouble());
				case 'J': return new CstLong(entry.asLong());
				case 'I', 'S', 'C', 'B', 'Z': return new CstInt(entry.asInt());
			}
		}
		if (o instanceof String) return new CstString(o.toString());
		if (o instanceof IType type) return new CstClass(type.rawType().toDesc());
		throw new UnsupportedOperationException("未预料的常量类型:"+o);
	}

	static ParseTask Method(CompileUnit file, MethodNode mn, List<String> argNames) throws ParseException {
		var wr = file.lc().lexer;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		int pos = wr.skipBrace();

		return new ParseTask() {
			@Override
			public int priority() {return 2;}
			@Override
			public void parse(LocalContext ctx) throws ParseException {
				var file = ctx.file;
				ctx.lexer.init(pos, linePos, lineIdx);
				MethodWriter cw = ctx.bp.parseMethod(file, mn, argNames);

				autoConstructor:
				if (ctx.notInvokeConstructor) {
					if ((file.modifier&Opcodes.ACC_ENUM) != 0) {
						EnumUtil.writeAutoConstructor(file, cw);
						break autoConstructor;
					}

					IClass pInfo = ctx.classes.getClassInfo(file.parent());
					int superInit = pInfo.getMethod("<init>", "()V");
					if (superInit < 0) {
						ctx.report(Kind.ERROR, "cu.noDefaultConstructor", file.parent());
						break autoConstructor;
					}
					if (!ctx.checkAccessible(pInfo, pInfo.methods().get(superInit), false, true)) break autoConstructor;

					var buf = IOUtil.getSharedByteBuf()
						.put(Opcodes.ALOAD_0)
						.put(Opcodes.INVOKESPECIAL)
						.putShort(file.cp.getMethodRefId(file.parent(), "<init>", "()V"));
					cw.insertBefore(buf);
					cw.visitSizeMax(1, 0);
				}

				cw.finish();

				mn.addAttribute(new UnparsedAttribute("Code", cw.bw.toByteArray()));
			}
		};
	}

	default boolean isStaticFinalField() {return false;}
	default int priority() {return 0;}
	void parse(LocalContext ctx) throws ParseException;
}