package roj.compiler.ast.block;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.LineNumberTable;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.*;
import roj.collect.*;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.VariableDeclare;
import roj.compiler.ast.expr.*;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.FieldResult;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.config.Word;
import roj.reflect.ClassDefiner;
import roj.util.Helpers;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;
import static roj.asm.util.InsnHelper.XALoad;
import static roj.compiler.JavaLexer.*;

/**
 * KScript语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class BlockParser {
	private final LocalContext ctx;
	private final ExprParser ep;
	private MethodWriter cw;
	private CompileUnit file;
	private JavaLexer wr;
	int methodType;

	public BlockParser(LocalContext ctx) {
		this.ctx = ctx;
		this.ep = ctx.ep;
	}

	public BlockParser reset() {
		this.variables.clear();
		this.regionNew.clear();
		this.sectionFlag = 0;

		this.returnHook = null;
		this.returnHookUsed = null;

		this.labels.clear();
		this.curBreak = this.curContinue = null;
		this.immediateLabel = null;

		return this;
	}

	/// region 解析

	public void parseStaticInit(CompileUnit file, XAttrCode attr, int begin) throws ParseException {
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		methodType = 1;
		//cw = new MethodWriter(file, mn);
		ctx.variables = Collections.emptyMap();
		cw.ctx1 = ctx;
		reset();
		parse0();
	}

	public void parseGlobalInit(CompileUnit file, XAttrCode attr, int begin) throws ParseException {
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		methodType = 2;
		//cw = new MethodWriter(file, mn);
		ctx.variables = Collections.emptyMap();
		cw.ctx1 = ctx;
		reset();
		parse0();
	}

	public MethodWriter parseMethod(CompileUnit file, MethodNode mn, List<String> names, int begin) throws ParseException {
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;

		methodType = 0;

		cw = new MethodWriter(file, mn);
		ctx.variables = this.variables;
		cw.ctx1 = ctx;

		wr.labelGen = cw;
		wr.table = new LineNumberTable();

		reset();

		if ((mn.modifier()&ACC_STATIC) == 0) fastSlot = 1;
		// TODO generic
		List<Type> parameters = mn.parameters();
		for (int i = 0; i < names.size(); i++) {
			String name = names.get(i);
			newVar(name, parameters.get(i));
		}

		parse0();

		//System.out.println(wr.table);
		wr.labelGen = null;
		wr.table = null;
		return cw;
	}

	private void parse0() throws ParseException {
		beginCodeBlock();

		while (true) {
			Word w = wr.next();
			if (w.type() == Word.EOF || w.type() == rBrace) {
				if ((sectionFlag&SF_BLOCK_END) == 0) {
					if (cw.mn.returnType().type == Type.VOID) cw.one(Opcodes.RETURN);
					else file.fireDiagnostic(Kind.ERROR, "block.missingReturnValue");
				}
				break;
			}

			statement(w);
		}

		endCodeBlock();
	}

	//endregion
	//region 函数

	/**
	 * Unreachable statement检测 <BR>
	 * bit1 MULTI_LINE BLOCK: { sth }
	 * bit2 MULTI_LINE BLOCK END
	 * bit3 SWITCH
	 * bit4 FIRST STATEMENT IN BLOCK
	 */
	private byte sectionFlag;
	private static final byte SF_BLOCK = 1, SF_BLOCK_END = 2, SF_SWITCH = 4, SF_EMPTY = 8;

	/**
	 * 函数体
	 */
	@SuppressWarnings("fallthrough")
	private void block() throws ParseException { block(true); }
	private void block(boolean shouldBegin) throws ParseException {
		boolean prevIsEmpty = (sectionFlag & SF_EMPTY) != 0;

		if (shouldBegin) beginCodeBlock();
		sectionFlag |= SF_BLOCK|SF_EMPTY;
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case Word.EOF: case rBrace: endCodeBlock(); return;
				default: statement(w); sectionFlag &= ~SF_EMPTY;
			}
		}
	}

	/**
	 * 语句
	 */
	@SuppressWarnings("fallthrough")
	private void statement(Word w) throws ParseException {
		if (w.type() == at) {
			ctx.report(Kind.WARNING, "lavac不支持方法体中的注解，且没有支持的计划，当然其实这并不难，你可以尝试自己实现");
			file._annotations(Collections.emptyList());
			// 只能在define （type/var）上用
			define(null);
			return;
		}

		checkImmediateLabel:
		if (w.type() == Word.LITERAL) {
			wr.retractWord();
			wr.mark();

			// 标签
			String val = w.val();
			if ((w = wr.next()).type() == colon) {
				wr.skip();
				if (labels.containsKey(val)) ctx.report(Kind.ERROR, "block.error.dupLabel", w.val());
				if (!regionNew.isEmpty()) regionNew.get(regionNew.size()-1).add(val);

				labels.put(val, immediateLabel = new LabelInfo(cw.label()));
				break checkImmediateLabel;
			}
			wr.retract();

			UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.CHECK_VARIABLE_DECLARE);
			assert expr != null;

			if (expr instanceof VariableDeclare vd) {
				wr.retractWord(); // get vd.name
				define(vd.type);
			} else {
				expr.resolve(ctx).write(cw, true);
			}
			return;
		}

		switch (w.type()) {
			case CONTINUE, BREAK -> _break(w.type() == BREAK);
			case JavaLexer.GOTO -> _goto();
			case SWITCH -> _switch();
			case JavaLexer.RETURN -> _return();
			case FOR -> _for();
			case WHILE -> _while();
			case THROW -> _throw();
			case IF -> _if();
			case DO -> _doWhile();
			case TRY -> _try();
			case SYNCHRONIZED -> _sync();
			case DEFER -> _defer();
			case WITH -> _with();
			//case YIELD -> _yield(); // require Generator function
			//case AWAIT -> _await(); // require Async function
			case semicolon -> ctx.report(Kind.WARNING, "block.emptyStatement");
			case lBrace -> block();
			case BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE -> {
				wr.retractWord();
				define(null);
			}
			default -> {
				if (w.val().equals("var") || w.val().equals("const")) {
					wr.mark();
					short type = wr.next().type();
					wr.retract();

					if (type == Word.LITERAL) {
						define(new Type("var"));
						return;
					}
				}

				wr.retractWord();
				UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON);
				if (expr != null) {
					ExprNode node = expr.resolve(ctx);
					if (node.isKind(ExprNode.ExprKind.INVOKE_CONSTRUCTOR)) {
						ctx.not_invoke_constructor = false;
					}

					node.write(cw, true);
					except(semicolon);
				}
			}
		}

		immediateLabel = null;
	}

	private void _sync() throws ParseException {
		except(lParen);

		ExprNode node = ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);

		if (node.type().isPrimitive()) {
			ctx.report(Kind.ERROR, "block.sync.unexpectedPrimitive", node.type());
		}

		node.write(cw, false);

		beginCodeBlock();
		Variable syncVar = newVar("@SYNC", node.type());

		cw.one(DUP);
		cw.store(syncVar);
		cw.one(MONITORENTER);

		Label start = cw.label();

		except(lBrace);
		block(false);

		Label end = cw.label();
		Label realEnd = new Label();

		cw.jump(realEnd);

		Label exception = cw.label();
		cw.load(syncVar);
		cw.one(MONITOREXIT);
		cw.one(ATHROW);

		cw.label(realEnd);
		cw.load(syncVar);
		cw.one(MONITOREXIT);

		cw.addException(start,end,exception,TryCatchEntry.ANY);
	}

	// endregion
	// region 自定义语法
	private void _with() throws ParseException {
		except(lParen);

		ExprNode node = ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);

		if (node.type().isPrimitive()) {
			ctx.report(Kind.ERROR, "block.sync.unexpectedPrimitive", node.type());
		}

		beginCodeBlock();
		except(lBrace);

		Variable ref;
		if (node.isKind(ExprNode.ExprKind.LDC_CLASS)) ref = null;
		else {
			node.write(cw, false);
			ref = newVar("@WITH", node.type());
			cw.store(ref);
		}

		IClass info = ctx.classes.getClassInfo(node.type().owner());

		var fi = ctx.dynamicFieldImport;
		var mi = ctx.dynamicMethodImport;

		ctx.dynamicFieldImport = name -> {
			ComponentList cl = ctx.fieldListOrReport(info, name);
			if (cl != null) {
				FieldResult result = cl.findField(ctx, ref == null ? ComponentList.IN_STATIC : 0);
				if (result.error == null) return new LocalContext.Import(info, result.field.name(), ref == null ? null : new LocalVariable(ref));
			}

			return fi == null ? null : fi.apply(name);
		};
		ctx.dynamicMethodImport = name -> {
			ComponentList cl = ctx.methodListOrReport(info, name);
			if (cl != null) return new LocalContext.Import(info, name, ref == null ? null : new LocalVariable(ref));

			return mi == null ? null : mi.apply(name);
		};

		try {
			block(false);
		} finally {
			ctx.dynamicFieldImport = fi;
			ctx.dynamicMethodImport = mi;
		}
	}

	private void _defer() throws ParseException {
		UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON | ExprParser.SKIP_SEMICOLON);
		if (expr == null) {
			ctx.report(Kind.ERROR, "noExpression");
			return;
		}
		regionNew.getLast().add(expr);
	}
	// region Block, SectionFlag和条件
	private void blockOrStatement() throws ParseException {
		Word word = wr.next();
		if (word.type() == lBrace) {
			block();
		} else {
			// TODO 有问题吗？
			sectionFlag &= ~SF_BLOCK;
			statement(word);
		}
	}
	/**
	 * will not resolve these expressions
	 */
	private void skipBlockOrStatement() throws ParseException {
		if (wr.nextIf(lBrace)) {
			int level = 1;
			while (true) {
				Word w = wr.next();
				if (w.type() == lBrace) level++;
				else if (w.type() == rBrace && --level == 0) break;
			}
		} else {
			ep.parse(file, ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON);
		}
	}
	private void controlFlowTerminate() throws ParseException {
		if ((sectionFlag & SF_BLOCK) != 0) {
			if (wr.next().type() != rBrace) ctx.report(Kind.ERROR, "block.unreachable");
			wr.retractWord();
		}
		sectionFlag |= SF_BLOCK_END;
	}

	// region 异常: try-catch-finally try-with-resource
	private Label returnHook;
	private IntList returnHookUsed;
	private void _try() throws ParseException {
		Label prevHook = returnHook;
		IntList prevUse = returnHookUsed;
		returnHook = new Label();
		returnHookUsed = new IntList();

		Word w = wr.next();

		// bit1: 存在【任意异常】处理程序(不能再有更多的catch)
		// bit2: 使用了AutoCloseable
		byte flag = 0;

		Label tryBegin = cw.label();

		Label tryADefined = null;
		Variable tryADefinedVar = null;
		boolean tryANullable = false;
		// try with resource
		if (w.type() != lBrace) {
			if (w.type() != lParen) wr.unexpected(w.val(), "block.except.tryOrAuto");

			beginCodeBlock();
			do {
				int modifier = file._modifier(wr, FINAL);
				// todo infer
				IType type = ctx.resolveType(file.readType(CompileUnit.TYPE_GENERIC));

				if (!ctx.instanceOf(type.owner(), "java/lang/AutoCloseable")) {
					ctx.report(Kind.ERROR, "block.try.error.noAutoCloseable", type);
				}

				tryADefinedVar = newVar(wr.except(Word.LITERAL).val(), type);
				tryADefinedVar.isFinal = true;
				regionNew.getLast().pop();

				except(assign);
				UnresolvedExprNode node = ep.parse(file, ExprParser.STOP_RSB|ExprParser.STOP_SEMICOLON);
				if (node == null) {
					ctx.report(Kind.ERROR, "noExpression");
				} else {
					tryANullable = !(node instanceof Invoke i && i.isNew());

					writeCast(node.resolve(ctx), type);
					cw.store(tryADefinedVar);
					tryADefined = cw.label();
				}
			} while (wr.next().type() == semicolon);

			flag |= 2;
			except(lBrace);
			block(false);
		} else {
			block();
		}

		Label tryEnd = cw.label();

		Label blockEnd = new Label();
		boolean anyNormal = cw.isContinuousControlFlow(), nowNormal = anyNormal;

		if ((flag&2) != 0) {
			beginCodeBlock();
			variables.remove(tryADefinedVar.name);
			regionNew.getLast().add(tryADefinedVar);

			Label tryACloser = new Label();

			int moreSituations = 0;
			if (anyNormal) moreSituations++;
			if (returnHookUsed.size() > 0) moreSituations++;

			Variable sri = moreSituations < 2 ? null : newVar("@TWR|跳转自", Type.std(Type.INT));
			// 到此为止，不要过早优化(LavaHelper.ABSOLUTE_0)
			if (anyNormal) {
				if (sri != null) {
					cw.ldc(0);
					cw.store(sri);
					cw.one(ACONST_NULL);
					cw.jump(tryACloser);
				} else {
					cw.one(ACONST_NULL);
				}
			}

			if (returnHookUsed.size() > 0) {
				cw.label(returnHook);
				if (sri != null) {
					cw.ldc(1);
					cw.store(sri);
					cw.one(ACONST_NULL);
					cw.jump(tryACloser);
				} else {
					cw.one(ACONST_NULL);
				}
			}

			cw.addException(tryADefined, tryEnd, cw.label(), TryCatchEntry.ANY);
			if (sri != null) {
				cw.ldc(0); // 这个值不可能用上
				cw.store(sri);
			}

			Variable exc = newVar("@TWR|异常", new Type("java/lang/Object"));
			cw.store(exc);

			Label tryCloseTotallyEnd = new Label();
			Label tryCloseHandler = new Label();

			cw.label(tryACloser);
			if (tryANullable) {
				cw.load(tryADefinedVar);
				cw.jump(IFNULL, tryCloseTotallyEnd);
			}
			cw.addException(cw.label(), tryCloseHandler, tryCloseHandler, TryCatchEntry.ANY);
			cw.load(tryADefinedVar);

			IClass info = ctx.classes.getClassInfo(tryADefinedVar.type.owner());
			if ((info.modifier()&ACC_INTERFACE) != 0) cw.invokeItf(tryADefinedVar.type.owner(), "close", "()V");
			else cw.invokeV(tryADefinedVar.type.owner(), "close", "()V");

			ComponentList list = ctx.methodListOrReport(info, "close");
			if (list != null) {
				MethodResult result = list.findMethod(ctx, tryADefinedVar.type, Collections.emptyList(), null, 0);
				if (result != null) {
					// TODO 提示是由隐式调用产生的异常
					result.addExceptions(ctx, info, 1);
				}
			}

			cw.jump(tryCloseTotallyEnd);
			cw.label(tryCloseHandler);
			cw.load(exc);
			Label nextHandler = new Label();
			cw.jump(IFNONNULL, nextHandler);
			cw.one(ATHROW);
			cw.jump(tryCloseTotallyEnd);
			cw.label(nextHandler);
			cw.load(exc);
			cw.invokeV("java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V");

			cw.label(tryCloseTotallyEnd);
			cw.load(exc);
			if (moreSituations > 0) {
				if (sri != null) {
					Label label1 = new Label();
					cw.jump(IFNULL, label1);
					cw.load(exc);
					cw.one(ATHROW);
					cw.label(label1);
					cw.load(sri);
					cw.jump(IFEQ, blockEnd);
					cw.jump(returnHook = new Label());
				} else {
					cw.jump(IFNULL, anyNormal ? blockEnd : (returnHook = new Label()));
					cw.load(exc);
					cw.one(ATHROW);
				}
			} else {
				cw.one(ATHROW);
			}

			endCodeBlock();
		} else {
			if (anyNormal) cw.jump(blockEnd);
		}

		// region catch
		SimpleList<String> exTypes = new SimpleList<>();
		while ((w = wr.next()).type() == CATCH) {
			if ((flag & 1) != 0) ctx.report(Kind.ERROR, "block.try.duplicateCatch");

			TryCatchEntry entry = cw.addException(tryBegin,tryEnd,cw.label(),null);
			beginCodeBlock();

			w = wr.next();
			switch (w.type()) {
				default: wr.unexpected(w.val(), "block.except.tryOrAuto");
				case lBrace: // {
					cw.one(POP);
					flag |= 1;
					break;
				case lParen: // (
					// TODO "不能抛出异常" 的检测
					IType type = ctx.resolveType(file.readType(0));
					entry.type = type.owner();

					if (!ctx.instanceOf(entry.type, "java/lang/Throwable")) {
						ctx.report(Kind.ERROR, "block.try.error.notException", type);
					}

					if (type.genericType() != IType.STANDARD_TYPE) {
						ctx.report(Kind.ERROR, "block.try.error.generic", type);
					}

					for (int i = 0; i < exTypes.size(); i++) {
						if (ctx.instanceOf(entry.type, exTypes.get(i))) {
							ctx.report(Kind.ERROR, "block.try.error.captured", type, type);
							break;
						}
					}
					exTypes.add(entry.type);

					if ("java/lang/Throwable".equals(entry.type)) flag |= 1;

					w = wr.except(Word.LITERAL, "block.except.name");
					Variable v = newVar(w.val(), type);
					cw.store(v);

					except(rParen);
					except(lBrace);
					break;
			}

			block(false);

			nowNormal = cw.isContinuousControlFlow();
			if (nowNormal) {
				cw.jump(blockEnd);
				anyNormal = true;
			}
		}
		// endregion

		Label hook = returnHook;
		IntList used = returnHookUsed;
		returnHook = prevHook;
		returnHookUsed = prevUse;

		if (w.type() == FINALLY) {
			boolean isNormalFinally = prevHook == null && w.val().equals("finally");

			beginCodeBlock();
			except(lBrace);

			Label finally_handler = new Label();
			Variable exc = newVar("@SRF|异常", new Type("java/lang/Object"));

			cw.addException(tryBegin, cw.label(), finally_handler, TryCatchEntry.ANY);

			if (isNormalFinally) {
				int bci = cw.bci();

				// 副本的 1/3: 异常处理
				int pos = wr.index;
				cw.label(finally_handler);
				cw.store(exc);

				MethodWriter tmp = cw.fork();
				MethodWriter prev = cw;

				cw = tmp;
				block();
				tmp.writeTo(prev);
				cw = prev;

				if (cw.isContinuousControlFlow()) {
					cw.load(exc);
					cw.one(ATHROW);
				}

				int delta = cw.bci() - bci;
				int copyCount = 0;

				// 副本的 2/3: return劫持(可选)
				if (used.size() > 0) {
					copyCount++;

					// 不能inline，因为finally可能丢异常，所以要把exception handler抠掉，这太麻烦了
					boolean isVoid = cw.mn.rawDesc().endsWith(")V");
					cw.label(hook);
					if (!isVoid) cw.store(exc);

					tmp.writeTo(cw);

					if (cw.isContinuousControlFlow()) {
						if (!isVoid) cw.load(exc);
						cw.one(cw.mn.returnType().shiftedOpcode(IRETURN));
					}
				}

				// 副本的 3/3: 正常执行(可选)
				if (anyNormal) {
					copyCount++;

					cw.label(blockEnd);

					tmp.writeTo(cw);

					blockEnd = new Label();
				}

				// 这个magic number是新finally方式的最大overhead
				if (copyCount*delta >= 36) {
					ctx.report(pos, Kind.WARNING, "block.try.warn.tooManyCopies", delta, copyCount);
				}
			} else {
				// sri => subroutine id
				// rva => return value
				Variable sri = newVar("@SRF|跳转自", Type.std(Type.INT));
				Variable rva = cw.mn.returnType().type == Type.VOID ? null : newVar("@SRF|返回值", new Type("java/lang/Object"));
				Label real_finally_handler = new Label();

				// 副本的 1/3: 正常执行(可选)
				if (anyNormal) {
					if (nowNormal) // 删掉一个多余的跳转
						cw.replaceSegment(cw.nextSegmentId()-1, new StaticSegment());

					cw.label(blockEnd);
					cw.one(ICONST_0);
					cw.store(sri);
					if (rva != null) {
						cw.one(ACONST_NULL);
						cw.store(rva);
					}
					cw.one(ACONST_NULL);
					cw.store(exc);
					cw.jump(real_finally_handler);
				}

				// 副本的 2/3: return劫持
				if (used.size() > 0) {
					cw.label(hook);

					if (rva != null) cw.store(rva);
					cw.one(ICONST_1);
					cw.store(sri);
					cw.one(ACONST_NULL);
					cw.store(exc);
					cw.jump(real_finally_handler);
				}

				// 副本的 3/3: 异常
				cw.label(finally_handler);
				cw.store(exc);
				cw.one(ICONST_M1);
				cw.store(sri);
				if (rva != null) {
					cw.one(ACONST_NULL);
					cw.store(rva);
				}

				cw.label(real_finally_handler);

				block();

				// finally可以执行完
				if (cw.isContinuousControlFlow()) {
					if (used.size() > 0) {
						Label returnHook  = new Label();

						cw.load(sri);
						cw.jump(IFLE, returnHook);

						// sri > 0 : return hook
						if (rva != null) cw.load(rva);
						cw.one(cw.mn.returnType().shiftedOpcode(IRETURN));

						cw.label(returnHook);
					}

					if (anyNormal) {
						cw.load(sri);
						cw.jump(IFEQ, blockEnd = new Label());

						// sri = 0 : normal execution
					}

					// sri < 0 : exception
					cw.load(exc);
					cw.one(ATHROW);
				}
			}
		} else if ((flag&2) == 0) {
			wr.retractWord();

			// 孤立的try
			if (exTypes.isEmpty()) ctx.report(Kind.ERROR, "block.try.error.noHandler");

			StaticSegment ret = new StaticSegment(cw.mn.returnType().shiftedOpcode(IRETURN));
			for (int i = 0; i < used.size(); i++) cw.replaceSegment(used.get(i), ret);
		} else {
			cw.label(hook);
		}

		if (anyNormal) cw.label(blockEnd);
	}
	// endregion
	// region 作用域 标签 控制流终止: break continue goto return throw

	// 当前作用域新增的东西
	private final SimpleList<SimpleList<Object>> regionNew = new SimpleList<>();

	private final MyHashMap<String, LabelInfo> labels = new MyHashMap<>();
	private final MyHashMap<String, Variable> variables = new MyHashMap<>();

	private void beginCodeBlock() {
		SimpleList<Object> list = new SimpleList<>();
		list.add(cw.label());
		regionNew.add(list);
	}
	private void endCodeBlock() {
		Label blockEnd = null;
		SimpleList<Object> added = regionNew.pop();
		for (int i = 1; i < added.size(); i++) {
			Object var = added.get(i);
			if (var instanceof Variable v) {
				if (v.startPos == v.endPos && !v.name.startsWith("@"))
					ctx.report(v.startPos, Kind.WARNING, "block.var.warn.unused", v.name);

				variables.remove(v.name);
				fastSlot -= v.type.rawType().length();
			} else if (var instanceof UnresolvedExprNode expr) {
				if (blockEnd == null) blockEnd = cw.label();
				expr.resolve(ctx).write(cw, true);
			} else {
				labels.remove(var.toString());
			}
		}

		if (blockEnd != null)
			cw.addException((Label) added.get(0), blockEnd, blockEnd, null);
	}

	// 自然也有slowSlot => VarMapperX
	private int fastSlot;
	private Variable newVar(String name, IType type) {
		// TODO => CONSIDER TYPE MERGING (if var)
		Variable v = new Variable(name, type);
		v.slot = fastSlot;
		v.startPos = v.endPos = wr.index;

		if (null != variables.putIfAbsent(name, v)) {
			ctx.report(Kind.ERROR, "block.var.error.duplicate", name);
			return v;
		}

		if (!regionNew.isEmpty()) regionNew.get(regionNew.size()-1).add(v);

		fastSlot += type.rawType().length();
		return v;
	}

	// 对循环或代码块上label的支持
	private LabelInfo immediateLabel;
	// 按照代码块(循环)深度递增的,方便break/continue寻找
	private Label curBreak, curContinue;

	private void _goto() throws ParseException {
		Word w = wr.except(Word.LITERAL);
		LabelInfo info = labels.get(w.val());
		if (info != null && info.head != null) {
			cw.jump(info.head);
			// TODO check variable change
		} else {
			ctx.report(Kind.ERROR, "block.goto.error.noSuchLabel", w.val());
		}
		except(semicolon);
		controlFlowTerminate();
	}

	/**
	 * @param isBreak true => break, false => continue
	 */
	private void _break(boolean isBreak) throws ParseException {
		String errId = wr.current().val();

		Word w = wr.next();
		if (w.type() == Word.LITERAL) {
			LabelInfo info = labels.get(w.val());
			Label node;
			if (info != null && (node = isBreak ? info.onBreak : info.onContinue) != null) {
				cw.jump(node);
			} else {
				ctx.report(Kind.ERROR, "block.goto.error.noSuchLabel", w.val());
			}

			except(semicolon);
		} else if (w.type() == semicolon) {
			Label node = isBreak ? curBreak : curContinue;
			if (node != null) {
				cw.jump(node);
			} else {
				ctx.report(Kind.ERROR, "block.goto.error.outsideLoop", errId);
			}
		} else {
			throw wr.err("block.except.labelOrSemi");
		}

		controlFlowTerminate();
	}
	private void _return() throws ParseException {
		Word w = wr.next();
		if (methodType != 0) ctx.report(Kind.ERROR, "block.return.error.outsideMethod");

		UnresolvedExprNode expr;
		if (w.type() != semicolon) {
			wr.retractWord();

			expr = ep.parse(file, ExprParser.STOP_SEMICOLON);
			assert expr != null;
			// TODO generic return value check (direct TypeParam, not checking bounds)
			writeCast(expr.resolve(ctx), cw.mn.returnType());
			expr.resolve(ctx).write(cw, false);

			except(semicolon);
		} else {
			expr = null;
		}

		Type rt = cw.mn.returnType();
		if (rt.getActualType() == Type.VOID) {
			if (expr != null) {
				ctx.report(Kind.ERROR, "block.return.error.exceptVoid");
			}
		} else if (expr == null) {
			ctx.report(Kind.ERROR, "block.return.error.exceptExpr");
		}

		// TODO 未来支持多返回值这里要改
		if (returnHook != null) {
			returnHookUsed.add(cw.nextSegmentId());
			cw.jump(returnHook);
		} else {
			cw.one(rt.shiftedOpcode(IRETURN));
		}

		controlFlowTerminate();
	}

	private void _throw() throws ParseException {
		UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON|ExprParser.NAE);
		except(semicolon);

		if (expr == null) {
			ctx.report(Kind.ERROR, "noExpression");
		} else {
			//writeCast(expr, new Type("java/lang/Throwable"));
			expr.resolve(ctx).write(cw, false);
		}

		cw.one(ATHROW);
		controlFlowTerminate();
	}
	// endregion
	// region 条件判断: if / generic-condition
	private void _if() throws ParseException {
		Label ifFalse = condition(true, null, false, false);

		if (constantHint < 0) skipBlockOrStatement();
		else blockOrStatement();

		if (!wr.nextIf(ELSE)) {
			cw.label(ifFalse);
			return;
		}

		Label end;
		if (constantHint <= 0 && cw.isContinuousControlFlow()) {
			cw.jump(end = new Label());
		} else {
			end = null;
		}

		// if goto else goto 由MethodWriter处理
		cw.label(ifFalse);
		if (constantHint > 0) skipBlockOrStatement();
		else blockOrStatement();
		if (end != null) cw.label(end);

		// if (xx) {} else if() {}
		//      is equivalent to
		// if (xx) {} else { if() {} }
	}

	// 宏的一部分，忽略常量部分的所有内容，其它的处理还在Trinary和switch
	private int constantHint;
	/**
	 * 条件判断
	 * @return 不成立的跳转目标
	 */
	@NotNull
	private Label condition(boolean checkLSB, Label target, boolean invert, boolean nullsafe) throws ParseException {
		if (checkLSB) except(lParen);

		if (target == null) target = new Label();

		constantHint = 0;
		UnresolvedExprNode expr = ep.parse(file, (checkLSB ? ExprParser.STOP_RSB|ExprParser.SKIP_RSB : ExprParser.STOP_SEMICOLON));
		if (expr == null) {
			if (!nullsafe) ctx.report(Kind.ERROR, "noExpression");
		} else {
			int i = cw.beginJumpOn(invert, target);

			ExprNode node = expr.resolve(ctx);
			if (node.isConstant() && node.type() == Type.std(Type.BOOLEAN))
				constantHint = ((boolean) node.constVal()) ? 1 : -1;
			writeCast(node, Type.std(Type.BOOLEAN));

			cw.endJumpOn(i);
		}

		if(!checkLSB) except(semicolon);
		return target;
	}
	// endregion
	// region 循环: for while do-while
	private void loopBody(@Nullable Label continueTo, @NotNull Label breakTo) throws ParseException {
		LabelInfo info = immediateLabel;
		if (info != null) {
			info.onBreak = breakTo;
			info.onContinue = continueTo;
			immediateLabel = null;
		}

		Label prevBreak = curBreak, prevContinue = curContinue;

		if (continueTo != null) curContinue = continueTo;
		curBreak = breakTo;

		blockOrStatement();

		curContinue = prevContinue;
		curBreak = prevBreak;
	}

	private void _for() throws ParseException {
		boolean concurrent = wr.current().val().equals("...for");
		except(lParen);

		Word w = wr.next();
		boolean hasVar;
		Label continueTo, breakTo, nBreakTo;
		UnresolvedExprNode execLast;

		NoForEach:{
		if (w.type() != semicolon) {
			wr.retractWord();

			beginCodeBlock();
			define(null);
			hasVar = true;

			// region ForEach for (Vtype vname : expr) {}
			if (wr.current().type() == colon) {
				Variable lastVar = (Variable) regionNew.getLast().getLast();

				ExprNode iter = ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);
				IType type = iter.type();
				IClass owner;
				TypeCast.Cast cast;

				breakTo = CodeWriter.newLabel();
				if (type.array() == 0) {
					if (type.isPrimitive()) {
						ctx.report(Kind.ERROR, "symbol.error.derefPrimitiveField", type);
						skipBlockOrStatement();
						return;
					}

					// 没有用到, 只是让它看起来一直有值
					if ((cast = ctx.castTo(type, new Generic("java/lang/Iterable", Collections.singletonList(lastVar.type)), 0)).type < 0) {
						skipBlockOrStatement();
						return;
					}

					owner = ctx.getClassOrArray(type);
					IntBiMap<String> fpCheck;
					try {
						fpCheck = ctx.classes.parentList(owner);
					} catch (ClassNotFoundException e) {
						ctx.report(Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
						fpCheck = new IntBiMap<>();
					}

					if (concurrent || !fpCheck.containsValue("java/util/List") || !fpCheck.containsValue("java/util/RandomAccess")) {
						// iterable

						Variable _itr = newVar("@ForEx|迭代器", new Type("java/util/Iterator"));
						iter.write(cw, false);
						cw.invokeItf("java/lang/Iterable", "iterator", "()Ljava/util/Iterator;");
						cw.store(_itr);

						continueTo = cw.label();
						execLast = null;

						cw.load(_itr);
						cw.invokeItf("java/util/Iterator", "hasNext", "()Z");
						cw.jump(IFEQ, breakTo);

						cw.load(_itr);
						cw.invokeItf("java/util/Iterator", "next", "()Ljava/lang/Object;");
						cw.clazz(CHECKCAST, lastVar.type.rawType());
						cw.store(lastVar);

						break NoForEach;
					}
					// else => indexed list access
				} else {
					// array access
					IType t1 = type.clone();
					t1.setArrayDim(t1.array()-1);
					cast = ctx.castTo(t1, lastVar.type, 0);
					owner = null;
				}

				Variable _arr;
				if (iter instanceof LocalVariable lv) {
					_arr = lv.getVariable();
				} else {
					_arr = newVar("@ForEx|表达式", type);
					iter.write(cw, false);
					cw.store(_arr);
				}
				Variable _i = newVar("@ForEx|索引", Type.std(Type.INT));
				Variable _len = newVar("@ForEx|长度", Type.std(Type.INT));

				// type[] __var = expr;
				// int __i = 0;
				// int __len = __var.length;
				cw.load(_arr);
				if (type.array() == 0) cw.invokeItf("java/util/List", "size", "()I");
				else cw.one(ARRAYLENGTH);
				cw.store(_len);
				cw.one(ICONST_0);
				cw.store(_i);

				continueTo = cw.label();
				execLast = new ExprNode() {
					@Override
					public String toString() {return "<internal> _i++;";}
					@Override
					public IType type() {return Type.std(Type.INT);}
					@Override
					public void write(MethodWriter cw, boolean noRet) {cw.iinc(_i, 1);}
				};

				// :continue_to
				cw.load(_i);
				cw.load(_len);
				cw.jump(IF_icmpge, breakTo);

				cw.load(_arr);
				cw.load(_i);
				if (type.array() == 0) {
					// 检查可能存在的override
					MethodResult result = ctx.methodListOrReport(owner, "get").findMethod(ctx, type, Collections.singletonList(Type.std(Type.INT)), Collections.emptyMap(), 0);
					assert result != null;

					cw.invoke((ctx.classes.getClassInfo(result.method.owner).modifier()&ACC_INTERFACE) != 0 ? INVOKEINTERFACE : INVOKEVIRTUAL, result.method);
					ctx.castTo(result.method.returnType(), lastVar.type, TypeCast.E_DOWNCAST).write(cw);
				} else {
					cw.one(XALoad(type.rawType()));
					cast.write(cw);
				}
				cw.store(lastVar);
				// type vname = __var[__i];

				break NoForEach;
			}
			// endregion
		} else {
			hasVar = false;
		}

			continueTo = cw.label();
			breakTo = condition(false, null, false, true);
			execLast = ep.parse(file, ExprParser.STOP_RSB | ExprParser.SKIP_RSB);
		}

		loopBody(continueTo, nBreakTo = CodeWriter.newLabel());

		if (execLast != null) {
			Label ct1 = new Label(continueTo);

			continueTo.clear();
			cw.label(continueTo);
			execLast.resolve(ctx).write(cw, true);
			cw.jump(ct1);
		} else {
			cw.jump(continueTo);
		}

		if (hasVar) endCodeBlock();

		cw.label(breakTo);

		// for-else (python语法) 如果在循环内使用break退出，则不会进入该分支
		// 如果循环正常结束，或从未开始，都会进入该分支
		if (wr.nextIf(ELSE)) {
			blockOrStatement();
			cw.label(nBreakTo);
		} else {
			nBreakTo.set(breakTo);
		}
	}
	private void _while() throws ParseException {
		MethodWriter fork = cw.fork(), prev = cw;
		cw = fork;
		Label head = condition(true, null, true, false);
		cw = prev;

		// 结构: (VM规范说这样每次循环都少一次goto)
		// ...
		// goto continueTo
		// head:
		// 循环体
		// continueTo:
		// 比较 => head
		// breakTo:

		switch (constantHint) {
			case 0 -> {
				Label continueTo = new Label(), breakTo = new Label();
				cw.jump(continueTo);
				Label head1 = cw.label();

				loopBody(continueTo, breakTo);

				cw.label(continueTo);
				fork.writeTo(cw);
				head.set(head1);
				cw.label(breakTo);
			}
			case 1 -> { // while true
				Label continueTo = new Label(), breakTo = new Label();
				cw.label(continueTo);

				loopBody(continueTo, breakTo);

				cw.jump(continueTo);
				cw.label(breakTo);
			}
			case -1 -> skipBlockOrStatement(); // while false
		}
	}
	private void _doWhile() throws ParseException {
		Label continueTo = cw.label(), breakTo = new Label();

		loopBody(continueTo, breakTo);

		except(WHILE);
		condition(true, breakTo, false, false);
		except(semicolon);

		// do {} while (false);
		// C的宏常这样使用……
		// 怎么，C没有代码块么，真是优优又越越啊
		if (constantHint != -1) cw.jump(continueTo);
		cw.label(breakTo);
	}
	// endregion

	/**
	 * TODO switch
	 */
	private void _switch() throws ParseException {
		boolean tableFix = wr.current().val().equals("...switch");
		except(lParen);

		var sval = ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);

		Object cst;
		if (sval.isConstant()) {
			if (sval.isKind(ExprNode.ExprKind.IMMEDIATE_CONSTANT))
				ctx.report(Kind.WARNING, "switch.constant");
			cst = sval.constVal();
		} else {
			cst = null;
		}
		except(lBrace);

		List<Object> labels = Helpers.cast(ctx.annotationTmp); labels.clear();

		Label breakTo = new Label();
		MethodWriter tmp = cw;

		List<SwitchNode> branches = new SimpleList<>();

		int flags = (sectionFlag << 8) | 128;
		boolean defaultBranch = false;

		loop:
		while (wr.hasNext()) {
			SwitchNode kase;
			boolean match = false;

			Word w = wr.next();
			switch (w.type()) {
				default: throw wr.err("unexpected_2:"+w.val()+":block.except.switch");
				case rBrace: break loop;

				case CASE:
					labels.clear();
					do {
						var expr = ep.parse(file, ExprParser.STOP_COLON|ExprParser.STOP_COMMA|ExprParser.STOP_LAMBDA|ExprParser.CHECK_VARIABLE_DECLARE|ExprParser.NAE);

						/*
						 * switch (o) {
						 *         case Integer i -> i.doubleValue();
						 *         case Float f -> f.doubleValue();
						 *         case String s -> Double.parseDouble(s);
						 *         default -> 0d;
						 * };
						 */
						if (expr instanceof VariableDeclare vd) {
							labels.add(vd);
							w = wr.next();
							flags |= 2;

							beginCodeBlock();
							newVar(vd.name, vd.type);
							break;
						}

						ExprNode node = expr.resolve(ctx);
						if (node.isConstant() && node.constVal().equals(cst)) match = true;
						labels.add(node);

						flags |= 1;
					} while ((w = wr.next()).type() == comma);

					kase = new SwitchNode();
					kase.labels = new SimpleList<>(labels);
				break;
				case DEFAULT:
					w = wr.next();
					kase = new SwitchNode();
					match = true;
				break;
			}

			if (w.type() == colon) {
				flags |= 4;
				// cannot use VD in case :
				if ((flags&2) != 0) ctx.report(Kind.ERROR, "block.switch.mixedCase");
			} else if (w.type() == lambda) {
				flags |= 8;
				// is not VD
				if ((flags&2) == 0) beginCodeBlock();
			} else throw wr.err("unexpected_2:"+w.type()+":block.except.switchCase");

			// has 4, 8 but not 16
			if ((flags&28) == 12) {
				ctx.report(Kind.ERROR, "block.switch.mixedCase");
				flags |= 16; // error already thrown
			}

			cw = tmp.fork();
			kase.block = cw;

			// default
			if (kase.labels == null) {
				if (defaultBranch) ctx.report(Kind.ERROR, "block.switch.manyDefault");
				defaultBranch = true;
			}

			boolean parse = cst == null || match;
			if (parse) branches.add(kase);

			switchBlock(breakTo, w.type() == lambda, parse);
			if ((sectionFlag&SF_BLOCK_END) == 0) flags &= ~128;
		}

		cw = tmp;

		sectionFlag = (byte) (flags >>> 8);
		// if switch will never continue
		// TODO check continuous control flow for EVERY subblock
		// if (defaultBranch && (flags&128) != 0 && cw.isContinuousControlFlow()) sectionFlag |= SF_BLOCK_END;

		// 检测type switch和number switch的混用
		switch (flags & 3) {
			case 3 -> ctx.report(Kind.ERROR, "block.switch.mixedCase");
			case 2 -> {
				// class switch, must be final/sealed class to use getClass();
				ctx.report(Kind.ERROR, "Class switch 暂未实现");
				return;
			}
		}

		smallOptimize:
		if (branches.size()-(defaultBranch?1:0) <= 1) {
			int size = branches.size();
			if (size == 0) return;

			SwitchNode node;

			// 找任意case，如果没有取default
			do {
				node = branches.get(--size);
				if (node.labels != null) break;
			} while (size != 0);

			myBlock:
			if (cst != null) {
				node.block.writeTo(cw);
			} else {
				ctx.report(Kind.SEVERE_WARNING, "block.switch.noBranch");

				if (node.labels == null) {
					// default, 没找到任何case

					sval.write(cw, false);
					// POP or POP2
					cw.one((byte) (0x56 + sval.type().rawType().length()));
					node.block.writeTo(cw);
					break myBlock;
				} else if (node.labels.size() > 1) {
					// 还是有超过一个case的
					break smallOptimize;
				}

				// if
				ExprNode cmp = ep.binary(equ, sval, ((ExprNode) node.labels.get(0))).resolve(ctx);

				Label ifNe = new Label();

				// 正确的，IFEQ对于Binary equ来说是0，是false，是不相等
				int i = cw.beginJumpOn(false, ifNe);
				cmp.write(cw, false);
				cw.endJumpOn(i);

				node.block.writeTo(cw);

				if (branches.size() != 1) {
					Label end = new Label();
					cw.jump(end);
					cw.label(ifNe);
					branches.get(size^1).block.writeTo(cw);

					ifNe = end;
				}
				cw.label(ifNe);
			}

			cw.label(breakTo);
			return;
		}

		IType sType = sval.type();
		/* see switch(kind) for detail */
		int kind = 0;
		switch (sType.getActualType()) {
			case Type.BOOLEAN, Type.VOID -> ctx.report(Kind.ERROR, "block.switch.incompatible", sType);
			case Type.LONG, Type.FLOAT, Type.DOUBLE -> kind = 3;
			case Type.CLASS -> {
				String owner = sType.owner();
				if (owner.equals("java/lang/Integer")) break;
				if (owner.equals("java/lang/String")) {
					if (tableFix) {
						kind = 4;
					} else {
						ctx.report(Kind.NOTE, "block.switch.lookupSwitch");
						kind = 1;
					}
					break;
				}

				IClass clazz = ctx.classes.getClassInfo(owner);
				if (ctx.instanceOf(owner, "java/lang/Enum")) {
					kind = tableFix ? 3 : 2;
					break;
				}

				Annotation switchable = ctx.getAnnotation(clazz, clazz, "roj/compiler/api/Switchable", false);
				if (switchable != null) {
					kind = switchable.getBoolean("identity", true) ? 3 : 4;
				} else {
					ctx.report(Kind.ERROR, "block.switch.incompatible", sType);
				}
			}
		}
		System.out.println("target kind="+kind);

		// TODO case null
		// TODO check duplicate case
		switch (kind) {
			case 0 -> {// int
				writeCast(sval, Type.std(Type.INT));

				SwitchSegment sw = new SwitchSegment(0);
				sw.def = breakTo;

				cw.addSegment(sw);

				for (int i = 0; i < branches.size();) {
					SwitchNode branch = branches.get(i++);

					Label pos = cw.label();
					branch.block.writeTo(cw);

					if (branch.labels == null) {
						sw.def = pos;
					} else {
						for (Object label : branch.labels)
							sw.branch(((AnnValInt) ((ExprNode) label).constVal()).value, pos);
					}
				}

				if (sw.findBestCode() == LOOKUPSWITCH && branches.size() > 2)
					ctx.report(Kind.WARNING, "block.switch.lookupSwitch");
			}
			case 1 -> {// string
				sval.write(cw, false);
				switchString(branches, breakTo);
			}
			case 2 -> {// enum
				sval.write(cw, false);

				// switchmap for enum =>
				linearMapping(breakTo, branches);
			}
			case 3, 4 -> {// (Identity)HashMap
				sval.write(cw, false);

				ConstantData switchMap = new ConstantData();
				switchMap.access |= ACC_SYNTHETIC;

				switchMap.name(ctx.file.name+"$SwitchMap$1");
				switchMap.newField(ACC_STATIC|ACC_FINAL, "switchMap", "Lroj/compiler/runtime/SwitchMap;");

				switchMap.newMethod(ACC_STATIC, "<clinit>", "()V");
				MethodWriter c = ctx.classes.createMethodPoet(switchMap, switchMap.methods.getLast());
				c.visitSize(10, 0);

				int size = 0;
				for (SwitchNode branch : branches) {
					List<Object> labels1 = branch.labels;
					if (labels1 != null) size += labels1.size();
				}

				c.ldc(size);
				c.one((byte) kind); // ICONST_0 => 3
				c.invokeS("roj/compiler/runtime/SwitchMap$Builder", "builder", "(IZ)Lroj/compiler/runtime/SwitchMap$Builder;");

				int i = 0;
				for (SwitchNode branch : branches) {
					List<Object> labels1 = branch.labels;
					if (labels1 == null) continue;
					for (Object o : labels1) {
						((ExprNode) o).write(c, false);
						c.ldc(i);
						c.invokeV("roj/compiler/runtime/SwitchMap$Builder", "add", "(Ljava/lang/Object;I)Lroj/compiler/runtime/SwitchMap$Builder;");
					}

					i++;
				}

				c.invokeV("roj/compiler/runtime/SwitchMap$Builder", "build", "()Lroj/compiler/runtime/SwitchMap;");
				c.field(PUTSTATIC, switchMap, 0);

				cw.field(GETSTATIC, switchMap, 0);
				sval.write(cw, false);
				cw.invoke(INVOKEVIRTUAL, "roj/compiler/runtime/SwitchMap", "get", "(Ljava/lang/Object;)I");
				linearMapping(breakTo, branches);

				c.one(Opcodes.RETURN);
				c.finish();
				c.mn.putAttr(new AttrUnknown("Code", c.bw.toByteArray()));
				switchMap.dump();
				ClassDefiner.INSTANCE.defineClass(switchMap);
			}
		}

		cw.label(breakTo);
	}
	private void linearMapping(Label breakTo, List<SwitchNode> branches) {
		SwitchSegment sw = new SwitchSegment(TABLESWITCH);
		sw.def = breakTo;

		cw.addSegment(sw);

		int j = 0;
		for (int i = 0; i < branches.size();) {
			SwitchNode branch = branches.get(i++);

			Label pos = cw.label();
			branch.block.writeTo(cw);

			if (branch.labels == null) sw.def = pos;
			else sw.branch(j++, pos);
		}
	}
	private void switchString(List<SwitchNode> branches, Label breakTo) {
		var v = newVar("@@SwitchStr|TEMP", new Type("java/lang/String"));
		var c = cw;

		c.one(DUP);
		c.store(v);
		c.invoke(INVOKESPECIAL, "java/lang/String", "hashCode", "()I");

		SwitchSegment sw = CodeWriter.newSwitch(LOOKUPSWITCH);
		c.addSegment(sw);
		sw.def = breakTo;


		// check duplicate
		IntMap<List<Map.Entry<String, Label>>> tmp = new IntMap<>();

		for (int i = 0; i < branches.size();) {
			SwitchNode branch = branches.get(i++);
			Label pos = branch.location = new Label();

			if (branch.labels == null) {
				sw.def = pos;
			} else {
				for (Object label : branch.labels) {
					String key = (String) ((ExprNode) label).constVal();
					int hash = key.hashCode();

					var dup = tmp.get(hash);
					if (dup == null) tmp.putInt(hash, dup = new SimpleList<>(2));

					dup.add(new AbstractMap.SimpleEntry<>(key, pos));
				}
			}
		}

		for (IntMap.Entry<List<Map.Entry<String, Label>>> entry : tmp.selfEntrySet()) {
			Label pos = new Label();
			sw.branch(entry.getIntKey(), pos);
			c.label(pos);
			List<Map.Entry<String, Label>> list1 = entry.getValue();
			for (int i = 0; i < list1.size(); i++) {
				Map.Entry<String, Label> entry1 = list1.get(i);
				c.load(v);
				c.ldc(entry1.getKey());
				c.invoke(INVOKESPECIAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z");
				c.jump(IFNE, entry1.getValue());
			}

			c.jump(sw.def);
		}

		// 如果要内联到上面的循环，那么需要确定不是continuousControlFlow（检查有没有人用break）
		for (int i = 0; i < branches.size();) {
			SwitchNode branch = branches.get(i++);
			c.label(branch.location);
			branch.block.writeTo(c);
		}
	}

	@SuppressWarnings("fallthrough")
	private void switchBlock(Label endPoint, boolean newSwitch, boolean parse) throws ParseException {
		while (true) {
			if (parse) loopBody(null, endPoint);
			else skipBlockOrStatement();

			Word w = wr.next();
			switch (w.type()) {
				case CASE, DEFAULT, rBrace:
					wr.retractWord();
					if (newSwitch) {
						if ((sectionFlag&SF_BLOCK_END) == 0) cw.jump(endPoint);
						endCodeBlock();
					}
				return;
			}

			if (newSwitch) ctx.report(Kind.ERROR, "unexpected_2", w.val(), "block.except.switch");
		}
	}

	// endregion
	// region 表达式和变量

	/**
	 * 定义变量 var/const/?
	 * 没有支持注解的计划。
	 */
	private void define(IType type) throws ParseException {
		int modifier = 0;
		if (type == null) {
			modifier = file._modifier(wr, FINAL);
			type = file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC);
		}

		boolean isVar = false;

		if (type.genericType() == 0 && "var".equals(type.owner())) {
			// dynamic type
			type = null;
			isVar = true;
		} else {
			ctx.resolveType(type);
		}

		Word w;
		do {
			String name = wr.except(Word.LITERAL).val();
			// TODO 自动分配还要一段时间
			Variable variable = newVar(name, isVar ? Type.std(Type.DOUBLE) : type);

			w = wr.next();
			if (w.type() == assign) {
				if (modifier != 0) variable.isFinal = true;

				ExprNode node = ep.parse(file, ExprParser.STOP_SEMICOLON|ExprParser.STOP_COMMA|ExprParser.NAE).resolve(ctx);
				if (isVar) {
					// TODO merge (optional ?)
					variable.type = type = node.type();
				}
				writeCast(node, type);
				cw.store(variable);

				w = wr.next();
			} else if (modifier != 0) {
				// TODO 支持延后赋值
				ctx.report(Kind.ERROR, "block.var.error.final");
			}
		} while (w.type() == comma);

		//if (w.type() != semicolon) wr.unexpected(w.val(), byId(stop));
	}

	private void writeCast(ExprNode node, IType type) {
		TypeCast.Cast cast = ctx.castTo(node.type(), type, 0);
		node.writeDyn(cw, cast);
	}

	//endregion
	// region 输出错误


	private void except(short id) throws ParseException {
		wr.except(id, byId(id));
	}

	//endregion
}