package roj.compiler.context;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.*;
import roj.asm.annotation.AList;
import roj.asm.annotation.Annotation;
import roj.asm.attr.*;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstString;
import roj.asm.insn.*;
import roj.asm.type.*;
import roj.asm.util.ClassLike;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.LavaFeatures;
import roj.compiler.Tokens;
import roj.compiler.api.Types;
import roj.compiler.asm.*;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.diagnostic.Kind;
import roj.compiler.doc.Javadoc;
import roj.compiler.resolve.ImportList;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.NestContext;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.config.Word;
import roj.config.data.CEntry;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.compiler.Tokens.*;
import static roj.config.Word.LITERAL;

/**
 * Lava Compiler - 类Resolver<p>
 * Parser levels: <ol>
 *     <li><b><i>Class Parser</i></b></li>
 *     <li>{@link ParseTask Segment Parser}</li>
 *     <li>{@link BlockParser Method Parser}</li>
 *     <li>{@link ExprParser Expression Parser}</li>
 * </ol>
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public abstract class CompileUnit extends ClassNode implements ClassLike {
	/**
	 * 暂存不完整的类名 - S1
	 * XHashSet索引项 - All
	 */
	protected String name;

	@Override
	public void name(String name) {
		super.name(name);
		this.name = name;
	}

	private Object _next;

	// Class level flags
	protected static final int _ACC_RECORD = 1 << 31, _ACC_STRUCT = 1 << 30, _ACC_INNER_CLASS = 1 << 29;
	// read via _modifier()
	protected static final int _ACC_DEFAULT = 1 << 16, _ACC_ANNOTATION = 1 << 17, _ACC_SEALED = 1 << 18, _ACC_NON_SEALED = 1 << 19, _ACC_ASYNC = 1 << 21;
	protected static final int CLASS_ACC = ACC_PUBLIC|ACC_FINAL|ACC_ABSTRACT|ACC_STRICT|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED;
	protected static final int
		ACC_CLASS_ILLEGAL = ACC_NATIVE|ACC_TRANSIENT|ACC_VOLATILE|ACC_SYNCHRONIZED | _ACC_DEFAULT|_ACC_ASYNC,
		ACC_METHOD_ILLEGAL = ACC_TRANSIENT|ACC_VOLATILE,
		ACC_FIELD_ILLEGAL = ACC_STRICT|ACC_NATIVE|ACC_ABSTRACT | _ACC_DEFAULT|_ACC_ASYNC;

	protected final String filename;
	private final CharSequence code;

	protected final ImportList importList;

	protected int extraModifier;

	// 诊断的起始位置
	protected int classIdx;
	protected final IntList methodIdx = new IntList(), fieldIdx = new IntList();

	// Generic
	public LPSignature signature, currentNode;

	// Supplementary
	protected int miscFieldId;
	public final MyHashSet<FieldNode> finalFields = new MyHashSet<>(Hasher.identity());

	// code block task
	private final SimpleList<ParseTask> lazyTasks = new SimpleList<>();
	private final MyHashMap<Attributed, List<AnnotationPrimer>> annoTask = new LinkedMyHashMap<>();

	protected final void addParseTask(ParseTask task) {lazyTasks.add(task);}
	protected final void addAnnotations(Attributed node, List<AnnotationPrimer> list) {annoTask.put(node, list);}
	protected final void commitAnnotations(Attributed node) {
		if (!ctx.tmpAnnotations.isEmpty()) {
			annoTask.put(node, new SimpleList<>(ctx.tmpAnnotations));
			ctx.tmpAnnotations.clear();
		}
	}

	// inner class owner, equal this if not
	@NotNull
	protected final CompileUnit _parent;
	// anonymous class index
	protected int _children;

	protected LocalContext ctx;
	public void _setCtx(LocalContext ctx) {this.ctx = ctx;}

	public void setMinimumBinaryCompatibility(int level) {
		int javaVersion = JavaVersion(level);
		if (javaVersion > version) {
			version = javaVersion;
			if (level > ctx.classes.getMaximumBinaryCompatibility())
				ctx.report(Kind.ERROR, "cu.binaryIncompatible", level, ctx.classes.getMaximumBinaryCompatibility());
		}
	}

	public CompileUnit(String name, String code) {
		filename = name;
		this.code = code;
		importList = new ImportList();

		_parent = this;
	}
	public ImportList getImportList() {return importList;}
	public LocalContext lc() {return ctx;}
	public String getSourceFile() {return filename;}
	public CharSequence getCode() {return code;}

	protected void attachJavadoc(Attributed node) {
		Javadoc javadoc = ctx.lexer.javadoc;
		if (javadoc != null) {
			ctx.lexer.javadoc = null;
			ctx.classes.createJavadocProcessor(javadoc, this).attach(node);
		}
	}

	// region 文件中的其余类
	protected CompileUnit(CompileUnit parent, boolean helperClass) {
		filename = parent.filename;
		ctx = parent.ctx;

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_SOURCE_FILE))
			addAttribute(parent.getRawAttribute("SourceFile"));

		_parent = helperClass ? this : parent;

		// [20241206] temporary workaround for Macro & NewAnonymousClass
		code = parent.ctx.lexer.getText();
		classIdx = parent.ctx.lexer.index;

		importList = parent.importList;
	}

	public abstract CompileUnit newAnonymousClass(@Nullable MethodNode mn) throws ParseException;
	public final ClassNode newAnonymousClass_NoBody(@Nullable MethodNode mn, @Nullable InnerClasses.Item desc) {
		var c = new ClassNode();

		c.name(IOUtil.getSharedCharBuf().append(name).append('$').append(++_children).toString());
		c.modifier = ACC_FINAL|ACC_SUPER;

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_INNER_CLASS)) {
			if (desc == null) desc = InnerClasses.Item.anonymous(c.name(), ACC_PRIVATE | ACC_STATIC | ACC_FINAL);
			else desc.self = c.name();
			this.innerClasses().add(desc);

			var ic = new InnerClasses();
			ic.classes.add(desc);
			c.addAttribute(ic);

			var ownerMethod = new EnclosingMethod();
			ownerMethod.owner = name;
			if (mn != null && !mn.name().startsWith("<")) {
				ownerMethod.name = mn.name();
				ownerMethod.parameters = mn.parameters();
				ownerMethod.returnType = mn.returnType();
			}
			c.addAttribute(ownerMethod);
		}

		if (ctx.classes.getMaximumBinaryCompatibility() >= LavaFeatures.JAVA_11)
			addNestMember(c);

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_SOURCE_FILE))
			c.addAttribute(getRawAttribute("SourceFile"));

		ctx.classes.addGeneratedClass(c);
		return c;
	}

	public final void addNestMember(ClassNode c) {
		assert ctx.classes.getMaximumBinaryCompatibility() >= LavaFeatures.JAVA_11;

		var top = _parent;
		while (top._parent != top) top = top._parent;

		if (c instanceof CompileUnit cu) cu.setMinimumBinaryCompatibility(LavaFeatures.JAVA_11);
		top.setMinimumBinaryCompatibility(LavaFeatures.JAVA_11);

		c.addAttribute(new StringAttribute("NestHost", top.name));
		ClassListAttribute nestMembers = (ClassListAttribute) top.getRawAttribute("NestMembers");
		if (nestMembers == null) top.addAttribute(nestMembers = new ClassListAttribute(Attribute.NestMembers));
		nestMembers.value.add(c.name());
	}
	public final List<InnerClasses.Item> innerClasses() {
		InnerClasses c = getAttribute(cp, Attribute.InnerClasses);
		if (c == null) addAttribute(c = new InnerClasses());
		return c.classes;
	}
	// endregion
	public abstract boolean S1_Struct() throws ParseException;
	// region 阶段1 类的结构 辅助方法 resolve MODIFIER TYPE GENERIC ANNOTATION
	public final int readModifiers(Tokens wr, int mask) throws ParseException {
		if ((mask & _ACC_ANNOTATION) != 0) ctx.tmpAnnotations.clear();

		Word w;
		int acc = 0;
		while (true) {
			int f;
			w = wr.next();
			switch (w.type()) {
				case at -> {
					if ((mask & _ACC_ANNOTATION) == 0) ctx.report(Kind.ERROR, "modifier.annotation");
					readAnnotations(ctx.tmpAnnotations);
					acc |= _ACC_ANNOTATION;
					continue;
				}
				// n << 25 => conflict mask
				case PUBLIC -> 		f = (        1 << 25) | ACC_PUBLIC;
				case PROTECTED -> 	f = (        1 << 25) | ACC_PROTECTED;
				case PRIVATE -> 	f = (     0b11 << 25) | ACC_PRIVATE;
				case NATIVE -> 		f = (    0b100 << 25) | ACC_NATIVE;
				case SYNCHRONIZED ->f = (   0b1000 << 25) | ACC_SYNCHRONIZED;
				case FINAL -> 		f = (0b1010000 << 25) | ACC_FINAL;
				case STATIC -> 		f = ( 0b100000 << 25) | ACC_STATIC;
				case CONST -> 		f = ( 0b110001 << 25) | ACC_PUBLIC | ACC_STATIC | ACC_FINAL;
				case DEFAULT -> 	f = ( 0b110110 << 25) | _ACC_DEFAULT;
				case ABSTRACT -> 	f = ( 0b111110 << 25) | ACC_ABSTRACT;
				case SEALED ->      f = (0b1000000 << 25) | _ACC_SEALED;
				case NON_SEALED ->  f = (0b1000000 << 25) | _ACC_NON_SEALED;
				case STRICTFP -> 	f = ACC_STRICT; // on method, cannot be used with abstract
				case VOLATILE -> 	f = ACC_VOLATILE;
				case TRANSIENT -> 	f = ACC_TRANSIENT;
				case ASYNC ->       f = _ACC_ASYNC;
				default -> {
					wr.retractWord();
					return acc & ((1<<25) - 1);
				}
			}

			if ((f & mask) == 0) {
				ctx.report(Kind.ERROR, "modifier.notAllowed", w.val());
				continue;
			}

			if ((acc & f) != 0) {
				ctx.report(Kind.ERROR, "modifier.conflict", w.val(), showModifiers(acc, ACC_SHOW_METHOD));
				continue;
			}
			acc |= f;
		}
	}

	public static final int TYPE_PRIMITIVE = 1, TYPE_GENERIC = 2, TYPE_NO_ARRAY = 4, TYPE_ALLOW_VOID = 8;
	protected static final int GENERIC_INNER = 8, SKIP_TYPE_PARAM = 16;
	private static final int GENERIC_TERMINATE = 32;
	// this function only invoke on Stage 4
	public final IType readType(@MagicConstant(flags = {TYPE_PRIMITIVE, TYPE_GENERIC, TYPE_NO_ARRAY, TYPE_ALLOW_VOID}) int flags) throws ParseException {
		IType type = readType(ctx.lexer, flags);
		if (currentNode != null && type instanceof LPGeneric g)
			return currentNode.applyTypeParam(g);
		return type;
	}
	/**
	 * 解析类型
	 */
	protected final IType readType(Tokens wr, int flags) throws ParseException {
		Word w = wr.next();
		IType type = FastPrimitive.get(w.type());

		if (type == null) {
			if (w.type() != LITERAL) throw wr.err("type.illegalType0\1"+w.val()+"\0");
			wr.retractWord();

			String klass = readRef();

			if ((flags&TYPE_GENERIC) != 0) {
				int prev = wr.state;
				wr.state = STATE_TYPE;

				try {
					w = wr.next();
					if (w.type() == lss) {
						return readGeneric(klass, w, flags&(TYPE_NO_ARRAY|GENERIC_INNER));
					}
					wr.retractWord();
				} finally {
					wr.state = prev;
				}
			}

			type = currentNode == null || !currentNode.isTypeParam(klass) ? Type.klass(klass) : (flags & SKIP_TYPE_PARAM) != 0 ? new LPGeneric(klass) : new TypeParam(klass);
		}

		if ((flags & TYPE_NO_ARRAY) == 0) {
			int arrLen = 0;
			while (wr.nextIf(lBracket)) {
				arrLen++;
				wr.except(rBracket);
			}

			if (arrLen > 0) {
				if (arrLen > 255) throw wr.err("type.arrayDepth");
				if (type.isPrimitive()) {
					if (type.rawType().type == Type.VOID && (flags&TYPE_ALLOW_VOID) == 0)
						ctx.report(Kind.ERROR, "type.voidNotAllowed");
					type = type.clone();
				}
				type.setArrayDim(arrLen);
				return type;
			}
		}

		if (type.isPrimitive()) {
			if ((flags & TYPE_PRIMITIVE) == 0)
				ctx.report(Kind.ERROR, "type.primitiveNotAllowed");
			if (type.rawType().type == Type.VOID && (flags&TYPE_ALLOW_VOID) == 0)
				ctx.report(Kind.ERROR, "type.voidNotAllowed");
		}
		return type;
	}
	private static final IntMap<Type> FastPrimitive = ExprParser.getPrimitiveWords();

	protected final String readRef() throws ParseException {return readRef(false).toString();}
	/**
	 * 解析引用类型 (a.b.c)
	 * @param allowStar allow * (in import)
	 */
	protected final CharList readRef(boolean allowStar) throws ParseException {
		var wr = ctx.lexer;
		var sb = ctx.getTmpSb();

		while (true) {
			Word w = wr.next();
			if (w.type() == LITERAL) {
				sb.append(w.val());
			} else if (allowStar && w.type() == mul) {
				sb.append("*");
				break;
			} else {
				throw wr.err("unexpected\1"+w.val()+"\0");
			}

			if (!wr.nextIf(dot)) break;
			sb.append('/');
		}

		return sb;
	}

	public final IType readGenericPart(String type) throws ParseException {
		int prev = ctx.lexer.setState(STATE_TYPE);
		try {
			return readGeneric(type, ctx.lexer.current(), GENERIC_INNER);
		} finally {
			ctx.lexer.state = prev;
		}
	}
	/**
	 * 解析泛型部件 (从&lt;开始，不包括&lt;)
	 */
	private IType readGeneric(String type, Word w, int flags) throws ParseException {
		var wr = ctx.lexer;

		var g = new LPGeneric();
		g.pos = wr.index;
		g.owner = type;

		if (w.type() == lss) {
			if (wr.nextIf(gtr)) {
				if ((flags&GENERIC_INNER) != 0) ctx.report(Kind.ERROR, "type.illegalAnyType");
				else g.addChild(Asterisk.anyGeneric);

				flags |= GENERIC_TERMINATE;
			} else {
				do {
					byte extendType = 0;
					// <? extends|super Type
					if (wr.nextIf(ask)) {
						w = wr.next();
						switch (w.type()) {
							// A<? super
							case SUPER: extendType = Generic.EX_SUPER; break;
							case EXTENDS: extendType = Generic.EX_EXTENDS; break;
							// A<?,
							case comma, gtr: g.addChild(Signature.any()); continue;
							default: wr.unexpected(w.val(), "type.except.afterAsk");
						}
					}

					// A <Type>
					var child = readType(wr, (extendType == 0 ? TYPE_PRIMITIVE|TYPE_GENERIC|GENERIC_INNER : TYPE_GENERIC|GENERIC_INNER));

					if (extendType != 0) {
						if (child instanceof LPGeneric gp) {
							gp.extendType = extendType;
						} else if (child instanceof TypeParam tp) {
							tp.extendType = extendType;
						} else {
							var gp = new LPGeneric();
							gp.extendType = extendType;
							gp.owner = child.owner();
							gp.setArrayDim(child.array());

							child = gp;
						}
					}

					g.addChild(child);

					w = wr.next();
				} while (w.type() == comma);

				if (w.type() != gtr) wr.unexpected(w.val(), "type.except.afterLss");
			}

			w = wr.next();
		}

		// GenericSub
		if ((flags & GENERIC_TERMINATE) == 0 && w.type() == dot) {
			IType sub = readGeneric(wr.except(LITERAL).val(), wr.next(), TYPE_NO_ARRAY);
			// 无法从参数化的类型中选择静态类
			if (!(sub instanceof LPGeneric gp)) ctx.report(Kind.ERROR, "type.partialGenericSub", sub);
			else {
				if (gp.extendType != Generic.EX_NONE) ctx.report(Kind.ERROR, "type.illegalSub", sub);
				else g.sub = new GenericSub(gp.owner, gp.children);
			}

			w = wr.next();
		}

		if ((flags & TYPE_NO_ARRAY) == 0) {
			int arrLen = 0;
			while (w.type() == lBracket) {
				arrLen++;
				wr.except(rBracket);
				w = wr.next();
			}

			g.setArrayDim(arrLen);
		}

		wr.retractWord();
		// TODO 我记得以前写了个方法？不过我现在没有IDE
		return g.children.isEmpty() && g.sub == null ? Type.klass(g.owner, g.array()) : g;
	}

	protected final LPSignature makeSignature() {
		if (currentNode == null) currentNode = new LPSignature(0);
		return currentNode;
	}
	protected final LPSignature finishSignature(LPSignature parent, int kind, Attributed attr) {
		var sign = currentNode;
		if (sign == null) return null;
		currentNode = null;

		sign.parent = parent;
		sign.type = (byte) kind;

		sign.applyTypeParam(attr instanceof MethodNode mn ? mn : null);
		if (attr != null) attr.addAttribute(sign);
		return sign;
	}

	// <T extends YYY<T, V> & ZZZ, V extends T & XXX>
	protected final void genericDecl(Tokens wr) throws ParseException {
		var s = makeSignature();
		List<IType> bounds = Helpers.cast(ctx.tmpList);

		Word w;
		do {
			w = wr.next();
			if (w.type() != LITERAL) throw wr.err("type.illegalAnyType");

			String name = w.val();
			bounds.clear();

			short id = EXTENDS;
			while (true) {
				w = wr.next();
				if (w.type() != id) break;

				IType g = readType(wr, TYPE_GENERIC);
				bounds.add(g);

				id = and;
			}

			s.typeParams.put(name, bounds.isEmpty() ? LPSignature.UNBOUNDED_TYPE_PARAM : Helpers.cast(Arrays.asList(bounds.toArray())));
		} while (w.type() == comma);

		if (w.type() != gtr) wr.unexpected(w.val(), "type.except.afterLss");
	}

	public final List<AnnotationPrimer> readAnnotations(List<AnnotationPrimer> list) throws ParseException {
		Tokens wr = ctx.lexer;

		while (true) {
			int pos = wr.index;

			var a = new AnnotationPrimer(readRef(), pos+1);
			// 允许忽略注解
			if (list != Collections.EMPTY_LIST) list.add(a);

			Word w = wr.next();
			if (w.type() != lParen) {
				if (w.type() == at) continue;

				wr.retractWord();
				return list;
			}

			wr.mark();

			String name = wr.next().val();
			w = wr.next();
			if (w.type() != assign) {
				wr.retract();

				a.valueOnly = true;
				a.newEntry(this, "value");

				w = wr.next();
			} else {
				wr.skip();

				while (true) {
					a.newEntry(this, name);

					if (wr.next().type() != comma) break;

					name = wr.next().val();
					wr.except(assign);
				}
			}

			if (w.type() != rParen) throw wr.err(a.valueOnly ? "cu.annotation.valueOnly" : "unexpected\1"+w.val()+"\0");

			if (!wr.nextIf(at)) return list;
		}
	}
	// endregion
	// region 阶段2 解析引用
	// region 2.1 名称引用
	/**
	 * Stage 2 (1/3) 并行解析本类的名称引用.
	 * 该阶段使用Stage 1已知的包名和类名和导入
	 * 本阶段将类【继承，实现，方法，字段，permits，注解】中的引用类名解析为全限定名称
	 */
	public void S2_ResolveName() {
		var ctx = LocalContext.get();
		ctx.setClass(this);
		ctx.errorReportIndex = classIdx;
		// TypeResolver
		importList.init(ctx);
		var s1 = signature;
		// class
		if (s1 != null) {
			currentNode = s1;
			s1.resolve(ctx);
		}
		// fields
		for (int i = 0; i < fieldIdx.size(); i++) {
			ctx.errorReportIndex = fieldIdx.get(i);
			var field = fields.get(i);
			var s = (LPSignature) field.getRawAttribute("Signature");
			if (s != null) {
				currentNode = s;
				s.resolve(ctx);
				field.fieldType(s.typeParamToBound(field.fieldType()));

				ctx.disableRawTypeWarning = true;
			}
			field.fieldType(ctx.transformPseudoType(ctx.resolveType(field.fieldType())).rawType());

			ctx.disableRawTypeWarning = false;
		}
		// methods
		for (int i = 0; i < methodIdx.size(); i++) {
			ctx.errorReportIndex = methodIdx.get(i);
			var method = methods.get(i);
			var s = (LPSignature) method.getRawAttribute("Signature");
			List<Type> par = method.parameters();
			if (s != null) {
				currentNode = s;
				s.resolve(ctx);
				for (int j = 0; j < par.size(); j++) par.set(j, s.typeParamToBound(par.get(j)));
				method.setReturnType(s.typeParamToBound(method.returnType()));

				ctx.disableRawTypeWarning = true;
			}
			for (int j = 0; j < par.size(); j++) par.set(j, ctx.transformPseudoType(ctx.resolveType(par.get(j))).rawType());
			method.setReturnType(ctx.transformPseudoType(ctx.resolveType(method.returnType())).rawType());

			ctx.disableRawTypeWarning = false;
		}
		ctx.errorReportIndex = classIdx;

		// extends
		var pInfo = importList.resolve(ctx, parent());
        if (pInfo == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", parent(), name+".parent");
		} else {
            int acc = pInfo.modifier;
			if (0 != (acc & ACC_FINAL)) {
				ctx.report(Kind.ERROR, "cu.resolve.notInheritable", "cu.final", parent());
			} else if (0 != (acc & ACC_INTERFACE)) {
				ctx.report(Kind.ERROR, "cu.resolve.notInheritable", "cu.interface", parent());
			}

			parent(pInfo.name());
        }

		// implements
		var itfs = interfaces;
		for (int i = 0; i < itfs.size(); i++) {
			String iname = itfs.get(i).name().str();
			var info = importList.resolve(ctx, iname);
            if (info == null) {
				ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", iname, name+".interface["+i+"]");
			} else {
                int acc = info.modifier;
                if (0 == (acc & ACC_INTERFACE)) {
					ctx.report(Kind.ERROR, "cu.resolve.notInheritable", "cu.class", info.name());
                }

				interfaces.set(i, cp.getClazz(info.name()));
            }
		}

		// permits
		if ((extraModifier&_ACC_SEALED) != 0) {
			var ps = (ClassListAttribute) getRawAttribute("PermittedSubclasses");

			List<String> value = ps.value;
			for (int i = 0; i < value.size(); i++) {
				String type = value.get(i);
				var info = importList.resolve(ctx, type);

				if (info == null) {
					ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", type, name+".permits["+i+"]");
				} else {
					value.set(i, info.name());
				}
			}
		}

		// annotation
		for (var list : annoTask.values()) {
			resolveAnnotationTypes(ctx, list);
		}
	}
	/**
	 * 解析注解类型引用.
	 * 也会被{@link BlockParser}调用
	 */
	public void resolveAnnotationTypes(LocalContext ctx, List<AnnotationPrimer> list) {
		for (int i = 0; i < list.size(); i++) {
			var a = list.get(i);
			resolveAnnotationType(ctx, a);

			for (Iterator<?> itr = a.values().iterator(); itr.hasNext(); ) {
				if (itr.next() instanceof CEntry value) {
					if (value instanceof AList array) {
						var list1 = array.raw();
						for (int j = 0; j < list1.size(); j++) {
							resolveAnnotationType(ctx, (AnnotationPrimer) list1.get(j));
						}
					} else {
						resolveAnnotationType(ctx, (AnnotationPrimer) value);
					}
				}
			}
		}
	}
	private void resolveAnnotationType(LocalContext ctx, AnnotationPrimer a) {
		var type = importList.resolve(ctx, a.type());
		if (type == null) {
			ctx.report(a.pos, Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", a.type(), ctx.currentCodeBlockForReport());
		} else {
			a.setType(type.name());
		}
	}
	// endregion
	// region 2.2 类型引用
	/**
	 * Stage 2 (2/3) 并行解析本类的类引用.
	 * 该阶段使用Stage 2.1解析的全限定名称，以获取类的对象
	 * 本阶段将进行循环继承，泛型异常，密封类完整性等检查
	 * 本阶段还将生成默认构造器和枚举、记录类的默认方法
	 * 注：方法的throws也在2.2解析，虽然应当在2.1解析，但是在2.2能同时判断是否instanceof Throwable
	 */
	public void S2_ResolveType() {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		ctx.errorReportIndex = classIdx;
		// 检测循环继承
		ctx.getHierarchyList(this);

		String parent = parent();
		// 检测泛型异常
		if (signature != null && ctx.instanceOf(parent, "java/lang/Throwable")) {
			ctx.report(Kind.ERROR, "cu.genericException");
		}
		// 不能静态继承非静态类
		var parentInfo = ctx.classes.getClassInfo(parent);
		var icFlag = ctx.classes.getInnerClassInfo(parentInfo).get(parent);
		if (icFlag != null && (icFlag.modifier&ACC_STATIC) == 0) {
			if (isNonStaticInnerClass()) {
				ctx.castTo(Type.klass(_parent.name()), Type.klass(icFlag.parent), 0);
			} else {
				ctx.report(Kind.ERROR, "cu.inheritNonStatic", icFlag.parent);
			}
		}

		var names = ctx.getTmpSet();

		// 权限和密封类完整性检查
		{
		int i = 0;
		var itfs = interfaces;
		var name = parent;
		while (true) {
			var info = ctx.classes.getClassInfo(name);

			ctx.assertAccessible(info);

			// 检查是否继承自密封类
			var ps = info.getAttribute(info.cp, Attribute.PermittedSubclasses);
			if (ps != null) {
				if ((extraModifier&(_ACC_SEALED|_ACC_NON_SEALED|ACC_FINAL)) == 0) {
					ctx.report(Kind.ERROR, "cu.sealed.missing");
				}

				if (!ps.value.contains(this.name)) {
					ctx.report(Kind.ERROR, i == 0 ? "cu.sealed.unlisted.c" : "cu.sealed.unlisted.i", name, ps.value);
				}
			}

			if (i == itfs.size()) break;
			name = itfs.get(i++).name().str();
		}
		}

		// 检查permits的子类是否真的继承了我
		if ((extraModifier&_ACC_SEALED) != 0) {
			var ps = (ClassListAttribute) getRawAttribute("PermittedSubclasses");

			List<String> value = ps.value;
			for (int i = 0; i < value.size(); i++) {
				String type = value.get(i);
				var info = ctx.classes.getClassInfo(type);

				ctx.assertAccessible(info);
				if (!info.parent().equals(name) && !info.interfaces().contains(name)) {
					ctx.report(Kind.ERROR, "cu.sealed.indirectInherit", type, name);
				}
			}
		}

		// 是否需要生成默认构造器
		// use extraModifier so that anonymous class can disable it
		boolean generateConstructor = (extraModifier & ACC_INTERFACE) == 0;

		names.clear();
		List<MethodNode> methods = this.methods;
		for (int i = 0; i < methodIdx.size(); i++) {
			ctx.errorReportIndex = methodIdx.get(i);
			var method = methods.get(i);

			String par = method.rawDesc();
			// 方法定义冲突在该阶段检查，因为更早的阶段拿不到完整的全限定参数类型
			if (!names.add(method.name()+par.substring(0, par.lastIndexOf(')')+1)))
				ctx.report(Kind.ERROR, "cu.nameConflict", this.name, "invoke.method", TypeHelper.humanize(Type.methodDesc(par), method.name(), false));

			if ((modifier&ACC_INTERFACE) != 0 && (method.modifier&ACC_ABSTRACT) == 0 && ctx.classes.getClassInfo("java/lang/Object").getMethodObj(method.name(), method.rawDesc()) != null) {
				ctx.report(Kind.ERROR, "cu.override.interfaceObject", method.owner, method.name());
			}

			if ((modifier&ACC_ANNOTATION) != 0 && (method.modifier&ACC_STATIC) == 0) {
				checkAnnotationReturnType(method.returnType());
			}

			if (method.name().equals("<init>")) {
				generateConstructor = false;
			}

			// 解析方法抛出的异常
			ClassListAttribute exThrown = (ClassListAttribute) method.getRawAttribute("Exceptions");
			if (exThrown != null) {
				List<String> classes = exThrown.value;
				for (int j = 0; j < classes.size(); j++) {
					IClass info = importList.resolve(ctx, classes.get(j));
					if (info == null) {
						ctx.report(Kind.ERROR, "symbol.error.noSuchClass", classes.get(i));
					} else {
						ctx.assertAccessible(info);

						if (!ctx.instanceOf(info.name(), "java/lang/Throwable"))
							ctx.report(Kind.ERROR, "cu.throwException", classes.get(i));

						classes.set(j, info.name());
					}
				}
			}
		}
		// region 枚举的默认方法生成
		if ((modifier&ACC_ENUM) != 0) {
			if (generateConstructor) {
				var cw = glinit = newWritableMethod(ACC_PUBLIC|ACC_SYNTHETIC, "<init>", "(Ljava/lang/String;I)V");
				cw.visitSize(3,3);
				cw.insertBefore(DynByteBuf.wrap(invokeDefaultConstructor()));
				generateConstructor = false;
			}

			String arrayType_ = "[L"+name+";";

			//int fid = getField("$VALUES");
			int fid = miscFieldId = newField(ACC_PRIVATE|ACC_STATIC|ACC_FINAL/*|ACC_SYNTHETIC*/, "$VALUES", arrayType_);

			CodeWriter w;

			// T[] values()
			int mid = getMethod("values", "()".concat(arrayType_));
			if (mid < 0) {
				w = newMethod(ACC_PUBLIC|ACC_STATIC, "values", "()".concat(arrayType_));
				w.visitSize(1, 1);
				w.field(GETSTATIC, this, fid);
				w.invoke(INVOKEVIRTUAL, arrayType_, "clone", "()Ljava/lang/Object;");
				w.clazz(CHECKCAST, arrayType_);
				w.insn(ARETURN);
				w.finish();
			} else if (methods.get(mid).modifier != (ACC_PUBLIC|ACC_STATIC)) {
				ctx.report(methodIdx.get(mid), Kind.ERROR, "cu.enumMethod");
			}

			mid = getMethod("valueOf", "(Ljava/lang/String;)L"+name+";");
			if (mid < 0) {
				// T valueOf(String name)
				w = newMethod(ACC_PUBLIC|ACC_STATIC, "valueOf", "(Ljava/lang/String;)L"+name+";");
				w.visitSize(2, 1);
				w.ldc(cp.getClazz(name));
				w.insn(ALOAD_0);
				w.invokeS("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
				w.clazz(CHECKCAST, name);
				w.insn(ARETURN);
				w.finish();
			} else if (methods.get(mid).modifier != (ACC_PUBLIC|ACC_STATIC)) {
				ctx.report(methodIdx.get(mid), Kind.ERROR, "cu.enumMethod");
			}
		}
		// endregion
		// region 记录和结构体的默认方法生成
		recordDefaults:
		if ((extraModifier&_ACC_RECORD) != 0) {
			var bArguments = new SimpleList<Constant>();
			var fieldNames = new CharList();
			bArguments.add(cp.getClazz(name));
			bArguments.add(null);

			for (int i = 0; i < miscFieldId; i++) {
				var field = fields.get(i);

				fieldNames.append(field.name()).append(';');
				bArguments.add(cp.getMethodHandle(name, field.name(), field.rawDesc(), BootstrapMethods.Kind.GETFIELD, Constant.FIELD));

				int mid = getMethod(field.name(), "()"+field.rawDesc());
				CodeWriter w;

				if (mid < 0) {
					w = newMethod(ACC_PUBLIC|ACC_FINAL, field.name(), "()"+field.rawDesc());
					Type fieldType = field.fieldType();
					w.visitSize(fieldType.length(), 1);
					w.insn(ALOAD_0);
					w.field(GETFIELD, this, i);
					w.insn(fieldType.shiftedOpcode(ARETURN));
					w.finish();

					var sign = ((LPSignature) field.getRawAttribute("Signature"));
					if (sign != null) {
						Signature attr = new Signature(Signature.METHOD);
						attr.values = sign.values;
						w.mn.addAttribute(attr);
					}
				} else if (methods.get(mid).modifier != (ACC_PUBLIC)) {
					ctx.report(methodIdx.get(mid), Kind.ERROR, "cu.enumMethod");
				}
			}

			if (generateConstructor) {
				generateConstructor = false;

				var cw = newWritableMethod(ACC_PUBLIC, "<init>", "()V");
				cw.insn(ALOAD_0);
				cw.invoke(INVOKESPECIAL, "java/lang/Record", "<init>", "()V");

				List<Type> parameters = methods.get(methods.size() - 1).parameters();
				for (int i = 0; i < miscFieldId; i++) parameters.add(fields.get(i).fieldType());

				int slot = 1, stack = 1;
				for (int i = 0; i < miscFieldId; i++) {
					finalFields.remove(fields.get(i));
					Type fieldType = fields.get(i).fieldType();

					cw.insn(ALOAD_0);
					cw.varLoad(fieldType, slot);
					cw.field(PUTFIELD, this, i);

					slot += fieldType.length();
					stack = Math.max(stack, 1+fieldType.length());
				}
				cw.visitSize(stack, slot);

				cw.insn(Opcodes.RETURN);
				cw.finish();
			}

			if ((extraModifier & _ACC_STRUCT) != 0) break recordDefaults;

			if (fieldNames.length() > 0) fieldNames.setLength(fieldNames.length()-1);
			bArguments.set(1, new CstString(fieldNames.toStringAndFree()));

			int refId = addLambdaRef(new BootstrapMethods.Item(
					"java/lang/runtime/ObjectMethods",
					"bootstrap",
					"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/TypeDescriptor;Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;",
					BootstrapMethods.Kind.INVOKESTATIC,
					Constant.METHOD,
					bArguments
			));

			// 嗯，因为当然标准库有了
			//  1. 以后降版本的话可以考虑一下模拟
			//  2. 不降版本可以试试这么实现@EqualsAndHashCode注解，only for final
			int mid = getMethod("toString", "()Ljava/lang/String;");
			CodeWriter w;

			if (mid < 0) {
				var cw = newWritableMethod(ACC_PUBLIC|ACC_FINAL, "toString", "()Ljava/lang/String;");
				cw.visitSize(2, 1);
				cw.insn(ALOAD_0);
				cw.invokeDyn(refId, "toString", "(L"+name+";)Ljava/lang/String;", 0);
				cw.insn(ARETURN);
				cw.finish();
			} else if (methods.get(mid).modifier != (ACC_PUBLIC|ACC_FINAL)) {
				ctx.report(methodIdx.get(mid), Kind.ERROR, "cu.enumMethod");
			}

			mid = getMethod("hashCode", "()I");
			if (mid < 0) {
				w = newMethod(ACC_PUBLIC|ACC_FINAL, "hashCode", "()I");
				w.visitSize(1, 1);
				w.insn(ALOAD_0);
				w.invokeDyn(refId, "hashCode", "(L"+name+";)I", 0);
				w.insn(IRETURN);
				w.finish();
			} else if (methods.get(mid).modifier != (ACC_PUBLIC|ACC_FINAL)) {
				ctx.report(methodIdx.get(mid), Kind.ERROR, "cu.enumMethod");
			}

			mid = getMethod("equals", "(Ljava/lang/Object;)Z");
			if (mid < 0) {
				w = newMethod(ACC_PUBLIC|ACC_FINAL, "equals", "(Ljava/lang/Object;)Z");
				w.visitSize(2, 2);
				w.insn(ALOAD_0);
				w.insn(ALOAD_1);
				w.invokeDyn(refId, "equals", "(L"+name+";Ljava/lang/Object;)Z", 0);
				w.insn(IRETURN);
				w.finish();
			} else if (methods.get(mid).modifier != (ACC_PUBLIC|ACC_FINAL)) {
				ctx.report(methodIdx.get(mid), Kind.ERROR, "cu.enumMethod");
			}
		}
		// endregion
		boolean isNewNonStaticInnerClass = isNonStaticInnerClass() && !isInheritedNonStaticInnerClass();
		if (isNewNonStaticInnerClass) newField(ACC_SYNTHETIC|ACC_FINAL, NestContext.InnerClass.FIELD_HOST_REF, Type.klass(_parent.name()));

		if (generateConstructor) {
			var cw = glinit = newWritableMethod(ACC_PUBLIC, "<init>", isNonStaticInnerClass() ? "(L"+_parent.name()+";)V" : "()V");
			cw.visitSize(1,1);
			cw.computeFrames(AttrCode.COMPUTE_SIZES);
			cw.insertBefore(DynByteBuf.wrap(invokeDefaultConstructor()));
		}

		if (isNewNonStaticInnerClass) {
			var glinit = getGlobalInit();
			glinit.insn(ALOAD_0);
			glinit.insn(ALOAD_1);
			glinit.field(PUTFIELD, name, NestContext.InnerClass.FIELD_HOST_REF, "L"+_parent.name+";");
		}
	}
	public boolean isNonStaticInnerClass() {return _parent != this && (extraModifier&(ACC_STATIC|_ACC_INNER_CLASS)) == _ACC_INNER_CLASS;}
	public boolean isInheritedNonStaticInnerClass() {
		if (isNonStaticInnerClass()) {
			String parent = parent();
			var parentInfo = ctx.classes.getClassInfo(parent);
			var icFlag = ctx.classes.getInnerClassInfo(parentInfo).get(parent);
			return icFlag != null && (icFlag.modifier & ACC_STATIC) == 0;
		}
		return false;
	}
	@Nullable private byte[] invokeDefaultConstructor;
	public byte[] invokeDefaultConstructor() {
		if (invokeDefaultConstructor != null) return invokeDefaultConstructor;

		var tmp = IOUtil.getSharedByteBuf();
		if ((modifier&ACC_ENUM) != 0) {
			tmp.put(Opcodes.ALOAD_0)
			   .put(Opcodes.ALOAD_1)
			   .put(Opcodes.ILOAD_2)
			   .put(Opcodes.INVOKESPECIAL)
			   .putShort(cp.getMethodRefId("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V"));
		} else {
			String parent = parent();
			ClassNode pInfo = ctx.classes.getClassInfo(parent);

			var icFlag = ctx.classes.getInnerClassInfo(pInfo).get(parent);

			String parentArg = icFlag != null && (icFlag.modifier & ACC_STATIC) == 0 ? "(L"+icFlag.parent+";)V" : "()V";

			var mn = pInfo.getMethodObj("<init>", parentArg);
			if (mn != null) {
				ctx.checkAccessible(pInfo, mn, false, true);
				if (_parent != this && pInfo instanceof CompileUnit) j11PrivateConstructor(mn);
			} else if (pInfo.getMethodObj("<init>") != null) {
				// 2.2阶段其实没法保证方法的确定性，但是已知只会生成无参构造器，就能在这里做检查了.
				// 如果存在任意有参构造器，就不会生成默认的无参public构造器了
				ctx.report(Kind.ERROR, "cu.noDefaultConstructor", parent);
				return invokeDefaultConstructor = ArrayCache.BYTES;
			}

			tmp.put(ALOAD_0);
			if (!parentArg.equals("()V")) tmp.put(ALOAD_1);
			tmp.put(INVOKESPECIAL).putShort(cp.getMethodRefId(parent(), "<init>", parentArg));
		}

		return invokeDefaultConstructor = tmp.toByteArray();
	}
	// 如果本类是注解类型，那么检测方法返回值是否被JVM允许
	private void checkAnnotationReturnType(Type type) {
		if (type.owner != null && !type.owner.equals("java/lang/String")) {
			if (name.equals(type.owner)) {
				ctx.report(Kind.ERROR, "cu.annotation.cyclic");
			} else {
				var mod = ctx.classes.getClassInfo(type.owner).modifier;
				if ((mod & (ACC_ENUM | ACC_ANNOTATION)) == 0 || (type.array() > 1 && (mod & ACC_ANNOTATION) != 0)) {
					ctx.report(Kind.ERROR, "cu.annotation.invalidDefault");
				}
			}
		}
	}
	// used to create enum constructor
	public final MethodWriter newWritableMethod(int acc, String name, String desc) {
		var mn = new MethodNode(acc, this.name, name, desc);
		methods.add(mn);
		if ((acc & (ACC_ABSTRACT|ACC_NATIVE)) != 0) return Helpers.nonnull();
		var cw = ctx.createMethodWriter(this, mn);
		mn.addAttribute(new AttrCodeWriter(cp, mn, cw));
		return cw;
	}
	// endregion
	// region 2.3 方法引用
	private static final char __ACC_UNRELATED = 32768, __ACC_INHERITED = 16384;
	/**
	 * Stage 2 (3/3) 并行解析本类的方法
	 * 该阶段使用Stage 2.2生成的方法（如有）
	 * 本阶段将构建重载列表，验证方法参数
	 */
	public void S2_ResolveMethod() {
		var ctx = LocalContext.get();
		ctx.setClass(this);
		ctx.errorReportIndex = classIdx;

		final MyHashSet<NameAndType> implementCheck = Helpers.cast(ctx.tmpSet);
		implementCheck.clear();
		final var overridableMethods = new MyHashMap<NameAndType, MethodResult>();

		// 搜索抽象方法，可重载的方法，Unrelated
		{
			List<ClassNode> inherits = new SimpleList<>();

			// 从一级父类获取，这样不会混入本类的方法
			int i = 0;
			var itfs = interfaces;
			var name = parent();
			while (true) {
				var info = ctx.classes.getClassInfo(name);
				skip: {
					for (int j = 0; j < inherits.size(); j++) {
						// 如果被父类或其它接口实现了，那么跳过
						if (ctx.getHierarchyList(inherits.get(j)).containsValue(info.name())) {
							break skip;
						}
					}

					var myParent = ctx.getHierarchyList(info);
					// 这里是 > 而不是 >= 在当前类实现了接口而继承Object的情况下，如果是>=那么第0项会被替换掉，所以要避免
					// 这个操作与下方的continue可以避免Object的方法被访问多次，不过好像没有意义（
					for (int j = inherits.size()-1; j > 0; j--) {
						// 如果又包括了别的接口
						if (myParent.containsValue(inherits.get(j).name())) inherits.remove(j);
					}

					inherits.add(info);
				}

				if (i == itfs.size()) break;
				name = itfs.get(i++).name().str();
			}

			var m = ctx.tmpNat;

			for (i = 0; i < inherits.size(); i++) {
				ClassNode info = inherits.get(i);
				for (var cl : ctx.classes.getResolveHelper(info).getMethods(ctx.classes).values()) {
					var methods = cl.getMethods();

					for (int j = 0; j < methods.size(); j++) {
						var method = methods.get(j);
						if ((method.modifier&ACC_PRIVATE) != 0   // 私有方法
								|| method.name().startsWith("<") // 构造器或静态初始化
								|| (i != 0 && method.owner.equals("java/lang/Object")) // 接口中继承自Object的方法
						) continue;

						// MethodList里没有桥接方法，可以放心这么用
						String param = method.rawDesc();
						param = param.substring(0, param.lastIndexOf(')')+1);

						m.owner = method.owner;
						m.name = method.name();
						m.param = param;

						// 可能是继承自接口的方法
						boolean isClass = i == 0 && (ctx.classes.getClassInfo(method.owner).modifier & ACC_INTERFACE) == 0;

						if ((method.modifier&ACC_ABSTRACT) != 0) {
							// 如果本类不是抽象的，那么必须实现所有的抽象方法，加入mustImplement以供后续代码检查是否真的实现了
							// 这里不需要检测权限，就像没法继承ByteBuffer一样，没有public构造器过不了2.2阶段
							if ((modifier&ACC_ABSTRACT) == 0) {
								// implementCheck包括这些类型:
								// 1. 父类的抽象方法 => 无法被接口默认实现 (PARENT)
								// 2. 接口的抽象方法
								// 3. 被多个接口实现的方法 (UNRELATED)
								// 4. 非抽象方法
								var existing = implementCheck.intern(m);
								if (m == existing) {
									// 状态 1 或 2
									m.modifier = (char) ((isClass ? __ACC_INHERITED|(method.modifier&ACC_PUBLIC) : 0) | ACC_ABSTRACT);
									m = new NameAndType();
								}
								// 状态转移 无
							}
						} else if ((method.modifier&ACC_STATIC) == 0) {
							// 处理有实现的方法

							// 当前类不再必须实现这个方法
							var existing = implementCheck.intern(m);
							if (existing == m) {
								// 状态 4
								m.modifier = (char) (isClass ? __ACC_INHERITED|(method.modifier&ACC_PUBLIC) : 0);
								m = new NameAndType();
							} else {
								if ((existing.modifier & ACC_PUBLIC) == 0) {
									existing.modifier |= __ACC_UNRELATED; // 状态转移: 2/4 => 3
								}
							}
						}

						// 重载&泛型
						if (ctx.checkAccessible(info, method, false, false)) {
							// 算了，慢就慢吧，我已经佛了
							MethodResult val = genericCheckInit(method);
							param = val.rawDesc();
							param = param.substring(0, param.lastIndexOf(')')+1);

							m.owner = method.owner;
							m.name = method.name();
							m.param = param;

							var prev = overridableMethods.getEntry(m);
							if (prev != null) {
								char idx = prev.k.modifier;
								ctx.errorReportIndex = idx == 0 ? classIdx : methodIdx.get(idx-1);
								MethodResult value = genericCombine(prev.getValue(), method);
								prev.setValue(value);
								ctx.errorReportIndex = classIdx;
							} else {
								m.modifier = 0;
								for (int k = 0; k < this.methods.size(); k++) {
									var myMethod = this.methods.get(k);
									if (method.name().equals(myMethod.name()) && myMethod.rawDesc().startsWith(param)) {
										m.modifier = (char) (k+1);
										break;
									}
								}

								overridableMethods.put(implementCheck.find(m), val);
								m = new NameAndType();
							}
						}
					}
				}
			}

			ctx.tmpNat = m;

			/*// 删除2和4状态
			for (var itr = implementCheck.iterator(); itr.hasNext(); ) {
				if ((itr.next().modifier & (ACC_ABSTRACT | __ACC_UNRELATED)) == 0) {
					itr.remove();
				}
			}
			// 删除未被自己覆盖的overridable
			for (Iterator<NameAndType> itr = overridableMethods.keySet().iterator(); itr.hasNext(); ) {
				if (itr.next().modifier == 0) itr.remove();
			}*/
		}

		var d = ctx.tmpNat;

		for (int i = 0; i < methods.size(); i++) {
			var impl = methods.get(i);

			String param = impl.rawDesc();
			d.name = impl.name();
			d.param = param.substring(0, param.lastIndexOf(')')+1);

			var inheritResult = overridableMethods.remove(d);
			if (inheritResult == null) continue; // 如果不是重载方法

			ctx.errorReportIndex = i >= methodIdx.size() ? -1 : methodIdx.get(i);

			var myGenericInfo = ctx.inferrer.getGenericParameters(this, impl, null);
			List<IType> myArgs = myGenericInfo.parameters();
			IType myReturn = myGenericInfo.returnType();

			var inherit = inheritResult.method;

			var overrideGenericInfo = ctx.inferrer.inferOverride(ctx, inherit, Type.klass(name), myArgs);
			if (overrideGenericInfo == null) continue;

			implementCheck.removeValue(d);
			// 检查覆盖静态或访问权限降低
			checkDowngrade(impl, inherit, ctx);

			// 检测Override
			var annotations = annoTask.getOrDefault(impl, Collections.emptyList());
			for (int j = 0; j < annotations.size(); j++) {
				AnnotationPrimer a = annotations.get(j);
				if (a.type().equals("java/lang/Override")) {
					// EMPTY_MAP is a special marker object, will check in GlobalContext
					a.setValues(Collections.emptyMap());
					break;
				}
			}

			IType overrideReturnType = overrideGenericInfo.returnType();

			// 返回值更精确而需要桥接，或更不精确而无法覆盖
			var cast = ctx.inferrer.overrideCast(myReturn, overrideReturnType);
			if (cast.type != 0) {
				String inline = "cu.override.returnType:\1typeCast.error."+cast.type+':'+myReturn+':'+overrideReturnType+"\0";
				ctx.report(Kind.ERROR, "cu.override.unable", name, inherit.owner, inherit, inline);
			} else

			// 生成桥接方法 这里不检测泛型(主要是TypeParam)
			if (!inherit.rawDesc().equals(impl.rawDesc())) {
				createDelegation((inherit.modifier&(ACC_PUBLIC|ACC_PROTECTED)) | ACC_FINAL | ACC_SYNTHETIC | ACC_BRIDGE, impl, inherit, true, false);
			}

			if (!ctx.classes.hasFeature(LavaFeatures.DISABLE_CHECKED_EXCEPTION)) {
				// 声明相同或更少的异常
				var myThrows = myGenericInfo.getExceptions(ctx);
				if (!myThrows.isEmpty()) {
					List<IType> itThrows = overrideGenericInfo.getExceptions(ctx);
					outer:
					for (IType f : myThrows) {
						if (ctx.castTo(f, Types.RUNTIME_EXCEPTION, TypeCast.E_NEVER).type == 0) continue;

						for (IType type : itThrows) {
							TypeCast.Cast c = ctx.castTo(f, type, TypeCast.E_NEVER);
							if (c.type == 0) continue outer;
						}

						String inline = "cu.override.thrown:"+f.owner().replace('/', '.');
						ctx.report(Kind.ERROR, "cu.override.unable", name, inherit.owner.replace('/', '.'), inherit, inline);
					}
				}
			}
		}

		ctx.errorReportIndex = classIdx;
		for (Desc method : implementCheck) {
			if ((method.modifier&__ACC_UNRELATED) != 0) {
				if ((method.modifier&__ACC_INHERITED) != 0) ctx.report(Kind.ERROR, "cu.unrelatedDefault.inherit", method, "");
				else ctx.report(Kind.ERROR, "cu.unrelatedDefault", method, name.replace('/', '.'));
			} else {
				// 前面没做删除，所以在这里做检查
				if ((method.modifier&ACC_ABSTRACT) == 0) continue;

				ctx.report(Kind.ERROR, "cu.override.noImplement", name, method.owner.replace('/', '.'), method.name);
			}
		}
		implementCheck.clear();
	}
	private MethodResult genericCheckInit(MethodNode method) {
		List<IType> resolvedGeneric = ctx.inferGeneric(Type.klass(name), method.owner);
		return ctx.inferrer.getGenericParameters(ctx.classes.getClassInfo(method.owner), method, resolvedGeneric == null ? Type.klass(method.owner) : new Generic(method.owner, resolvedGeneric));
	}
	// 判断两个方法的泛型参数是否兼容
	private MethodResult genericCombine(MethodResult prev, MethodNode method) {
		var curr = genericCheckInit(method);

		var arg1 = prev.parameters();
		var arg2 = curr.parameters();

		if (!arg1.equals(arg2)) {
			String inline = "cu.override.rawArgument:\1typeCast.error.-98:"+arg1+':'+arg2+"\0";
			ctx.report(Kind.ERROR, "cu.override.unable", prev.method.owner, method.owner, method.name(), inline);
		}

		IType oldReturnType = prev.returnType();
		IType newReturnType = curr.returnType();
		IType parent = ctx.getCommonParent(newReturnType, oldReturnType);

		if (parent.equals(oldReturnType)) {
			return curr;
		} else if (parent.equals(newReturnType)) {
			// no-op
		} else {
			String inline = "cu.override.returnType:\1typeCast.error.-3:"+oldReturnType+':'+newReturnType+"\0";
			ctx.report(Kind.ERROR, "cu.override.unable", prev.method.owner, method.owner, method.name(), inline);
		}

		return prev;
	}
	// 访问权限是否降级以及能否override, 此处对override的函数已经具备访问权限
	private void checkDowngrade(MethodNode my, MethodNode it, LocalContext ctx) {
		if ((my.modifier&ACC_STATIC) != (it.modifier&ACC_STATIC)) {
			String inline = "cu.override.static."+((my.modifier&ACC_STATIC) != 0 ? "self" : "other");
			ctx.report(Kind.ERROR, "cu.override.unable", my.owner.replace('/', '.'), it.owner.replace('/', '.'), it, inline);
		}

		int myLevel = my.modifier&(ACC_PUBLIC|ACC_PROTECTED);
		int itLevel = it.modifier&(ACC_PUBLIC|ACC_PROTECTED);
		pass: {
			if (myLevel == ACC_PUBLIC || myLevel == itLevel) break pass;
			if (itLevel == 0 && myLevel == ACC_PROTECTED) break pass;

			String inline = "cu.override.access:"+
					(itLevel==0?"\1package-private\0":showModifiers(itLevel, ACC_SHOW_METHOD))+":"+
					(myLevel==0?"\1package-private\0":showModifiers(myLevel, ACC_SHOW_METHOD));
			ctx.report(Kind.ERROR, "cu.override.unable", my.owner.replace('/', '.'), it.owner.replace('/', '.'), it, inline);
		}
	}
	// endregion
	public final MethodWriter createDelegation(int acc, MethodNode it, MethodNode my, boolean end, boolean newObject) {
		var c = newWritableMethod(acc, my.name(), my.rawDesc());
		int base = 1;

		if (newObject) {
			c.clazz(Opcodes.NEW, it.owner);
			c.insn(DUP);
			c.visitSize(TypeHelper.paramSize(it.rawDesc())+2, TypeHelper.paramSize(my.rawDesc()));
		} else if ((it.modifier&ACC_STATIC) == 0) {
			c.insn(ALOAD_0);
			c.visitSize(TypeHelper.paramSize(it.rawDesc())+1, TypeHelper.paramSize(my.rawDesc())+1);
		} else {
			base = 0;
			c.visitSize(TypeHelper.paramSize(it.rawDesc()), TypeHelper.paramSize(my.rawDesc()));
		}

		List<Type> myPar = my.parameters();
		List<Type> itPar = it.parameters();

		for (int i = 0; i < myPar.size(); i++) {
			Type from = myPar.get(i);
			c.varLoad(from, base);
			ctx.castTo(from, itPar.get(i), TypeCast.E_DOWNCAST).write(c);
			base += from.length();
		}

		c.invoke(it.name().equals("<init>") ? INVOKESPECIAL : (it.modifier&ACC_STATIC) == 0 ? INVOKEVIRTUAL : INVOKESTATIC, it);

		ctx.castTo(it.returnType(), my.returnType(), TypeCast.E_DOWNCAST).write(c);
		if (end) {
			c.insn(my.returnType().shiftedOpcode(IRETURN));
			c.finish();
		}
		return c;
	}
	// endregion
	// region 阶段3 注解处理 & 常量字段
	private int fieldParseState;
	void _setSign(Attributed method) {
		var sign = (LPSignature) method.getRawAttribute("Signature");
		currentNode = sign != null ? sign : signature;
	}
	// 解析static final字段，不过顺便把非final也解析了
	void S3_DFSField() throws ParseException {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		synchronized (this) {
			if (fieldParseState < 0) {
				ctx.errorReportIndex = classIdx;
				ctx.report(Kind.WARNING, "cu.constLoop", name);
				return;
			}
			if (fieldParseState != 0) return;
			fieldParseState = -1;
		}

		ctx.setupFieldDFS();
		addEnclosingContext(ctx);

		currentNode = signature;
		ctx.lexer.state = STATE_EXPR;
		// 优先级的定义写在ParseTask中
		lazyTasks.sort((o1, o2) -> Integer.compare(o1.priority(), o2.priority()));
		int taskId;
		for (taskId = 0; taskId < lazyTasks.size(); taskId++) {
			var task = lazyTasks.get(taskId);
			if (task.priority() > 0) break;
			if (task.isStaticFinalField()) task.parse(ctx);
		}

		ctx.enclosing.clear();
		fieldParseState = 1;
	}
	public void S3_Annotation() throws ParseException {
		S3_DFSField();

		MyHashMap<String, Object> dup = ctx.tmpMap1, extra = ctx.tmpMap2;
		var missed = ctx.getTmpSet();
		for (var annotated : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			var list = annotated.getValue();
			for (int i = 0; i < list.size(); i++) {
				var a = list.get(i);
				ctx.errorReportIndex = a.pos;

				String type = a.type();
				var desc = ctx.classes.getAnnotationDescriptor(ctx.classes.getClassInfo(type));

				Object prev;
				if (!desc.stackable && (prev = dup.putIfAbsent(type, i)) != null) {
					if (desc.repeatOn == null) ctx.report(Kind.ERROR, "cu.annotation.noRepeat", type);
					else {
						List<CEntry> values;
						if (prev instanceof Integer p) {
							var removable = list.get(p);

							values = new SimpleList<>();
							values.add(removable);

							var repeat = new AnnotationPrimer(desc.repeatOn, removable.pos);
							repeat.setValues(Collections.singletonMap("value", new AList(values)));
							list.set(p, repeat);
							if (desc.kind != AnnotationSelf.SOURCE) {
								//noinspection all
								List<Annotation> list1 = (desc.kind == AnnotationSelf.CLASS ? inv : vis).annotations;
								list1.set(list1.indexOf(removable), repeat);
							}

							dup.put(type, values);
						} else {
							values = Helpers.cast(prev);
						}

						values.add(a);
						list.remove(i--);
					}
				} else {
					int targetType = applicableToNode(annotated.getKey());
					if ((desc.applicableTo & targetType) == 0) {
						ctx.report(Kind.ERROR, "cu.annotation.notApplicable", type, targetType);
					}

					if (desc.kind != AnnotationSelf.SOURCE) {
						if (desc.kind == AnnotationSelf.CLASS) {
							if (inv == null) {
								inv = new Annotations(false);
								annotated.getKey().addAttribute(inv);
							}
							inv.annotations.add(a);
						} else {
							if (vis == null) {
								vis = new Annotations(true);
								annotated.getKey().addAttribute(vis);
							}
							vis.annotations.add(a);
						}
					}
				}

				dup.clear();
				extra.putAll(a.raw());

				for (Map.Entry<String, Type> entry : desc.types.entrySet()) {
					String name = entry.getKey();

					Object node = Helpers.cast(extra.remove(name));
					if (node instanceof Expr expr) a.raw().put(name, AnnotationPrimer.toAnnVal(ctx, expr, entry.getValue()));
					else if (node == null && !desc.values.containsKey(entry.getKey())) missed.add(name);
				}

				if (!extra.isEmpty()) ctx.report(Kind.ERROR, "cu.annotation.extra", type, extra.keySet());
				if (!missed.isEmpty()) ctx.report(Kind.ERROR, "cu.annotation.missing", type, missed);

				extra.clear();
				missed.clear();
			}

			ctx.classes.runAnnotationProcessor(this, annotated.getKey(), list);
		}
		annoTask.clear();
	}
	// 注解的适用类型
	private static int applicableToNode(Attributed key) {
		int mask;
		if (key instanceof FieldNode) {
			mask = AnnotationSelf.FIELD;
		} else if (key instanceof MethodNode m) {
			mask = m.name().startsWith("<") ? AnnotationSelf.CONSTRUCTOR : AnnotationSelf.METHOD;
		} else if (key instanceof IClass c) {
			if (c.name().endsWith("/package-info")) mask = AnnotationSelf.PACKAGE;
			else if (c.name().equals("module-info")) mask = AnnotationSelf.MODULE;
			else mask = (c.modifier()&Opcodes.ACC_ANNOTATION) != 0 ? AnnotationSelf.ANNOTATION_TYPE : AnnotationSelf.TYPE;
		} else if (key instanceof ParamAnnotationRef) {
			mask = AnnotationSelf.PARAMETER;
		} else if (key instanceof Variable) {
			mask = AnnotationSelf.LOCAL_VARIABLE;
		} else {
			throw new AssertionError("不支持的注解目标："+key.getClass());
		}
		return mask;
	}
	// endregion
	// region 阶段4 编译代码块
	private MethodWriter clinit, glinit;
	// 静态初始化
	public MethodWriter getStaticInit() {
		if (clinit != null) return clinit;

		int v = getMethod("<clinit>");
		if (v < 0) {
			v = methods.size();
			methods.add(new MethodNode(ACC_PUBLIC|ACC_STATIC, name, "<clinit>", "()V"));
		}

		MethodNode node = methods.get(v);
		clinit = ctx.createMethodWriter(this, node);
		node.addAttribute(new AttrCodeWriter(cp, node, clinit));
		clinit.computeFrames(AttrCode.COMPUTE_SIZES|AttrCode.COMPUTE_FRAMES);
		return clinit;
	}
	// 公共构造器初始化块
	public MethodWriter getGlobalInit() {
		// 隐式构造器会主动设置这个，不再需要额外检测
		if (glinit != null) return glinit;

		SimpleList<ClassNode> _throws = new SimpleList<>();

		for (int i = 0, size = methods.size(); i < size; i++) {
			MethodNode method = methods.get(i);
			if (method.name().equals("<init>")) {
				var list = method.getAttribute(cp, Attribute.Exceptions);
				if (list == null) {
					_throws.clear();
					break;
				}

				if (_throws.isEmpty()) {
					for (String exc : list.value)
						_throws.add(ctx.classes.getClassInfo(exc));
				} else {
					loop:
					for (int j = _throws.size() - 1; j >= 0; j--) {
						var exParent = ctx.getHierarchyList(_throws.get(j));
						for (String s : list.value) {
							var self = ctx.classes.getClassInfo(s);

							if (ctx.getHierarchyList(self).containsValue(exParent.get(0))) {
								if (!_throws.contains(self)) _throws.add(self);
							} else if (exParent.containsValue(s)) continue loop;
						}

						_throws.remove(j);
					}

					if (_throws.isEmpty()) break;
				}
			}
		}

		var mn = new MethodNode(ACC_PRIVATE, name, "<glinit>", "()V");
		if (!_throws.isEmpty()) {
			String[] strings = new String[_throws.size()];
			int i = 0;
			for (ClassNode data : _throws) strings[i++] = data.name();

			GlobalContext.debugLogger().info("Common parent of all constructor throws: "+Arrays.toString(strings));
			// common parent of all constructor throws'
			mn.addAttribute(new ClassListAttribute(Attribute.Exceptions, SimpleList.asModifiableList(strings)));
		}
		return glinit = ctx.createMethodWriter(this, mn);
	}

	private DynByteBuf glInitBytes;
	private char glStack, glLocal;
	private void serializeGlInit() {
		if (glInitBytes == null) {
			glinit.computeFrames(AttrCode.COMPUTE_SIZES);
			glInitBytes = glinit.writeTo();
			glStack = glInitBytes.readChar();
			glLocal = glInitBytes.readChar();
			glInitBytes.rIndex += 4;
		}
	}
	// call from newAnonymousClass
	public void NAC_SetGlobalInit(MethodWriter constructor) {glinit = constructor;}

	public void appendGlobalInit(MethodWriter target, DynByteBuf insertBefore, LineNumberTable lines) {
		if (glinit != null) {
			serializeGlInit();

			if (insertBefore != null) {
				int initialLength = insertBefore.wIndex();
				insertBefore.put(glInitBytes);
				target.visitSizeMax(glStack, glLocal);

				if (glinit.lines != null) {
					for (LineNumberTable.Item item : glinit.lines.list) {
						lines.add(new Label(initialLength+item.pos.getValue()), item.getLine());
					}
				}
				return;
			}

			int moveCount;
			if ((moveCount = target.getPlaceholderId(MethodWriter.GLOBAL_INIT_INSERT)) != 0) {
				target.replaceSegment(moveCount, new SolidBlock(glInitBytes));
			} else {
				moveCount = target.nextSegmentId();
				target.addSegment(new SolidBlock(glInitBytes));
			}
			target.visitSizeMax(glStack, glLocal);

			if (glinit.lines != null) {
				for (var item : glinit.lines.list) {
					Label pos = new Label(item.pos);
					pos.__move(moveCount);
					target.__addLabel(pos);
					lines.add(pos, item.getLine());
				}
			}
		}
	}

	private int assertionId = -1;
	public int getAssertEnabled() {
		if (assertionId >= 0) return assertionId;

		int fid = newField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "$assertionEnabled", "Z");
		assertionId = fid;

		MethodWriter initAssert;
		if (clinit == null) initAssert = getStaticInit();
		else initAssert = clinit.fork();
		initAssert.ldc(new CstClass(name));
		initAssert.invoke(INVOKEVIRTUAL, "java/lang/Class", "desiredAssertionStatus", "()Z");
		initAssert.field(PUTSTATIC, this, fid);

		if (clinit != initAssert) {
			clinit.insertBefore(initAssert.writeTo());
		}
		return fid;
	}

	public int addLambdaRef(BootstrapMethods.Item item) {
		var bsm = (BootstrapMethods) getRawAttribute("BootstrapMethods");
		if (bsm == null) addAttribute(bsm = new BootstrapMethods());
		int i = bsm.methods.indexOf(item);
		if (i < 0) {
			i = bsm.methods.size();
			bsm.methods.add(item);
		}
		return i;
	}

	public void S4_Code() throws ParseException {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		addEnclosingContext(ctx);

		currentNode = signature;
		ctx.lexer.state = STATE_EXPR;

		for (int i = 0; i < lazyTasks.size(); i++) lazyTasks.get(i).parse(ctx);
		lazyTasks.clear();

		if (clinit != null) clinit.insn(Opcodes.RETURN);

		for (FieldNode field : finalFields) {
			ctx.report(fieldIdx.get(fields.indexOfAddress(field)), Kind.ERROR, "var.notAssigned", field.name());
		}
		finalFields.clear();

		// 隐式构造器
		if (glinit != null && glInitBytes == null && extraModifier != (ACC_FINAL|ACC_INTERFACE)) {
			glinit.computeFrames(AttrCode.COMPUTE_SIZES|AttrCode.COMPUTE_FRAMES);
			glinit.insn(Opcodes.RETURN);
			glinit.finish();
		}

		ctx.enclosing.clear();
	}

	private void addEnclosingContext(LocalContext ctx) {
		if (isNonStaticInnerClass()) {
			var that = this;
			if (!ctx.enclosing.isEmpty()) throw new AssertionError("Enclosing owner is not empty for non-static inner class ??");
			do {
				ctx.enclosing.add(0, NestContext.innerClass(ctx, that._parent.name(), that));
				that = that._parent;
			} while (that != that._parent && that.isNonStaticInnerClass());
		}
	}

	public void j11PrivateConstructor(MethodNode method) {
		// package-private on demand
		if ((method.modifier&Opcodes.ACC_PRIVATE) != 0 && ctx.classes.getMaximumBinaryCompatibility() < LavaFeatures.JAVA_11) {
			method.modifier ^= Opcodes.ACC_PRIVATE;
		}
	}

	public String getNextAccessorName() {return "`acc$"+ctx.nameIndex++;}
	// endregion
	//region 阶段5 序列化
	private List<RawNode> noStore = Collections.emptyList();
	public void markFakeNode(RawNode fn) {
		if (noStore.isEmpty()) noStore = new SimpleList<>();
		noStore.add(fn);
	}

	public void S5_noStore() {
		for (RawNode node : noStore) {
			fields.remove(node);
			methods.remove(node);
		}
		verify();
	}

	@Override public String getFileName() {return name.concat(".class");}
	@Override public ByteList get() { return Parser.toByteArrayShared(this); }
	//endregion
}