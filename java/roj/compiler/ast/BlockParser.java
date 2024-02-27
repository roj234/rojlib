package roj.compiler.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.tree.attr.*;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.StaticSegment;
import roj.asm.visitor.SwitchSegment;
import roj.collect.*;
import roj.compiler.CompilerSpec;
import roj.compiler.JavaLexer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.*;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.config.Word;
import roj.util.Helpers;
import roj.util.VarMapper;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static roj.asm.Opcodes.*;
import static roj.asm.util.InsnHelper.ToPrimitiveArrayId;
import static roj.asm.util.InsnHelper.XALoad;
import static roj.compiler.JavaLexer.*;

/**
 * Lava Compiler - 代码块<p>
 * Parser levels: <ol>
 *     <li>{@link CompileUnit Class Parser}</li>
 *     <li>{@link ParseTask Segment Parser}</li>
 *     <li><b><i>Method Parser</i></b></li>
 *     <li>{@link ExprParser Expression Parser}</li>
 * </ol>
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public final class BlockParser {
	static final String VARIABLE_IGNORE = "_";
	static final Type T_EXCEPTION = new Type("java/lang/Throwable");
	private static final Type T_VAR = new Type(";"), T_CONST = new Type(";", 1);

	private final LocalContext ctx;
	private final ExprParser ep;
	private final JavaLexer wr;

	private CompileUnit file;
	private MethodWriter cw;
	private boolean blockMethod;

	public BlockParser(LocalContext ctx) {
		this.ctx = ctx;
		this.ep = ctx.ep;
		this.wr = ctx.lexer;
		this.variables = ctx.variables;
	}

	public BlockParser reset() {
		this.variables.clear();
		this.regionNew.clear();
		this.sectionFlag = 0;
		this.fastSlot = 0;

		this.returnHook = null;
		this.returnHookUsed = null;

		this.labels.clear();
		this.curBreak = this.curContinue = null;
		this.immediateLabel = null;

		this.switchMapId = 0;
		return this;
	}

	// region 解析
	public void parseBlockMethod(CompileUnit file, MethodWriter mw) throws ParseException {
		blockMethod = true;
		this.file = file;

		ctx.setMethod(mw.mn);
		reset();
		wr.next(); wr.retractWord();
		wr.setLines(new LineNumberTable());
		setCw(mw);
		parse0();

		wr.getLines(cw);
	}

	public MethodWriter parseMethod(CompileUnit file, MethodNode mn, List<String> names) throws ParseException {
		blockMethod = false;
		this.file = file;

		ctx.setMethod(mn);
		reset();
		wr.next(); wr.retractWord();
		var cw = ctx.classes.createMethodPoet(file, mn);
		var lines = new LineNumberTable();
		if (mn.name().equals("<init>")) file.appendGlobalInit(cw, lines);
		wr.setLines(lines);
		setCw(cw);

		if ((mn.modifier()&ACC_STATIC) == 0) fastSlot = 1;

		var flags = mn.parsedAttr(null, Attribute.MethodParameters);
		var sign = mn.parsedAttr(null, Attribute.SIGNATURE);
		var parameters = mn.parameters();
		for (int i = 0; i < parameters.size(); i++) {
			IType type = parameters.get(i);
			if (i < names.size()) {
				// TODO @skip(_)不应该占据slot
				Variable var = newVar(names.get(i), sign != null ? sign.values.get(i) : type);
				if (flags != null && (flags.getFlag(i, 0)&ACC_FINAL) != 0) var.isFinal = true;
				var.hasValue = true;
			} else {
				fastSlot += type.rawType().length();
			}
		}

		parse0();

		wr.getLines(cw);
		return cw;
	}

	private void parse0() throws ParseException {
		beginCodeBlock();

		while (true) {
			Word w = wr.next();
			if (w.type() == Word.EOF || w.type() == rBrace) {
				if (blockMethod != cw.isContinuousControlFlow()) {
					if (blockMethod) {
						ctx.report(Kind.ERROR, "block.initializorCannotComplete");
					} else {
						if (cw.mn.returnType().type == Type.VOID) cw.one(Opcodes.RETURN);
						else ctx.report(Kind.ERROR, "block.missingReturnValue");
					}
				}
				break;
			}

			statement(w);
		}

		endCodeBlock();
	}
	//endregion
	/**
	 * 语句
	 */
	@SuppressWarnings("fallthrough")
	private void statement(Word w) throws ParseException {
		LabelNode imLabel = null;
		for(;;) {
		switch (w.type()) {
			case semicolon -> ctx.report(Kind.WARNING, "block.emptyStatement");
			case lBrace -> blockV();

			case TRY -> _try();

			case CONTINUE, BREAK -> _break(w.type() == BREAK);
			case JavaLexer.GOTO -> _goto();
			case JavaLexer.RETURN -> _return();
			case THROW -> _throw();

			case IF -> _if();
			case FOR -> {
				immediateLabel = imLabel;
				_for();
			}
			case WHILE -> {
				immediateLabel = imLabel;
				_while();
			}
			case DO -> {
				immediateLabel = imLabel;
				_doWhile();
			}
			case SWITCH -> _switch();
			case YIELD -> _yield();

			case SYNCHRONIZED -> _sync();
			case WITH -> _with();
			case ASSERT -> _assert();
			//case AWAIT -> _await(); // require Async function

			case at -> {
				ctx.report(Kind.WARNING, "lavac不支持方法体中的注解");
				file._annotations(Collections.emptyList());
			}

			case FINAL -> {
				wr.retractWord();
				define(null);
			}
			case CONST -> {
				if (!checkVarConst(true)) ctx.report(Kind.ERROR, "noExpression");
			}
			case Word.LITERAL -> {
				// var作为literal处理,也许是为了兼容性？
				if (w.val().equals("var") && checkVarConst(false)) break;

				wr.mark();

				// 标签
				String val = w.val();
				if (wr.next().type() == colon) {
					wr.skip();
					if (labels.containsKey(val)) ctx.report(Kind.ERROR, "block.error.dupLabel", val);
					if (!regionNew.isEmpty()) regionNew.get(regionNew.size()-1).add(val);

					if (imLabel == null) {
						imLabel = new LabelNode(cw.label());
						imLabel.onBreak = new Label();
					}
					labels.put(val, imLabel);

					w = wr.next();
					continue;
				}

				wr.retract();
				wr.retractWord();

				var expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.CHECK_VARIABLE_DECLARE|ExprParser.NAE);
				if (expr instanceof VariableDeclare vd) {
					wr.retractWord(); // get vd.name
					define(vd.type);
				} else {
					expr.resolve(ctx).write(cw, true);
				}
			}
			default -> {
				wr.retractWord();

				var expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.CHECK_VARIABLE_DECLARE);
				// case BOOLEAN, BYTE, SHORT, CHAR, INT, LONG, FLOAT, DOUBLE
				if (expr instanceof VariableDeclare vd) {
					wr.retractWord(); // get vd.name
					define(vd.type);
				} else if (expr != null) {
					ExprNode node = expr.resolve(ctx);
					if (node.isKind(ExprNode.ExprKind.INVOKE_CONSTRUCTOR)) {
						ctx.not_invoke_constructor = false;
					}

					node.write(cw, true);

					if (!wr.nextIf(semicolon) && !ctx.classes.isSpecEnabled(CompilerSpec.OPTIONAL_SEMICOLON)) {
						wr.unexpected(w.val(), ";");
					}
				}
			}
		}

		if (imLabel != null) {
			// 不是循环语句
			if (imLabel.onContinue == null) {
				cw.label(imLabel.onBreak);
			} else {
				imLabel.onContinue = null;
			}
			imLabel.onBreak = null;
		}

		return;
		}
	}
	// region Block, SectionFlag, 变量ID和作用域
	/**
	 * Unreachable statement检测
	 */
	private byte sectionFlag;
	private static final byte SF_BLOCK = 1, SF_SWITCH = 2;

	/**
	 * 好点的代码块
	 */
	private void blockV() throws ParseException {beginCodeBlock();block();}
	private void block() throws ParseException {
		sectionFlag |= SF_BLOCK;
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case Word.EOF: case rBrace: endCodeBlock(); return;
				default: statement(w);
			}
		}
	}

	private void blockOrStatement() throws ParseException {
		Word w = wr.next();
		if (w.type() == lBrace) {
			blockV();
		} else {
			sectionFlag &= ~SF_BLOCK;
			statement(w);
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
			ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON);
		}
	}
	private void controlFlowTerminate() throws ParseException {
		if ((sectionFlag & SF_BLOCK) != 0) {
			if (!wr.nextIf(rBrace)) ctx.report(Kind.ERROR, "block.unreachable");
			else wr.retractWord();
		}
	}

	// 当前作用域新增的东西
	private final SimpleList<SimpleList<Object>> regionNew = new SimpleList<>();
	private final MyHashMap<String, Variable> variables;

	private void beginCodeBlock() {
		SimpleList<Object> list = new SimpleList<>();
		list.add(cw.label());
		regionNew.add(list);
	}
	private void endCodeBlock() {
		SimpleList<Object> added = regionNew.pop();
		for (int i = 1; i < added.size(); i++) {
			Object var = added.get(i);
			if (var instanceof Variable v) {
				if (v.startPos == v.endPos && !v.name.startsWith("@"))
					ctx.report(v.startPos, Kind.WARNING, "block.var.unused", v.name);

				variables.remove(v.name);
				fastSlot -= v.type.rawType().length();
			} else {
				labels.remove(var.toString());
			}
		}
	}

	private final VarMapper varMapper = new VarMapper();
	// 自然也有slowSlot => VarMapper
	private int fastSlot;
	private Variable newVar(String name, IType type) {
		// TODO => CONSIDER TYPE MERGING (if var)
		Variable v = new Variable(name, type);
		v.slot = fastSlot;
		v.startPos = v.endPos = wr.index;

		if (!name.startsWith("@") && null != variables.putIfAbsent(name, v)) {
			ctx.report(Kind.ERROR, "block.var.error.duplicate", name);
			return v;
		}

		if (!regionNew.isEmpty()) regionNew.get(regionNew.size()-1).add(v);

		fastSlot += type.rawType().length();
		return v;
	}
	// endregion
	// try-finally重定向return
	private Label returnHook;
	private IntList returnHookUsed;
	// region 异常: try-catch-finally try-with-resource
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

		var tryNode = new TryNode();
		// try with resource
		if (w.type() != lBrace) {
			if (w.type() != lParen) wr.unexpected(w.val(), "block.except.tryOrAuto");

			beginCodeBlock();
			do {
				w = wr.next();
				if (w.type() == rParen && !tryNode.vars.isEmpty()) break;

				if (w.val().equals("defer")) {
					tryNode.add(ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.STOP_RSB|ExprParser.NAE).resolve(ctx));
				} else {
					IType type;
					if (w.val().equals("var") || w.val().equals("const")) {
						type = null;
					} else {
						wr.retractWord();
						file._modifiers(wr, FINAL);
						type = ctx.resolveType(file.readType(CompileUnit.TYPE_GENERIC));
					}

					String name = wr.except(Word.LITERAL).val();

					except(assign);
					var expr = ep.parse(ExprParser.STOP_RSB|ExprParser.STOP_SEMICOLON);
					if (expr == null) {
						ctx.report(Kind.ERROR, "noExpression");
					} else {
						ExprNode node = expr.resolve(ctx);
						if (type == null) type = node.type();

						var tryADefinedVar = newVar(name, type);
						tryADefinedVar.isFinal = true;
						regionNew.getLast().pop();

						if (!ctx.instanceOf(type.owner(), "java/lang/AutoCloseable")) {
							ctx.report(Kind.ERROR, "block.try.noAutoCloseable", type);
						}

						var tryANullable = !(node instanceof Invoke i && i.isNew());

						writeCast(node, type);
						cw.store(tryADefinedVar);

						var tryADefined = cw.label();

						tryNode.add(tryADefinedVar, tryANullable, tryADefined);
					}
				}
			} while (wr.next().type() == semicolon);

			if (tryNode.hasDefer && tryNode.pos.get(0) == null)
				tryNode.pos.set(0, cw.label());

			flag |= 2;
			except(lBrace);
			block();
		} else {
			blockV();
		}

		Label tryEnd = cw.label();

		Label blockEnd = new Label();
		boolean anyNormal = cw.isContinuousControlFlow(), nowNormal = anyNormal;

		if ((flag&2) != 0) {
			beginCodeBlock();

			var vars = tryNode.vars;
			for (int i = 0; i < vars.size(); i++) {
				if (vars.get(i) instanceof Variable v) {
					variables.remove(v.name);
					regionNew.getLast().add(v);
				}
			}

			closeResource(anyNormal, tryNode, blockEnd, tryEnd);

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
				default -> wr.unexpected(w.val(), "block.except.tryOrAuto");
				case lBrace -> { // {
					cw.one(POP);
					flag |= 1;
				}
				case lParen -> { // (
					// TODO "不能抛出异常" 的检测
					IType type = ctx.resolveType(file.readType(0));
					entry.type = type.owner();

					if (!ctx.instanceOf(entry.type, "java/lang/Throwable")) {
						ctx.report(Kind.ERROR, "block.try.notException", type);
					}

					if (type.genericType() != IType.STANDARD_TYPE) {
						ctx.report(Kind.ERROR, "block.try.generic", type);
					}

					for (int i = 0; i < exTypes.size(); i++) {
						if (ctx.instanceOf(entry.type, exTypes.get(i))) {
							ctx.report(Kind.ERROR, "block.try.captured", type, type);
							break;
						}
					}
					exTypes.add(entry.type);

					if ("java/lang/Throwable".equals(entry.type)) flag |= 1;

					w = wr.except(Word.LITERAL, "block.except.name");
					if (w.val().equals(VARIABLE_IGNORE)) cw.one(POP);
					else cw.store(newVar(w.val(), type));

					except(rParen);
					except(lBrace);
				}
			}

			block();

			nowNormal = cw.isContinuousControlFlow();
			if (nowNormal) {
				cw.jump(blockEnd);
				anyNormal = true;
			}
		}
		// endregion

		// TODO breakHook
		Label hook = returnHook;
		IntList used = returnHookUsed;
		returnHook = prevHook;
		returnHookUsed = prevUse;

		if (w.type() == FINALLY) {
			boolean isNormalFinally = prevHook == null && w.val().equals("finally");

			beginCodeBlock();
			except(lBrace);

			Label finally_handler = new Label();
			Variable exc = newVar("@异常", LocalContext.OBJECT_TYPE);

			cw.addException(tryBegin, cw.label(), finally_handler, TryCatchEntry.ANY);

			if (isNormalFinally) {
				int bci = cw.bci();

				// 副本的 1/3: 异常处理
				int pos = wr.index;
				cw.label(finally_handler);
				cw.store(exc);

				MethodWriter tmp = cw.fork();
				MethodWriter prev = cw;

				setCw(tmp);
				blockV();
				tmp.writeTo(prev);
				setCw(prev);

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
					ctx.report(pos, Kind.WARNING, "block.try.waste", delta, copyCount);
				}
			} else {
				// sri => subroutine id
				// rva => return value
				Variable sri = newVar("@跳转自", Type.std(Type.INT));
				Variable rva = cw.mn.returnType().type == Type.VOID ? null : newVar("@返回值", LocalContext.OBJECT_TYPE);
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

				blockV();

				// finally可以执行完
				if (cw.isContinuousControlFlow()) {
					if (used.size() > 0) {
						Label returnHook = new Label();

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
		} else {
			wr.retractWord();
			if ((flag&2) == 0) {
				// 孤立的try
				if (exTypes.isEmpty()) ctx.report(Kind.ERROR, "block.try.noHandler");

				StaticSegment ret = new StaticSegment(cw.mn.returnType().shiftedOpcode(IRETURN));
				for (int i = 0; i < used.size(); i++) cw.replaceSegment(used.get(i), ret);
			} else {
				cw.label(hook);
			}
		}

		if (anyNormal) cw.label(blockEnd);
	}
	private void closeResource(boolean anyNormal, TryNode tn, Label blockEnd, Label tryEnd) {
		int moreSituations = 0;
		if (anyNormal) moreSituations++;
		if (returnHookUsed.size() > 0) moreSituations++;

		var sri = moreSituations < 2 ? null : newVar("@跳转自", Type.std(Type.INT));
		var exc = newVar("@异常", LocalContext.OBJECT_TYPE);

		if (sri != null) {
			var label = new Label();
			cw.ldc(0);
			cw.store(sri);
			cw.jump(label);

			cw.label(returnHook);
			cw.ldc(1);
			cw.store(sri);

			cw.label(label);
		} else if (returnHookUsed.size() > 0) {
			cw.label(returnHook);
		}

		Label[] normalHandlers;
		if (moreSituations > 0) {
			List<Object> vars = tn.vars;
			Label prev_nc = null;

			normalHandlers = ctx.getTmpLabels(vars.size() << 1);
			for (int i = vars.size()-1; i >= 0; i--) {
				var o = vars.get(i);

				Label nc = new Label();
				//    TCE [a2_normal_close, a1_normal_close, @local] => LABEL1
				if (prev_nc != null) {
					normalHandlers[i*2] = prev_nc;
					normalHandlers[i*2 + 1] = new Label();
				}
				prev_nc = nc;

				if (o instanceof Variable v) {
					if (tn.exception.contains(i)) {
						cw.load(v);
						cw.jump(IFNULL, nc);
					}

					invokeClose(v, false);
				} else {
					((ExprNode) o).write(cw, true);
				}

				cw.label(nc);
			}

			if (sri != null) {
				cw.load(sri);
				cw.jump(IFEQ, blockEnd);
				cw.jump(returnHook = new Label());
			} else {
				cw.jump(anyNormal ? blockEnd : (returnHook = new Label()));
			}
		} else {
			normalHandlers = null;
		}

		var pos = tn.pos;
		int i = pos.size()-1;

		for (int j = 1; j < tn.pos.size(); j++) {
			if (pos.get(j) == null) pos.set(j, pos.get(j-1));
		}

		cw.addException(pos.get(i), tryEnd, cw.label(), TryCatchEntry.ANY);

		boolean smallTwr = i > 2;
		while (true) {
			var o = tn.vars.get(i);

			noCatch:
			if (smallTwr && o instanceof Variable v) {
				cw.load(v);
				cw.invokeS("roj/compiler/runtime/QuickUtils", "twr", "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)Ljava/lang/Throwable;");
			} else {
				cw.store(exc);

				var l_closed = new Label();
				Label l_preClose;
				boolean isException = tn.exception.contains(i);

				if (o instanceof Variable v) {
					if (isException) {
						cw.load(v);
						cw.jump(IFNULL, l_closed);
					}

					l_preClose = cw.label();
					invokeClose(v, true);
				} else {
					l_preClose = cw.label();
					((ExprNode) o).write(cw, true);

					if (!isException) {
						cw.load(exc);
						break noCatch;
					}
				}

				cw.jump(l_closed);

				var l_postClose = cw.label();
				cw.addException(l_preClose, l_postClose, l_postClose, null);
				cw.load(exc);
				cw.one(SWAP);
				cw.invokeV("java/lang/Throwable", "addSuppressed", "(Ljava/lang/Throwable;)V");

				cw.label(l_closed);
				cw.load(exc);
			}

			if (i == 0) {
				cw.one(ATHROW);
				break;
			}
			i--;

			if (normalHandlers == null) {
				if (!pos.get(i).equals(pos.get(i+1)))
					cw.addException(pos.get(i), pos.get(i+1), cw.label(), TryCatchEntry.ANY);
			} else {
				Label ph_start = normalHandlers[i*2], ph_handler = normalHandlers[i*2 + 1];
				cw.label(ph_handler);
				cw.addException(pos.get(i), ph_start, ph_handler, TryCatchEntry.ANY);
			}
		}
	}
	private void invokeClose(Variable v, boolean report) {
		IClass info = ctx.classes.getClassInfo(v.type.owner());
		var list = ctx.methodListOrReport(info, "close");
		var result = list.findMethod(ctx, Collections.emptyList(), 0);
		assert result != null;

		if (report) result.addExceptions(ctx, info, true);

		cw.load(v);
		cw.invoke((result.method.modifier&ACC_INTERFACE) != 0 ? INVOKEINTERFACE : INVOKEVIRTUAL, result.method);
	}
	// endregion
	// 带名称的label
	private final MyHashMap<String, LabelNode> labels = new MyHashMap<>();
	// 对循环上标签的支持 (continue XX)
	private LabelNode immediateLabel;
	// 当前代码块可用的break和continue目标
	private Label curBreak, curContinue;
	// region 控制流终止: break continue goto return throw
	private void _goto() throws ParseException {
		Word w = wr.except(Word.LITERAL);
		LabelNode info = labels.get(w.val());
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

		if ((sectionFlag&SF_SWITCH) != 0) ctx.report(Kind.ERROR, "block.switch.exprMode", errId);

		Word w = wr.next();
		if (w.type() == Word.LITERAL) {
			LabelNode info = labels.get(w.val());
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
		if (blockMethod) ctx.report(Kind.ERROR, "block.return.error.outsideMethod");

		UnresolvedExprNode expr;
		if (!wr.nextIf(semicolon)) {
			expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE);

			var signature = (Signature) cw.mn.attrByName("Signature");
			ctx.inReturn = true;
			try {
				writeCast(expr.resolve(ctx), signature == null ? cw.mn.returnType() : signature.values.get(signature.values.size()-1));
			} finally {
				ctx.inReturn = false;
			}
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

		if (returnHook != null) {
			if ((sectionFlag&SF_SWITCH) != 0) ctx.report(Kind.ERROR, "block.switch.exprMode.returnHook");

			if (expr instanceof MultiReturn mr) {
				cw.ldc(mr.capacity());
				cw.invokeV("roj/compiler/runtime/ReturnStack", "toImmutable", "(I)Lroj/compiler/runtime/ReturnStack;");
			}

			returnHookUsed.add(cw.nextSegmentId());
			cw.jump(returnHook);
		} else {
			cw.one(rt.shiftedOpcode(IRETURN));
		}

		controlFlowTerminate();
	}

	private void _throw() throws ParseException {
		var expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON);
		if (expr == null) {
			ctx.report(Kind.ERROR, "noExpression");
		} else {
			writeCast(expr.resolve(ctx), T_EXCEPTION);
		}

		cw.one(ATHROW);
		controlFlowTerminate();
	}
	// endregion
	// region 条件判断: if / generic-condition
	private void _if() throws ParseException {
		Label ifFalse = condition(null, 0);

		if (constantHint < 0) skipBlockOrStatement();
		else blockOrStatement();

		if (!wr.nextIf(ELSE)) {
			cw.label(ifFalse);
			return;
		}

		Label end;
		if (constantHint == 0 && cw.isContinuousControlFlow()) {
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
	private Label condition(Label target, int mode) throws ParseException {
		boolean checkLSB = mode != /*is not FOR*/1;
		if (checkLSB) except(lParen);

		if (target == null) target = new Label();

		constantHint = 0;
		UnresolvedExprNode expr = ep.parse((checkLSB ? ExprParser.STOP_RSB|ExprParser.SKIP_RSB : ExprParser.STOP_SEMICOLON));
		if (expr == null) {
			if (checkLSB) ctx.report(Kind.ERROR, "noExpression");
		} else {
			int i = cw.beginJumpOn(mode == /*is WHILE*/2, target);

			ExprNode node = expr.resolve(ctx);
			if (node.isConstant() && node.type() == Type.std(Type.BOOLEAN)) {
				boolean flag = (boolean) node.constVal();
				constantHint = flag ? 1 : -1;
				cw.skipJumpOn(i);

				if (node.isKind(ExprNode.ExprKind.CONSTANT_WRITABLE)) {
					if (mode != 0) ctx.report(Kind.ERROR, "expr.constantWritable.ifOnly");
					node.write(cw, true);
				}
			} else {
				writeCast(node, Type.std(Type.BOOLEAN));
				cw.endJumpOn(i);
			}
		}

		if(!checkLSB) except(semicolon);
		return target;
	}
	// endregion
	// region 循环: for while do-while
	private void loopBody(@Nullable Label continueTo, @NotNull Label breakTo) throws ParseException {
		LabelNode info = immediateLabel;
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

				ExprNode iter = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);
				IType type = iter.type();
				IClass owner;
				TypeCast.Cast cast;

				breakTo = CodeWriter.newLabel();
				if (type.array() == 0) {
					if (type.isPrimitive()) {
						ctx.report(Kind.ERROR, "symbol.error.derefPrimitive", type);
						skipBlockOrStatement();
						return;
					}

					// 没有用到, 只是让它看起来一直有值
					if ((cast = ctx.castTo(type, new Generic("java/lang/Iterable", Collections.singletonList(lastVar.type)), 0)).type < 0) {
						skipBlockOrStatement();
						return;
					}

					owner = ctx.getClassOrArray(type);
					boolean foreach = ctx.classes.getResolveHelper(owner).isFastForeach(ctx.classes);
					if (concurrent || !foreach) {
						// iterable

						Variable _itr = newVar("@迭代器", new Type("java/util/Iterator"));
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
					_arr = newVar("@数组表达式的结果", type);
					iter.write(cw, false);
					cw.store(_arr);
				}
				Variable _i = newVar("@索引", Type.std(Type.INT));
				Variable _len = newVar("@数组长度", Type.std(Type.INT));

				// type[] __var = expr;
				// int __i = 0;
				// int __len = __var.length;
				cw.load(_arr);
				if (type.array() == 0) {
					MethodResult result = ctx.methodListOrReport(owner, "size").findMethod(ctx, type, Collections.emptyList(), 0);
					assert result != null;
					MethodResult.writeInvoke(result.method, ctx, cw);
				} else {
					cw.one(ARRAYLENGTH);
				}
				cw.store(_len);
				cw.one(ICONST_0);
				cw.store(_i);

				continueTo = cw.label();
				execLast = ctx.ep.newUnaryPost(inc, new LocalVariable(_i));

				// :continue_to
				cw.load(_i);
				cw.load(_len);
				cw.jump(IF_icmpge, breakTo);

				cw.load(_arr);
				cw.load(_i);
				if (type.array() == 0) {
					// 检查可能存在的override
					MethodResult result = ctx.methodListOrReport(owner, "get").findMethod(ctx, type, Collections.singletonList(Type.std(Type.INT)), 0);
					assert result != null;
					MethodResult.writeInvoke(result.method, ctx, cw);
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
			breakTo = condition(null, 1);
			execLast = ep.parse(ExprParser.STOP_RSB | ExprParser.SKIP_RSB);
		}

		nBreakTo = CodeWriter.newLabel();
		if (constantHint < 0) skipBlockOrStatement();
		else {
			loopBody(continueTo, nBreakTo);

			if (execLast != null) {
				Label ct1 = new Label(continueTo);

				continueTo.clear();
				cw.label(continueTo);
				execLast.resolve(ctx).write(cw, true);
				cw.jump(ct1);
			} else {
				cw.jump(continueTo);
			}
		}

		if (hasVar) endCodeBlock();

		cw.label(breakTo);

		// for-else (python语法) 如果在循环内使用break退出，则不会进入该分支
		// 如果循环正常结束，或从未开始，都会进入该分支
		if (wr.nextIf(ELSE)) {
			blockOrStatement();
			cw.label(nBreakTo);
		} else {
			cw.label(nBreakTo); // add to labels
			nBreakTo.set(breakTo);
		}
	}
	private void _while() throws ParseException {
		MethodWriter fork = cw.fork(), prev = cw;
		setCw(fork);
		Label head = condition(null, 2);
		setCw(prev);

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

				if (cw.isContinuousControlFlow())
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
		condition(continueTo, 2);
		if (constantHint > 0) cw.jump(continueTo);
		except(semicolon);

		// do {} while (false);
		// C的宏常这样使用……
		// 怎么，C没有代码块么，真是优优又越越啊
		cw.label(breakTo);
	}
	// endregion
	//region switch
	@NotNull
	public SwitchNode parseSwitch(boolean isExpr) throws ParseException {
		boolean tableFix = wr.current().val().equals("...switch");
		except(lParen);

		var sval = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);
		IType sType = sval.type();
		/* see switch(kind) for detail */
		int kind = tableFix ? 3 : 0;
		Function<String, LocalContext.Import> prevDFI = ctx.dynamicFieldImport, DFI = prevDFI;

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
					DFI = getFieldDFI(clazz, null, prevDFI);
					break;
				}

				var switchable = ctx.getAnnotation(clazz, clazz, "roj/compiler/api/Switchable", false);
				if (switchable != null) {
					kind = switchable.getBoolean("identity", true) ? 3 : 4;
					if (switchable.getBoolean("suggest", false)) {
						DFI = getFieldDFI(clazz, null, prevDFI);
					}
				} else {
					kind = -1;
				}
			}
		}

		Object cst = sval.isConstant() ? sval.constVal() : null;
		except(lBrace);

		List<ExprNode> labels = Helpers.cast(ctx.tmpAnnotations); labels.clear();
		MyHashSet<Object> labelDeDup = Helpers.cast(ctx.tmpSet); labelDeDup.clear();

		Label breakTo = new Label();
		MethodWriter tmp = cw;

		var branches = new SimpleList<SwitchNode.Case>();
		SwitchNode.Case nullBranch = null;

		/*
		 * error checking:
		 * 1  => type switch
		 * 2  => pattern switch
		 *
		 * 4  => legacy switch grammar
		 * 8  => new switch grammar
		 * 16 => grammar error reported
		 *
		 * 32 => non-constant error reported
		 *
		 * 64 => hasDefault
		 */
		int flags = 0;
		boolean lastBreak = false;

		loop:
		while (wr.hasNext()) {
			SwitchNode.Case kase;
			boolean match = false;
			boolean blockBegin = true;

			Word w = wr.next();
			skipVD:
			switch (w.type()) {
				default: throw wr.err("unexpected_2:"+w.val()+":block.except.switch");
				case rBrace: break loop;

				case CASE:
					labels.clear();
					boolean hasNull = false;
					do {
						var expr = ep.parse(ExprParser.STOP_COLON|ExprParser.STOP_COMMA|ExprParser.STOP_LAMBDA|ExprParser.CHECK_VARIABLE_DECLARE|ExprParser.NAE);

						if (expr instanceof VariableDeclare vd) {
							IType type = ctx.resolveType(vd.type);
							if (type.isPrimitive()) {
								ctx.report(Kind.ERROR, "type.primitiveNotAllowed");
								type = new Type("java/lang/Void");
							}

							for (Object o : labelDeDup) {
								if (o instanceof IType t1) {
									// 没有反向检查，因为本来就是一个一个instanceof
									if (ctx.parentListOrReport(ctx.getClassOrArray(t1)).containsValue(type.owner())) {
										ctx.report(Kind.ERROR, "block.switch.collisionType", t1, type);
									}
								}
							}
							ctx.castTo(type, sType, 0);
							labelDeDup.add(type);

							w = wr.next();
							// flag8: check type switch in case ':'
							flags |= 10;
							blockBegin = false;

							beginCodeBlock();
							var v = newVar(vd.name, type);
							v.hasValue = true;
							v.endPos++;
							kase = new SwitchNode.Case(v);
							break skipVD;
						}

						ctx.dynamicFieldImport = DFI;
						ExprNode node = expr.resolve(ctx);
						ctx.dynamicFieldImport = prevDFI;
						if (node.isConstant()) {
							if (node.constVal() == null) {
								if (sval.type().isPrimitive()) ctx.report(Kind.ERROR, "block.switch.nullfail");
								hasNull = true;
							}
							if (cst != null && cst.equals(node.constVal()))
								match = true;
						} else {
							if (kind < 3 && (flags&32) == 0) {
								ctx.report(Kind.ERROR, "block.switch.nonConstant");
								flags |= 32;
							}
						}
						if (!labelDeDup.add(node)) ctx.report(Kind.ERROR, "block.switch.duplicate", node);
						labels.add(node);

						flags |= 1;
					} while ((w = wr.next()).type() == comma);

					kase = new SwitchNode.Case(new SimpleList<>(labels));
					kase.lineNumber = wr.LN;

					if (hasNull) {
						if (nullBranch != null) ctx.report(Kind.ERROR, "block.switch.duplicate:null");
						nullBranch = kase;
					}
				break;
				case DEFAULT:
					w = wr.next();
					kase = new SwitchNode.Case((List<ExprNode>) null);
					match = true;
				break;
			}

			if (w.type() == colon) {
				flags |= 4;
			} else if (w.type() == lambda) {
				flags |= 8;
				if (blockBegin) beginCodeBlock();
			} else throw wr.err("unexpected_2:"+w.type()+":block.except.switchCase");

			// has 4, 8 but not 16
			if ((flags&28) == 12) {
				ctx.report(Kind.ERROR, "block.switch.mixedCase");
				flags |= 16; // error already thrown
			}

			setCw(tmp.fork());
			kase.block = cw;

			// default
			if (kase.labels == null && kase.variable == null) {
				if ((flags&64) != 0) ctx.report(Kind.ERROR, "block.switch.manyDefault");
				flags |= 64;
			}

			boolean parse = cst == null || match;
			if (parse) branches.add(kase);

			if (isExpr) {
				if (w.type() != lambda) ctx.report(Kind.ERROR, "block.switch.exprMode.legacy");
				kase.value = switchExprBlock(parse);
				// switchExpr必然返回一个值，所以不可能有lastBreak
			} else {
				lastBreak = switchBlock(breakTo, w.type() == lambda, parse);
			}
		}

		// 忽略最后一个分支的最后的break;
		var last = branches.getLast();
		if (lastBreak) last.block.replaceSegment(last.block.nextSegmentId()-1, new StaticSegment());

		setCw(tmp);

		// if switch will never continue
		// TODO check continuous control flow for EVERY subblock
		// if (defaultBranch && (flags&128) != 0 && cw.isContinuousControlFlow()) sectionFlag |= SF_BLOCK_END;

		boolean defaultBranch = (flags & 64) != 0;
		// 检测type switch和pattern switch的混用
		flags &= 3;
		mixedButOnlyNull:
		if (flags == 3) {
			fail:
			if (nullBranch != null && nullBranch.labels.size() == 1) {
				for (var branch : branches) {
					if (branch.labels != null && branch != nullBranch) break fail;
				}

				kind = -1;
				break mixedButOnlyNull;
			}

			ctx.report(Kind.ERROR, "block.switch.mixedCase");
		} else if (flags == 2) {
			kind = -1;
		} else if (kind == -1) {
			ctx.report(Kind.ERROR, "block.switch.incompatible", sType);
		}

		return new SwitchNode(sval, kind, cst, breakTo, branches, nullBranch, defaultBranch);
	}
	private void _switch() throws ParseException {writeSwitch(parseSwitch(false));}
	public void writeSwitch(SwitchNode result) {
		if (result.kind < 0) writePatternSwitch(result);
		else writeTypeSwitch(result);
	}
	private void writePatternSwitch(SwitchNode result) {
		/*
		 * switch (o) {
		 *         case Integer i -> i.doubleValue();
		 *         case Float f -> f.doubleValue();
		 *         case String s -> Double.parseDouble(s);
		 *         default -> 0d;
		 * };
		 */
		// class switch, must be final/sealed class to use getClass();
		ctx.report(Kind.ERROR, "Class switch 暂未实现");
	}
	private void writeTypeSwitch(SwitchNode result) {
		var branches = result.branches;
		var sval = result.sval;
		var breakTo = result.breakTo;

		smallOptimize:
		if (branches.size()-(result.defaultBranch?1:0) <= 1) {
			int size = branches.size();
			if (size == 0) {
				ctx.report(Kind.SEVERE_WARNING, "block.switch.noBranch");
				return;
			}

			SwitchNode.Case node;

			// 找任意case，如果没有取default
			do {
				node = branches.get(--size);
				if (node.labels != null) break;
			} while (size != 0);

			myBlock:
			if (result.cst != null) {
				node.block.writeTo(cw);
			} else {
				if (node.labels == null) {
					// default, 没找到任何case
					ctx.report(Kind.SEVERE_WARNING, "block.switch.noBranch");

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
				ExprNode cmp = ep.binary(equ, sval, node.labels.get(0)).resolve(ctx);

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

		switch (result.kind) {
			case 0 -> {// int
				writeCast(sval, Type.std(Type.INT));

				SwitchSegment sw = new SwitchSegment(0);
				sw.def = breakTo;

				cw.addSegment(sw);

				for (int i = 0; i < branches.size();) {
					var branch = branches.get(i++);

					Label pos = cw.label();
					branch.block.writeTo(cw);

					if (branch.labels == null) {
						sw.def = pos;
					} else {
						for (ExprNode label : branch.labels)
							sw.branch(((AnnValInt) label.constVal()).value, pos);
					}
				}

				if (sw.findBestCode() == LOOKUPSWITCH && branches.size() > 2)
					ctx.report(Kind.WARNING, "block.switch.lookupSwitch");
			}
			case 1 -> {// legacy string
				sval.write(cw, false);
				if (result.nullBranch != null) makeNullsafe(result.nullBranch, sval);
				switchString(branches, breakTo);
				ctx.report(Kind.WARNING, "block.switch.lookupSwitch");
			}
			case 2 -> {// enum
				ConstantData switchMap = switchEnum(branches);
				addGeneratedClass(switchMap);

				Label next = new Label();

				Label start = cw.label();
				cw.field(GETSTATIC, switchMap, 0);
				sval.write(cw, false);
				if (result.nullBranch != null) makeNullsafe(result.nullBranch, sval);

				cw.invoke(INVOKEVIRTUAL, "java/lang/Enum", "ordinal", "()I");
				cw.one(IALOAD);
				cw.jump(next);
				Label end = cw.label();

				cw.one(POP);
				cw.one(ICONST_0); // 0, default
				cw.label(next);
				cw.addException(start, end, end, "java/lang/ArrayIndexOutOfBoundsException");

				linearMapping(breakTo, branches);
			}
			case 3, 4 -> {// (Identity)HashMap
				ConstantData switchMap = switchExpr(branches, (byte) result.kind);
				addGeneratedClass(switchMap);

				cw.field(GETSTATIC, switchMap, 0);
				sval.write(cw, false);
				cw.invoke(INVOKEVIRTUAL, "roj/compiler/runtime/SwitchMap", "get", "(Ljava/lang/Object;)I");

				linearMapping(breakTo, branches);
			}
		}

		cw.label(breakTo);
	}

	// 以后可以用load constantDynamic实现？
	private int switchMapId;
	private void addGeneratedClass(ConstantData data) {
		CompileUnit owner = ctx.file;
		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_INNER_CLASS)) {
			var map = new InnerClasses.Item(data.name, owner.name, "SwitchMap"+switchMapId++, ACC_PRIVATE|ACC_STATIC|ACC_FINAL);
			owner.innerClasses().add(map);

			InnerClasses attr1 = new InnerClasses();
			attr1.classes.add(map);
			data.putAttr(attr1);

			var attr = new EnclosingMethod();
			attr.owner = owner.name;
			if (!ctx.method.name().startsWith("<")) {
				attr.name = ctx.method.name();
				attr.parameters = ctx.method.parameters();
				attr.returnType = ctx.method.returnType();
			}
			data.putAttr(attr);
		}

		if (ctx.classes.isSpecEnabled(CompilerSpec.NESTED_MEMBER)) owner.addNestMember(data);

		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_SOURCE_FILE)) {
			data.putAttr(new AttrString("SourceFile", owner.getSourceFile()));
		}

		ctx.classes.addGeneratedClass(data);
	}

	private void linearMapping(Label breakTo, List<SwitchNode.Case> branches) {
		SwitchSegment sw = new SwitchSegment(TABLESWITCH);
		sw.def = breakTo;

		cw.addSegment(sw);

		int j = 0;
		for (int i = 0; i < branches.size();) {
			var branch = branches.get(i++);

			Label pos;
			if (branch.location != null) cw.label(pos = branch.location);
			else pos = cw.label();

			branch.block.writeTo(cw);

			if (branch.labels == null) sw.def = pos;
			else sw.branch(++j, pos);
		}
	}
	private void makeNullsafe(SwitchNode.Case branch, ExprNode sval) {
		var labels = branch.labels;
		for (int i = 0; i < labels.size(); i++) {
			var n = (ExprNode) labels.get(i);
			if (n.isConstant() && n.constVal() == null) {
				labels.remove(i);
				break;
			}
		}

		beginCodeBlock();

		Variable xvar = newVar("@", sval.type());
		cw.one(DUP);
		cw.store(xvar);
		cw.jump(IFNULL, branch.location = new Label());
		cw.load(xvar);

		endCodeBlock();
	}
	private void switchString(List<SwitchNode.Case> branches, Label breakTo) {
		var v = newVar("@", new Type("java/lang/String"));
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
			var branch = branches.get(i++);
			Label pos = branch.location == null ? branch.location = new Label() : branch.location;

			if (branch.labels == null) {
				sw.def = pos;
			} else {
				for (ExprNode label : branch.labels) {
					String key = (String) label.constVal();
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

		// 如果要内联到上面的循环，条件判断比较麻烦，每个list1的长度都是1才可以，懒得做了
		for (int i = 0; i < branches.size();) {
			SwitchNode.Case branch = branches.get(i++);
			c.label(branch.location);
			branch.block.writeTo(c);
		}
	}
	@NotNull
	private ConstantData switchEnum(List<SwitchNode.Case> branches) {
		ConstantData sm = new ConstantData();
		sm.modifier |= ACC_SYNTHETIC;

		sm.name(ctx.file.name+"$"+ ++ctx.file._children);
		sm.newField(ACC_SYNTHETIC|ACC_STATIC|ACC_FINAL, "switchMap", "[I");

		sm.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		MethodWriter c = ctx.classes.createMethodPoet(sm, sm.methods.getLast());
		c.visitSize(3, 1);

		int size = 0;
		for (int i = 0; i < branches.size(); i++) {
			var labels = branches.get(i).labels;
			if (labels != null) size += labels.size();
		}
		c.ldc(size);
		c.newArray(ToPrimitiveArrayId('I'));
		c.field(PUTSTATIC, sm, 0);

		int i = 0;
		for (SwitchNode.Case branch : branches) {
			var labels = branch.labels;
			if (labels == null) continue;

			for (ExprNode o : labels) {
				Label start = c.label();

				// an enum constant
				c.field(GETSTATIC, sm, 0);
				o.write(c, false);
				c.invoke(INVOKEVIRTUAL, "java/lang/Enum", "ordinal", "()I");
				c.ldc(i);
				c.one(IASTORE);

				Label next = new Label();
				c.jump(next);

				Label end = c.label();
				c.one(POP);
				// 若类正常加载，还可能抛出ArrayIndexOutOfBoundsException
				// 但是我并无办法预测这个数组多大
				// 也许可以使用IntMap
				// 但是我已经有了SwitchMap，所以这不会是个todo，而只是注释
				c.addException(start, end, end, "java/lang/NoSuchFieldError");

				c.label(next);
			}

			i++;
		}

		c.one(Opcodes.RETURN);
		c.finish();
		c.mn.putAttr(new AttrUnknown("Code", c.bw.toByteArray()));
		return sm;
	}
	@NotNull
	private ConstantData switchExpr(List<SwitchNode.Case> branches, byte kind) {
		ConstantData sm = new ConstantData();
		sm.modifier |= ACC_SYNTHETIC;

		sm.name(ctx.file.name+"$"+ ++ctx.file._children);
		sm.newField(ACC_SYNTHETIC|ACC_STATIC|ACC_FINAL, "map", "Lroj/compiler/runtime/SwitchMap;");

		sm.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		MethodWriter c = ctx.classes.createMethodPoet(sm, sm.methods.getLast());
		c.visitSize(5, 1);

		int size = 0;
		for (SwitchNode.Case branch : branches) {
			var labels = branch.labels;
			if (labels != null) size += labels.size();
		}

		c.ldc(size);
		c.one(kind); // ICONST_0 => 3
		c.invokeS("roj/compiler/runtime/SwitchMap$Builder", "builder", "(IZ)Lroj/compiler/runtime/SwitchMap$Builder;");
		c.one(ASTORE_0);

		var lines = new LineNumberTable();

		int i = 1; // start from 1, 0 is default
		for (SwitchNode.Case branch : branches) {
			var labels = branch.labels;
			if (labels == null) continue;

			Label start = null;
			for (ExprNode o : labels) {
				if (start == null) lines.add(start = c.label(), branch.lineNumber);
				else start = c.label();

				c.one(ALOAD_0);
				// TODO write with cast (primitive), while this is better implemented via
				//  PrimitiveGenericHelper.forClass(ctx.classes.getClassInfo("...")).argumentType(Type.std(Type.INT)).make();
				//  and this will have a snippet: ;PGEN;I;...
				o.write(c, false);
				Label end = c.label();
				c.ldc(i);
				c.invokeV("roj/compiler/runtime/SwitchMap$Builder", "add", "(Ljava/lang/Object;I)Ljava/lang/Object;");

				Label handler = c.label();
				c.one(POP);
				c.addException(start, end, handler, null);
			}

			i++;
		}

		c.one(ALOAD_0);
		c.invokeV("roj/compiler/runtime/SwitchMap$Builder", "build", "()Lroj/compiler/runtime/SwitchMap;");
		c.field(PUTSTATIC, sm, 0);

		c.one(Opcodes.RETURN);
		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_LINE_NUMBERS)) {
			c.visitExceptions();
			c.visitAttributes();
			c.visitAttribute(lines);
		}
		c.finish();
		c.mn.putAttr(new AttrUnknown("Code", c.bw.toByteArray()));
		return sm;
	}

	@SuppressWarnings("fallthrough")
	private boolean switchBlock(Label endPoint, boolean newSwitch, boolean parse) throws ParseException {
		while (true) {
			if (parse) loopBody(null, endPoint);
			else skipBlockOrStatement();

			Word w = wr.next();
			wr.retractWord();

			switch (w.type()) {
				case CASE, DEFAULT, rBrace:
					boolean flow = cw.isContinuousControlFlow();
					if (newSwitch) {
						if (flow) cw.jump(endPoint);
						endCodeBlock();
						return flow;
					} else if (flow) ctx.report(Kind.WARNING, "block.switch.fallthrough");
				return cw.isJumpingTo(endPoint);
			}

			if (newSwitch) ctx.report(Kind.ERROR, "unexpected_2", w.val(), "block.except.switch");
		}
	}
	private ExprNode switchExprBlock(boolean parse) throws ParseException {
		ExprNode expr;

		if (parse) {
			Word w = wr.next();
			if (w.type() == THROW) {
				_throw();
				expr = null;
			} else if (w.type() == lBrace) {
				wr.retractWord();

				byte flag = sectionFlag;
				sectionFlag |= SF_SWITCH;

				try {
					blockV();
					// athrow
					if (!cw.isContinuousControlFlow()) return null;

					ctx.report(Kind.ERROR, "block.switch.exprMode.noYield");
					return NaE.RESOLVE_FAILED;
				} catch (OperationDone od) {
					expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE).resolve(ctx);
					controlFlowTerminate();
					except(rBrace);
				} finally {
					sectionFlag = flag;
				}

			} else  {
				wr.retractWord();

				expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE).resolve(ctx);
			}
		} else {
			skipBlockOrStatement();
			expr = null;
		}

		endCodeBlock();
		return expr;
	}
	// endregion
	// region 其它: yield synchronized with assert multiReturn var/const 变量定义
	private void _yield() throws ParseException {
		if ((sectionFlag&SF_SWITCH) != 0) {
			throw OperationDone.INSTANCE;
		} else {
			var expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE).resolve(ctx);

			ctx.report(Kind.ERROR, "block.switch.yield");
			// require Generator function
			// not implemented yet !
		}
	}

	private void _sync() throws ParseException {
		except(lParen);

		ExprNode node = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);

		if (node.type().isPrimitive()) {
			ctx.report(Kind.ERROR, "type.primitiveNotAllowed", node.type());
		}

		node.write(cw, false);

		beginCodeBlock();
		Variable syncVar = newVar("@", node.type());

		cw.one(DUP);
		cw.store(syncVar);
		cw.one(MONITORENTER);

		Label start = cw.label();

		except(lBrace);
		block();

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

	private void _with() throws ParseException {
		except(lParen);

		ExprNode node = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);

		if (node.type().isPrimitive()) {
			ctx.report(Kind.ERROR, "block.sync.unexpectedPrimitive", node.type());
		}

		beginCodeBlock();
		except(lBrace);

		Variable ref;
		if (node.isKind(ExprNode.ExprKind.LDC_CLASS)) ref = null;
		else if (node instanceof LocalVariable lv) {
			// trust user
			ref = lv.getVariable();
		} else {
			node.write(cw, false);
			ref = newVar("@", node.type());
			cw.store(ref);
		}

		IClass info = ctx.classes.getClassInfo(node.type().owner());

		var fi = ctx.dynamicFieldImport;
		var mi = ctx.dynamicMethodImport;

		ctx.dynamicFieldImport = getFieldDFI(info, ref, fi);
		ctx.dynamicMethodImport = name -> {
			ComponentList cl = ctx.methodListOrReport(info, name);
			if (cl != null) return new LocalContext.Import(info, name, ref == null ? null : new LocalVariable(ref));

			return mi == null ? null : mi.apply(name);
		};

		try {
			block();
		} finally {
			ctx.dynamicFieldImport = fi;
			ctx.dynamicMethodImport = mi;
		}
	}
	@NotNull
	private Function<String, LocalContext.Import> getFieldDFI(IClass info, Variable ref, Function<String, LocalContext.Import> prev) {
		return name -> {
			ComponentList cl = ctx.fieldListOrReport(info, name);
			if (cl != null) {
				FieldResult result = cl.findField(ctx, ref == null ? ComponentList.IN_STATIC : 0);
				if (result.error == null) return new LocalContext.Import(info, result.field.name(), ref == null ? null : new LocalVariable(ref));
			}

			return prev == null ? null : prev.apply(name);
		};
	}

	private void _assert() throws ParseException {
		if (ctx.classes.isSpecEnabled(CompilerSpec.DISABLE_ASSERT)) {
			ep.parse(ExprParser.STOP_SEMICOLON | ExprParser.SKIP_SEMICOLON | ExprParser.STOP_COLON | ExprParser.NAE);
			if (wr.nextIf(colon)) ep.parse(ExprParser.STOP_SEMICOLON | ExprParser.SKIP_SEMICOLON | ExprParser.NAE);
			return;
		}

		int assertEnabled = file.getAssertEnabled();

		cw.field(GETSTATIC, file, assertEnabled);
		Label assertDone = new Label();
		cw.jump(IFEQ, assertDone);

		ExprNode condition = ep.parse(ExprParser.STOP_SEMICOLON | ExprParser.SKIP_SEMICOLON | ExprParser.STOP_COLON | ExprParser.NAE).resolve(ctx);
		alwaysThrow: {
			if (condition.isConstant()) {
				if (condition.constVal() instanceof Boolean v) {
					if (v) {
						ctx.report(Kind.SEVERE_WARNING, "block.assert.constant");
					} else {
						break alwaysThrow;
					}
				}
			}
			writeCast(condition, Type.std(Type.BOOLEAN));
			cw.jump(IFNE, assertDone);
		}

		if (wr.nextIf(colon)) {
			ExprNode message = ep.parse(ExprParser.STOP_SEMICOLON | ExprParser.SKIP_SEMICOLON | ExprParser.NAE).resolve(ctx);

			int type = message.type().getActualType();
			switch (type) {
				case 'Z', 'C', 'I', 'J', 'F', 'D' -> {
					cw.clazz(Opcodes.NEW, "java/lang/AssertionError");
					cw.one(DUP);
					message.write(cw, false);
					cw.invoke(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "("+(char)type+")V");
					cw.one(ATHROW);
				}
				default -> {
					cw.clazz(Opcodes.NEW, "java/lang/AssertionError");
					cw.one(DUP);
					writeCast(message, LocalContext.OBJECT_TYPE);
					cw.invoke(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "(Ljava/lang/Object;)V");
					cw.one(ATHROW);
				}
			}
		} else {
			cw.clazz(Opcodes.NEW, "java/lang/AssertionError");
			cw.one(DUP);
			cw.invoke(INVOKESPECIAL, "java/lang/AssertionError", "<init>", "()V");
			cw.one(ATHROW);
		}

		cw.label(assertDone);
	}

	private boolean checkVarConst(boolean isFinal) throws ParseException {
		wr.mark();
		short type = wr.next().type();

		// 多返回值的语法糖
		if (type == lBracket) {
			wr.skip();
			_multiReturn();
			return true;
		}

		wr.retract();

		if (type == Word.LITERAL) {
			define(isFinal ? T_CONST : T_VAR);
			return true;
		}
		return false;
	}
	private void _multiReturn() throws ParseException {
		List<Object> variables = new SimpleList<>();

		Word w;
		do {
			String name = wr.except(Word.LITERAL).val();
			variables.add(name);
			w = wr.next();
		} while(w.type() == comma);
		if (w.type() != rBracket) throw wr.err("unexpected_2:"+w.val()+":block.except.multiReturn");

		wr.except(assign);

		var node = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE).resolve(ctx);
		IType type = node.type();
		if (!type.owner().equals("roj/compiler/runtime/ReturnStack") || ! (type instanceof Generic g)) {
			ctx.report(Kind.ERROR, "block.multiReturn.typeError");
			return;
		}

		List<IType> types = g.children;
		if (variables.size() != types.size()) {
			ctx.report(Kind.ERROR, "block.multiReturn.countError");
			return;
		}

		node.write(cw, false);
		cw.ldc(types.hashCode()); // 检测篡改 IncompatibleClassChangeError
		cw.invokeV("roj/compiler/runtime/ReturnStack", "forRead", "(I)Lroj/compiler/runtime/ReturnStack;");

		for (int i = 0; i < variables.size(); i++) {
			variables.set(i, newVar(variables.get(i).toString(), types.get(i)));
		}

		var tmp = newVar("@", new Type("roj/compiler/runtime/ReturnStack"));
		cw.store(tmp);

		for (int i = 0; i < variables.size(); i++) {
			Variable v = (Variable) variables.get(i);
			v.endPos++; // disable unused variable warning

			cw.load(tmp);

			int tc = v.type.getActualType();
			if (tc == 'L') {
				cw.invokeV("roj/compiler/runtime/ReturnStack", "getL", "()Ljava/lang/Object;");
				cw.clazz(CHECKCAST, v.type.rawType());
			} else {
				cw.invokeV("roj/compiler/runtime/ReturnStack", "get"+(char)tc, "()"+(char)tc);
			}

			cw.store(v);
		}

		regionNew.getLast().remove(tmp);
	}
	private void define(IType type) throws ParseException {
		int isFinal = 0;
		noResolve: {
			if (type == null) {
				isFinal = file._modifiers(wr, ACC_FINAL);
				type = file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC);
			} else if (";".equals(type.owner())) {
				isFinal = type.array();
				type = T_VAR;
				break noResolve;
			}

			ctx.resolveType(type);
		}

		Word w;
		do {
			var name = wr.except(Word.LITERAL).val();
			var var = new Variable(name, type);
			var.slot = fastSlot;
			var.startPos = var.endPos = wr.index;

			w = wr.next();
			if (w.type() == assign) {
				var.hasValue = true;
				if (isFinal != 0) var.isFinal = true;

				ExprNode node = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.STOP_COMMA|ExprParser.NAE).resolve(ctx);

				if (type == T_VAR) {
					var.isDynamic = true;
					type = var.type = node.type();
					if (Inferrer.hasUndefined(type)) {
						var g = (Generic) type;
						if (g.children.size() != 1 || g.children.get(0) != Asterisk.anyGeneric) {
							//ctx.report(Kind.ERROR, "invoke.noExact");
						}
					}
					node.write(cw, false);
				} else {
					writeCast(node, type);
				}

				cw.store(var);

				w = wr.next();
			} else {
				if (isFinal != 0) ctx.report(Kind.ERROR, "block.var.final");
				if (type == T_VAR) ctx.report(Kind.ERROR, "block.var.var");
			}

			if (null != variables.putIfAbsent(name, var))
				ctx.report(Kind.ERROR, "block.var.error.duplicate", name);

			if (!regionNew.isEmpty()) regionNew.getLast().add(var);
			fastSlot += type.rawType().length();
		} while (w.type() == comma);

		if (!ctx.classes.isSpecEnabled(CompilerSpec.OPTIONAL_SEMICOLON) && w.type() != semicolon)
			wr.unexpected(w.val(), ";");
	}
	//endregion

	private void writeCast(ExprNode node, IType type) {
		var r = ctx.castTo(node.type(), type, 0);
		if (r.type < 0) return;
		node.writeDyn(cw, r);
	}

	private void except(short id) throws ParseException {wr.except(id, byId(id));}

	private void setCw(MethodWriter cw) {wr.setCw(this.cw = cw);}
	public MethodWriter getCw() {return cw;}
}