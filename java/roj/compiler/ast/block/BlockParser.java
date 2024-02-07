package roj.compiler.ast.block;

import org.jetbrains.annotations.NotNull;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.asm.visitor.StaticSegment;
import roj.asm.visitor.SwitchSegment;
import roj.asm.visitor.XAttrCode;
import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.VariableDeclare;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.UnresolvedExprNode;
import roj.compiler.context.CompileContext;
import roj.compiler.context.CompileUnit;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;

/**
 * KScript语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class BlockParser {
	private final CompileContext ctx;
	private final ExprParser ep;
	private MethodWriter cw;
	private CompileUnit file;
	private JavaLexer wr;
	int methodType;

	public BlockParser(CompileContext ctx) {
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

	@Deprecated
	public void init(CompileUnit u, int start, MethodNode mn) {
		file = u;
		wr = u.getLexer();
		wr.index = start;
	}

	/// region 解析

	public void parseStaticInit(CompileUnit file, XAttrCode attr, int begin, int end) throws ParseException {
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		parse0();
	}

	public void parseGlobalInit(CompileUnit file, XAttrCode attr, int begin, int end) throws ParseException {
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		parse0();
	}

	public MethodWriter parseMethod(CompileUnit file, MethodNode mn, List<String> names, int begin, int end) throws ParseException {
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		methodType = 0;
		cw = new MethodWriter(file, mn);
		ctx.variables = this.variables;
		cw.ctx1 = ctx;
		beginCodeBlock();
		parse0();
		endCodeBlock();
		// 这也太拉了
		// TODO => 想个办法实现方法整体中的推断（估计很难...）
		/*var test = new SimpleList<>();
		test.add("3");
		String o = (String) test.get(0);*/
		return cw;
	}

	public void parse0() throws ParseException {
		//wr.setLineHandler(this);
		//TODO expr写的时候要注意line

		while (true) {
			Word w = wr.next();
			if (w.type() == Word.EOF) {
				break;
			} else {
				statement(w);
			}
		}
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
	byte sectionFlag;
	static final byte SF_BLOCK = 1, SF_BLOCK_END = 2, SF_SWITCH = 4;

	/**
	 * 函数体
	 */
	@SuppressWarnings("fallthrough")
	private void block() throws ParseException { block(true); }
	private void block(boolean shouldBegin) throws ParseException {
		boolean chained = (sectionFlag & 8) != 0;

		if (shouldBegin) beginCodeBlock();
		sectionFlag |= 9;
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case Word.EOF: case rBrace: endCodeBlock(); return;
				default: statement(w); sectionFlag &= ~8;
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
				if (labels.containsKey(val)) ctx.report(Kind.ERROR, "duplicate_label:"+w.val());
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

				// TODO remove this after skip_semicolon implemented
				except(semicolon);
			}
			return;
		}

		switch (w.type()) {
			case CASE, DEFAULT -> {
				if ((sectionFlag & 4) != 0) {
					wr.retractWord();
				} else {
					ctx.report(Kind.ERROR, "not_statement");
				}
			}
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
			case semicolon -> ctx.report(Kind.WARNING, "statement.empty");
			case lBrace -> block();
			case BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE -> {
				wr.retractWord();
				define(null);
			}
			default -> {
				wr.retractWord();
				UnresolvedExprNode expr = ep.parse(file, 0);
				if (expr != null) {
					expr.resolve(ctx).write(cw, true);
					except(semicolon);
				}
			}
		}

		immediateLabel = null;
	}

	private void _sync() throws ParseException {
		except(lParen);

		UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB);
		if (expr == null) {
			ctx.report(Kind.ERROR, "statement.empty");
			return;
		}
		writeCast(expr.resolve(ctx), new Type("java/lang/Object"));

		beginCodeBlock();
		Variable syncVar = newVar("@SYNC", new Type("java/lang/Object"));

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
	// region 条件

	private List<ExprNode> deferrers = new SimpleList<>();
	// TODO layered
	private List<String> willThrowUnchecked = new SimpleList<>();

	private void blockOrStatement() throws ParseException {
		Word word = wr.next();
		if (word.type() == lBrace) {
			block();
		} else {
			sectionFlag &= ~1;
			statement(word);
		}
	}

	private void controlFlowTerminate() throws ParseException {
		// FIXME
		if ((sectionFlag & 1) != 0) {
			Word w = wr.next();
			if (w.type() != rBrace) {
				ctx.report(Kind.ERROR, "statement.unreachable");
			}
			wr.retractWord();
		}

		sectionFlag |= 2;
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
					ctx.report(Kind.ERROR, "not_statement");
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

			try {
				ComponentList list = ctx.classes.methodList(info, "close");
				if (list != null) {
					MethodResult result = list.findMethod(ctx, tryADefinedVar.type, new SimpleList<>(), null, 0);
					if (result != null) {
						// TODO 提示是由隐式调用产生的异常
						result.addExceptions(ctx, info, 1);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
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
					// TODO use already known index
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

	private void beginCodeBlock() { regionNew.add(new SimpleList<>()); }
	private void endCodeBlock() {
		SimpleList<Object> added = regionNew.pop();
		for (int i = 0; i < added.size(); i++) {
			Object var = added.get(i);
			if (var instanceof Variable v) {
				if (v.startPos == v.endPos && !v.name.startsWith("@"))
					ctx.report(v.startPos, Kind.WARNING, "unused.var", v.name);

				variables.remove(v.name);
				fastSlot -= v.type.rawType().length();
			} else {
				labels.remove(var.toString());
			}
		}
	}

	// 自然也有slowSlot => VarMapperX
	private int fastSlot;
	private Variable newVar(String name, IType type) {
		// TODO => CONSIDER TYPE MERGING (if var)
		Variable v = new Variable(name, type);
		v.slot = fastSlot;
		v.startPos = v.endPos = wr.index;

		if (null != variables.putIfAbsent(name, v)) ctx.report(Kind.ERROR, "var.exist");
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
		UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON);
		except(semicolon);

		if (expr == null) {
			ctx.report(Kind.ERROR, "block.error.noExpression");
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
		Label ifFalse = condition(true, null, false);

		blockOrStatement();

		Word word = wr.next();
		if (word.type() != ELSE) {
			wr.retractWord();
			// if false goto : false
			//   ifTrue
			// : false
			cw.label(ifFalse);
			return;
		}

		Label end;
		if (cw.isContinuousControlFlow()) {
			cw.jump(end = new Label());
		} else {
			end = null;
		}

		// if goto else goto 由MethodWriter处理
		cw.label(ifFalse);
		blockOrStatement();
		if (end != null) cw.label(end);

		// if (xx) {} else if() {}
		//      is equivalent to
		// if (xx) {} else { if() {} }
	}

	/**
	 * 条件判断
	 * @return 不成立的跳转目标
	 */
	@NotNull
	private Label condition(boolean checkLSB, Label target, boolean invert) throws ParseException {
		if (checkLSB) except(lParen);

		if (target == null) target = new Label();

		UnresolvedExprNode expr = ep.parse(file, (checkLSB ? ExprParser.STOP_RSB|ExprParser.SKIP_RSB : ExprParser.STOP_SEMICOLON));
		if (expr == null) {
			ctx.report(Kind.ERROR, "block.error.noExpression");
		} else {
			int i = cw.beginJumpOn(invert, target);
			expr.resolve(cw.ctx1).write(cw, false);
			cw.endJumpOn(i);
		}

		if(!checkLSB) except(semicolon);
		return target;
	}
	// endregion
	// region 循环: for while do-while
	private void loopBody(@NotNull Label continueTo, @NotNull Label breakTo) throws ParseException {
		LabelInfo info = immediateLabel;
		if (info != null) {
			info.onBreak = breakTo;
			info.onContinue = continueTo;
			immediateLabel = null;
		}

		Label prevBreak = curBreak, prevContinue = curContinue;

		curContinue = continueTo;
		curBreak = breakTo;

		blockOrStatement();

		curContinue = prevContinue;
		curBreak = prevBreak;
	}

	private void _for() throws ParseException {
		except(lParen);

		Word w = wr.next();
		boolean hasVar;
		if (w.type() != semicolon) {
			wr.retractWord();

			beginCodeBlock();
			define(null);
			hasVar = true;

			if (wr.current().type() == colon) {
				// TODO enhanced for loop
			}
		} else {
			hasVar = false;
		}

		Label continueTo = cw.label();
		Label breakTo = condition(false, null, false);

		UnresolvedExprNode execLast = ep.parse(file, ExprParser.STOP_RSB);
		except(rParen);

		loopBody(continueTo, breakTo);

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

		// for-else (python语法)
		if (wr.next().type() == ELSE) {
			blockOrStatement();
		} else {
			wr.retractWord();
		}

		cw.label(breakTo);
	}
	private void _while() throws ParseException {
		MethodWriter fork = cw.fork(), prev = cw;
		cw = fork;
		Label head = condition(true, null, true);
		cw = prev;

		// 结构: (VM规范说这样每次循环都少一次goto)
		// ...
		// goto continueTo
		// head:
		// 循环体
		// continueTo:
		// 比较 => head
		// breakTo:

		Label continueTo = new Label(), breakTo = new Label();
		cw.jump(continueTo);
		Label head1 = cw.label();

		loopBody(continueTo, breakTo);

		cw.label(continueTo);
		fork.writeTo(cw);
		head.set(head1);
		cw.label(breakTo);
	}
	private void _doWhile() throws ParseException {
		Label continueTo = cw.label(), breakTo = new Label();

		loopBody(continueTo, breakTo);

		except(WHILE);
		condition(true, breakTo, false);
		except(semicolon);

		cw.jump(continueTo);
		cw.label(breakTo);
	}
	// endregion

	/**
	 * TODO switch
	 */
	private void _switch() throws ParseException {
		except(lParen);

		ExprNode expr = (ExprNode) ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB);
		if (expr == null) {
			throw wr.err("block.error.noExpression");
		}
		Object cst;
		if (expr.isConstant()) {
			ctx.report(Kind.WARNING, "switch.warn.always");
			cst = expr.isConstant();
		} else {
			cst = null;
		}
		expr.write(cw, false);

		except(lBrace);

		Label breakTo = new Label();
		SwitchSegment node = new SwitchSegment(TABLESWITCH);

		/*
		 * switch (o) {
		 *         case Integer i -> i.doubleValue();
		 *         case Float f -> f.doubleValue();
		 *         case String s -> Double.parseDouble(s);
		 *         default -> 0d;
		 * };
		 */
		IType sType = expr.type();
		/*
		 * 0: int
		 * 1: string
		 * 2: enum
		 * 3: ToIntMap
		 */
		int kind = 0;
		switch (sType.getActualType()) {
			case VOID: throw wr.err("未预料的类型");
			case BOOLEAN, LONG, FLOAT, DOUBLE: kind = 3; break;
			case CLASS:
				// TODO check generic bound
				String owner = sType.owner();
				if (owner.equals("java/lang/String")) {
					kind = 1;
					break;
				}

				IClass clazz = ctx.classes.getClassInfo(owner);
				if (clazz == null) throw wr.err("symbol.error.noSuchClass:"+owner);

				if (ctx.instanceOf(owner, "java/lang/Enum")) {
					kind = 2;
					break;
				} else if (ctx.classes.hasAnnotation(clazz, "roj/compiler/api/Switchable")) {
					kind = 3;
					break;
				}

				kind = -1; // 还有可能是type switch
				// throw wr.err("switch.incorrect_type:"+(char) sType.type);
		}

		byte prev = sectionFlag;
		sectionFlag |= 4;

		List<Object> labelsCur = Helpers.cast(CompileContext.get().annotationTmp); labelsCur.clear();
		MyHashSet<Object> labels = Helpers.cast(CompileContext.get().toResolve_unc); labels.clear();

		byte state;
		o:
		while (wr.hasNext()) {
			Word w = wr.next();
			switch (w.type()) {
				case CASE:
					labelsCur.clear();
					moreCase:
					while (true) {
						expr = (ExprNode) ep.parse(file, ExprParser.STOP_COLON|ExprParser.STOP_COMMA|ExprParser.STOP_LAMBDA|ExprParser.CHECK_VARIABLE_DECLARE);
						if (expr == null) {
							ctx.report(Kind.ERROR, "block.error.noExpression");
							sectionFlag = prev;
							return;
						}
						// todo check twice
						if (!(expr = expr.resolve(null)).isConstant()) ctx.report(Kind.ERROR, "case.not_constant");

						Object cv = expr.constVal();
						if (!labels.add(cv)) ctx.report(Kind.ERROR, "case.duplicate:" + cv);
						labelsCur.add(cv);

						w = wr.current();
						switch (w.type()) {
							case colon: break moreCase;
							case comma: continue;
							default: throw wr.err("unexpected:" + w.type() + ":':|,'");
						}
					}

					if (cst != null && !cst.equals(null)) {
						// todo skip block
					} else {
						Label here = cw.label();
						for (int i = 0; i < labelsCur.size(); i++) {
							node.branch((int) labelsCur.get(i), here);
						}
						switchBlock(breakTo);
					}

					break;
				case DEFAULT:
					except(colon);
					if (node.def != null) {
						ctx.report(Kind.ERROR, "duplicate:default");
						continue;
					}

					node.def = cw.label();
					switchBlock(breakTo);
					break;
				case rBrace:
					break o;
				default:
					String v = "unexpected:" + w.val();
					ctx.report(Kind.ERROR, v);
					sectionFlag = prev;
					return;
			}

			sectionFlag = prev;

			if ((node.targets.size() + (node.def == null ? 0 : 1)) < 2) {
				ctx.report(Kind.WARNING, "switch.too_less_case");
				if (node.def != null) {
					Label next = node.def;
					System.out.println("switch.default.next= " + next);
					System.out.println("todo: switch less than 2 branches to if");
				}
			} else if (cst != null) {
				System.out.println("todo: switch constant deduplicate");
			}

			if (node.def == null) node.def = breakTo;
			cw.label(breakTo);

			except(rBrace);
		}
	}

	@SuppressWarnings("fallthrough")
	private void switchBlock(Label endPoint) throws ParseException {
		Label prevBrk = curBreak;
		loopBody(null, endPoint);
		o:
		while (wr.hasNext()) {
			Word w = wr.next();
			switch (w.type()) {
				case CASE:
				case DEFAULT:
				case rBrace:
					wr.retractWord();
				case Word.EOF:
					break o;
			}
			if (w.type() == lBrace) {
				block();
			} else {
				sectionFlag &= ~3;
				statement(w);
				if ((sectionFlag & 2) != 0) {
					break;
				}
			}
		}
	}

	// endregion
	// region 表达式和变量

	private void define(IType type) throws ParseException {
		define(type, semicolon);
	}
	/**
	 * 定义变量 var/const/?
	 * 没有支持注解的计划。
	 */
	private void define(IType type, short stop) throws ParseException {
		int modifier = 0;
		if (type == null) {
			modifier = file._modifier(wr, FINAL);
			// todo generic supporting
			type = file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC);
		}
		// TODO : var let const
		if (type.genericType() == 0 && "var".equals(type.owner())) {
			// dynamic type
			type = Asterisk.anyType;
		} else {
			ctx.resolveType(type);
		}

		Word w;
		do {
			String name = wr.except(Word.LITERAL).val();
			Variable variable = newVar(name, type);

			w = wr.next();
			if (w.type() == assign) {
				if (modifier != 0) variable.isFinal = true;

				UnresolvedExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON|ExprParser.STOP_COMMA);
				if (expr == null) {
					ctx.report(Kind.ERROR, "not_statement");
					return;
				}

				writeCast(expr.resolve(ctx), type);
				cw.store(variable);

				w = wr.next();
			} else if (modifier != 0) {
				// TODO 支持延后赋值
				ctx.report(Kind.ERROR, "block.var.error.final");
			}
		} while (w.type() == comma);

		if (w.type() != stop) wr.unexpected(w.val(), byId(stop));
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