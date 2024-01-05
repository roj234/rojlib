package roj.mildwind.parser;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.Segment;
import roj.asm.visitor.SwitchSegment;
import roj.collect.Hasher;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.ParseException;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.mildwind.JsContext;
import roj.mildwind.api.ErrorHandler;
import roj.mildwind.asm.AsmClosure;
import roj.mildwind.asm.JsFunctionUncompiled;
import roj.mildwind.asm.JsMethodWriter;
import roj.mildwind.parser.ast.Binary;
import roj.mildwind.parser.ast.ExprParser;
import roj.mildwind.parser.ast.Expression;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;
import roj.mildwind.util.LabelInfo;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.GOTO;
import static roj.asm.Opcodes.*;
import static roj.mildwind.parser.JSLexer.*;
import static roj.mildwind.parser.ast.ExprParser.*;

/**
 * KScript语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class KParser implements ParseContext {
	JSLexer wr;
	String file;
	JsMethodWriter mw;
	AsmClosure ctx;

	private final int depth;

	private ErrorHandler handler;

	public void setErrorHandler(ErrorHandler handler) {
		if (handler == null) throw new NullPointerException("handler");
		this.handler = handler;
	}

	public KParser(AsmClosure ctx, ErrorHandler handler) {
		this.ctx = ctx;
		this.depth = -1;
		this.handler = handler;
		this.wr = new JSLexer();
	}

	public KParser(AsmClosure ctx) {
		this(ctx, (type, file, e) -> {
			System.err.print(type + ": " + file + "   ");
			e.printStackTrace();
		});
	}

	@Internal
	public KParser(int depth) {
		this.depth = depth;
	}

	@Internal
	public KParser reset(KParser parent) {
		if (parent != null) {
			this.file = parent.file;
			this.ctx = parent.ctx.child(this.parameters);
			this.wr = parent.wr;

			this.handler = parent.handler;
		}

		this.success = true;

		this.parameters.clear();
		this.sectionFlag = 0;

		this.namedLabels.clear();
		this.labelTmp = null;
		this.labelPath = 0;
		this.labels.clear();

		this.returnHook = null;

		return this;
	}

	/// region 解析

	public JsFunction parse(File file) throws IOException, ParseException {
		wr.init(IOUtil.readUTF(file));
		this.file = file.getName();
		return parse0();
	}

	public JsFunction parse(String file, CharSequence text) throws ParseException {
		wr.init(text);
		this.file = file;
		return parse0();
	}

	private JsFunction parse0() throws ParseException {
		reset(null);

		mw = JsMethodWriter.builder(file);
		mw.variables = ctx;
		ctx.setAsm(mw);
		wr.setLineHandler(mw);

		try {
			while (true) {
				Word w = wr.next();
				if (w.type() == Word.EOF) {
					break;
				} else {
					statement(w);
				}
			}
		} catch (NotStatementException e) {
			fireError(wr.err("statement.not"));
		}

		_checkUnused();

		if (success) {
			return checkAndBuild();
		} else {
			throw wr.err("compile_error_occurs");
		}
	}

	private JsFunction checkAndBuild() { return new JsFunctionUncompiled(mw, mw.currentMethodId, depth); }

	@Override
	public JsFunction inlineFunction() throws ParseException {
		JsFunction fn = JsContext.retainScriptParser(depth+1, this).parseFunction(mw);
		wr.setLineHandler(mw);
		return fn;
	}

	@Override
	public JsFunction parseLambda() throws ParseException {
		JsFunction fn = JsContext.retainScriptParser(depth+1, this).parseLambda(mw);
		wr.setLineHandler(mw);
		return fn;
	}

	//endregion
	//region 函数

	private JsFunction parseLambda(JsMethodWriter parent) throws ParseException {
		mw = JsMethodWriter.builder(parent);
		mw.variables = ctx;
		ctx.setAsm(mw);
		wr.setLineHandler(mw);

		parameters();
		except(lambda);

		Word w = wr.next();
		if (w.type() == left_l_bracket) {
			body();
			except(right_l_bracket);
		} else {
			wr.retractWord();

			Expression expr = JsContext.retainExprParser(depth+1).parse(this, STOP_RSB|STOP_COMMA);
			if (expr == null) {
				fireError(w, "empty.lambda");
				return null;
			}

			expr.write(mw, false);
			mw.one(ARETURN);
		}

		_checkUnused();

		return success ? checkAndBuild() : null;
	}

	private JsFunction parseFunction(JsMethodWriter parent) throws ParseException {
		mw = JsMethodWriter.builder(parent);
		mw.variables = ctx;
		ctx.setAsm(mw);
		wr.setLineHandler(mw);

		Word w = wr.next();
		switch (w.type()) {
			case Word.LITERAL: mw.funcName(w.val()); except(left_s_bracket); break; // named
			case left_s_bracket: break; // anonymous
			default: fireError(w, "unexpected_2:"+w.val()+":nob"); wr.retractWord(); return null;
		}

		parameters();

		except(left_l_bracket);
		body();
		except(right_l_bracket);

		_checkUnused();

		return success ? checkAndBuild() : null;
	}

	private void _checkUnused() throws ParseException {
		wr.retractWord();
		Word wd = wr.next();

		List<AsmClosure.Variable> unused = ctx.finish();
		for (AsmClosure.Variable v : unused) fireWarn(wd, "unused.var:" + v.name);

		mw.end();
	}

	/**
	 * 当前方法的参数
	 */
	ToIntMap<String> parameters = new ToIntMap<>();

	/**
	 * Unreachable statement检测 <BR>
	 * bit1 <BR>
	 * bit2 <BR>
	 * bit3 <BR>
	 */
	byte sectionFlag;

	/// 参数
	private void parameters() throws ParseException {
		parameters.clear();

		int i = 0;
		int flag = 0;

		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case right_s_bracket: return;
				case equ:
					if ((flag & 4) != 0) fireError(w, "rest.unable_default");
					if ((flag & 8) != 0) fireError(w, "duplicate:=");
					flag |= 8;
					Expression def = JsContext.retainExprParser(0).parse(this, STOP_COMMA);
					if (def == null) {
						fireError(w = wr.readWord(), "unexpected_2:"+w.val()+":type.expr");
						wr.retractWord();
					} else if (!def.isConstant()) {
						fireWarn(wr.readWord(), "default_prefers_constant");
						wr.retractWord();
					} else {
						ctx.setDefault(i-1, def.constVal());
					}
					break;
				case rest:
					if ((flag & 2) != 0) fireError(w, "duplicate:...");
					flag |= 2;
					break;
				case Word.LITERAL:
					if ((flag & 1) != 0) fireError(w, "missing:,");
					else if ((flag & 4) != 0) fireError(w, "rest.last_formal_parameter");
					else if ((flag & 2) != 0) {
						flag |= 4;
						ctx.setRestId(i);
					}
					parameters.putInt(w.val(), i++);
					flag |= 1;
					break;
				case comma:
					if ((flag & 1) == 0) fireError(w, "unexpected_2:,:type.literal");

					flag &= ~9;
					break;
				case colon: // TypeScript parameter definition
					JsContext.retainExprParser(0).parse(this, STOP_COMMA|STOP_RSB);
					break;
				default: throw wr.err("unexpected:"+w.val());
			}
		}
	}

	/**
	 * 函数体
	 */
	@SuppressWarnings("fallthrough")
	private void body() throws ParseException {
		sectionFlag |= 1;

		ctx.enterBlock();

		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case Word.EOF:
				case right_l_bracket: wr.retractWord(); break o;
				default: statement(w);
			}
		}

		ctx.exitBlock();
	}

	/**
	 * 语句
	 */
	@SuppressWarnings("fallthrough")
	private void statement(Word w) {
		if (labelTmp != null && labelPath-- == 0) {
			labelTmp = null;
		}

		try {
			switch (w.type()) {
				case CASE:
				case DEFAULT:
					if ((sectionFlag & 4) != 0) wr.retractWord();
					else fireError(w, "statement.not");
				return;
				case VAR: case CONST: case LET: define(w.type()); break;
				case CONTINUE: case BREAK: _break(w.type() == BREAK); break;
				case JSLexer.GOTO: _goto(); break;
				case SWITCH: _switch(); break;
				case JSLexer.RETURN: _return(); break;
				case FOR: _for(); break;
				case WHILE: _while(); break;
				case Word.LITERAL: literalExpr(w); break;
				case THROW: _throw(); break;
				case IF: _if(); break;
				case DO: _do(); break;
				case FUNCTION: _func(); break;
				case TRY: _try(); break;
				case semicolon: fireWarn(w, "statement.empty"); break;
				case left_l_bracket: body(); except(right_l_bracket); break;
				default:
					boolean flag = isSymbol(w);
					if (!flag) {
						switch (w.type()) {
							case JSLexer.NEW:
							case NULL:
							case UNDEFINED:
							case THIS:
							case ARGUMENTS:
							case DELETE:
								flag = true;
						}
					}

					if (flag) {
						wr.retractWord();
						Expression expr = JsContext.retainExprParser(0).parse(this, STOP_SEMICOLON);
						if (expr != null) {
							expr.write(mw, true);
							except(semicolon);
						}
					} else {
						fireError(w, "statement.not");
					}
				break;
			}
		} catch (ParseException e) {
			fireError(e);
		}
	}

	/**
	 * 返回
	 */
	private void _return() throws ParseException {
		if (depth == -1) {
			fireError(wr.next(), "return.on_top");
			wr.retractWord();
		}

		Expression expr = JsContext.retainExprParser(0).parse(this, STOP_SEMICOLON);
		if (expr == null) mw.field(GETSTATIC, "roj/mildwind/type/JsNull", "UNDEFINED", "Lroj/mildwind/type/JsObject;");
		else expr.write(mw, false);

		except(semicolon);

		if (returnHook != null) mw.jump(GOTO, returnHook);
		else mw.one(ARETURN);

		_chkBlockEnd();
	}

	/**
	 * 内部函数
	 */
	private void _func() throws ParseException {
		String name;
		Word w = wr.next();
		if (w.type() != Word.LITERAL) {
			// no anonymous not in expr
			fireError(w, "unexpected:"+w.val()+":type.literal");
			wr.retractWord();
			return;
		} else {
			name = w.val();
		}
		wr.retractWord();

		JsFunction fn = JsContext.retainScriptParser(depth+1, this).parseFunction(mw);

		wr.setLineHandler(mw);

		if (fn == null) {
			success = false;
			// 无效的函数
			return;
		}

		int fid = mw.sync(fn);

		// var a = function() {};
		if (ctx.has(name)) {
			mw.one(ALOAD_0);
			mw.field(GETFIELD, mw.data, fid);

			ctx._var(name);
			ctx.set(name);
		} else { // function a() {}
			ctx._var(name);
			// 整个地方都可以用
			ctx.set_on_enter(name, fid);
		}
	}

	// endregion
	// region 条件

	private final SimpleList<Map<String, LabelInfo>> namedLabelChunk = new SimpleList<>();
	private final MyHashMap<String, LabelInfo> namedLabels = new MyHashMap<>();
	private final SimpleList<LabelInfo> labels = new SimpleList<>();

	// 支持在for while do switch上放的label
	private LabelInfo labelTmp;
	private byte labelPath = 0;

	private void _namedLabel(String val) {
		namedLabels.put(val, labelTmp = new LabelInfo(mw.label()));
		labelPath = 1;
	}

	// todo return之前的hook
	private Label returnHook;
	private List<Label> jumpHooks;

	private void _try() throws ParseException {
		TryCatchEntry entry = mw.exception();

		Label prevRh = returnHook;
		returnHook = new Label();

		entry.start = mw.label();
		except(left_l_bracket);
		body();
		except(right_l_bracket);
		entry.end = mw.label();

		if ((sectionFlag & 1) != 0) {
			mw.one(ICONST_1);
			mw.one(ACONST_NULL);
			mw.jump(GOTO, returnHook);
		}

		int flag = 0;
		Word w = wr.next();
		if (w.type() == CATCH) {
			w = wr.next();

			String varId = null;
			switch (w.type()) {
				case left_s_bracket: // (
					w = wr.next();
					if (w.type() != Word.LITERAL) throw wr.err("unexpected:" + w);
					varId = w.val();

					except(right_s_bracket);
					except(left_l_bracket);
					break;
				case left_l_bracket: break; // {
				default:
					fireError(w, "unexpected:" + w.val() + ':' + byId(left_s_bracket));
					wr.retractWord();
					return;
			}

			entry.handler = mw.label();

			if (varId != null) {
				mw.invoke(INVOKESTATIC, "roj/mildwind/type/JsError", "wrap", "(Ljava/lang/Throwable;)Lroj/mildwind/type/JsError;");
				ctx._let(varId);
				ctx.set(varId);
			} else {
				mw.one(POP);
			}

			body();
			except(right_l_bracket);

			mw.one(ICONST_1);
			mw.one(ACONST_NULL);
			mw.jump(GOTO, returnHook);

			w = wr.next();

			flag |= 1;
		}

		mw.label(returnHook);
		returnHook = prevRh;

		if (w.type() == FINALLY) {
			int a = mw.getTmpVar();
			int b = mw.getTmpVar();
			mw.vars(ASTORE, b);
			mw.vars(ISTORE, a);

			except(left_l_bracket);
			body();
			except(right_l_bracket);

			Label rhLabel = new Label();
			// todo switch and goto hook
			mw.vars(ILOAD, a);
			mw.jump(IFNE, rhLabel);
			mw.vars(ALOAD, b);
			mw.one(ARETURN); // returnHook
			mw.label(rhLabel);
			// 1: continueNext
			flag |= 2;

			mw.delTmpVar(a);
			mw.delTmpVar(b);
		} else {
			mw.one(POP2);
			wr.retractWord();
		}

		if (flag == 0) {
			// 孤立的try
			fireError(w, "try.alone");
		}
	}

	/**
	 * goto <x>
	 */
	private void _goto() throws ParseException {
		Word w = wr.next();
		if (w.type() != Word.LITERAL) {
			errNoLabel(w);
			return;
		}
		LabelInfo info = namedLabels.get(w.val());
		if (info != null && info.head != null) {
			mw.jump(GOTO, info.head);
		} else {
			fireError(w, "goto.unknown:" + w.val());
		}
		except(semicolon);
	}

	/**
	 * break [x] and continue [x]
	 */
	private void _break(boolean isBreak) throws ParseException {
		Word w = wr.next();
		switch (w.type()) {
			case Word.LITERAL: {
				LabelInfo info = namedLabels.get(w.val());
				if (info != null) {
					__break(isBreak, w, info);

					except(semicolon);
				} else {
					fireError(w, "goto.unknown" + w.val());
				}
			}
			break;
			case semicolon:
				if (labels.isEmpty()) fireError(w, "goto.not_label");
				else __break(isBreak, w, null);
				break;
			default: errNoLabel(w); break;
		}
	}

	private void __break(boolean isBreak, Word w, LabelInfo info) throws ParseException {
		if (info == null) {
			for (int i = 0; i < labels.size(); i++) {
				info = labels.get(i);
				Label label = isBreak ? info.onBreak : info.onContinue;
				if (label != null) {
					mw.jump(GOTO, label);
					_chkBlockEnd();
					return;
				}
			}
		} else {
			Label label = isBreak ? info.onBreak : info.onContinue;
			if (label != null) {
				mw.jump(GOTO, label);
				_chkBlockEnd();
				return;
			}
		}
		errNoLabel(w);
		_chkBlockEnd();
	}

	private void errNoLabel(Word w) throws ParseException {
		fireError(w, "goto.illegal_label");
		except(semicolon);
	}

	private void _throw() throws ParseException {
		Word w = wr.next();
		wr.retractWord();

		Expression expr = JsContext.retainExprParser(0).parse(this, STOP_SEMICOLON);
		if (expr == null) {
			fireError(w, "statement.empty");
			return;
		}
		expr.write(mw, false);
		// todo wrap
		mw.one(ATHROW);

		except(semicolon);

		_chkBlockEnd();
	}

	private void _chkBlockEnd() throws ParseException {
		if ((sectionFlag & 1) != 0) {
			Word w = wr.next();
			if (w.type() != right_l_bracket) {
				fireError(w, "statement.unreachable");
				wr.retractWord();
				return;
			}

			wr.retractWord();
		}
		sectionFlag |= 2;
	}

	/**
	 * if 条件判断语句
	 */
	private void _if() throws ParseException {
		Label ifFalse = condition(right_s_bracket);
		if (ifFalse == null) return;

		block();

		_else(ifFalse);
	}

	/**
	 * 语句块 or 单行语句
	 */
	private void block() throws ParseException {
		Word word = wr.next();
		if (word.type() == left_l_bracket) {
			body();
			except(right_l_bracket);
		} else {
			sectionFlag &= ~1;
			statement(word);
		}
	}

	/**
	 * 条件判断
	 *
	 * @return false 跳转点
	 */
	@Nullable
	private Label condition(short end) throws ParseException {
		if (end == right_s_bracket) except(left_s_bracket);

		ExprParser parser = JsContext.retainExprParser(0);

		Label ifFalse = new Label();

		Expression expr = parser.parse(this, (end == right_s_bracket ? STOP_RSB : STOP_SEMICOLON), ifFalse);
		if (expr == null) {
			fireError(wr.readWord(), "statement.empty.if");
			return null;
		}

		expr.write(mw, false);
		if (!(expr instanceof Binary)) {
			mw.invokeV("roj/mildwind/type/JsObject", "asBool", "()I");
			mw.jump(IFEQ, ifFalse);
		}

		except(end);

		return ifFalse;
	}


	/**
	 * else 语句
	 * @param ifFalse false跳转点
	 */
	private void _else(Label ifFalse) throws ParseException {
		Word w = wr.next();
		if (w.type() != ELSE) {
			wr.retractWord();
			// if false goto : false
			//   ifTrue
			// : false
			mw.label(ifFalse);
			return;
		}

		Label end = new Label();

		w = wr.next();
		mw.jump(GOTO, end);
		mw.label(ifFalse);
		if (w.type() == left_l_bracket) {
			body();
			except(right_l_bracket);
		} else {
			statement(w);
		}
		mw.label(end);

		// if (xx) {} else if() {}
		//      is equivalent to
		// if (xx) {} else { if() {} }
	}

	/**
	 * for循环 <BR>
	 */
	private void _for() throws ParseException {
		Label continueTo = mw.label();

		except(left_s_bracket);

		Word w = wr.next();
		boolean createdVar;
		switch (w.type()) {
			case semicolon: createdVar = false; break;
			case VAR: case LET: case CONST:
				ctx.enterBlock();
				createdVar = true;
				define(w.type());
				break;
			default: fireError(w, "unexpected:"+w.val()+":var or ;"); return;
		}

		Label breakTo = condition(semicolon);
		if (breakTo == null) {
			fireError(w, "not_condition");
			return;
		}

		Expression expr = JsContext.retainExprParser(0).parse(this, STOP_RSB);
		except(right_s_bracket);

		loopEnter(continueTo, breakTo);
		block();
		loopExit();

		if (expr != null) {
			Label ol = mw.label();
			ol.set(continueTo);

			continueTo.clear();
			mw.label(continueTo);
			expr.write(mw, true);
			mw.jump(GOTO, ol);
		} else {
			mw.jump(GOTO, continueTo);
		}

		mw.label(breakTo);

		if (createdVar) ctx.exitBlock();
	}

	private void loopExit() {
		labels.remove(labels.size()-1);
		namedLabelChunk.remove(namedLabelChunk.size()-1);
	}

	private void loopEnter(Label continueTo, Label breakTo) {
		LabelInfo info;
		if (labelTmp != null) {
			info = labelTmp;
			labelTmp.onBreak = breakTo;
			labelTmp.onContinue = continueTo;
			labelTmp = null;
			labelPath = 0;
		} else {
			info = new LabelInfo(null, breakTo, continueTo);
		}

		labels.add(info);
		namedLabelChunk.add(new MyHashMap<>());
	}

	/**
	 * do-while循环
	 */
	private void _do() throws ParseException {
		Label continueTo = mw.label();

		Label breakTo = new Label();

		loopEnter(continueTo, breakTo);
		block(); // do {}
		loopExit();

		except(WHILE, "while");
		Label breakTo1 = condition(right_s_bracket);
		if (breakTo1 == null) return;

		mw.jump(GOTO, continueTo);
		mw.label(breakTo);
		mw.label(breakTo1);

		except(semicolon);
	}

	/**
	 * while循环
	 */
	private void _while() throws ParseException {
		Label continueTo = mw.label();

		Label breakTo = condition(right_s_bracket);
		if (breakTo == null) return;

		loopEnter(continueTo, breakTo);
		block();
		loopExit();

		mw.jump(GOTO, continueTo);
		mw.label(breakTo);
	}

	/**
	 * switch
	 */
	private void _switch() throws ParseException {
		except(left_s_bracket);

		Expression expr = JsContext.retainExprParser(0).parse(this, STOP_RSB|SKIP_RSB);
		if (expr == null) throw wr.err("statement.empty.switch");
		expr.write(mw, false);
		JsObject cv = expr.isConstant() ? expr.constVal() : null;

		except(left_l_bracket);

		ToIntMap<JsObject> jumpInfo = new ToIntMap<>();
		jumpInfo.setHasher(new Hasher<JsObject>() {
			@Override
			public int hashCode(@Nullable JsObject object) { return object.hashCode(); }
			@Override
			public boolean equals(JsObject from_argument, Object stored_in) { return from_argument.op_feq((JsObject) stored_in); }
		});
		Label end = new Label();

		int id = mw.sync(jumpInfo);
		mw.load(id);
		mw.ldc(-1);
		mw.invokeV("roj/mildwind/util/SwitchMap", "getOrDefault", "(Ljava/lang/Object;I)I");

		id = 0;
		SwitchSegment sw = new SwitchSegment(true);
		mw.addSegment(sw);

		byte prev = sectionFlag;
		sectionFlag |= 4;

		Word w;
		loopEnter(null, end);

		MyHashMap<Expression, Label> defaultExpr = new MyHashMap<>();
		Segment defSegment = new Segment() {
			@Override
			protected boolean put(CodeWriter to) {
				return false;
			}

			@Override
			protected int length() {
				return 0;
			}
		};

		try {
			while (true) {
				w = wr.next();
				switch (w.type()) {
					case CASE:
						expr = JsContext.retainExprParser(0).parse(this, STOP_COLON);
						if (expr == null) {
							fireError(w, "case.empty");
							return;
						}
						except(colon);

						if (!(expr = expr.compress()).isConstant()) {
							fireWarn(w, "case.not_constant");
							defaultExpr.put(expr, mw.label());
						} else {
							sw.branch(id, mw.label());
							if (jumpInfo.putInt(expr.constVal(), id) != null) {
								fireError(w, "case.duplicate:" + expr.constVal());
							}
							id++;
						}

						switchBlock();
					continue;
					case DEFAULT:
						except(colon);
						if (sw.def != null) {
							fireError(w, "duplicate:default");
							continue;
						}

						sw.def = mw.label();
						mw.addSegment(defSegment);
						switchBlock();
					continue;
				}
				break;
			}
			if (w.type() != right_l_bracket) fireError(w, "unexpected:"+w.val()+":}");
		} finally {
			sectionFlag = prev;
			loopExit();
		}

		// TODO implement defaultExpr
		if (jumpInfo.isEmpty() && defaultExpr.isEmpty()) {
			fireWarn(w, "switch.empty");
			if (sw.def == null) {
				mw.one(POP);
			} // else: directly to default
		} else if (cv != null) {
			fireWarn(w, "switch.constant");

			int label = jumpInfo.getInt(cv);
			// drop into case
			if (label >= 0) sw.def = sw.targets.get(label).pos;

			sw.targets.clear();
		}

		if (sw.def == null) sw.def = end;

		mw.label(end);
	}

	@SuppressWarnings("fallthrough")
	private void switchBlock() throws ParseException {
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case CASE: case DEFAULT: case right_l_bracket: wr.retractWord();
				case Word.EOF: return;
			}

			if (w.type() == left_l_bracket) {
				body();
				except(right_l_bracket);
			} else {
				sectionFlag &= ~3;
				statement(w);
				if ((sectionFlag & 2) != 0) break;
			}
		}
	}

	// endregion
	// region 表达式和变量

	/**
	 * 定义变量 var/const/let
	 */
	@SuppressWarnings("fallthrough")
	private void define(int type) throws ParseException {
		while (wr.hasNext()) {
			Word w = wr.next();
			if (w.type() != Word.LITERAL) throw wr.err("unexpected_2:"+w.val()+":type.literal");

			String name = w.val();

			if (ctx.has(name) || parameters.containsKey(name)) fireWarn(w, "var.exist");
			switch (type) {
				case LET: ctx._let(name); break;
				case CONST: ctx._const(name); break;
				case VAR: default: ctx._var(name); break;
			}

			w = wr.next();
			if (w.type() == assign) {
				Expression expr = JsContext.retainExprParser(0).parse(this, STOP_SEMICOLON|STOP_COMMA);
				if (expr == null) {
					fireError(w, "statement.not");
					return;
				}

				if (expr.isConstant()) {
					ctx.set(name, expr.constVal());
				} else {
					expr.write(mw, false);
					ctx.set(name);
				}

				 w = wr.next();
			} else {
				if (type == CONST) {
					fireError(w, "var.initialize_const");
				}
			}

			if (w.type() == semicolon) break;
			if (w.type() != comma) throw wr.err("unexpected_2:"+w.val()+":, or ;");
		}
	}

	/**
	 * 标准单个表达式
	 */
	private void literalExpr(Word k) throws ParseException {
		// check label
		if (k.type() == Word.LITERAL) {
			String val = k.val();
			if (wr.readWord().type() == colon) {
				_namedLabel(val);
				return;
			}
		}
		wr.retractWord();

		Expression expr = JsContext.retainExprParser(0).parse(this, STOP_SEMICOLON);
		if (expr == null) fireError(k, "statement.not");
		else expr.write(mw, true);

		except(semicolon);
	}

	@Override
	public JSLexer lex() { return wr; }

	@Nullable
	@Override
	@Deprecated
	public JsObject maybeConstant(String name) { return ctx.getIfConstant(name); }

	//endregion
	// region 输出错误

	private boolean success = true;     // 语法解析结果

	private void fireError(ParseException e) {
		success = false;
		handler.handle("ERR", file, e);
	}

	/**
	 * Notice: 语法级错误(return ,,,;)需要return, 定义级的(const v;)没必要 <BR>
	 * 不过随便了, 反正是[错误]
	 */
	private void fireError(Word word, String v) {
		success = false;
		handler.handle("ERR", file, wr.err(v, word));
	}

	private void fireWarn(Word word, String v) {
		handler.handle("WARN", file, wr.err(v, word));
	}

	private void except(short id) throws ParseException {
		except(id, byId(id));
	}

	private void except(short id, String s) throws ParseException {
		Word w = wr.next();
		if (w.type() != id) {
			fireError(w, "unexpected:" + w.val() + ':' + s);
			wr.retractWord();
		}
	}

	//endregion
}