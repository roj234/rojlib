package roj.kscript.parser;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.ParseException;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.kscript.api.ErrorHandler;
import roj.kscript.asm.*;
import roj.kscript.func.KFunction;
import roj.kscript.parser.ast.Binary;
import roj.kscript.parser.ast.ExprParser;
import roj.kscript.parser.ast.Expression;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.ContextPrimer;
import roj.kscript.util.LabelInfo;
import roj.kscript.util.SwitchMap;
import roj.kscript.util.Variable;
import roj.kscript.vm.KScriptVM;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KScript语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class KParser implements ParseContext {
	/**
	 * todo 常量（自定义来没有修改过的变量）传播， 删除未使用的(var)变量
	 */

	/**
	 * 分词器
	 */
	JSLexer wr;
	/**
	 * 语法树
	 */
	KS_ASM tree;
	/**
	 * 文件名
	 */
	String file;

	/**
	 * 作用域
	 */
	ContextPrimer ctx;

	/**
	 * 缓存索引
	 */
	private final int depth;

	/**
	 * 错误处理器
	 */
	private ErrorHandler handler;

	/**
	 * 严格模式
	 */
	boolean strict;

	public void setErrorHandler(ErrorHandler handler) {
		if (handler == null) throw new NullPointerException("handler");
		this.handler = handler;
	}

	public KParser(ContextPrimer ctx, ErrorHandler handler) {
		this.ctx = ctx.makeChild();
		this.depth = -1;
		this.handler = handler;
		this.wr = new JSLexer();
	}

	public KParser(ContextPrimer ctx) {
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
			this.ctx = parent.ctx.makeChild();
			ctx.setParameter(this.parameters);
			this.wr = parent.wr;

			this.handler = parent.handler;
			this.strict = parent.strict;
		}

		this.success = true;

		this.parameters.clear();
		this.sectionFlag = 0;

		this.namedLabels.clear();
		this.labelTmp = null;
		this.labelPath = 0;
		this.labels.clear();

		this.tryLevel = 0;
		this.maxTryLevel = 0;
		this.catchTryLevel = 0;
		this.maxCatchTryLevel = 0;

		return this;
	}

	/// region 解析

	public KFunction parse(File file) throws IOException, ParseException {
		wr.init(IOUtil.readUTF(file));
		this.file = file.getName();
		return parse0();
	}

	public KFunction parse(String file, CharSequence text) throws ParseException {
		wr.init(text);
		this.file = file;
		return parse0();
	}

	private KFunction parse0() throws ParseException {
		reset(null);

		tree = KS_ASM.builder(file);
		wr.setLineHandler(tree);

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
			_onError(wr.err("statement.not"));
		}

		_checkUnused();

		if (success) {
			return checkAndBuild();
		} else {
			throw wr.err("compile_error_occurs");
		}
	}

	private KFunction checkAndBuild() {
		if (maxTryLevel > 65535 || maxCatchTryLevel > 65535) throw new IllegalArgumentException("What fucking code you write???");
		return tree.build(ctx, (maxTryLevel << 16) | maxCatchTryLevel);
	}

	@Override
	public KFunction parseInnerFunc(short type) throws ParseException {
		KFunction fn = KScriptVM.retainScriptParser(depth + 1, this).parseInner(tree);
		wr.setLineHandler(tree);

		return fn;
	}

	private KFunction parseInner(KS_ASM parent) throws ParseException {
		tree = KS_ASM.builder(parent);
		wr.setLineHandler(tree);

		functionParser();

		return success ? checkAndBuild() : null;
	}

	//endregion
	//region 函数

	private void functionParser() throws ParseException {
		String name;
		Word w = wr.next();
		switch (w.type()) {
			case Word.LITERAL:  // named
				name = w.val();
				break;
			case JSLexer.left_s_bracket: // anonymous
				name = null;
				break;
			default:
				_onError(w, "unexpected:" + w.val() + ":nob");
				wr.retractWord();
				return;
		}

		tree.funcName(name);

		if (name != null) except(JSLexer.left_s_bracket);
		parameters();
		except(JSLexer.right_s_bracket);

		except(JSLexer.left_l_bracket);
		body();
		except(JSLexer.right_l_bracket);

		_checkUnused();
	}

	private void _checkUnused() throws ParseException {
		wr.retractWord();
		Word wd = wr.next();
		for (int i = 0; i < ctx.locals.size(); i++) {
			Variable v = ctx.locals.get(i);
			if (v.end == null) _onWarning(wd, "unused.let:" + v.name);
		}
		for (String s : ctx.unusedGlobal) {
			_onWarning(wd, "unused.var:" + s);
		}
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

		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case JSLexer.right_s_bracket:
					wr.retractWord();
					break o;
				case JSLexer.equ:
					if ((flag & 4) != 0) _onError(w, "rest.unable_default");
					if ((flag & 8) != 0) _onError(w, "duplicate:=");
					flag |= 8;
					Expression def = KScriptVM.retainExprParser(0).read(this, (short) 16);
					if (def == null) {
						_onError(w = wr.readWord(), "unexpected:" + w.val() + ":type.expr");
						wr.retractWord();
					} else if (!def.isConstant()) {
						_onWarning(wr.readWord(), "default_prefers_constant");
						wr.retractWord();
					}
					ctx.setDefault(i - 1, def);
					break;
				case JSLexer.rest:
					if ((flag & 2) != 0) _onError(w, "duplicate:...");
					flag |= 2;
					break;
				case Word.LITERAL:
					if ((flag & 1) != 0) {_onError(w, "missing:,");} else if ((flag & 4) != 0) {_onError(w, "rest.last_formal_parameter");} else if ((flag & 2) != 0) {
						flag |= 4;
						ctx.setRestId(i);
					}
					parameters.putInt(w.val(), i++);
					flag |= 1;
					break;
				case JSLexer.comma:
					if ((flag & 1) == 0) {
						_onError(w, "unexpected:,:type.literal");
					}
					flag &= ~9;
					break;
			}
		}
	}

	/**
	 * 函数体
	 */
	@SuppressWarnings("fallthrough")
	private void body() throws ParseException {
		sectionFlag |= 1;

		ctx.enterRegion();

		o:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case Word.EOF:
				case JSLexer.right_l_bracket:
					wr.retractWord();
					break o;
				default:
					statement(w);
			}
		}

		ctx.endRegion();
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
				case JSLexer.CASE:
				case JSLexer.DEFAULT:
					if ((sectionFlag & 4) != 0) {
						wr.retractWord();
					} else {
						_onError(w, "statement.not");
					}
					return;
				case JSLexer.VAR:
				case JSLexer.CONST:
				case JSLexer.LET:
					define(w.type());
					break;
				case JSLexer.CONTINUE:
				case JSLexer.BREAK:
					_break(w.type() == JSLexer.BREAK);
					break;
				case JSLexer.GOTO:
					_goto();
					break;
				case JSLexer.SWITCH:
					_switch();
					break;
				case JSLexer.RETURN:
					_return();
					break;
				case JSLexer.FOR:
					_for();
					break;
				case JSLexer.WHILE:
					_while();
					break;
				case Word.LITERAL:
					wr.retractWord();
					assign();
					break;
				case JSLexer.THROW:
					_throw();
					break;
				case JSLexer.IF:
					_if();
					break;
				case JSLexer.DO:
					_do();
					break;
				case JSLexer.FUNCTION:
					_func();
					break;
				case JSLexer.TRY:
					_try();
					break;
				case JSLexer.semicolon:
					_onWarning(w, "statement.empty");
					break;
				case JSLexer.left_l_bracket:
					body();
					except(JSLexer.right_l_bracket);
					break;
				default:
					boolean flag = JSLexer.isSymbol(w);
					if (!flag) {
						switch (w.type()) {
							case JSLexer.NEW:
							case JSLexer.NULL:
							case JSLexer.UNDEFINED:
							case JSLexer.THIS:
							case JSLexer.ARGUMENTS:
							case JSLexer.DELETE:
								flag = true;
						}
					}

					if (flag) {
						wr.retractWord();
						Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
						if (expr != null) {
							expr.write(tree, true);
							except(JSLexer.semicolon);
						}
					} else {
						_onError(w, "statement.not");
					}
					break;
			}
		} catch (ParseException e) {
			_onError(e);
		}
	}

	/**
	 * 返回
	 */
	private void _return() throws ParseException {
		Word w = wr.next();
		if (depth == -1) {
			_onError(w, "return.on_top");
		}

		if (w.type() == JSLexer.semicolon) {
			tree.Std(Opcode.RETURN_EMPTY);
		} else {
			wr.retractWord();
			Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
			if (expr == null) {
				_onError(w, "return.unexpected");
				return;
			}

			expr.write(tree, false);
			tree.Std(Opcode.RETURN);

			except(JSLexer.semicolon);
		}

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
			_onError(w, "unexpected:" + w.val() + ":type.literal");
			wr.retractWord();
			return;
		} else {
			name = w.val();
		}
		wr.retractWord();

		KFunction fn = KScriptVM.retainScriptParser(depth + 1, this).parseInner(tree);

		wr.setLineHandler(tree);

		if (fn == null) {
			success = false;
			// 无效的函数
			return;
		}

		// 定义函数, 整个地方都可以用
		if (ctx.selfExists(name)) {
			tree.Load(fn).Set(name);
		} else {
			ctx.global(name, fn);
			ctx.unusedGlobal.remove(name);
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
		namedLabels.put(val, labelTmp = new LabelInfo(tree.Label()));
		labelPath = 1;
	}

	private int tryLevel, maxTryLevel, catchTryLevel, maxCatchTryLevel;

	private void _try() throws ParseException {
		except(JSLexer.left_l_bracket);

		LabelNode catchTo = new LabelNode(), finallyTo = new LabelNode(), end = new LabelNode();

		TryEnterNode try_id = tree.TryEnter(catchTo, finallyTo, end);

		maxTryLevel = Math.max(maxTryLevel, ++tryLevel);

		body();

		tryLevel--;

		except(JSLexer.right_l_bracket);

		TryNormalNode normal = new TryNormalNode();
		tree.node0(normal);

		byte flag = 0;

		Word w;
		o:
		while (true) {
			w = wr.next();
			switch (w.type()) {
				case JSLexer.FINALLY:
					if ((flag & 1) != 0) throw wr.err("duplicate:finally");
					tree.node0(finallyTo);
					except(JSLexer.left_l_bracket);
					body();
					except(JSLexer.right_l_bracket);

					// END ?
					tree.TryRegionEnd(end);

					flag |= 1;
					break;
				case JSLexer.CATCH:
					if ((flag & 2) != 0) throw wr.err("duplicate:catch");

					w = wr.next();
					boolean hasVar = false;
					switch (w.type()) {
						case JSLexer.left_s_bracket: // (
							w = wr.next();
							if (w.type() != Word.LITERAL) throw wr.err("unexpected:" + w);
							except(JSLexer.right_s_bracket);
							except(JSLexer.left_l_bracket);

							hasVar = true;
							break;
						case JSLexer.left_l_bracket: // {
							break;
						default:
							_onError(w, "unexpected:" + w.val() + ':' + JSLexer.byId(JSLexer.left_s_bracket));
							wr.retractWord();
							return;
					}

					tree.node0(catchTo);

					if (hasVar) {
						ctx.local(w.val(), null, catchTo); // 定义 catch(e)
						// end try | e = pop();

						tree.Set(w.val());
					} else {
						tree.Std(Opcode.POP);
					}

					maxCatchTryLevel = Math.max(maxCatchTryLevel, ++catchTryLevel);

					body();
					except(JSLexer.right_l_bracket);

					catchTryLevel--;

					// END ?
					tree.TryRegionEnd(end);

					flag |= 2;
					break;
				default:
					wr.retractWord();
					break o;
			}
		}

		normal.setTarget((normal.gotoFinal = (flag & 1) != 0) ? finallyTo : end);

		if (flag == 0) {
			// 孤立的try
			_onError(w, "try.alone");
			return;
		}

		tree.node0(end);
	}

	/**
	 * goto <x>
	 */
	private void _goto() throws ParseException {
		Word w = wr.next();
		if (w.type() != Word.LITERAL) {
			_uLabel(w);
			return;
		}
		LabelInfo info = namedLabels.get(w.val());
		if (info != null && info.head != null) {
			tree.Goto(info.head);
		} else {
			_onError(w, "goto.unknown:" + w.val());
		}
		except(JSLexer.semicolon);
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

					except(JSLexer.semicolon);
				} else {
					_onError(w, "goto.unknown" + w.val());
				}
			}
			break;
			case JSLexer.semicolon:
				if (labels.isEmpty()) {
					_onError(w, "goto.not_label");
				} else {
					__break(isBreak, w, null);
				}
				break;
			default:
				_uLabel(w);
				break;
		}
	}

	private void __break(boolean isBreak, Word w, LabelInfo info) throws ParseException {
		if (info == null) {
			for (LabelInfo info1 : labels) {
				if (isBreak ? info1.onBreak != null : info1.onContinue != null) {
					tree.Goto(isBreak ? info1.onBreak : info1.onContinue);
					_chkBlockEnd();
					return;
				}
			}
		} else {
			if (isBreak ? info.onBreak != null : info.onContinue != null) {
				tree.Goto(isBreak ? info.onBreak : info.onContinue);
				_chkBlockEnd();
				return;
			}
		}
		_uLabel(w);
		_chkBlockEnd();
	}

	private void _uLabel(Word w) throws ParseException {
		_onError(w, "goto.illegal_label");
		except(JSLexer.semicolon);
	}

	private void _throw() throws ParseException {
		Word w = wr.next();
		wr.retractWord();

		Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
		if (expr == null/* || expr.type() != -1*/) {
			_onError(w, "statement.empty");
			return;
		}
		expr.write(tree, false);
		tree.Std(Opcode.THROW);

		except(JSLexer.semicolon);

		_chkBlockEnd();
	}

	private void _chkBlockEnd() throws ParseException {
		if ((sectionFlag & 1) != 0) {
			Word w = wr.next();
			if (w.type() != JSLexer.right_l_bracket) {
				_onError(w, "statement.unreachable");
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
		LabelNode ifFalse = condition(true, JSLexer.right_s_bracket);
		if (ifFalse == null) return;

		block();

		_else(ifFalse);
	}

	/**
	 * 语句块 or 单行语句
	 */
	private void block() throws ParseException {
		Word word = wr.next();
		if (word.type() == JSLexer.left_l_bracket) {
			body();
			except(JSLexer.right_l_bracket);
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
	private LabelNode condition(boolean checkBracket, short end) throws ParseException {
		if (checkBracket) except(JSLexer.left_s_bracket);

		ExprParser parser = KScriptVM.retainExprParser(0);

		LabelNode ifFalse = new LabelNode();

		Expression equ = parser.read(this, (short) (checkBracket ? 16 : 0), ifFalse);
		if (equ == null) {
			_onError(wr.readWord(), "statement.empty.if");
			return null;
		}

		equ.write(tree, false);

		if (!(equ instanceof Binary)) // 简单表达式 => IS_TRUE, 复杂的话有，嗯，Binary todo 测试
		{tree.If(ifFalse, IfNode.TRUE);}

		except(end);

		return ifFalse;
	}


	/**
	 * else 语句 <BR>
	 * 和数学书上写的一样, 它是可选的
	 *
	 * @param ifFalse false跳转点
	 */
	private void _else(LabelNode ifFalse) throws ParseException {
		Word word = wr.next();
		if (word.type() != JSLexer.ELSE) {
			wr.retractWord();
			// if false goto : false
			//   ifTrue
			// : false
			tree.node0(ifFalse);
			return;
		}

		LabelNode end = new LabelNode();

		word = wr.next();
		tree.Goto(end).node0(ifFalse);
		if (word.type() == JSLexer.left_l_bracket) {
			body();
			except(JSLexer.right_l_bracket);
		} else {
			statement(word);
		}
		tree.node0(end);

		// if (xx) {} else if() {}
		//      is equals to
		// if (xx) {} else { if() {} }
	}

	/**
	 * for循环 <BR>
	 * 重要通知：不要管作用域了，<BR>
	 * 重复一遍，不要管作用域了
	 */
	private void _for() throws ParseException {
		LabelNode continueTo = new LabelNode();
		tree.node0(continueTo);

		except(JSLexer.left_s_bracket);

		Word w = wr.next();
		boolean createdVar;
		switch (w.type()) {
			case JSLexer.semicolon:
				createdVar = false;
				break;
			case JSLexer.VAR:
			case JSLexer.LET:
			case JSLexer.CONST:
				ctx.enterRegion();
				createdVar = true;
				define(w.type());
				break;
			default:
				_onError(w, "unexpected:" + w.val() + ":var or ;");
				return;
		}

		LabelNode breakTo = condition(false, JSLexer.semicolon);
		if (breakTo == null) {
			_onError(w, "not_condition");
			return;
		}

		List<Expression> execLast = new ArrayList<>();

		final ExprParser parser = KScriptVM.retainExprParser(0);
		do {
			Expression expr = parser.read(this, (short) (16 | 1024));
			if (expr == null) break;
			execLast.add(expr);

		} while (wr.next().type() == JSLexer.colon);
		wr.retractWord();

		except(JSLexer.right_s_bracket);

		enterCycle(continueTo, breakTo);

		try {
			block();
		} finally {
			endCycle();
		}

		if (!execLast.isEmpty()) {
			LabelNode ol = new LabelNode(continueTo);
			tree.node0(continueTo);
			for (Expression expr : execLast) {
				expr.write(tree, true);
			}
			tree.Goto(ol);
		} else {
			tree.Goto(continueTo);
		}

		tree.node0(breakTo);

		if (createdVar) {
			ctx.endRegion();
		}
	}

	@Deprecated
	private void endCycle() {
		labels.remove(labels.size());
		namedLabelChunk.remove(namedLabelChunk.size());
	}

	private void enterCycle(LabelNode continueTo, LabelNode breakTo) {
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
		LabelNode continueTo = new LabelNode();
		tree.node0(continueTo);

		LabelNode breakTo = new LabelNode();

		enterCycle(continueTo, breakTo);

		try {
			block(); // do {]
		} finally {
			endCycle();
		}

		except(JSLexer.WHILE, "while");
		LabelNode breakTo1 = condition(true, JSLexer.right_s_bracket);
		if (breakTo1 == null) {
			return;
		}

		tree.Goto(continueTo).Node(breakTo).node0(breakTo1);

		except(JSLexer.semicolon);
	}

	/**
	 * a
	 * while循环
	 */
	private void _while() throws ParseException {
		LabelNode continueTo = new LabelNode();
		tree.node0(continueTo);

		LabelNode breakTo = condition(true, JSLexer.right_s_bracket);
		if (breakTo == null) return;

		enterCycle(continueTo, breakTo);

		try {
			block();
		} finally {
			endCycle();
		}

		tree.Goto(continueTo).node0(breakTo);
	}

	/**
	 * switch
	 */
	private void _switch() throws ParseException {
		except(JSLexer.left_s_bracket);

		Node last = tree.last();

		Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 256);
		if (expr == null) throw wr.err("statement.empty.switch");

		KType cstv = expr.isConstant() ? expr.asCst().val() : null;

		expr.write(tree, false);

		except(JSLexer.left_l_bracket);

		SwitchMap nodeMap = new SwitchMap();
		LabelNode end = new LabelNode();

		SwitchNode sw = tree.Switch(null, nodeMap);

		byte prev = sectionFlag;
		sectionFlag |= 4;

		o:
		while (wr.hasNext()) {
			Word wd = wr.next();
			switch (wd.type()) {
				case JSLexer.CASE:

					expr = KScriptVM.retainExprParser(0).read(this, (short) 512);
					if (expr == null) {
						_onError(wd, "case.empty");
						sectionFlag = prev;
						return;
					}
					if (!(expr = expr.compress()).isConstant()) {
						_onError(wd, "case.not_constant");
					}
					wr.retractWord();
					except(JSLexer.colon);

					final LabelNode label = tree.Label();
					if (nodeMap.put(expr.asCst().val(), label) != null) {
						_onError(wd, "case.duplicate:" + expr.asCst().val());
					}

					if (cstv != null) {
						if (!expr.asCst().val().equalsTo(cstv)) {
							KS_ASM tmp = tree;
							tree = KS_ASM.builder(tree);
							switchBlock(end);
							tree = tmp;
							break;
						} else {
							nodeMap.put(null, label);
						}
					}

					switchBlock(end);
					break;
				case JSLexer.DEFAULT:
					except(JSLexer.colon);
					if (sw.def != null) {
						_onError(wd, "duplicate:default");
						continue;
					}

					tree.node0(sw.def = new LabelNode());

					switchBlock(end);
					break;
				case JSLexer.right_l_bracket:
					break o;
				default:
					_onError(wd, "unexpected:" + wd.val());
					sectionFlag = prev;
					return;
			}

			sectionFlag = prev;

			if (nodeMap.isEmpty()) {
				_onWarning(wd, "switch.empty");
				if (sw.def != null) {
					Node next = sw.def.next;
					System.out.println("empty.next= " + next);
					tree.last_A(next == null ? last : next);
				} else {
					tree.last_A(last);
				}
			} else if (cstv != null) {
				_onWarning(wd, "switch.constant");
				// todo test
				Node label = nodeMap.get(null);
				// drop into case
				if (label != null) {
					if (sw.def == null) {
						last.next = sw.next;
					} else {
						last.next = new GotoNode((LabelNode) label);
					}
					// found
				} else if (sw.def != null) {
					// using default
					last.next = new GotoNode(end);
				} else {
					// nothing collided

					last.next = null;
					tree.last_A(last);
					return;
				}
			}

			if (sw.def == null) sw.def = end;

			tree.node0(end);
			except(JSLexer.right_l_bracket);
		}
	}

	@SuppressWarnings("fallthrough")
	private void switchBlock(LabelNode endPoint) throws ParseException {
		enterCycle(null, endPoint);
		try {
			o:
			while (wr.hasNext()) {
				Word w = wr.next();
				switch (w.type()) {
					case JSLexer.CASE:
					case JSLexer.DEFAULT:
					case JSLexer.right_l_bracket:
						wr.retractWord();
					case Word.EOF:
						break o;
				}
				if (w.type() == JSLexer.left_l_bracket) {
					body();
					except(JSLexer.right_l_bracket);
				} else {
					sectionFlag &= ~3;
					statement(w);
					if ((sectionFlag & 2) != 0) {
						break;
					}
				}
			}
		} finally {
			endCycle();
		}
	}

	// endregion
	// region 表达式和变量

	/**
	 * 定义变量 var/const/let
	 */
	@SuppressWarnings("fallthrough")
	private void define(int type) throws ParseException {
		String name = null;
		int first = 0;

		o:
		while (wr.hasNext()) {
			Word w = wr.next();
			switch (w.type()) {
				case JSLexer.left_m_bracket:

					break;
				case Word.LITERAL:
					if (name != null) {
						_onError(w, "unexpected:" + w.val());
						return;
					}

					name = w.val();

					if (ctx.exists(name) || parameters.containsKey(name)) {
						_onWarning(w, "var.exist");
					}

					first = 1;

					break;
				case JSLexer.assign:
					if (name == null) {
						_onError(w, "unexpected:=");
						return;
					}

					Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
					if (expr == null) {
						_onError(w, "statement.not");
						return;
					}

					KType val;

					// Note: 变量第一次初始化时才可以用def，后面就必须用opcode了
					// Note2: 这里检测同级就好
					boolean exist = ctx.selfExists(name);
					if (expr.type() != -1 && !exist) {
						val = expr.asCst().val();
					} else {
						if (exist && type == JSLexer.CONST) {
							_onError(w, "var.redefine");
						}

						expr.write(tree, false);
						tree.Set(name);

						val = null;
					}

					switch (type) {
						case JSLexer.LET:
							ctx.local(name, val, tree.last());
							break;
						case JSLexer.CONST:
							ctx.Const(name, val);
							break;
						case JSLexer.VAR:
							ctx.global(name, val);
							break;
					}
					name = null;
					break;
				case JSLexer.comma:
					if (name != null && type == JSLexer.CONST) {
						_onError(w, "var.initialize_const");
					}

					if (first++ == 1) {
						// lazy define
						if (name != null) {
							switch (type) {
								case JSLexer.LET:
									ctx.local(name, KUndefined.UNDEFINED, tree.last());
									break;
								case JSLexer.CONST:
									ctx.Const(name, KUndefined.UNDEFINED);
									break;
								case JSLexer.VAR:
									ctx.global(name, KUndefined.UNDEFINED);
									break;
							}
						}

						name = null;
						break;
					}
				default:
					_onError(w, "unexpected:" + w.val() + ":;");
				case JSLexer.semicolon:
					if (name != null) {
						switch (type) {
							case JSLexer.LET:
								ctx.local(name, KUndefined.UNDEFINED, tree.last());
								break;
							case JSLexer.CONST:
								_onError(w, "var.initialize_const");
								break;
							case JSLexer.VAR:
								ctx.global(name, KUndefined.UNDEFINED);
								break;
						}
					}

					break o;
			}

		}
	}

	/**
	 * 标准单个表达式
	 */
	private void assign() throws ParseException {
		// check label
		Word k = wr.next();
		if (k.type() == Word.LITERAL) {
			String val = k.val();
			if (wr.readWord().type() == JSLexer.colon) {
				_namedLabel(val);
				return;
			}
		}
		wr.retractWord();

		Expression expr = KScriptVM.retainExprParser(0).read(this, (short) 0);
		if (expr == null) {
			_onError(k, "statement.not");
		} else {
			expr.write(tree, true);
		}

		except(JSLexer.semicolon);
	}

	/**
	 * @return 常量值
	 */
	@Nullable
	@Override
	public KType maybeConstant(String name) {
		return ctx.isConst(name) ? ctx.globals.get(name) : null;
	}

	/**
	 * 使用（访问）变量
	 */
	@Override
	public void useVariable(String name) {
		ctx.chainUpdate(name);
		if (!ctx.selfExists(name)) {
			int i = parameters.getOrDefault(name, -1);
			if (i != -1) {
				ctx.loadPar(i, name);
			}
		} else {
			// 只有存在才会更新
			ctx.updateRegion(name, tree.last());
		}
	}

	@Override
	public void assignVariable(String name) {
		ctx.chainUpdate(name);
		if (ctx.isConst(name)) Helpers.athrow(wr.err("var.write_const"));
	}

	@Override
	public JSLexer getLexer() {
		return wr;
	}

	//endregion
	// region 输出错误

	private boolean success = true;     // 语法解析结果

	private void _onError(ParseException e) {
		success = false;
		handler.handle("ERR", file, e);
	}

	/**
	 * Notice: 语法级错误(return ,,,;)需要return, 定义级的(const v;)没必要 <BR>
	 * 不过随便了, 反正是[错误]
	 */
	private void _onError(Word word, String v) {
		success = false;
		handler.handle("ERR", file, wr.err(v, word));
	}

	private void _onWarning(Word word, String v) {
		handler.handle("WARN", file, wr.err(v, word));
	}

	private void except(short id) throws ParseException {
		except(id, JSLexer.byId(id));
	}

	private void except(short id, String s) throws ParseException {
		Word w = wr.next();
		if (w.type() != id) {
			_onError(w, "unexpected:" + w.val() + ':' + s);
			wr.retractWord();
		}
	}

	//endregion
}
