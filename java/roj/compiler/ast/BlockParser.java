package roj.compiler.ast;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.IClass;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.*;
import roj.asm.insn.*;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.JavaLexer;
import roj.compiler.LavaFeatures;
import roj.compiler.api.Types;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.*;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.Inferrer;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.TypeCast;
import roj.compiler.runtime.RtUtil;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.config.Word;
import roj.config.data.CInt;
import roj.util.Helpers;

import java.util.*;
import java.util.function.Function;

import static roj.asm.Opcodes.*;
import static roj.asm.insn.AbstractCodeWriter.ToPrimitiveArrayId;
import static roj.compiler.JavaLexer.*;
import static roj.compiler.ast.GeneratorUtil.RETURNSTACK_TYPE;

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
	private static final Type T_VAR = Type.klass(";"), T_CONST = Type.klass(";", 1);

	private final LocalContext ctx;
	private final ExprParser ep;
	private final JavaLexer wr;

	private final VisMap visMap = new VisMap();
	@Nullable private MyHashSet<String> varInLoop;
	private final VarMapper varMapper = new VarMapper();
	private int paramSize;

	private CompileUnit file;
	private MethodWriter cw;
	private Type returnType;
	private IType returnTypeG;
	private boolean blockMethod;

	public BlockParser(LocalContext ctx) {
		this.ctx = ctx;
		this.ep = ctx.ep;
		this.wr = ctx.lexer;
	}

	public void reset() {
		this.visMap.clear();
		this.varMapper.clear();
		this.varInLoop = null;

		this.variables.clear();
		this.regionNew.clear();
		this.sectionFlag = 0;

		this.returnHook = null;
		this.returnHookUsed = null;
		this.breakHookUsed = null;
		this.checkedExceptions = null;
		this.deferNode = null;

		this.labels.clear();
		this.imBreak = this.imContinue = null;
		this.imPendingVis.clear();
		this.immediateLabel = null;

		this.switchMapId = 0;
		this.generatorEntry = null;
	}
	// for GC ()
	public void clear() {
		file = null;
		cw = null;
		returnType = null;
		returnTypeG = null;
	}

	// region 解析
	public void parseBlockMethod(CompileUnit file, MethodWriter mw) throws ParseException {
		blockMethod = true;
		this.file = file;
		ctx.setMethod(mw.mn);
		reset();

		paramSize = 0;
		returnTypeG = returnType = Type.primitive(Type.VOID);

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

		returnType = mn.returnType();
		Signature signature = (Signature) mn.attrByName("Signature");
		returnTypeG = signature == null ? returnType : signature.values.get(signature.values.size()-1);

		wr.next(); wr.retractWord();
		var cw = ctx.classes.createMethodWriter(file, mn);
		var lines = new LineNumberTable();
		if (mn.name().equals("<init>")) file.appendGlobalInit(cw, lines);
		wr.setLines(lines);
		setCw(cw);

		paramSize = (mn.modifier() & ACC_STATIC) == 0 ? 1 : 0;

		var flags = mn.parsedAttr(null, Attribute.MethodParameters);
		var sign = mn.parsedAttr(null, Attribute.SIGNATURE);
		var parameters = mn.parameters();
		for (int i = 0; i < parameters.size(); i++) {
			IType type = parameters.get(i);
			if (i < names.size()) {
				Variable var = newVar(names.get(i), sign != null ? sign.values.get(i) : type);
				if (flags != null && (flags.getFlag(i, 0)&ACC_FINAL) != 0) var.isFinal = true;
			} else {
				paramSize += type.rawType().length();
			}
		}

		parse0();

		wr.getLines(cw);
		return cw;
	}

	public MethodWriter parseGeneratorMethod(CompileUnit file, MethodNode mn, List<String> names, ClassNode generatorOwner, MethodNode generator) throws ParseException {
		blockMethod = true;
		this.file = file;
		ctx.setMethod(mn);
		reset();

		Signature signature = Objects.requireNonNull((Signature) mn.attrByName("Signature"));
		returnTypeG = ((Generic) signature.values.get(signature.values.size()-1)).children.get(0);
		returnType = returnTypeG.rawType();

		wr.next(); wr.retractWord();
		var cw = ctx.classes.createMethodWriter(generatorOwner, generator);
		var lines = new LineNumberTable();
		wr.setLines(lines);
		setCw(cw);

		cw.one(ALOAD_1);
		cw.one(ASTORE_0);

		paramSize = 1;
		generatorEntry = SwitchBlock.ofSwitch(TABLESWITCH);

		cw.one(ALOAD_0);
		cw.invokeV(RETURNSTACK_TYPE, "getI", "()I");
		cw.addSegment(generatorEntry);

		// INIT
		generatorEntry.branch(0, generatorEntry.def = cw.label());

		if (ctx.thisSlot != 0) {
			Variable that = newVar("_internal_this", Type.klass(mn.owner));
			that.isFinal = true;
			that.hasValue = true;

			cw.one(ALOAD_0);
			cw.invokeV(RETURNSTACK_TYPE, "getL", "()Ljava/lang/Object;");
			cw.clazz(CHECKCAST, that.type.rawType());
			cw.store(that);
		}

		var flags = mn.parsedAttr(null, Attribute.MethodParameters);
		var sign = mn.parsedAttr(null, Attribute.SIGNATURE);
		var parameters = mn.parameters();
		for (int i = 0; i < parameters.size(); i++) {
			Variable var = newVar(names.get(i), sign != null ? sign.values.get(i) : parameters.get(i));
			if (flags != null && (flags.getFlag(i, 0)&ACC_FINAL) != 0) var.isFinal = true;
			var.hasValue = true;

			cw.one(ALOAD_0);
			char vType = (char) var.type.getActualType();
			if (vType == Type.CLASS) {
				cw.invokeV(RETURNSTACK_TYPE, "getL", "()Ljava/lang/Object;");
				cw.clazz(CHECKCAST, var.type.rawType());
			} else {
				cw.invokeV(RETURNSTACK_TYPE, "get"+vType, "()"+vType);
			}
			cw.store(var);
		}

		parse0();

		cw.visitSizeMax(2, 2);
		cw.one(ALOAD_0);
		cw.invokeV(RETURNSTACK_TYPE, "forWrite", "()L"+RETURNSTACK_TYPE+";");
		cw.ldc(-1);
		cw.invokeV(RETURNSTACK_TYPE, "put", "(I)L"+RETURNSTACK_TYPE+";");
		cw.one(POP);
		cw.one(Opcodes.RETURN);

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
						if (generatorEntry == null)
							ctx.report(Kind.ERROR, "block.initializorCannotComplete");
					} else {
						if (returnType.type == Type.VOID) cw.one(Opcodes.RETURN);
						else ctx.report(Kind.ERROR, "block.missingReturnValue");
					}
				}
				break;
			}

			statement(w);
		}

		endCodeBlock();

		if (ctx.thisUsed) varMapper.reserve(0);
		for (Variable v : variables.values()) {
			if (v.endPos != 0) {
				varMapper.reserve(v.slot);
				if (v.type.rawType().length() > 1)
					varMapper.reserve(v.slot+1);
			}
		}
		int lvtSize = varMapper.map();

		// TODO 计算stackSize
		cw.visitSizeMax(10, Math.max(lvtSize, paramSize));
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
			case lBrace -> {
				if (imLabel != null) visMap.enter();
				blockV();
				if (imLabel != null) visMap.exit();
			}

			case TRY -> _try();
			case DEFER -> _defer();

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
				ctx.tmpAnnotations.clear();
				file.readAnnotations(ctx.tmpAnnotations);
				wr.next();
				file.resolveAnnotationTypes(ctx, ctx.tmpAnnotations);
				//TODO
				_BP_ResolveAnnotationType_2();
				continue;
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
					if (!regionNew.isEmpty()) regionNew.getLast().add(val);

					if (imLabel == null) {
						imLabel = new LabelNode(cw.label());
						imLabel.onBreak = new Label();
						visMap.blockHook(imLabel);
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
					if (node.hasFeature(ExprNode.ExprFeat.INVOKE_CONSTRUCTOR)) {
						ctx.not_invoke_constructor = false;
					}

					node.write(cw, true);

					if (!wr.nextIf(semicolon) && !ctx.classes.hasFeature(LavaFeatures.OPTIONAL_SEMICOLON)) {
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

		if (!ctx.tmpAnnotations.isEmpty()) {
			ctx.report(Kind.WARNING, "lavac不完全支持方法体中的注解" +ctx.tmpAnnotations);
			ctx.tmpAnnotations.clear();
		}

		return;
		}
	}

	private boolean getCompilerIntrinsic(String name, boolean def) {
		for (AnnotationPrimer annotation : ctx.tmpAnnotations) {
			String type = annotation.type();

			var self = ctx.classes.getAnnotationDescriptor(ctx.classes.getClassInfo(type));
			for (var entry : self.values.entrySet()) annotation.raw().putIfAbsent(entry.getKey(), entry.getValue());

			System.out.println("CompilerIntrinsic "+annotation);
			if (type.endsWith(name)) return annotation.getBool("value");
		}
		return def;
	}
	private void _BP_ResolveAnnotationType_2() {
		MyHashMap<String, Object> dup = ctx.tmpMap1, extra = ctx.tmpMap2;
		var missed = ctx.getTmpSet();

		var list = ctx.tmpAnnotations;
		for (int i = 0; i < list.size(); i++) {
			var a = list.get(i);
			ctx.errorReportIndex = a.pos;

			var type = ctx.classes.getClassInfo(a.type());
			var desc = ctx.classes.getAnnotationDescriptor(type);

			if (0 == (desc.applicableTo&AnnotationSelf.TYPE_USE)) {
				ctx.report(Kind.ERROR, "cu.annotation.notApplicable", type, "@@method_internal akka TYPE_USE@@");
			}

			if (desc.kind != AnnotationSelf.SOURCE) {
				ctx.report(Kind.ERROR, "cu.annotation.notApplicable", type, "@@method_internal@@");
			}

			dup.clear();
			extra.putAll(a.raw());

			for (Map.Entry<String, Type> entry : desc.types.entrySet()) {
				String name = entry.getKey();

				Object node = Helpers.cast(extra.remove(name));
				if (node instanceof ExprNode expr) a.raw().put(name, AnnotationPrimer.toAnnVal(ctx, expr, entry.getValue()));
				else if (node == null && !desc.values.containsKey(entry.getKey())) missed.add(name);
			}

			if (!extra.isEmpty()) ctx.report(Kind.ERROR, "cu.annotation.extra", type, extra.keySet());
			if (!missed.isEmpty()) ctx.report(Kind.ERROR, "cu.annotation.missing", type, missed);

			missed.clear();
			extra.clear();
		}

		dup.clear();
		extra.clear();
		ctx.errorReportIndex = -1;
		//ctx.classes.runAnnotationProcessor(this, annotated.getKey(), list);
	}

	// region Block, SectionFlag, 变量ID和作用域
	/**
	 * Unreachable statement检测
	 */
	private byte sectionFlag;
	private static final byte SF_BLOCK = 1, SF_SWITCH = 2, SF_FOREACH = 4;

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
			if (wr.next().type() != rBrace) ctx.report(Kind.ERROR, "block.unreachable");
			wr.retractWord();
		}
	}

	// 当前作用域新增的东西
	private final SimpleList<SimpleList<Object>> regionNew = new SimpleList<>();
	private final MyHashMap<String, Variable> variables = new MyHashMap<>();

	private void beginCodeBlock() {
		SimpleList<Object> list = new SimpleList<>();
		list.add(cw.__attrLabel());
		regionNew.add(list);
	}
	private void endCodeBlock() {
		var added = regionNew.pop();
		Label label = null;
		for (int i = 1; i < added.size(); i++) {
			Object var = added.get(i);
			if (var instanceof Variable v) {
				variables.remove(v.name);

				if (v.endPos == 0) {
					if (!v.name.startsWith("@")) {
						ctx.report(v.startPos, Kind.WARNING, "block.var.unused", v.name);
						v.slot = -1; // magic, 如果这个变量只赋值，那么它的赋值语句会变成pop(see LazyLoadStore)
						continue;
					}
				} else if (v.start != null) {
					if (!regionNew.isEmpty()) {
						assert v.end == null;
						if (label == null) label = cw.__attrLabel();
						v.end = label;
					}
					cw.addLVTEntry(v);
				}

				if (v.endPos <= v.startPos) v.endPos = v.startPos+1;
				varMapper.add(v);
			} else {
				labels.remove(var.toString());
			}
		}

		// TODO 如果一个变量在其赋值过的区域内被再次赋值，（无前向依赖），那么它可以拆分为赋值前和赋值后两个变量，以方便VarMapper优化
		// 这个还可以反向传播，不过太麻烦了，先不去管
		// 可能需要SSA转换
		// 或者LoopNode，但是因为它的自包含所以事实上要求任意类型的Node
		for (Variable value : variables.values()) {
			//value.takeLoopEnd = value.endPos;
		}
	}

	private Variable newVar(String name, IType type) {
		Variable v = new Variable(name, type);
		v.startPos = cw.bci();
		postDefineVar(type, v);
		return v;
	}
	private void postDefineVar(IType type, Variable v) {
		if (!v.name.startsWith("@")) {
			if (null != variables.putIfAbsent(v.name, v)) ctx.report(Kind.ERROR, "block.var.error.duplicate", v.name);
			if (ctx.classes.hasFeature(LavaFeatures.ATTR_LOCAL_VARIABLES)) v.start = cw.__attrLabel();
		} else {
			v.hasValue = true;
		}

		if (!regionNew.isEmpty()) regionNew.getLast().add(v);
		else {
			v.slot = paramSize;
			v.isParam = true;
			v.hasValue = true;
			paramSize += type.rawType().length();
		}
	}
	// endregion
	//region 变量使用 (ExprNode -> LocalContext 的回调)
	public MyHashMap<String, Variable> getVariables() {return variables;}
	public void loadVar(Variable v) {
		if (!visMap.hasValue(v)) {
			ctx.report(Kind.ERROR, "var.notAssigned", v.name);
		}
		if (varInLoop != null) varInLoop.add(v.name);
	}
	public void storeVar(Variable v) {
		if (v.refByNest) {
			ctx.report(Kind.NOTE, "var.refByNest", v.name);
			v.refByNest = false;
		}
		visMap.assign(v);
		if (varInLoop != null) varInLoop.add(v.name);
	}
	public void assignVar(Variable v, Object o) {
		visMap.assignWithValue(v);
		v.constantValue = o;
	}
	public Variable tempVar(IType type) {return newVar("@exprNode_temp", type);}
	//endregion
	// try-finally和synchronized重定向return/break需要的字段
	private Label returnHook;
	// TODO 嵌套的finally在return和break中也要按顺序正确处理，现在能做到吗？
	private IntList returnHookUsed, breakHookUsed;
	private SimpleList<IType> checkedExceptions;
	private TryNode deferNode;

	private SimpleList<Runnable> beforeExit;
	private void beforeExitPush(Runnable x) {beforeExit.add(x);}
	private void beforeExitPop() {beforeExit.pop();}
	// 也是回调 抓住能被try捕获的异常
	public boolean addException(IType type) {
		if (checkedExceptions != null) {
			checkedExceptions.add(type);
			return true;
		}
		return false;
	}
	// region 异常: try-catch-finally try-with-resource
	private void _try() throws ParseException {
		var prevHook = returnHook;
		var prevReturnHook = returnHookUsed;
		var prevBreakHook = breakHookUsed;
		var prevCheckedExceptions = checkedExceptions;
		var prevTryNode = deferNode;
		returnHook = new Label();
		returnHookUsed = new IntList();
		breakHookUsed = new IntList();
		checkedExceptions = new SimpleList<>();

		Word w = wr.next();

		// bit1: 存在【任意异常】处理程序(不能再有更多的catch)
		// bit2: 使用了AutoCloseable
		byte flag = 0;

		Label tryBegin = cw.label();

		var tryNode = deferNode = new TryNode();
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
						file.readModifiers(wr, FINAL);
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
							continue;
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
			visMap.enter();
			block();
		} else {
			visMap.enter();
			blockV();
		}

		deferNode = prevTryNode;
		// check for catch
		var _checkedExceptions = checkedExceptions;
		checkedExceptions = prevCheckedExceptions;

		Label tryEnd = cw.label();
		if (tryBegin.equals(tryEnd)) flag |= 4;

		Label blockEnd = new Label();
		boolean anyNormal = cw.isContinuousControlFlow(), nowNormal = anyNormal;

		if ((flag&2) != 0 || tryNode.hasDefer) {
			if (tryNode.hasDefer && tryNode.pos.get(0) == null)
				tryNode.pos.set(0, tryEnd);

			visMap.orElse();
			beginCodeBlock();

			var vars = tryNode.vars;
			// 如果所有变量都不是autocloseable类型（错误）
			if (!vars.isEmpty()) {
				for (int i = 0; i < vars.size(); i++) {
					if (vars.get(i) instanceof Variable v) {
						variables.remove(v.name);
						regionNew.getLast().add(v);
					}
				}

				closeResource(anyNormal, tryNode, blockEnd, tryEnd);
			}

			visMap.terminate();
			endCodeBlock();
		} else {
			if (anyNormal) cw.jump(blockEnd);
		}

		// region catch
		SimpleList<String> exTypes = new SimpleList<>();
		while ((w = wr.next()).type() == CATCH) {
			wr.except(lParen, "block.except.tryOrAuto");

			if ((flag&1) != 0) ctx.report(Kind.ERROR, "block.try.duplicateCatch");

			TryCatchEntry entry = cw.addException(tryBegin,tryEnd,cw.label(),null);
			visMap.orElse();
			beginCodeBlock();

			var type = file.readType(0);
			w = wr.next();
			if (w.type() == Word.LITERAL) {
				entry.type = ctx.resolveType(type).owner();

				if (!ctx.instanceOf(entry.type, "java/lang/Throwable")) {
					ctx.report(Kind.ERROR, "block.try.notException", type);
				}

				if (type.genericType() != IType.STANDARD_TYPE) {
					ctx.report(Kind.ERROR, "block.try.generic", type);
				}

				for (int i = 0; i < exTypes.size(); i++) {
					if (ctx.instanceOf(entry.type, exTypes.get(i))) {
						ctx.report(Kind.ERROR, "block.try.captured", type, exTypes.get(i));
						break;
					}
				}

				if (!ctx.classes.hasFeature(LavaFeatures.NO_CHECKED_EXCEPTION)
					&& !ctx.instanceOf(entry.type, "java/lang/RuntimeException")) {

					boolean notFound = true;
					for (int i = _checkedExceptions.size() - 1; i >= 0; i--) {
						if (ctx.instanceOf(_checkedExceptions.get(i).owner(), entry.type)) {
							_checkedExceptions.remove(i);
							notFound = false;
						}
					}

					if (notFound) ctx.report(Kind.ERROR, "block.try.cantThrow", entry.type);
				}

				exTypes.add(entry.type);

				if ("java/lang/Throwable".equals(entry.type)) flag |= 1;

				if (w.val().equals(VARIABLE_IGNORE)) cw.one(POP);
				else cw.store(newVar(w.val(), type));

				except(rParen);
			} else {
				if (w.type() != rParen) throw wr.err("block.except.name");

				//entry.type = TryCatchEntry.ANY;
				_checkedExceptions.clear();
				flag |= 1;
				cw.store(newVar(type.owner(), Types.THROWABLE_TYPE));
			}

			except(lBrace);
			if ((flag&4) != 0) {
				skipBlockOrStatement();
				endCodeBlock();
				continue;
			}
			block();

			nowNormal = cw.isContinuousControlFlow();
			if (nowNormal) {
				cw.jump(blockEnd);
				anyNormal = true;
			}
		}
		// endregion

		for (int i = 0; i < _checkedExceptions.size(); i++) {
			ctx.addException(_checkedExceptions.get(i));
		}

		Label hook = returnHook;
		IntList used = returnHookUsed;
		var breakHook = breakHookUsed;
		returnHook = prevHook;
		returnHookUsed = prevReturnHook;
		breakHookUsed = prevBreakHook;

		if (w.type() == FINALLY) {
			visMap.orElse();

			boolean disableOptimization = getCompilerIntrinsic("DisableOptimization", prevHook == null);

			beginCodeBlock();
			except(lBrace);

			Label finally_handler = new Label();
			Variable exc = newVar("@异常", Types.OBJECT_TYPE);

			cw.addException(tryBegin, cw.label(), finally_handler, TryCatchEntry.ANY);

			if (disableOptimization) {
				int bci = cw.bci();

				// 副本的 1/3: 异常处理
				int pos = wr.index;
				cw.label(finally_handler);
				cw.store(exc);

				MethodWriter tmp = cw.fork();
				MethodWriter prev = cw;

				setCw(tmp);
				block();
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

						_returnHook();
					}
				}

				// 副本的 n/3: break劫持
				for (int i = 0; i < breakHook.size(); i++) {
					var segment = (JumpBlock) cw.getSegment(breakHook.get(i));

					var target = segment.target;
					if (target.isValid() && target.compareTo(tryBegin) >= 0) continue;

					copyCount++;

					segment.target = cw.label();
					tmp.writeTo(cw);

					if (cw.isContinuousControlFlow()) cw.jump(target);
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
				Variable sri = newVar("@跳转自", Type.primitive(Type.INT));
				Variable rva = returnType.type == Type.VOID ? null : newVar("@返回值", Types.OBJECT_TYPE);
				Label real_finally_handler = new Label();
				int branchId = 0;

				// 副本的 1/3: 正常执行(可选)
				if (anyNormal) {
					if (nowNormal) // 删掉一个多余的跳转
						cw.replaceSegment(cw.nextSegmentId()-1, SolidBlock.EMPTY);

					cw.label(blockEnd);
					cw.ldc(branchId++);
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
					cw.ldc(branchId++);
					cw.store(sri);
					cw.one(ACONST_NULL);
					cw.store(exc);
					cw.jump(real_finally_handler);
				}

				// 副本的 n/3: break劫持
				ToIntMap<Label> breakHookId;
				if (breakHook.size() > 0) {
					breakHookId = new ToIntMap<>();
					breakHookId.setHasher(Hasher.identity());
					for (int i = 0; i < breakHook.size(); i++) {
						var segmentId = breakHook.get(i);
						Label target = ((JumpBlock) cw.getSegment(segmentId)).target;
						//  当它跳转到try外部时，才需要执行finally
						//   外部：Label未定义（后） or 在tryBegin之前
						//   同时,goto target替换成ldc + switch -> target
						if (target.isValid() && target.compareTo(tryBegin) >= 0) continue;

						int _branchId = breakHookId.getOrDefault(target, 0);
						if (_branchId == 0) breakHookId.putInt(target, _branchId = branchId++);

						var cw = this.cw.fork();
						cw.ldc(_branchId);
						cw.store(sri);
						if (rva != null) {
							cw.one(ACONST_NULL);
							cw.store(rva);
						}
						cw.one(ACONST_NULL);
						cw.store(exc);
						cw.jump(real_finally_handler);

						this.cw.replaceSegment(segmentId, cw);
					}
				} else {
					breakHookId = null;
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
				_finallyHookPostProc:
				if (cw.isContinuousControlFlow()) {
					if (breakHookId != null) {
						var segment = SwitchBlock.ofSwitch(TABLESWITCH);
						cw.load(sri);
						cw.addSegment(segment);

						segment.def = cw.label();
						cw.load(exc);
						cw.one(ATHROW);

						branchId = 0;
						if (anyNormal) segment.branch(branchId++, blockEnd = new Label());
						if (used.size() > 0) {
							segment.branch(branchId, cw.label());
							if (rva != null) cw.load(rva);
							_returnHook();
						}

						if (breakHookUsed != null) {
							for (var entry : breakHookId.selfEntrySet()) {
								segment.branch(entry.v, cw.label());
								breakHookUsed.add(cw.nextSegmentId());
								cw.jump(entry.k);
							}
						} else {

							for (var entry : breakHookId.selfEntrySet()) {
								segment.branch(entry.v, entry.k);
							}
						}

						break _finallyHookPostProc;
					}

					if (used.size() > 0) {
						Label returnHook = new Label();

						cw.load(sri);
						// 匹配branchId
						cw.jump(anyNormal ? IFLE : IFLT, returnHook);

						// sri > 0 : return hook
						if (rva != null) cw.load(rva);

						_returnHook();

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
				} else {
					// TODO 如果finally不会正常完成，就不要生成赋值和跳转语句 (延后创建到blockV之后）
				}
			}

			visMap.terminate();
		} else {
			wr.retractWord();
			if ((flag&2) == 0) {
				// 孤立的try
				if (exTypes.isEmpty() && (flag&1) == 0) ctx.report(Kind.ERROR, "block.try.noHandler");

				if (returnHookUsed != null) {
					returnHookUsed.addAll(used);
				} else {
					SolidBlock ret = new SolidBlock(returnType.shiftedOpcode(IRETURN));
					for (int i = 0; i < used.size(); i++) cw.replaceSegment(used.get(i), ret);
				}
			} else {
				cw.label(hook);
			}
		}

		visMap.exit();

		if (anyNormal) cw.label(blockEnd);
	}
	private void _returnHook() {
		if (returnHookUsed != null) {
			returnHookUsed.add(cw.nextSegmentId());
			cw.jump(returnHook);
		} else {
			cw.one(returnType.shiftedOpcode(IRETURN));
		}
	}

	private void _defer() throws ParseException {
		if (deferNode == null) ctx.report(Kind.ERROR, "block.try.noDefer");
		else deferNode.add((ExprNode) ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.STOP_RSB|ExprParser.NAE));
	}
	private void closeResource(boolean anyNormal, TryNode tn, Label blockEnd, Label tryEnd) {
		int moreSituations = 0;
		if (anyNormal) moreSituations++;
		if (returnHookUsed.size() > 0) moreSituations++;

		var sri = moreSituations < 2 ? null : newVar("@跳转自", Type.primitive(Type.INT));
		var exc = newVar("@异常", Types.OBJECT_TYPE);

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

		var vars = tn.vars;
		for (int i = 0; i < vars.size(); i++) {
			if (vars.get(i) instanceof ExprNode n)
				vars.set(i, n.resolve(ctx));
		}

		Label[] normalHandlers;
		if (moreSituations > 0) {
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
			var o = vars.get(i);

			noCatch:
			if (smallTwr && o instanceof Variable v) {
				cw.load(v);
				cw.invokeS(RtUtil.CLASS_NAME, "twr", "(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)Ljava/lang/Throwable;");
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
		var result = ctx.getMethodList(info, "close").findMethod(ctx, Collections.emptyList(), 0);
		assert result != null;

		if (report) result.addExceptions(ctx, true);

		cw.load(v);
		cw.invoke((result.method.modifier&ACC_INTERFACE) != 0 ? INVOKEINTERFACE : INVOKEVIRTUAL, result.method);
	}
	// endregion
	// 带名称的label
	private final MyHashMap<String, LabelNode> labels = new MyHashMap<>();
	// 对循环上标签的支持 (continue XX)
	private LabelNode immediateLabel;
	// 当前代码块可用的break和continue目标
	private Label imBreak, imContinue;
	// VisMap变量定义状态
	private final SimpleList<VisMap.State> imPendingVis = new SimpleList<>();
	// region 控制流终止: break continue goto return throw
	private void _goto() throws ParseException {
		var w = wr.except(Word.LITERAL);
		var info = labels.get(w.val());
		if (info != null && info.head != null) {
			if (breakHookUsed != null) breakHookUsed.add(cw.nextSegmentId());
			cw.jump(info.head);
			info.addState(visMap.jump());
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
		var errId = wr.current().val();

		if ((sectionFlag&SF_SWITCH) != 0) ctx.report(Kind.ERROR, "block.switch.exprMode", errId);

		var w = wr.next();
		if (w.type() == Word.LITERAL) {
			var info = labels.get(w.val());
			Label node;
			if (info != null && (node = isBreak ? info.onBreak : info.onContinue) != null) {
				if (breakHookUsed != null) breakHookUsed.add(cw.nextSegmentId());
				cw.jump(node);

				if (isBreak) info.addState(visMap.jump());
				else visMap.terminate();
			} else {
				ctx.report(Kind.ERROR, "block.goto.error.noSuchLabel", w.val());
			}

			except(semicolon);
		} else if (w.type() == semicolon) {
			Label node = isBreak ? imBreak : imContinue;
			if (node != null) {
				if (breakHookUsed != null) breakHookUsed.add(cw.nextSegmentId());
				cw.jump(node);

				if (isBreak) imPendingVis.add(visMap.jump());
				else visMap.terminate();
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
			expr = ep.parse(ExprParser._ENV_TYPED_ARRAY|ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE);

			ctx.inReturn = true;
			writeCast(expr.resolve(ctx), returnTypeG);
			ctx.inReturn = false;
		} else {
			expr = null;
		}

		var rt = returnType;
		if (rt.type == Type.VOID) {
			if (expr != null) {
				ctx.report(Kind.ERROR, "block.return.error.exceptVoid");
			}
		} else if (expr == null) {
			ctx.report(Kind.ERROR, "block.return.error.exceptExpr");
		}

		if (returnHookUsed != null) {
			if ((sectionFlag&SF_SWITCH) != 0) ctx.report(Kind.ERROR, "block.switch.exprMode.returnHook");

			if (RETURNSTACK_TYPE.equals(rt.owner))
				cw.invokeV(RETURNSTACK_TYPE, "toImmutable", "()L"+RETURNSTACK_TYPE+";");

			returnHookUsed.add(cw.nextSegmentId());
			cw.jump(returnHook);
		} else {
			cw.one(rt.shiftedOpcode(IRETURN));
		}

		visMap.terminate();
		controlFlowTerminate();
	}

	private void _throw() throws ParseException {
		var expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON);
		if (expr == null) {
			ctx.report(Kind.ERROR, "noExpression");
		} else {
			var node = expr.resolve(ctx);
			writeCast(node, Types.THROWABLE_TYPE);
			ctx.addException(node.type());
		}

		cw.one(ATHROW);
		visMap.terminate();
		controlFlowTerminate();
	}
	// endregion
	// region 条件判断: if / generic-condition
	private void _if() throws ParseException {
		Label ifFalse = condition(null, 0);

		visMap.enter();

		if (constantHint < 0) skipBlockOrStatement();
		else {
			blockOrStatement();
			visMap.orElse();
		}

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
		else {
			blockOrStatement();
			visMap.orElse();
		}
		if (end != null) cw.label(end);

		// if (xx) {} else if() {}
		//      is equivalent to
		// if (xx) {} else { if() {} }
		visMap.terminate();
		visMap.exit();
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
			ExprNode node = expr.resolve(ctx);
			if (node.isConstant() && node.type() == Type.primitive(Type.BOOLEAN)) {
				boolean flag = (boolean) node.constVal();
				constantHint = flag ? 1 : -1;

				if (node.hasFeature(ExprNode.ExprFeat.CONSTANT_WRITABLE)) {
					if (mode != 0) ctx.report(Kind.ERROR, "expr.constantWritable.ifOnly");
					node.write(cw, true);
				}
			} else {
				var r = ctx.castTo(node.type(), Type.primitive(Type.BOOLEAN), 0);
				if (r.type >= 0) node.writeShortCircuit(cw, r, mode == 2/*WHILE*/, target);
			}
		}

		if(!checkLSB) except(semicolon);
		return target;
	}
	// endregion
	// region 循环: for while do-while
	private void loopBody(@Nullable Label continueTo, @NotNull Label breakTo) throws ParseException {
		var info = immediateLabel;
		if (info != null) {
			info.onBreak = breakTo;
			info.onContinue = continueTo;
			immediateLabel = null;
		}

		Label prevBreak = imBreak, prevContinue = imContinue;
		int combineSize = imPendingVis.size();
		var prevCol = varInLoop; varInLoop = new MyHashSet<>();

		if (continueTo != null) imContinue = continueTo;
		imBreak = breakTo;

		blockOrStatement();

		// [20241207] 对于循环 如果一个在循环之外定义的变量的endPos在循环体中，那么将其提升至循环块末尾
		//		也许以后可以优化，不过目前生成LVT足够了，就是反编译器不认，这不是我的问题
		int endPos = wr.index;
		for (var name : varInLoop) {
			var v = variables.get(name);
			if (v != null) v.endPos = endPos;
		}
		if (prevCol != null) prevCol.addAll(varInLoop);
		varInLoop = prevCol;

		while (combineSize < imPendingVis.size()) {
			visMap.orElse(imPendingVis.pop());
		}
		imContinue = prevContinue;
		imBreak = prevBreak;
	}

	private void _for() throws ParseException {
		boolean disableOptimization = getCompilerIntrinsic("DisableOptimization", false);
		except(lParen);

		Word w = wr.next();
		boolean hasVar;
		Label continueTo, breakTo, nBreakTo;
		UnresolvedExprNode execLast;

		NoForEach:{
		if (w.type() != semicolon) {
			beginCodeBlock();
			sectionFlag |= SF_FOREACH;
			statement(w);
			sectionFlag &= ~SF_FOREACH;
			hasVar = true;

			// region ForEach for (Vtype vname : expr) {}
			if (wr.nextIf(colon)) {
				Variable lastVar = null;

				int varFound = 0;
				var rn = regionNew.getLast();
				for (int i = 0; i < rn.size(); i++) {
					if (rn.get(i) instanceof Variable v) {
						lastVar = v;
						if (varFound++ > 0)
							ctx.report(Kind.ERROR, "block.for.manyVariablesInForeach", varFound);
					}
				}
				assert lastVar != null;

				ExprNode iter = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);
				IType type = iter.type();
				IClass owner;
				TypeCast.Cast cast = null;

				breakTo = CodeWriter.newLabel();
				if (type.array() == 0) {
					if (type.isPrimitive()) {
						ctx.report(Kind.ERROR, "symbol.error.derefPrimitive", type);
						skipBlockOrStatement();
						return;
					}

					owner = ctx.getClassOrArray(type);
					int foreachType = ctx.classes.getResolveHelper(owner).getForeachType(ctx.classes);
					if (foreachType == 2) { // 2 : ListIterable
						var result = ctx.getMethodList(owner, "get").findMethod(ctx, type, Collections.singletonList(Type.primitive(Type.INT)), 0);
						if (result == null) {
							ctx.report(Kind.INTERNAL_ERROR, "ListIterable.get resolve failed");
							skipBlockOrStatement();
							return;
						}

						IType inferredType = result.desc == null ? result.method.returnType() : result.desc[1];

						if (lastVar.type == Asterisk.anyType) {
							lastVar.type = inferredType;
						} else {
							if (ctx.castTo(inferredType, lastVar.type, 0).type < 0) {
								skipBlockOrStatement();
								return;
							}
						}
					} else { // -1 : Iterator || 1 : RandomAccessList
						// 没有用到, 只是让它看起来一直有值
						if (ctx.castTo(type, new Generic("java/lang/Iterable", Collections.singletonList(lastVar.type)), 0).type < 0) {
							skipBlockOrStatement();
							return;
						}

						// var x : t 获取正确类型
						if (lastVar.type == Asterisk.anyType) lastVar.type = ctx.inferGeneric(type, "java/lang/Iterable").get(0);
					}

					// iterable
					if (disableOptimization || foreachType < 0) {
						Variable _itr = newVar("@迭代器", Types.ITERATOR_TYPE);
						iter.write(cw);
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
					owner = null; // checked below
				}

				Variable _arr;
				if (iter instanceof LocalVariable lv) {
					_arr = lv.getVariable();
				} else {
					_arr = newVar("@数组表达式的结果", type);
					iter.write(cw);
					cw.store(_arr);
				}
				Variable _i = newVar("@索引", Type.primitive(Type.INT));
				Variable _len = newVar("@数组长度", Type.primitive(Type.INT));

				// type[] __var = expr;
				// int __i = 0;
				// int __len = __var.length;
				cw.load(_arr);
				if (owner != null) {
					var result = ctx.getMethodList(owner, "size").findMethod(ctx, type, Collections.emptyList(), 0);
					if (result != null) MethodResult.writeInvoke(result.method, ctx, cw);
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
				if (owner != null) {
					// 检查可能存在的override
					var result = ctx.getMethodList(owner, "get").findMethod(ctx, type, Collections.singletonList(Type.primitive(Type.INT)), 0);
					if (result != null) {
						MethodResult.writeInvoke(result.method, ctx, cw);
						ctx.castTo(result.method.returnType(), lastVar.type, TypeCast.E_DOWNCAST).write(cw);
					}
				} else {
					cw.arrayLoad(type.rawType());
					cast.write(cw);
				}
				cw.store(lastVar);
				// type vname = __var[__i];

				_arr.endPos = cw.bci();
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

		visMap.enter();

		nBreakTo = CodeWriter.newLabel();
		if (constantHint < 0) skipBlockOrStatement();
		else {
			loopBody(continueTo, nBreakTo);
			boolean runRest = cw.isContinuousControlFlow();
			if (!runRest && !cw.willJumpTo(continueTo)) {
				ctx.report(Kind.WARNING, "block.loop.notLoop");
			} else if (execLast != null) {
				Label ct1 = new Label(continueTo);
				cw.__addLabel(ct1);

				continueTo.clear();
				cw.label(continueTo);
				execLast.resolve(ctx).write(cw, true);
				cw.jump(ct1);
			} else if (runRest) {
				cw.jump(continueTo);
			}
		}

		if (hasVar) endCodeBlock();

		cw.label(breakTo);

		visMap.orElse();

		// for-else (python语法) 如果在循环内使用break退出，则不会进入该分支
		// 如果循环正常结束，或从未开始，都会进入该分支
		if (wr.nextIf(ELSE)) {
			if (!cw.isContinuousControlFlow()) controlFlowTerminate();

			blockOrStatement();
			cw.label(nBreakTo);
		} else {
			cw.__addLabel(nBreakTo);
			nBreakTo.set(breakTo);

			if (!cw.isContinuousControlFlow()) controlFlowTerminate();
		}

		visMap.exit();
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

		visMap.enter();
		switch (constantHint) {
			case 0 -> {
				Label continueTo = new Label(), breakTo = new Label();
				cw.jump(continueTo);
				int i = cw.nextSegmentId();
				cw.label(head);

				loopBody(continueTo, breakTo);

				cw.label(continueTo);
				if (!cw.isImmediateBeforeContinuous(cw.nextSegmentId()) && !cw.willJumpTo(continueTo, i)) {
					ctx.report(Kind.WARNING, "block.loop.notLoop");
				}
				fork.writeTo(cw);
				cw.label(breakTo);

				visMap.orElse();
			}
			case 1 -> { // while true
				Label continueTo = new Label(), breakTo = new Label();
				cw.label(continueTo);

				loopBody(continueTo, breakTo);

				if (cw.isContinuousControlFlow()) cw.jump(continueTo);
				if (!cw.willJumpTo(continueTo)) ctx.report(Kind.WARNING, "block.loop.notLoop");
				cw.label(breakTo);
				if (!cw.isContinuousControlFlow()) controlFlowTerminate();
			}
			case -1 -> skipBlockOrStatement(); // while false
		}
		visMap.exit();
	}
	private void _doWhile() throws ParseException {
		// 这个不需要VisMap因为无论如何都会执行
		Label continueTo = new Label(), breakTo = new Label();
		var head = cw.label();

		visMap.enter();
		loopBody(continueTo, breakTo);

		cw.label(continueTo);
		boolean neverContinue = !cw.isContinuousControlFlow();

		except(WHILE);
		condition(head, 2);
		except(semicolon);
		// do {} while (false);
		// C的宏常这样使用……
		// 怎么，C没有代码块么，真是优优又越越啊
		if (constantHint > 0) cw.jump(head);
		cw.label(breakTo);
		if (!cw.isContinuousControlFlow()) {
			// condition() resets SF_BLOCK
			sectionFlag |= SF_BLOCK;
			controlFlowTerminate();
		} else if (neverContinue) ctx.report(Kind.WARNING, "block.loop.notLoop");

		visMap.exit();
	}
	// endregion
	//region switch
	@NotNull
	public SwitchNode parseSwitch(boolean isExpr) throws ParseException {
		boolean disableOptimization = getCompilerIntrinsic("DisableOptimization", false);
		except(lParen);

		var sval = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);
		IType sType = sval.type();
		/* see SwitchNode#kind javadoc */
		int kind = disableOptimization ? 0 : 3;
		Function<String, LocalContext.Import> prevDFI = ctx.dynamicFieldImport, DFI = prevDFI;

		switch (sType.getActualType()) {
			case Type.BOOLEAN, Type.VOID -> ctx.report(Kind.ERROR, "block.switch.incompatible", sType);
			case Type.LONG, Type.FLOAT, Type.DOUBLE -> kind = 3;
			case Type.CLASS -> {
				String owner = sType.owner();
				if (owner.equals("java/lang/Integer")) break;
				if (owner.equals("java/lang/String")) {
					if (disableOptimization) {
						ctx.report(Kind.NOTE, "block.switch.lookupSwitch");
						kind = 1;
					} else {
						kind = 4;
					}
					break;
				}

				IClass clazz = ctx.classes.getClassInfo(owner);
				if (ctx.instanceOf(owner, "java/lang/Enum")) {
					kind = disableOptimization ? 2 : 3;
					DFI = ctx.getFieldDFI(clazz, null, prevDFI);
					break;
				}

				var switchable = ctx.getAnnotation(clazz, clazz, "roj/compiler/api/Switchable", false);
				if (switchable != null) {
					kind = switchable.getBool("identity", true) ? 3 : 4;
					if (switchable.getBool("suggest", false)) {
						DFI = ctx.getFieldDFI(clazz, null, prevDFI);
					}
				} else {
					kind = -1;
				}
			}
		}

		Object cst = sval.isConstant() ? sval.constVal() : null;
		except(lBrace);

		List<ExprNode> labels = Helpers.cast(ctx.tmpAnnotations); labels.clear();
		MyHashSet<Object> labelDeDup = Helpers.cast(ctx.getTmpSet());

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
		 *
		 * 128 => isContinuousControlFlow ever return true
		 */
		int flags = 0;
		boolean lastBreak = false;

		visMap.enter();
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
								type = Types.VOID_TYPE;
							}

							for (Object o : labelDeDup) {
								if (o instanceof IType t1) {
									// 没有反向检查，因为本来就是一个一个instanceof
									if (ctx.getParentList(ctx.getClassOrArray(t1)).containsValue(type.owner())) {
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
							// 防止用case (a = b)之类的搞坏VisMap的预测
							if (!node.hasFeature(ExprNode.ExprFeat.STATIC_BEGIN) || (kind < 3 && (flags&32) == 0)) {
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
			if (lastBreak) flags |= 128;
			visMap.orElse();
		}

		// 忽略最后一个分支的最后的break;
		var last = branches.getLast();
		if (lastBreak) last.block.replaceSegment(last.block.nextSegmentId()-1, SolidBlock.EMPTY);

		setCw(tmp);

		visMap.terminate();
		visMap.exit();
		if ((flags&128) == 0) controlFlowTerminate();

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
	private void writePatternSwitch(SwitchNode node) {
		var sval = node.sval;
		var switchVal = newVar("@临时_switchValue", sval.type());
		sval.write(cw);
		cw.store(switchVal);

		var nullBranch = node.nullBranch;
		if (nullBranch != null) {
			var endOfBlock = new Label();

			cw.load(switchVal);
			cw.jump(IFNONNULL, endOfBlock);

			if (cw.lines != null) cw.lines.add(cw.label(), nullBranch.lineNumber);
			nullBranch.block.writeTo(cw);

			if (cw.isContinuousControlFlow())
				cw.jump(node.breakTo);

			cw.label(endOfBlock);
		}

		int defaultId = -1;
		for (int i = 0; i < node.branches.size(); i++) {
			var kase = node.branches.get(i);
			if (kase == nullBranch) continue;
			if (kase.variable == null) {
				defaultId = i;
				continue;
			}

			var endOfBlock = new Label();

			cw.load(switchVal);
			var vType = kase.variable.type.rawType();
			cw.clazz(Opcodes.INSTANCEOF, vType);
			cw.jump(IFEQ, endOfBlock);
			cw.load(switchVal);
			cw.clazz(CHECKCAST, vType);
			cw.store(kase.variable);

			if (cw.lines != null) cw.lines.add(cw.label(), kase.lineNumber);
			kase.block.writeTo(cw);

			if (cw.isContinuousControlFlow())
				cw.jump(node.breakTo);

			cw.label(endOfBlock);
		}

		if (defaultId >= 0) {
			var kase = node.branches.get(defaultId);

			if (cw.lines != null) cw.lines.add(cw.label(), kase.lineNumber);
			kase.block.writeTo(cw);

			if (cw.isContinuousControlFlow())
				cw.jump(node.breakTo);
		}

		cw.label(node.breakTo);
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

					sval.write(cw);
					// POP or POP2
					cw.one((byte) (0x56 + sval.type().rawType().length()));
					node.block.writeTo(cw);
					break myBlock;
				} else if (node.labels.size() > 1) {
					// 还是有超过一个case的
					break smallOptimize;
				}

				boolean shouldInvert = branches.size() != 1 && size != 0;
				// if
				ExprNode cmp = ep.binary(shouldInvert ? neq : equ, sval, node.labels.get(0)).resolve(ctx);

				Label ifNe = new Label();

				// 正确的，IFEQ对于Binary equ来说是0，是false，是不相等
				cmp.writeShortCircuit(cw, null, false, ifNe);

				if (shouldInvert) {
					branches.get(size^1).block.writeTo(cw);
					cw.label(ifNe);
					node.block.writeTo(cw);
				} else {
					node.block.writeTo(cw);
					cw.label(ifNe);
					if (branches.size() != 1) {
						branches.get(size^1).block.writeTo(cw);
					}
				}
			}

			cw.label(breakTo);
			return;
		}

		switch (result.kind) {
			case 0 -> {// int
				writeCast(sval, Type.primitive(Type.INT));

				SwitchBlock sw = SwitchBlock.ofAuto();
				sw.def = breakTo;

				cw.addSegment(sw);

				for (int i = 0; i < branches.size();) {
					var branch = branches.get(i++);

					Label pos = cw.label();
					branch.block.writeTo(cw);

					if (branch.labels == null) {
						sw.def = pos;
					} else {
						for (ExprNode label : branch.labels) {
							var tmp1 = label.constVal();
							int key;
							if (tmp1 instanceof CInt x) {
								key = x.value;
							} else if (tmp1 instanceof String s && s.length() == 1) {
								key = s.charAt(0);
								ctx.report(Kind.SEVERE_WARNING, "block.switch.stringAsIntLiteral");
							} else {
								int type = ctx.castTo(label.type(), Type.primitive(Type.INT), 0).type;
								assert type < 0;
								continue;
							}

							sw.branch(key, pos);
						}
					}
				}

				if (sw.findBestCode() == LOOKUPSWITCH && branches.size() > 2)
					ctx.report(Kind.WARNING, "block.switch.lookupSwitch");
			}
			case 1 -> {// legacy string
				sval.write(cw);
				if (result.nullBranch != null) makeNullsafe(result.nullBranch, sval);
				switchString(branches, breakTo);
				ctx.report(Kind.WARNING, "block.switch.lookupSwitch");
			}
			case 2 -> {// enum
				ClassNode switchMap = switchEnum(branches);
				addGeneratedClass(switchMap);

				Label next = new Label();

				Label start = cw.label();
				cw.field(GETSTATIC, switchMap, 0);
				sval.write(cw);
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
				ClassNode switchMap = switchExpr(branches, (byte) result.kind);
				addGeneratedClass(switchMap);

				cw.field(GETSTATIC, switchMap, 0);
				sval.write(cw);
				cw.invoke(INVOKEVIRTUAL, "roj/compiler/runtime/SwitchMap", "get", "(Ljava/lang/Object;)I");

				linearMapping(breakTo, branches);
			}
		}

		cw.label(breakTo);
	}

	// 以后可以用load constantDynamic实现？
	private int switchMapId;
	private void addGeneratedClass(ClassNode data) {
		CompileUnit owner = ctx.file;
		if (ctx.classes.hasFeature(LavaFeatures.ATTR_INNER_CLASS)) {
			var map = new InnerClasses.Item(data.name(), owner.name(), "SwitchMap"+switchMapId++, ACC_PRIVATE|ACC_STATIC|ACC_FINAL);
			owner.innerClasses().add(map);

			InnerClasses attr1 = new InnerClasses();
			attr1.classes.add(map);
			data.putAttr(attr1);

			var attr = new EnclosingMethod();
			attr.owner = owner.name();
			if (!ctx.method.name().startsWith("<")) {
				attr.name = ctx.method.name();
				attr.parameters = ctx.method.parameters();
				attr.returnType = ctx.method.returnType();
			}
			data.putAttr(attr);
		}

		if (ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER)) owner.addNestMember(data);

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_SOURCE_FILE)) {
			data.putAttr(new AttrString("SourceFile", owner.getSourceFile()));
		}

		ctx.classes.addGeneratedClass(data);
	}

	private void linearMapping(Label breakTo, List<SwitchNode.Case> branches) {
		SwitchBlock sw = SwitchBlock.ofSwitch(TABLESWITCH);
		sw.def = breakTo;

		cw.addSegment(sw);

		int j = 0;
		for (int i = 0; i < branches.size();) {
			var branch = branches.get(i++);

			Label pos;
			if (branch.tmpLoc != null) cw.label(pos = branch.tmpLoc);
			else pos = cw.label();

			branch.block.writeTo(cw);

			if (branch.labels == null) sw.def = pos;
			else sw.branch(++j, pos);
		}
	}
	private void makeNullsafe(SwitchNode.Case branch, ExprNode sval) {
		var labels = branch.labels;
		for (int i = 0; i < labels.size(); i++) {
			var n = labels.get(i);
			if (n.isConstant() && n.constVal() == null) {
				labels.remove(i);
				break;
			}
		}

		beginCodeBlock();

		Variable xvar = newVar("@", sval.type());
		cw.one(DUP);
		cw.store(xvar);
		cw.jump(IFNULL, branch.tmpLoc = new Label());
		cw.load(xvar);

		endCodeBlock();
	}
	private void switchString(List<SwitchNode.Case> branches, Label breakTo) {
		var v = newVar("@", Types.STRING_TYPE);
		var c = cw;

		c.one(DUP);
		c.store(v);
		c.invoke(INVOKESPECIAL, "java/lang/String", "hashCode", "()I");

		SwitchBlock sw = SwitchBlock.ofSwitch(LOOKUPSWITCH);
		c.addSegment(sw);
		sw.def = breakTo;


		// check duplicate
		IntMap<List<Map.Entry<String, Label>>> tmp = new IntMap<>();

		for (int i = 0; i < branches.size();) {
			var branch = branches.get(i++);
			Label pos = branch.tmpLoc == null ? branch.tmpLoc = new Label() : branch.tmpLoc;

			if (branch.labels == null) {
				sw.def = pos;
			} else {
				for (ExprNode label : branch.labels) {
					var tmp1 = label.constVal();
					String key;
					if (tmp1 instanceof String) {
						key = (String) tmp1;
					} else if (label.type().equals(Type.primitive(Type.CHAR))) {
						key = String.valueOf((char) ((CInt) tmp1).value);
					} else {
						int type = ctx.castTo(label.type(), Types.STRING_TYPE, 0).type;
						assert type < 0;
						continue;
					}
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
			c.label(branch.tmpLoc);
			branch.block.writeTo(c);
		}
	}
	@NotNull
	private ClassNode switchEnum(List<SwitchNode.Case> branches) {
		var sm = new ClassNode();
		sm.modifier |= ACC_SYNTHETIC;

		sm.name(ctx.file.name()+"$"+ ++ctx.file._children);
		sm.newField(ACC_SYNTHETIC|ACC_STATIC|ACC_FINAL, "switchMap", "[I");

		sm.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		var c = ctx.classes.createMethodWriter(sm, sm.methods.getLast());
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
				o.write(c);
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
	private ClassNode switchExpr(List<SwitchNode.Case> branches, byte kind) {
		var sm = new ClassNode();
		sm.modifier |= ACC_SYNTHETIC;

		sm.name(ctx.file.name()+"$"+ ++ctx.file._children);
		sm.newField(ACC_SYNTHETIC|ACC_STATIC|ACC_FINAL, "map", "Lroj/compiler/runtime/SwitchMap;");

		sm.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
		var c = ctx.classes.createMethodWriter(sm, sm.methods.getLast());
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
				o.write(c);
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
		if (ctx.classes.hasFeature(LavaFeatures.ATTR_LINE_NUMBERS)) {
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
					} else if (flow) {
						if (wr.next().type() != rBrace) ctx.report(Kind.WARNING, "block.switch.fallthrough");
						wr.retractWord();
					}
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
	private SwitchBlock generatorEntry;
	private void _yield() throws ParseException {
		if ((sectionFlag&SF_SWITCH) != 0) throw OperationDone.INSTANCE;

		var expr = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON|ExprParser.NAE);

		if (generatorEntry == null) {ctx.report(Kind.ERROR, "block.switch.yield");return;}

		int point = generatorEntry.targets.size();

		cw.one(ALOAD_0);
		cw.invokeV(RETURNSTACK_TYPE, "forWrite", "()L"+RETURNSTACK_TYPE+";");
		cw.ldc(point);
		cw.invokeV(RETURNSTACK_TYPE, "put", "(I)L"+RETURNSTACK_TYPE+";");

		writeCast(expr.resolve(ctx), returnTypeG);
		cw.one(ASTORE_0);

		int varType;
		for (var variable : variables.values()) {
			if (visMap.hasValue(variable)) {
				varType = variable.type.getActualType();

				cw.load(variable);
				cw.invokeV(RETURNSTACK_TYPE, "put", "("+(varType=='L'?"Ljava/lang/Object;":(char)varType)+")L"+RETURNSTACK_TYPE+";");
			}
		}

		varType = returnTypeG.getActualType();
		cw.one(ALOAD_0);
		cw.invokeV(RETURNSTACK_TYPE, "put", "("+(varType=='L'?"Ljava/lang/Object;":(char)varType)+")L"+RETURNSTACK_TYPE+";");

		cw.one(POP);
		cw.one(Opcodes.RETURN);

		generatorEntry.branch(point, cw.label());
		for (var variable : variables.values()) {
			if (visMap.hasValue(variable)) {
				varType = variable.type.getActualType();

				cw.one(ALOAD_0);
				if (varType == Type.CLASS) {
					cw.invokeV(RETURNSTACK_TYPE, "getL", "()Ljava/lang/Object;");
					cw.clazz(CHECKCAST, variable.type.rawType().getActualClass());
				} else {
					cw.invokeV(RETURNSTACK_TYPE, "get"+(char)varType, "()"+(char)varType);
				}
				cw.store(variable);
			}
		}
	}

	private void _sync() throws ParseException {
		except(lParen);

		ExprNode node = ep.parse(ExprParser.STOP_RSB|ExprParser.SKIP_RSB|ExprParser.NAE).resolve(ctx);

		if (node.type().isPrimitive()) {
			ctx.report(Kind.ERROR, "type.primitiveNotAllowed", node.type());
		}

		node.write(cw);

		beginCodeBlock();
		Variable syncVar = newVar("@", node.type());

		cw.one(DUP);
		cw.store(syncVar);
		cw.one(MONITORENTER);

		Label start = cw.label();

		beforeExitPush(() -> {
			cw.load(syncVar);
			cw.one(MONITOREXIT);
		});

		except(lBrace);
		block();

		beforeExitPop();

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
		if (node.hasFeature(ExprNode.ExprFeat.LDC_CLASS)) ref = null;
		else if (node instanceof LocalVariable lv) {
			// trust user
			ref = lv.getVariable();
		} else {
			node.write(cw);
			ref = newVar("@", node.type());
			ref.hasValue = true;
			cw.store(ref);
		}

		IClass info = ctx.classes.getClassInfo(node.type().owner());

		var prevDFI = ctx.dynamicFieldImport;
		var prevDMI = ctx.dynamicMethodImport;

		ctx.dynamicFieldImport = ctx.getFieldDFI(info, ref, prevDFI);
		ctx.dynamicMethodImport = name -> {
			var cl = ctx.getMethodList(info, name);
			if (cl != ComponentList.NOT_FOUND) return new LocalContext.Import(info, name, ref == null ? null : new LocalVariable(ref));

			return prevDMI == null ? null : prevDMI.apply(name);
		};

		try {
			block();
		} finally {
			ctx.dynamicFieldImport = prevDFI;
			ctx.dynamicMethodImport = prevDMI;
		}
	}

	private void _assert() throws ParseException {
		if (ctx.classes.hasFeature(LavaFeatures.DISABLE_ASSERT)) {
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
			writeCast(condition, Type.primitive(Type.BOOLEAN));
			cw.jump(IFNE, assertDone);
		}

		cw.clazz(Opcodes.NEW, "java/lang/AssertionError");
		cw.one(DUP);

		String desc;
		if (wr.nextIf(colon)) {
			ExprNode message = ep.parse(ExprParser.STOP_SEMICOLON | ExprParser.SKIP_SEMICOLON | ExprParser.NAE).resolve(ctx);

			//ctx.stackEnter(2);
			int type = message.type().getActualType();
			if (type == 'L') {
				writeCast(message, Types.OBJECT_TYPE);
				desc = "(Ljava/lang/Object;)V";
			} else {
				message.write(cw);
				desc = "("+(char)type+")V";
			}
		} else {
			desc = "()V";
		}

		cw.invoke(INVOKESPECIAL, "java/lang/AssertionError", "<init>", desc);
		cw.one(ATHROW);
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
		if (!type.owner().equals(RETURNSTACK_TYPE) || ! (type instanceof Generic g)) {
			ctx.report(Kind.ERROR, "block.multiReturn.typeError");
			return;
		}

		List<IType> types = g.children;
		if (variables.size() != types.size()) {
			ctx.report(Kind.ERROR, "block.multiReturn.countError");
			return;
		}

		node.write(cw);
		cw.ldc(types.hashCode()); // 检测篡改 IncompatibleClassChangeError
		cw.invokeV(RETURNSTACK_TYPE, "forRead", "(I)L"+RETURNSTACK_TYPE+";");

		for (int i = 0; i < variables.size(); i++) {
			var v = newVar(variables.get(i).toString(), types.get(i));
			v.hasValue = true;
			variables.set(i, v);
		}

		var tmp = newVar("@", Type.klass(RETURNSTACK_TYPE));
		cw.store(tmp);

		for (int i = 0; i < variables.size(); i++) {
			Variable v = (Variable) variables.get(i);
			v.endPos++; // disable unused variable warning

			cw.load(tmp);

			int tc = v.type.getActualType();
			if (tc == 'L') {
				cw.invokeV(RETURNSTACK_TYPE, "getL", "()Ljava/lang/Object;");
				cw.clazz(CHECKCAST, v.type.rawType());
			} else {
				cw.invokeV(RETURNSTACK_TYPE, "get"+(char)tc, "()"+(char)tc);
			}

			cw.store(v);
		}

		regionNew.getLast().pop();
	}
	private void define(IType type) throws ParseException {
		int isFinal = 0;
		noResolve: {
			if (type == null) {
				isFinal = file.readModifiers(wr, ACC_FINAL);
				type = file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC);
			} else if (";".equals(type.owner())) {
				isFinal = type.array();
				type = Asterisk.anyType;
				break noResolve;
			}

			ctx.resolveType(type);
		}

		Word w;
		do {
			var name = wr.except(Word.LITERAL).val();
			var var = new Variable(name, type);
			var.startPos = cw.bci();//wr.index;

			w = wr.next();
			if (w.type() == assign) {
				var.hasValue = true;
				if (isFinal != 0) var.isFinal = true;

				var node = ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.STOP_COMMA|ExprParser.NAE).resolve(ctx);

				if (type == Asterisk.anyType) {
					type = var.type = node.type();

					if (type instanceof Generic g && g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric) {
						var info = ctx.classes.getClassInfo(type.owner());
						var sign = info.parsedAttr(info.cp, Attribute.SIGNATURE);

						MyHashMap<String, IType> realType = new MyHashMap<>();
						Inferrer.fillDefaultTypeParam(sign.typeParams, realType);

						g.children.clear();
						for (List<IType> value : sign.typeParams.values()) {
							IType type1 = value.get(0);
							if (type1.genericType() == IType.PLACEHOLDER_TYPE) type1 = value.get(1);

							g.children.add(type1);
						}
						//System.out.println("Generic="+g);
					} else
					if (RETURNSTACK_TYPE.equals(type.owner()))
						ctx.report(Kind.WARNING, "multiReturn.sideEffect");
					node.write(cw);
				} else {
					var.type = writeCast(node, type);
				}

				cw.store(var);

				w = wr.next();
			} else {
				if ((sectionFlag&SF_FOREACH) != 0) {
					var.hasValue = true;
				} else {
					if (isFinal != 0) ctx.report(Kind.ERROR, "block.var.final");
					if (type == Asterisk.anyType) ctx.report(Kind.ERROR, "block.var.var");
					visMap.add(var);
				}
			}

			postDefineVar(type, var);
		} while (w.type() == comma);

		if (w.type() != semicolon) {
			if (!ctx.classes.hasFeature(LavaFeatures.OPTIONAL_SEMICOLON))
				wr.unexpected(w.val(), ";");
			wr.retractWord();
		}
	}
	//endregion

	private IType writeCast(ExprNode node, IType type) {
		var cast = ctx.castTo(node.type(), type, TypeCast.E_NEVER);
		if (cast.type < 0) {
			var override = ctx.getOperatorOverride(node, type, rParen);
			if (override == null) {
				ctx.report(Kind.ERROR, "typeCast.error."+cast.type, node.type(), type);
				return type;/*Might be <Unresolved>*/
			} else {
				override.write(cw);
				return override.type();
			}
		}
		node.write(cw, cast);
		return type;
	}

	private void except(short id) throws ParseException {wr.except(id, byId(id));}

	private void setCw(MethodWriter cw) {wr.setCw(this.cw = cw);}
}