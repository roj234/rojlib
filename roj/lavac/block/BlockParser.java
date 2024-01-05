package roj.lavac.block;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrLavaSpec;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeCast;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.asm.visitor.XAttrCode;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.ast.expr.ExprNode;
import roj.config.ParseException;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.lavac.CompilerConfig;
import roj.lavac.asm.Variable;
import roj.lavac.expr.Binary;
import roj.lavac.expr.ExprParser;
import roj.lavac.parser.*;
import roj.util.Helpers;

import javax.annotation.Nullable;
import javax.tools.Diagnostic;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.lavac.parser.JavaLexer.*;

/**
 * KScript语法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class BlockParser {
	JavaLexer wr;
	CompileContext ctx;
	CompileUnit file;
	MethodWriterL cw;
	int type;

	private final int depth;
	private ExprParser ep;

	public BlockParser(MethodWriterL ctx) {
		this.cw = ctx;
		this.depth = -1;
		this.wr = new JavaLexer();
	}

	@Internal
	public BlockParser(int depth) {
		this.depth = depth;
	}

	@Internal
	public BlockParser retain(BlockParser parent) {
		if (parent != null) {
			this.file = parent.file;
			this.ctx = parent.ctx;
			this.wr = parent.wr;
		}

		this.success = true;

		this.variables.clear();
		this.variableAdded.clear();
		this.sectionFlag = 0;

		this.returnHook = null;
		this.returnHookUsed = 0;

		this.labelAdded.clear();
		this.labels.clear();
		this.curBreak = this.curContinue = null;
		this.labelBeforeLoop = null;

		ep = CompileLocalCache.get().ep;

		return this;
	}

	public void init(CompileUnit u, int start, MethodNode mn) {
		ctx = u.ctx();
		file = u;
		wr = u.getLexer();
		wr.index = start;
		type = 0;
		cw = new MethodWriterL(mn);
	}

	/// region 解析

	public void parseStaticInit(CompileUnit file, XAttrCode attr, int begin, int end) throws ParseException {
		ctx = file.ctx();
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		parse0();
	}

	public void parseGlobalInit(CompileUnit file, XAttrCode attr, int begin, int end) throws ParseException {
		ctx = file.ctx();
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		parse0();
	}

	public void parseMethod(CompileUnit file, XAttrCode attr, List<String> names, int begin, int end) throws ParseException {
		ctx = file.ctx();
		this.file = file;
		this.wr = file.getLexer();
		this.wr.index = begin;
		parse0();
	}

	public void parse0() throws ParseException {
		//wr.setLineHandler(this);

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
			throw wr.err("not_statement");
		}

		removeUnusedVar();
	}

	//endregion
	//region 函数

	private void removeUnusedVar() throws ParseException {
		wr.retractWord();
		Word wd = wr.next();
		for (Variable v : variables.values()) {
			if (v.curType == null)
				_onWarning(wd, "unused.var:" + v.name);
		}
	}

	MyHashMap<String, Variable> variables = new MyHashMap<>();
	List<List<String>> variableAdded = new SimpleList<>();

	// 作用域
	private void pushVar() {
		variableAdded.add(new SimpleList<>());
	}

	private void popVar() {
		for (String name : variableAdded.remove(variableAdded.size() - 1))
			variables.remove(name);
	}

	/**
	 * Unreachable statement检测 <BR>
	 * bit1 MULTI_LINE BLOCK: { sth }
	 * bit2 MULTI_LINE BLOCK END
	 * bit3 SWITCH
	 * bit4 FIRST STATEMENT IN BLOCK
	 */
	byte sectionFlag;

	/// 参数
	private void parameters() throws ParseException {
		// 参数不用我管了，lambda稍后再说
	}

	/**
	 * 函数体
	 */
	@SuppressWarnings("fallthrough")
	private void block() throws ParseException {
		boolean chained = (sectionFlag & 8) != 0;
		// todo remove doubleing {{}} and split {{}xx} as if {} and {xx}

		sectionFlag |= 9;

		pushVar();
		try {
			o:
			while (true) {
				Word w = wr.next();
				switch (w.type()) {
					case Word.EOF:
					case right_l_bracket:
						wr.retractWord();
						break o;
					default:
						statement(w);
						sectionFlag &= ~8;
				}
			}
		} finally {
			popVar();
			sectionFlag &= ~8;
		}
	}

	/**
	 * 语句
	 */
	@SuppressWarnings("fallthrough")
	private void statement(Word w) {
		try {
			// return: keep previous label
			// break: clear previous label
			// if clear without define:
			//     its three nodes are both null, not care
			switch (w.type()) {
				case at:
					_onWarning(w, "lavac不支持方法体中的注解，且没有支持的计划，当然其实这并不难，你可以尝试自己实现");
					file._annotations(Collections.emptyList());
					return;
				case CASE:
				case DEFAULT:
					if ((sectionFlag & 4) != 0) {
						wr.retractWord();
					} else {
						_onError(w, "not_statement");
					}
					return;
				case CONTINUE: case BREAK: _break(w.type() == BREAK); break;
				case JavaLexer.GOTO: _goto(); break;
				case SWITCH: _switch(); break;
				case JavaLexer.RETURN: _return(); break;
				case FOR: _for(); break;
				case WHILE: _while(); break;
				case THROW: _throw(); break;
				case IF: _if(); break;
				case DO: _doWhile(); break;
				case TRY: _try(); break;
				case SYNCHRONIZED: _sync(); break;
				case semicolon: _onWarning(w, "statement.empty"); break;
				case left_l_bracket: block(); except(right_l_bracket); break;

				case Word.LITERAL:
					if (exprDefineLabel(w))
						// 不清理label
						// label由变量定义块处理
						return;
					break;
				case INT:
				case LONG:
				case DOUBLE:
				case FLOAT:
				case SHORT:
				case BYTE:
				case CHAR:
				case BOOLEAN:
					wr.retractWord();
					define(null);
					break;
				default:
					wr.retractWord();
					ExprNode expr = ep.parse(file, 0);
					if (expr != null) {
						expr.write(cw, true);
						except(semicolon);
					}
					break;
			}
		} catch (ParseException e) {
			Helpers.athrow(e);
		}

		labelBeforeLoop = null;
	}

	private Variable tmpVar(String desc, int type) {
		return new Variable(desc, type==Type.CLASS?new Type("java/lang/Object"):Type.std(type));
	}

	private void _sync() throws ParseException {
		except(left_s_bracket);

		ExprNode expr = ep.parse(file, ExprParser.STOP_RSB|ExprParser.SKIP_RSB);
		if (expr == null) {
			wr.retractWord();
			_onError(wr.next(), "statement.empty");
			return;
		}
		expr.write(cw, false);

		Variable v0 = tmpVar("_synchronize_helper_", Type.CLASS);

		cw.one(DUP);
		cw.store(v0);
		cw.one(MONITORENTER);

		Label start = cw.label();

		except(left_s_bracket);
		block();
		except(right_s_bracket);

		Label end = cw.label();
		Label realEnd = new Label();

		cw.jump(Opcodes.GOTO, realEnd);

		Label exception = cw.label();
		cw.load(v0);
		cw.one(MONITOREXIT);
		cw.one(ATHROW);

		cw.label(realEnd);
		cw.load(v0);
		cw.one(MONITOREXIT);

		cw.addException(start,end,exception, TryCatchEntry.ANY);
	}

	/**
	 * 返回
	 */
	private void _return() throws ParseException {
		Word w = wr.next();
		if (depth == -1) _onError(w, "return.on_top");

		ExprNode expr = null;
		Type rt = cw.method.returnType();

		if (w.type() != semicolon) {
			wr.retractWord();

			expr = ep.parse(file, ExprParser.STOP_SEMICOLON);
			assert expr != null;
			expr.write(cw, false);

			except(semicolon);
		}

		TypeCast result = cw.checkCast(expr == null ? Type.std(Type.VOID) : expr.type(), rt);
		if (result.type < 0) {
			// todo
		}

		if (returnHook != null) {
			cw.jump(Opcodes.GOTO, returnHook);
			returnHookUsed++;
		} else {
			cw.one(rt.shiftedOpcode(IRETURN));
		}

		_assertBlockEnd();
	}

	/**
	 * 内部函数
	 */
	private void _func() throws ParseException {
		// todo
	}

	// endregion
	// region 条件

	// finally的return之前的hook
	private Label returnHook;
	private int returnHookUsed;

	// 记录当前代码块增加的label, 以便超出作用域之后移除
	private final SimpleList<List<String>> labelAdded = new SimpleList<>();
	private final MyHashMap<String, LabelInfo> labels = new MyHashMap<>();
	// 按照代码块(循环)深度递增的,方便break/continue寻找
	private Label curBreak, curContinue;

	// 对于循环上一条语句的label的支持
	private LabelInfo labelBeforeLoop;

	private boolean newLabel(String val) {
		if (labels.containsKey(val)) return false;
		if (labelAdded.size() > 0) labelAdded.get(labelAdded.size()-1).add(val);

		labels.put(val, labelBeforeLoop = new LabelInfo(cw.label()));
		return true;
	}

	private void _try() throws ParseException {
		Label prevHook = returnHook;
		int prevUse = returnHookUsed;
		returnHook = new Label();
		returnHookUsed = 0;

		Label blockEnd = new Label();
		Label tryBegin = new Label(), tryEnd = new Label();

		cw.label(tryBegin);
		except(left_l_bracket);
		block();
		except(right_l_bracket);
		cw.label(tryEnd);

		boolean anyNormalFlow = cw.hasNormalEnd(tryEnd);
		if (anyNormalFlow) cw.jump(Opcodes.GOTO, blockEnd);

		// 1: <any> handler, 2: at least one non <any> handler, 4: autoClose
		byte flag = 0;

		Word w;
		while (true) {
			w = wr.next();
			if (w.type() != CATCH) break;

			if ((flag & 1) != 0) throw wr.err("duplicate:catch");

			TryCatchEntry ex = cw.addException(tryBegin,tryEnd,null,null);
			pushVar();

			w = wr.next();
			switch (w.type()) {
				case left_s_bracket: // (
					Type exType = file.resolveType(0).rawType();
					if (ex.type.equals("java/lang/Throwable")) {
						flag |= 1;
					} else {
						ex.type = exType.owner;
					}

					w = wr.next();
					if (w.type() != Word.LITERAL) throw wr.err("unexpected:" + w);
					if (variables.containsKey(w.val())) _onError(w, "variable.exist:"+w.val());

					cw.store(new Variable(w.val(), exType));

					except(right_s_bracket);
					except(left_l_bracket);

					flag |= 2;
				break;
				case left_l_bracket: // {
					if (!ctx.isSpecEnabled(CompilerConfig.SHORT_CATCH))
						throw wr.err("disabled_spec:short_catch");
					if ((flag & 2) != 0) throw wr.err("spec.short_catch.has_other_branches");
					cw.one(POP);
					flag |= 1;
				break;
				default:
					_onError(w, "unexpected:"+w.val()+":( or {");
					wr.retractWord();
				return;
			}

			ex.handler = cw.label();

			block();
			except(right_l_bracket);

			popVar();

			if (cw.hasNormalEnd()) {
				cw.jump(Opcodes.GOTO, blockEnd);
				anyNormalFlow = true;
			}
		}

		Label hook = returnHook;
		int used = returnHookUsed;
		returnHook = prevHook;
		returnHookUsed = prevUse;

		if (w.type() == FINALLY) {
			Label finally_handler = new Label();
			Variable exception = tmpVar("exception", Type.CLASS);

			cw.addException(tryBegin, cw.label(), finally_handler, TryCatchEntry.ANY);

			except(left_l_bracket);

			if (prevHook == null || !ctx.isSpecEnabled(CompilerConfig.FINALLY_OPTIMIZE)) {
				int bci = cw.bci();

				// 副本的 3/3: 异常处理
				int pos = wr.index;
				cw.label(finally_handler);
				cw.store(exception);
				block();
				if (cw.hasNormalEnd()) {
					cw.load(exception);
					cw.one(ATHROW);
				}

				// if (cw.bci() - bci > 511) throw wr.err("finally_optimize_required");

				// 副本的 1/3: 正常执行(可选)
				if (anyNormalFlow) {
					cw.label(blockEnd);
					wr.index = pos;
					block();
					cw.jump(blockEnd = new Label());
				}

				// 副本的 2/3: return劫持
				if (used > 0) {
					boolean isVoid = cw.method.returnType().type == VOID;
					cw.label(hook);
					if (!isVoid) cw.store(exception);
					wr.index = pos;
					block();
					if (cw.hasNormalEnd()) {
						if (!isVoid) cw.load(exception);
						cw.one(cw.method.returnType().shiftedOpcode(IRETURN));
					}
				}
			} else {
				// finally优化
				Variable category = tmpVar("finally优化|跳转自", Type.INT);
				Variable return_value = cw.method.returnType().type == VOID ? null : tmpVar("finally优化|返回值", Type.CLASS);
				Label real_finally_handler = new Label();

				// 副本的 1/3: 正常执行(可选)
				if (anyNormalFlow) {
					cw.label(blockEnd);
					cw.one(ICONST_0);
					cw.store(category);
					if (return_value != null) {
						cw.one(ACONST_NULL);
						cw.store(return_value);
					}
					cw.one(ACONST_NULL);
					cw.store(exception);
					cw.jump(real_finally_handler);
				}

				// 副本的 2/3: return劫持
				if (used > 0) {
					cw.label(hook);
					if (return_value != null) {
						cw.store(return_value);
					}
					cw.one(ICONST_1);
					cw.store(category);
					cw.one(ACONST_NULL);
					cw.store(exception);
					cw.jump(real_finally_handler);
				}

				// 副本的 3/3: 异常
				cw.label(finally_handler);
				cw.store(exception);
				cw.one(ICONST_M1);
				cw.store(category);
				if (return_value != null) {
					cw.one(ACONST_NULL);
					cw.store(return_value);
				}

				cw.label(real_finally_handler);

				block();

				// finally可以执行完
				if (cw.hasNormalEnd()) {
					Label returnHook = new Label();

					if (anyNormalFlow) {// = 0 : normal execution
						cw.load(category);
						cw.jump(IFEQ, blockEnd = new Label());
					}
					if (used > 0) {     // > 0 : return hook
						cw.load(category);
						cw.jump(IFGT, returnHook);
					}
					cw.load(exception);// < 0 : exception
					cw.one(ATHROW);

					if (used > 0) {
						cw.label(returnHook);
						if (return_value != null) cw.load(return_value);
						cw.one(cw.method.returnType().shiftedOpcode(IRETURN));
					}
				}
			}
			except(right_l_bracket);
		} else {
			wr.retractWord();

			// 孤立的try
			if (flag == 0) {
				_onError(w, "try.alone");
				return;
			}
		}

		cw.label(blockEnd);
	}

	/**
	 * goto <x>
	 */
	private void _goto() throws ParseException {
		Word w = wr.except(Word.LITERAL);
		LabelInfo info = labels.get(w.val());
		if (info != null && info.head != null) {
			cw.jump(info.head);
		} else {
			_onError(w, "goto.illegal_label:"+w.val());
		}
		except(semicolon);
		_assertBlockEnd();
	}

	/**
	 * break [x] or continue [x]
	 */
	private void _break(boolean isBreak) throws ParseException {
		Word w = wr.next();
		switch (w.type()) {
			case Word.LITERAL:
				LabelInfo info = labels.get(w.val());
				if (info != null) {
					Label node = isBreak ? info.onBreak : info.onContinue;
					if (node != null) {
						cw.jump(node);
						break;
					}
				}
				_onError(w, "goto.illegal_label:"+w.val());
				break;
			case semicolon:
				Label node = isBreak ? curBreak : curContinue;
				if (node == null) {
					_onError(w, "goto.not_label");
				} else {
					cw.jump(node);
					_assertBlockEnd();
				}
				return;
		}
		except(semicolon);
		_assertBlockEnd();
	}

	/**
	 * throw sth
	 */
	private void _throw() throws ParseException {
		Word w = wr.next().copy();
		wr.retractWord();

		ExprNode expr = ep.parse(file, ExprParser.STOP_SEMICOLON);
		except(semicolon);

		if (expr == null) {
			_onError(w, "statement.empty");
			return;
		}
		expr.write(cw, false);
		cw.one(ATHROW);

		_assertBlockEnd();
	}

	/**
	 * assert end of code block '{ sth }'
	 */
	private void _assertBlockEnd() throws ParseException {
		if ((sectionFlag & 1) != 0) {
			Word w = wr.next();
			if (w.type() != right_l_bracket) {
				_onError(w, "statement.unreachable");
			}
			wr.retractWord();
		}

		sectionFlag |= 2;
	}

	/**
	 * if 条件判断语句
	 */
	private void _if() throws ParseException {
		Label ifFalse = condition(true, right_s_bracket);
		if (ifFalse == null) return;

		body();

		_else(ifFalse);
	}

	/**
	 * 语句块 or 单行语句
	 */
	private void body() throws ParseException {
		Word word = wr.next();
		if (word.type() == left_l_bracket) {
			block();
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
	private Label condition(boolean checkBracket, short end) throws ParseException {
		if (checkBracket) except(left_s_bracket);

		ExprParser parser = ep;

		Label ifFalse = new Label();

		ExprNode equ = parser.parse(file, (checkBracket ? 16 : 0), ifFalse);
		if (equ == null) {
			_onError(wr.readWord(), "statement.empty.if");
			return null;
		}

		equ.write(cw, false);

		if (!(equ instanceof Binary)) { // 简单表达式 => IS_TRUE, 复杂的话有，嗯，Binary todo 测试
			cw.jump(IFEQ, ifFalse);
		}

		except(end);

		return ifFalse;
	}


	/**
	 * else 语句 <BR>
	 * 和数学书上写的一样, 它是可选的
	 *
	 * @param ifFalse false跳转点
	 */
	private void _else(Label ifFalse) throws ParseException {
		Word word = wr.next();
		if (word.type() != ELSE) {
			wr.retractWord();
			// if false goto : false
			//   ifTrue
			// : false
			cw.label(ifFalse);
			return;
		}

		Label end = new Label();

		word = wr.next();
		cw.jump(end);
		cw.label(ifFalse);
		if (word.type() == left_l_bracket) {
			block();
			except(right_l_bracket);
		} else {
			statement(word);
		}
		cw.label(end);

		// if (xx) {} else if() {}
		//      is equals to
		// if (xx) {} else { if() {} }
	}

	/**
	 * for循环
	 */
	private void _for() throws ParseException {
		Label continueTo = new Label();
		cw.label(continueTo);

		except(left_s_bracket);

		Word w = wr.next();

		boolean createdVar;
		if (w.type() != semicolon) {
			wr.retractWord();

			pushVar();
			define(null);
			createdVar = true;
		} else {
			createdVar = false;
		}

		Label breakTo = condition(false, semicolon);
		if (breakTo == null) return;

		List<ExprNode> execLast = new SimpleList<>();
		do {
			ExprNode expr = ep.parse(file, (16 | 1024));
			if (expr == null) break;
			execLast.add(expr);
		} while (wr.next().type() == colon);
		wr.retractWord();

		except(right_s_bracket);

		Label prevBrk = curBreak, prevCon = curContinue;
		enterLoop(continueTo, breakTo);
		try {
			body();
		} finally {
			endLoop(prevCon, prevBrk);
		}

		if (!execLast.isEmpty()) {
			cw.label(continueTo);
			for (int i = 0; i < execLast.size(); i++) {
				execLast.get(i).write(cw, true);
			}
			cw.jump(continueTo);
		} else {
			cw.jump(continueTo);
		}

		cw.label(breakTo);

		if (createdVar) popVar();
	}

	private void enterLoop(Label continueTo, Label breakTo) {
		LabelInfo info = labelBeforeLoop;
		if (info != null) {
			info.onBreak = breakTo;
			info.onContinue = continueTo;
			labelBeforeLoop = null;
		}

		if (continueTo != null) curContinue = continueTo;
		if (breakTo != null) curBreak = breakTo;

		labelAdded.add(new SimpleList<>());
	}

	private void endLoop(Label prevContinueTo, Label prevBreakTo) {
		curContinue = prevContinueTo;
		curBreak = prevBreakTo;
		List<String> set = labelAdded.remove(labelAdded.size() - 1);
		for (String key : set) labels.remove(key);
	}

	/**
	 * do-while循环
	 */
	private void _doWhile() throws ParseException {
		Label prevBrk = curBreak, prevCon = curContinue;

		Label continueTo = cw.label();
		Label breakTo = new Label();

		enterLoop(continueTo, breakTo);
		try {
			body();
		} finally {
			endLoop(prevCon, prevBrk);
		}

		except(WHILE);

		Label breakTo2 = condition(true, right_s_bracket);
		if (breakTo2 == null) return;
		except(semicolon);

		cw.jump(continueTo);
		cw.label(breakTo);
		cw.label(breakTo2);
	}

	/**
	 * while循环
	 */
	private void _while() throws ParseException {
		Label prevBrk = curBreak, prevCon = curContinue;

		Label continueTo = cw.label();
		Label breakTo = condition(true, right_s_bracket);
		if (breakTo == null) return;

		enterLoop(continueTo, breakTo);
		try {
			body();
		} finally {
			endLoop(prevCon, prevBrk);
		}

		cw.jump(continueTo);
		cw.label(breakTo);
	}

	/**
	 * switch
	 */
	private void _switch() throws ParseException {
		except(left_s_bracket);

		ExprNode expr = ep.parse(file, 256);
		if (expr == null) throw wr.err("statement.empty.switch");
		Object cst = expr.isConstant() ? expr.constVal() : null;
		if (cst != null) {
			wr.retractWord();
			_onWarning(wr.next(), "switch.constant");
		}
		expr.write(cw, false);

		except(left_l_bracket);

		Label breakTo = new Label();
		SwitchSegment node = new SwitchSegment(TABLESWITCH);

		Type sType = (Type) expr.type();
		int kind = 0;
		switch (sType.type) {
			case CLASS:
				if (sType.owner.equals("java/lang/String")) {
					kind = 1;
					break;
				} else {
					IClass clazz = ctx.getClassInfo(sType.owner);
					if (clazz == null) throw wr.err("unable_resolve:SWITCH:"+sType.owner);
					if (ctx.canInstanceOf(sType.owner, "java/lang/Enum", -1)) {
						kind = 2;
						break;
					} else if (ctx.canInstanceOf(sType.owner, "java/lang/CharSequence", 1)) {
						kind = 3;
						break;
					} else if (ctx.isSpecEnabled(CompilerConfig.SWITCHABLE)) {
						AttrLavaSpec attr = (AttrLavaSpec) clazz.attrByName("LavacSpec");
						if (attr != null && attr.switchable() >= 0) {
							// todo
							//break;
						}
					}
				}
				throw wr.err("switch.incorrect_type:"+(char) sType.type);
			case VOID:
			case BOOLEAN:
			case FLOAT:
			case DOUBLE:
			case LONG:
				throw wr.err("switch.incorrect_type:"+(char) sType.type);
		}

		byte prev = sectionFlag;
		sectionFlag |= 4;

		List<Object> labelsCur = Helpers.cast(CompileLocalCache.get().annotationTmp); labelsCur.clear();
		MyHashSet<Object> labels = Helpers.cast(CompileLocalCache.get().toResolve_unc); labels.clear();

		o:
		while (wr.hasNext()) {
			Word w = wr.next();
			switch (w.type()) {
				case CASE:
					labelsCur.clear();
					moreCase:
					while (true) {
						expr = ep.parse(file, 512);
						if (expr == null) {
							_onError(w, "case.empty");
							sectionFlag = prev;
							return;
						}
						// todo check twice
						if (!(expr = expr.resolve()).isConstant())
							_onError(w, "case.not_constant");

						Object cv = expr.constVal();
						if (!labels.add(cv)) _onError(w, "case.duplicate:" + cv);
						labelsCur.add(cv);

						wr.retractWord();
						w = wr.next();
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
						_onError(w, "duplicate:default");
						continue;
					}

					node.def = cw.label();
					switchBlock(breakTo);
					break;
				case right_l_bracket:
					break o;
				default:
					_onError(w, "unexpected:" + w.val());
					sectionFlag = prev;
					return;
			}

			sectionFlag = prev;

			if ((node.targets.size() + (node.def == null ? 0 : 1)) < 2) {
				_onWarning(w, "switch.too_less_case");
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

			except(right_l_bracket);
		}
	}

	@SuppressWarnings("fallthrough")
	private void switchBlock(Label endPoint) throws ParseException {
		Label prevBrk = curBreak;
		enterLoop(null, endPoint);
		try {
			o:
			while (wr.hasNext()) {
				Word w = wr.next();
				switch (w.type()) {
					case CASE:
					case DEFAULT:
					case right_l_bracket:
						wr.retractWord();
					case Word.EOF:
						break o;
				}
				if (w.type() == left_l_bracket) {
					block();
					except(right_l_bracket);
				} else {
					sectionFlag &= ~3;
					statement(w);
					if ((sectionFlag & 2) != 0) {
						break;
					}
				}
			}
		} finally {
			endLoop(curContinue, prevBrk);
		}
	}

	// endregion
	// region 表达式和变量

	/**
	 * 定义变量 var/const/?
	 * 没有支持注解的计划。
	 */
	@SuppressWarnings("fallthrough")
	private void define(IType type) throws ParseException {
		if (type == null) {
			int modifier = file._modifier(wr, FINAL);
			type = file.resolveType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_AUTO);
		}

		chained:
		while (wr.hasNext()) {
			Word w = wr.except(Word.LITERAL);
			String name = w.val();

			// todo generic supporting
			if (null != variables.putIfAbsent(name, new Variable(name, type == null ? null : type.rawType())))
				_onWarning(w, "var.exist");
			if (!variables.isEmpty()) variableAdded.get(variableAdded.size()-1).add(name);

			w = wr.next();
			if (w.type() == assign) {
				if (name == null) {
					_onError(w, "unexpected:=");
					return;
				}

				ExprNode expr = ep.parse(file, 0);
				if (expr == null) {
					_onError(w, "not_statement");
					return;
				}

				expr.write(cw, false);
				cw.store(variables.get(name));

				w = wr.next();
			}

			switch (w.type()) {
				case comma: continue;
				case semicolon: break chained;
				default: wr.unexpected(w.val(), ";");
			}
		}
	}

	/**
	 * 表达式/变量定义/标签
	 */
	private boolean exprDefineLabel(Word w) throws ParseException {
		Word errorInfo = w.copy();

		// 标签
		String val = w.val();
		if (wr.readWord().type() == colon) {
			if (!newLabel(w.val())) {
				_onError(w, "duplicate_label:"+w.val());
			}
			return true;
		}

		int pos = wr.prevIndex;
		wr.index = pos;

		// 变量赋值只能是 TYPE + LITERAL
		IType type1 = file.resolveType(CompileUnit.TYPE_PRIMITIVE);
		if (Word.LITERAL == wr.next().type()) {
			wr.retractWord();
			define(type1);
			return false;
		}

		wr.index = pos;
		ExprNode expr = ep.parse(file, 0);
		if (expr == null) {
			_onError(errorInfo, "not_statement");
		} else {
			expr.write(cw, true);
		}

		except(semicolon);
		return false;
	}

	/**
	 * @return 常量值
	 */
	@Nullable
	public Constant maybeConstant(Variable name) {
		return null;
	}

	//endregion
	// region 输出错误

	private boolean success = true;

	private void _onError(Word word, String v) {
		success = false;
		ctx.report(file, Diagnostic.Kind.ERROR, word.pos(), v);
	}

	private void _onWarning(Word word, String v) {
		ctx.report(file, Diagnostic.Kind.WARNING, word.pos(), v);
	}

	private void except(short id) throws ParseException {
		wr.except(id, byId(id));
	}

	//endregion
}