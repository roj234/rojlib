package roj.compiler.context;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.tree.*;
import roj.asm.tree.anno.*;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asmx.AnnotationSelf;
import roj.asmx.mapper.util.NameAndType;
import roj.collect.*;
import roj.compiler.JavaLexer;
import roj.compiler.LavaFeatures;
import roj.compiler.asm.*;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.EnumUtil;
import roj.compiler.ast.GeneratorUtil;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.ResolveHelper;
import roj.compiler.resolve.TypeCast;
import roj.compiler.resolve.TypeResolver;
import roj.config.ParseException;
import roj.config.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;
import static roj.config.Word.*;

/**
 * Lava Compiler - 类结构Parser<p>
 * Parser levels: <ol>
 *     <li><b><i>Class Parser</i></b></li>
 *     <li>{@link ParseTask Segment Parser}</li>
 *     <li>{@link BlockParser Method Parser}</li>
 *     <li>{@link ExprParser Expression Parser}</li>
 * </ol>
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public final class CompileUnit extends ConstantData {
	public static final Type RUNTIME_EXCEPTION = new Type("java/lang/RuntimeException");
	private Object _next;

	// Class level flags
	static final int _ACC_RECORD = 1 << 31, _ACC_STRUCT = 1 << 30, _ACC_INNER_CLASS = 1 << 29;
	// read via _modifier()
	static final int _ACC_DEFAULT = 1 << 16, _ACC_ANNOTATION = 1 << 17, _ACC_SEALED = 1 << 18, _ACC_NON_SEALED = 1 << 19, _ACC_JAVADOC = 1 << 20, _ACC_ASYNC = 1 << 21;
	static final int CLASS_ACC = ACC_PUBLIC|ACC_FINAL|ACC_ABSTRACT|ACC_STRICT|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED|_ACC_JAVADOC;
	static final int
		ACC_CLASS_ILLEGAL = ACC_NATIVE|ACC_TRANSIENT|ACC_VOLATILE|ACC_SYNCHRONIZED | _ACC_DEFAULT|_ACC_ASYNC,
		ACC_METHOD_ILLEGAL = ACC_TRANSIENT|ACC_VOLATILE,
		ACC_FIELD_ILLEGAL = ACC_STRICT|ACC_NATIVE|ACC_ABSTRACT | _ACC_DEFAULT|_ACC_ASYNC;

	private static final char UNRELATED_MARKER = 32768, PARENT_MARKER = 16384;

	private final String source;
	private CharSequence code;

	private final TypeResolver tr;

	private int extraModifier;

	// 诊断的起始位置
	private int classIdx;
	private final IntList methodIdx = new IntList(), fieldIdx = new IntList();

	// Generic
	public LPSignature signature, currentNode;

	// Supplementary
	private int miscFieldId;
	public MyHashSet<FieldNode> finalFields = new MyHashSet<>(Hasher.identity());
	private final MyHashSet<Desc> abstractOrUnrelated = new MyHashSet<>();
	private final AccessData overridableMethods = new AccessData(null, 0, "_", null);
	private final List<MethodNode> methodCache = new SimpleList<>();

	// code block task
	private final List<ParseTask> lazyTasks = new SimpleList<>();
	private final MyHashMap<Attributed, List<AnnotationPrimer>> annoTask = new MyHashMap<>();

	// Inner Class
	@NotNull
	private final CompileUnit _parent;
	public int _children;

	LocalContext ctx;

	public void _setCtx(LocalContext ctx) {this.ctx = ctx;}

	public void setMinimumBinaryCompatibility(int level) {
		this.version = Math.max(JavaVersion(level), version);
	}

	public CompileUnit(String name, String code) {
		source = name;
		this.code = code;
		tr = new TypeResolver();

		_parent = this;
	}

	public TypeResolver getTypeResolver() {return tr;}

	// region 文件中的其余类
	private CompileUnit(CompileUnit parent, boolean helperClass) {
		source = parent.source;
		ctx = parent.ctx;

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_SOURCE_FILE))
			putAttr(parent.attrByName("SourceFile"));

		_parent = helperClass ? this : parent;

		code = parent.code;
		classIdx = parent.ctx.lexer.index;

		tr = parent.tr;
	}

	private CompileUnit _newHelper(int acc) throws ParseException {
		CompileUnit c = new CompileUnit(this, true);

		int i = name.lastIndexOf('/') + 1;
		c.name(i <= 0 ? "" : name.substring(0, i));
		c.header(acc);

		ctx.classes.addCompileUnit(c, true);
		return c;
	}
	private CompileUnit _newInner(int acc) throws ParseException {
		CompileUnit c = new CompileUnit(this, false);

		c.name(name.concat("$"));
		c.header(acc|_ACC_INNER_CLASS);
		acc = c.modifier;

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_INNER_CLASS)) {
			if ((acc & (ACC_INTERFACE|ACC_ENUM|_ACC_RECORD)) != 0) acc |= ACC_STATIC;

			var desc = InnerClasses.Item.innerClass(c.name, acc);
			this.innerClasses().add(desc);
			c.innerClasses().add(desc);
		}

		if (ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER)) addNestMember(c);

		if ((acc&ACC_PROTECTED) != 0) {
			acc &= ~ACC_PROTECTED;
			acc |= ACC_PUBLIC;
		}
		c.modifier = (char) (acc&~(ACC_PRIVATE|ACC_STATIC));

		ctx.classes.addCompileUnit(c, true);
		return c;
	}
	public CompileUnit newAnonymousClass(@Nullable MethodNode mn) throws ParseException {
		CompileUnit c = new CompileUnit(this, false);
		// [20241206] temporary workaround for Macro & NewAnonymousClass
		c.code = ctx.lexer.getText();

		c.name(IOUtil.getSharedCharBuf().append(name).append("$").append(++_children).toString());
		c.modifier = ACC_FINAL|ACC_SUPER;
		// magic number to disable auto constructor
		c.extraModifier = ACC_FINAL|ACC_INTERFACE;

		c.body();

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_INNER_CLASS)) {
			var desc = InnerClasses.Item.anonymous(c.name, c.modifier);
			this.innerClasses().add(desc);
			c.innerClasses().add(desc);

			var ownerMethod = new EnclosingMethod();
			ownerMethod.owner = name;
			if (mn != null && !mn.name().startsWith("<")) {
				ownerMethod.name = mn.name();
				ownerMethod.parameters = mn.parameters();
				ownerMethod.returnType = mn.returnType();
			}
			c.putAttr(ownerMethod);
		}

		if (ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER)) addNestMember(c);

		return c;
	}
	public ConstantData newAnonymousClass_NoBody(@Nullable MethodNode mn) {
		var c = new ConstantData();

		c.name(IOUtil.getSharedCharBuf().append(name).append("$").append(++_children).toString());
		c.modifier = ACC_FINAL|ACC_SUPER;

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_INNER_CLASS)) {
			var desc = InnerClasses.Item.anonymous(c.name, c.modifier);
			this.innerClasses().add(desc);
			//c.innerClasses().add(desc);

			var ownerMethod = new EnclosingMethod();
			ownerMethod.owner = name;
			if (mn != null && !mn.name().startsWith("<")) {
				ownerMethod.name = mn.name();
				ownerMethod.parameters = mn.parameters();
				ownerMethod.returnType = mn.returnType();
			}
			c.putAttr(ownerMethod);
		}

		if (ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER)) addNestMember(c);

		ctx.classes.addGeneratedClass(c);
		return c;
	}

	public void addNestMember(ConstantData c) {
		assert ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER);

		var top = _parent;
		while (top._parent != top) top = top._parent;

		if (c instanceof CompileUnit cu) cu.setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_11);
		top.setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_11);

		c.putAttr(new AttrString("NestHost", top.name));
		AttrClassList nestMembers = (AttrClassList) top.attrByName("NestMembers");
		if (nestMembers == null) top.putAttr(nestMembers = new AttrClassList(Attribute.NestMembers));
		nestMembers.value.add(c.name);
	}
	public List<InnerClasses.Item> innerClasses() {
		InnerClasses c = parsedAttr(cp, Attribute.InnerClasses);
		if (c == null) putAttr(c = new InnerClasses());
		return c.classes;
	}
	// endregion
	// region 阶段1非公共部分: package, import, package-info, module-info
	public boolean S1_Struct() throws ParseException {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		var wr = ctx.lexer;
		var tmp = IOUtil.getSharedCharBuf();

		wr.index = 0;
		wr.state = STATE_CLASS;
		//wr.javadocCollector = new SimpleList<>();

		// 默认包""
		String pkg = "";
		Word w = wr.next();

		// 空
		if (w.type() == EOF) return false;

		boolean isNormalClass = !source.contains("-info");
		boolean packageAnnotation;

		if (w.type() == at) {
			packageAnnotation = isNormalClass;

			commitJavadoc(this);
			ctx.tmpAnnotations.clear();
			_annotations(ctx.tmpAnnotations);
			commitAnnotations(this);
			w = wr.next();
		} else {
			packageAnnotation = false;
		}

		boolean moduleIsOpen = w.val().equals("open");
		if (w.type() == MODULE || moduleIsOpen) {
			if (isNormalClass) ctx.report(Kind.ERROR, "module.unexpected");

			if (moduleIsOpen) wr.except(MODULE);

			wr.state = STATE_MODULE;
			parseModuleInfo(wr, moduleIsOpen);
			return false;
		}

		if (w.type() == PACKAGE) {
			if (packageAnnotation) ctx.report(Kind.ERROR, "package.annotation");

			readRef(wr, tmp, false);
			pkg = tmp.append('/').toString();

			w = wr.optionalNext(semicolon);

			// package-info without helper classes
			if (w.type() == EOF) return false;
		}

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_SOURCE_FILE))
			putAttr(new AttrString(Annotations.SourceFile, source));

		tr.clear();
		if (w.type() == PACKAGE_RESTRICTED) {
			tr.setRestricted(true);
			w = wr.optionalNext(semicolon);
		}

		while (w.type() == IMPORT) {
			boolean impField = wr.nextIf(STATIC);
			boolean unimport = !wr.nextIf(sub);

			readRef(wr, tmp, unimport);

			importBlock:
			if (tmp.charAt(tmp.length()-1) == '*') {
				// import *
				if (tmp.length() == 1) {
					tmp.clear();
					tr.setImportAny(true);
				} else {
					tmp.setLength(tmp.length()-2);
				}

				List<String> list = !impField ? tr.getImportPackage() : tr.getImportStaticClass();
				// noinspection all
				if (!list.contains(tmp)) list.add(tmp.toString());
			} else {
				int i = tmp.lastIndexOf("/");
				if (i < 0) ctx.report(Kind.SEVERE_WARNING, "import.unpackaged");

				MyHashMap<String, String> map = !impField ? tr.getImportClass() : tr.getImportStatic();
				String qualified = tmp.toString();

				if (!unimport) {
					map.put(qualified.substring(i+1), null);
					break importBlock;
				}

				String name = wr.nextIf(AS) ? wr.next().val() : qualified.substring(i+1);
				if ((qualified = map.put(name, impField ? qualified.substring(0, i) : qualified)) != null) {
					ctx.report(Kind.ERROR, "import.conflict", tmp, name, qualified);
				}
			}

			wr.optionalNext(semicolon);
		}

		wr.retractWord();
		name(pkg);

		if (isNormalClass) {
			int acc = _modifiers(wr, CLASS_ACC);
			header(acc);
			ctx.classes.addCompileUnit(this, false);
		} else name(pkg+"package-info");

		// 辅助类
		while (!wr.nextIf(EOF)) {
			int acc = _modifiers(wr, CLASS_ACC);
			_newHelper(acc);
		}

		return isNormalClass;
	}
	private void parseModuleInfo(JavaLexer wr, boolean moduleIsOpen) throws ParseException {
		setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_9);
		modifier = ACC_MODULE;
		name("module-info");

		var a = new AttrModule(wr.except(LITERAL, "cu.name").val(), moduleIsOpen ? ACC_OPEN : 0);
		putAttr(a);

		var names = ctx.tmpSet; names.clear();

		// TODO ModulePackages ModuleMainClass
		// 某些重复可能并非错误
		// 我从未用过模块系统，因为它不让我引用未命名模块
		// 当然，也许很快就变了
		// https://blog.csdn.net/qq_60914456/article/details/126206715
		loop:
		while (true) {
			Word w = wr.next();
			switch (w.type()) {
				case EOF -> throw wr.err("module.eof");
				case lBrace -> {break loop;}
				case REQUIRES -> {
					int access = 0;
					if (wr.nextIf(TRANSITIVE)) access |= ACC_TRANSITIVE;
					if (wr.nextIf(STATIC)) access |= ACC_STATIC_PHASE;
					do {
						String name = readRef();
						if (!names.add("R;"+name)) ctx.report(Kind.ERROR, "module.dup.requires", name);

						a.requires.add(new AttrModule.Module(name, access));

						w = wr.next();
					} while (w.type() == comma);
				}
				case EXPORTS, OPENS -> {
					boolean isExport = w.type() == EXPORTS;
					if (!isExport && moduleIsOpen) ctx.report(Kind.ERROR, "module.open");

					String from = readRef();

					var info = new AttrModule.Export(from);
					(isExport?a.exports:a.opens).add(info);

					if (!names.add((isExport?"E;":"O;")+from)) ctx.report(Kind.ERROR, "module.dup.exports", from);

					w = wr.next();
					if (w.type() == TO) {
						do {
							String to = readRef();
							if (!names.add((isExport?"E;":"O;")+from+';'+to)) ctx.report(Kind.ERROR, "module.dup.exports2", from, to);

							info.to.add(to);

							w = wr.next();
						} while (w.type() == comma);
					}
				}
				case USES -> {
					String spi = readRef();
					w = wr.next();

					if (!names.add("U;"+spi)) ctx.report(Kind.ERROR, "module.dup.uses", spi);
					a.uses.add(spi);
				}
				case PROVIDES -> {
					String spi = readRef();

					var info = new AttrModule.Provide(spi);
					a.provides.add(info);

					if (!names.add("P;"+spi)) ctx.report(Kind.ERROR, "module.dup.provides", spi);

					w = wr.next();
					if (w.type() == WITH) {
						do {
							String to = readRef();
							if (!names.add("P;"+spi+";"+to)) ctx.report(Kind.ERROR, "module.dup.provides2", spi, to);

							info.impl.add(to);

							w = wr.next();
						} while (w.type() == comma);
					}
				}
			}

			if (w.type() != semicolon) ctx.report(Kind.ERROR, "module.semicolon");
		}

		ctx.classes.setModule(a);
	}
	// endregion
	// region 阶段1 类的结构
	private void header(int acc) throws ParseException {
		JavaLexer wr = ctx.lexer;

		// 虽然看起来很怪，但是确实满足了this inner和helper正确的javadoc提交逻辑
		_parent.commitJavadoc(this);
		// 修饰符和注解
		commitAnnotations(this);

		// 类型(class enum...)
		Word w = wr.next();
		switch (w.type()) {
			case INTERFACE: // interface
				if ((acc & (ACC_FINAL)) != 0) ctx.report(Kind.ERROR, "modifier.conflict:interface:final");
				acc |= ACC_ABSTRACT|ACC_INTERFACE;
			break;
			case AT_INTERFACE: // @interface
				if ((acc & (ACC_FINAL)) != 0) ctx.report(Kind.ERROR, "modifier.conflict:@interface:final");
				acc |= ACC_ANNOTATION|ACC_INTERFACE|ACC_ABSTRACT;
				addInterface("java/lang/annotation/Annotation");
			break;
			case ENUM: // enum
				acc |= ACC_ENUM|ACC_FINAL;
				parent("java/lang/Enum");
			break;
			case RECORD:
				setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_17);
				acc |= ACC_FINAL|_ACC_RECORD;
				parent("java/lang/Record");
			break;
			case STRUCT:
				acc |= ACC_FINAL|_ACC_RECORD|_ACC_STRUCT;
				parent("roj/compiler/runtime/Struct");
			break;
			case CLASS:
				acc |= ACC_SUPER;
			break;
			default: throw wr.err("unexpected_2:"+w.val()+":cu.except.type");
		}
		modifier = (char)acc;
		extraModifier = acc;

		// 名称
		w = wr.except(LITERAL, "cu.name");
		classIdx = w.pos()+1;

		if (ctx.classes.hasFeature(LavaFeatures.VERIFY_FILENAME) && (acc&ACC_PUBLIC) != 0 && _parent == this && !IOUtil.fileName(source).equals(w.val())) {
			ctx.report(Kind.SEVERE_WARNING, "cu.filename", source);
		}

		name(name.concat(w.val()));

		// 泛型参数和范围
		w = wr.next();
		if (w.type() == lss) { // <
			genericDecl(wr);

			// 泛型非静态类
			if ((acc & _ACC_INNER_CLASS) != 0) {
				CompileUnit t = _parent;
				do {
					if ((currentNode.parent = t.signature) != null) break;
					t = t._parent;
				} while (t._parent != t);
			}

			w = wr.next();
		}

		// 继承
		checkExtends:
		if (w.type() == EXTENDS) {
			if ((acc & (ACC_ENUM|ACC_ANNOTATION|_ACC_RECORD)) != 0) ctx.report(Kind.ERROR, "cu.noInheritance", name, parent);
			if ((acc & ACC_INTERFACE) != 0) break checkExtends;

			IType type = readType(wr, TYPE_GENERIC|TYPE_NO_ARRAY);
			if (type.genericType() > 0 || currentNode != null) makeSignature()._add(type);
			parent(type.owner());

			w = wr.next();
		} else if ((acc & ACC_ENUM) != 0) {
			makeSignature()._add(new Generic("java/lang/Enum", Collections.singletonList(new Type(name))));
		} else if (currentNode != null) {
			currentNode._add(LocalContext.OBJECT_TYPE);
		}

		structCheck:{
		// record
		recordCheck:
		if ((acc & _ACC_RECORD) != 0) {
			if (w.type() != lParen) {
				ctx.report(Kind.ERROR, "cu.record.header");
				break recordCheck;
			}

			var classSelf = currentNode;
			currentNode = null;

			if (!wr.nextIf(rParen)) do {
				_modifiers(wr, _ACC_ANNOTATION);

				IType type = readType(wr, TYPE_PRIMITIVE|TYPE_GENERIC|SKIP_TYPE_PARAM);
				if (type.genericType() != 0) makeSignature().returns = type;

				String name = wr.except(LITERAL, "cu.name").val();

				FieldNode field = new FieldNode(ACC_PUBLIC|ACC_FINAL, name, type.rawType());

				commitAnnotations(field);
				finishSignature(null, Signature.FIELD, field);

				fields.add(field);
				fieldIdx.add(w.pos());

				w = wr.next();
				if (w.type() == lBracket) {
					// C-style array definition
					field.modifier |= ACC_NATIVE; // SPECIAL PROCESSING

					List<AnnVal> list = new SimpleList<>();
					do {
						list.add(AnnValInt.valueOf(wr.except(INTEGER).asInt()));
						wr.except(rBracket);
						w = wr.next();
					} while (w.type() == lBracket);

					Type clone = field.fieldType().clone();
					clone.setArrayDim(list.size());
					field.fieldType(clone);

					field.putAttr(new AnnotationDefault(new AnnValArray(list)));
				}
			} while (w.type() == comma);

			currentNode = classSelf;

			if (w.type() != rParen) throw wr.err("unexpected_2:"+w.val()+":cu.except.record");
			w = wr.next();

			miscFieldId = fields.size();

			if ((acc & _ACC_STRUCT) != 0) {
				modifier |= ACC_NATIVE;
				if (w.type() != lBrace || !wr.nextIf(rBrace)) throw wr.err("cu.struct.antibody");
				break structCheck;
			}
		}

		// 实现
		if (w.type() == ((acc & ACC_INTERFACE) != 0 ? EXTENDS : IMPLEMENTS)) {
			var interfaces = interfaceWritable();
			do {
				IType type = readType(wr, TYPE_GENERIC|TYPE_NO_ARRAY);
				if (type.genericType() > 0 || currentNode != null) makeSignature()._impl(type);
				interfaces.add(new CstClass(type.owner()));

				w = wr.next();
			} while (w.type() == comma);
		}

		// 密封
		sealedCheck:
		if ((acc & _ACC_SEALED) != 0) {
			if (w.type() != PERMITS) {
				ctx.report(Kind.ERROR, "cu.sealed.noPermits");
				break sealedCheck;
			}

			var subclasses = new AttrClassList(Attribute.PermittedSubclasses);
			putAttr(subclasses);

			do {
				IType type = readType(wr, TYPE_NO_ARRAY);
				subclasses.value.add(type.owner());

				w = wr.next();
			} while (w.type() == comma);
		}

		wr.retractWord();

		signature = finishSignature(_parent == this ? null : _parent.signature, Signature.CLASS, this);
		for (int i = 0; i < miscFieldId; i++) {
			var sign = (LPSignature) fields.get(i).attrByName("Signature");
			if (sign != null) sign.parent = signature;
		}

		body();
		}
	}
	private void body() throws ParseException {
		LocalContext ctx = this.ctx;
		JavaLexer wr = ctx.lexer;
		Word w;

		wr.except(lBrace);

		var names = ctx.tmpSet; names.clear();
		// for record
		for (int i = 0; i < fields.size(); i++) {
			String name = fields.get(i).name();
			if (!names.add(name)) ctx.classes.report(this, Kind.ERROR, fieldIdx.get(i), "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
		}

		// 枚举的字段
		if ((modifier & ACC_ENUM) != 0) enumFields(wr, names, ctx);

		// ## 3. 方法，字段，静态初始化，动态初始化，内部类

		int acc;
		mfsdcLoop:
		while (wr.hasNext()) {
			w = wr.next();
			switch (w.type()) {
				case semicolon: continue;
				case rBrace: break mfsdcLoop;
				default: wr.retractWord(); break;
			}

			// ## 3.1 访问级别和注解
			acc = ACC_PUBLIC|ACC_STATIC|ACC_STRICT|ACC_FINAL|ACC_ABSTRACT|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED|_ACC_JAVADOC|_ACC_ASYNC;
			switch (modifier & (ACC_INTERFACE|ACC_ANNOTATION)) {
				case ACC_INTERFACE:
					acc |= _ACC_DEFAULT|ACC_PRIVATE;
					break;
				case ACC_INTERFACE|ACC_ANNOTATION:
					break;
				case 0:
					acc |= ACC_NATIVE|ACC_PRIVATE|ACC_PROTECTED|ACC_TRANSIENT|ACC_VOLATILE|ACC_SYNCHRONIZED;
					break;
			}
			acc = _modifiers(wr, acc);

			if ((modifier & ACC_INTERFACE) != 0) {
				if ((acc & ACC_PRIVATE) == 0) {
					acc |= ACC_PUBLIC;
				} else {
					setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_9);
				}
			}

			// ## 3.2 泛型参数<T>
			w = wr.next();
			if (w.type() == lss) {
				genericDecl(wr);
				w = wr.next();
			}

			// ## 3.3.1 初始化和内部类
			switch (w.type()) {
				case lBrace: // static initializator
					commitJavadoc(null);
					if ((acc& ~(ACC_STATIC|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED)) != 0) ctx.report(Kind.ERROR, "modifier.conflict", showModifiers(acc & ~ACC_STATIC, ACC_SHOW_METHOD), "cu.except.initBlock");
					if ((acc & ACC_STATIC) == 0) {
						if ((acc & ACC_INTERFACE) != 0) ctx.report(Kind.ERROR, "cu.interfaceInit");
						lazyTasks.add(ParseTask.InstanceInitBlock(this));
					} else {
						// no interface checks 😄
						lazyTasks.add(ParseTask.StaticInitBlock(this));
					}
					if (currentNode != null) {
						ctx.report(Kind.ERROR, "type.illegalGenericDecl");
						currentNode = null;
					}
				continue;
				case CLASS, INTERFACE, ENUM, AT_INTERFACE, RECORD:
					if (currentNode != null) {
						ctx.report(Kind.ERROR, "type.illegalGenericDecl");
						currentNode = null;
					}

					wr.retractWord();
					if ((acc&ACC_CLASS_ILLEGAL) != 0) ctx.report(Kind.ERROR, "modifier.notAllowed", showModifiers(acc&ACC_CLASS_ILLEGAL, ACC_SHOW_METHOD));
					_newInner(acc);
				continue;
				default:
					if ((acc & (_ACC_SEALED|_ACC_NON_SEALED)) != 0) ctx.report(Kind.ERROR, "modifier.conflict", "sealed/non-sealed", "method/field");
			}

			String name;
			Type type;

			// 3.3.2 方法、字段、构造器
			mof: {
				constructor:
				if (isConstructor(w.val())) {
					wr.mark();
					if (wr.next().type() != lParen) {
						wr.retract();
						break constructor;
					}
					wr.skip();

					name = "<init>";
					type = Type.std(Type.VOID);

					// 接口不能有构造器
					if ((modifier & ACC_INTERFACE) != 0)
						ctx.report(Kind.ERROR, "cu.interfaceInit");

					// 枚举必须是private构造器
					else if ((modifier & ACC_ENUM) != 0) {
						if ((acc & (ACC_PUBLIC|ACC_PROTECTED)) != 0) {
							ctx.report(Kind.ERROR, "cu.enumInit");
						} else {
							acc |= ACC_PRIVATE;
						}
					}
					break mof;
				}

				// ## 5.1.3 类型
				IType type1;
				if (w.type() == lBracket) {
					// [ra, rb, rc]
					Generic g = new Generic(GeneratorUtil.RETURNSTACK_TYPE, 0, Generic.EX_NONE);
					type1 = g;
					do {
						g.addChild(readType(wr, TYPE_PRIMITIVE|TYPE_GENERIC));
						w = wr.next();
					} while (w.type() == comma);
					if (w.type() != rBracket) throw wr.err("unexpected_2:"+w.type()+":cu.except.multiArg");
				} else {
					wr.retractWord();
					type1 = readType(wr, TYPE_PRIMITIVE|TYPE_GENERIC|TYPE_ALLOW_VOID|SKIP_TYPE_PARAM);
				}

				if (type1.genericType() != 0)
					makeSignature().returns = type1;
				type = type1.rawType();

				// method or field
				name = wr.except(LITERAL, "cu.name").val();

				w = wr.next();
			}

			if (w.type() == lParen) { // method
				methodIdx.add(w.pos());
				if ((acc&ACC_METHOD_ILLEGAL) != 0) ctx.report(Kind.ERROR, "modifier.notAllowed", showModifiers(acc&ACC_METHOD_ILLEGAL, ACC_SHOW_FIELD));
				if ((acc & ACC_ABSTRACT) != 0) {
					if ((acc&ACC_STRICT) != 0) ctx.report(Kind.ERROR, "modifier.conflict:strictfp:abstract");
					if ((modifier & ACC_ABSTRACT) == 0) ctx.report(Kind.ERROR, "cu.method.noAbstract", this.name, name);
				}

				MethodNode method = new MethodNode(acc, this.name, name, "()V");
				commitJavadoc(method);
				method.setReturnType(type);
				commitAnnotations(method);

				List<String> paramNames = ctx.tmpList; paramNames.clear();

				if (name.equals("<init>") && (modifier&ACC_ENUM) != 0) {
					paramNames.add("@name");
					paramNames.add("@ordinal");
					var par = method.parameters();
					par.add(EnumUtil.TYPE_STRING);
					par.add(Type.std(Type.INT));
				}

				w = wr.next();
				if (w.type() != rParen) {
					wr.retractWord();

					boolean lsVarargs = false;
					MethodParameters parAccName;
					if (ctx.classes.hasFeature(LavaFeatures.ATTR_METHOD_PARAMETERS)) {
						parAccName = new MethodParameters();
						method.putAttr(parAccName);
						if (name.equals("<init>") && (modifier&ACC_ENUM) != 0) {
							parAccName.flags.add(new MethodParam(null, ACC_SYNTHETIC));
							parAccName.flags.add(new MethodParam(null, ACC_SYNTHETIC));
						}
					} else {
						parAccName = null;
					}

					do {
						if (lsVarargs) ctx.report(Kind.ERROR, "cu.method.paramVararg");

						int acc1 = _modifiers(wr, ACC_FINAL|_ACC_ANNOTATION);

						IType parType = readType(wr, TYPE_PRIMITIVE|TYPE_GENERIC|SKIP_TYPE_PARAM);
						if (parType.genericType() != 0) makeSignature()._add(paramNames.size(), (Generic) parType);

						w = wr.next();
						if (w.type() == varargs) {
							lsVarargs = true;
							w = wr.next();
						}

						List<Type> p = method.parameters();
						p.add(parType.rawType());
						if (parType.rawType().type == VOID) ctx.report(Kind.ERROR, "cu.method.paramVoid");
						if (p.size() > 255) ctx.report(Kind.ERROR, "cu.method.paramCount");

						if (!ctx.tmpAnnotations.isEmpty()) {
							Attributed node = new ParamAnnotationRef(method, w.pos(), paramNames.size());
							annoTask.put(node, new SimpleList<>(ctx.tmpAnnotations));
						}

						if (w.type() == LITERAL) {
							var pname = w.val();
							if (pname.equals("_")) {
								pname = "@skip";
							} else {
								if (paramNames.contains(pname)) ctx.report(Kind.ERROR, "cu.method.paramConflict");
							}
							paramNames.add(pname);
						} else {
							throw wr.err("unexpected:"+w.val());
						}

						if (parAccName != null) {
							parAccName.flags.add(new MethodParam(w.val(), (char) acc1));
						}

						w = wr.next();
						if (w.type() == assign) {
							ParseTask.MethodDefault(this, method, paramNames.size());
							w = wr.next();
						}
					} while (w.type() == comma);

					if (w.type() != rParen) throw wr.err("unexpected:"+w.val());

					if (lsVarargs) acc |= ACC_VARARGS;
				}

				method.modifier = (char) acc;
				methods.add(method);

				w = wr.next();
				// throws XX异常
				if (w.type() == THROWS) {
					if ((modifier & ACC_ANNOTATION) != 0) {
						ctx.report(Kind.ERROR, "cu.method.annotationThrow");
					}

					var excList = new AttrClassList(Attribute.Exceptions);
					method.putAttr(excList);

					do {
						IType type1 = readType(wr, TYPE_GENERIC|TYPE_NO_ARRAY);
						excList.value.add(type1.owner());
						if (type1.genericType() != 0) makeSignature().Throws.add(type1);

						w = wr.next();
					} while (w.type() == comma);
				}

				finishSignature(signature, Signature.METHOD, method);

				// 不能包含方法体:
				noMethodBody: {
					//   被abstract或native修饰
					if ((acc & (ACC_ABSTRACT|ACC_NATIVE)) != 0) break noMethodBody;

					//   是注解且没有default
					//   注解不允许静态方法
					if ((modifier & ACC_ANNOTATION) != 0) {
						// 注解的default
						if (w.type() == DEFAULT) {
							lazyTasks.add(ParseTask.AnnotationDefault(this, method));
							continue;
						}

						break noMethodBody;
					}

					//   是接口且没有default且不是static
					if ((modifier & ACC_INTERFACE) != 0) {
						if ((acc & (ACC_STATIC|_ACC_DEFAULT|ACC_PRIVATE)) == 0) {
							if ((acc & ACC_FINAL) != 0) {
								// 接口不能加final
								ctx.report(Kind.ERROR, "modifier.notAllowed:final");
							}
							break noMethodBody;
						}
					}

					if (w.type() != lBrace) {
						ctx.report(Kind.ERROR, "cu.method.mustHasBody");
						break noMethodBody;
					}

					lazyTasks.add((acc & _ACC_ASYNC) != 0
						? GeneratorUtil.Generator(this, method, ArrayUtil.copyOf(paramNames))
						: ParseTask.Method(this, method, ArrayUtil.copyOf(paramNames)));
					continue;
				}

				if ((acc & ACC_NATIVE) == 0) method.modifier |= ACC_ABSTRACT;
				if (w.type() != semicolon) throw wr.err("cu.method.mustNotBody");
			} else {
				// field
				fieldAcc(acc, ACC_FIELD_ILLEGAL);
				// 接口的字段必须是静态的
				if ((modifier & ACC_INTERFACE) != 0) {
					fieldAcc(acc, ACC_PRIVATE);
					acc |= ACC_STATIC|ACC_FINAL;
				}

				wr.retractWord();

				Signature s = finishSignature(signature, Signature.FIELD, null);

				var list = ctx.tmpAnnotations.isEmpty() ? null : new SimpleList<>(ctx.tmpAnnotations);

				while (true) {
					FieldNode field = new FieldNode(acc, name, type);
					commitJavadoc(field);
					if (!names.add(name)) {
						ctx.report(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
					}

					if (list != null) annoTask.put(field, list);
					if (s != null) field.putAttr(s);

					fields.add(field);
					if ((acc & ACC_FINAL) != 0) finalFields.add(field);
					fieldIdx.add(w.pos());

					w = wr.next();
					if (w.type() == assign) lazyTasks.add(ParseTask.Field(this, field));

					if (w.type() != comma) {
						if (w.type() != semicolon) throw wr.err("cu.except.fieldEnd");
						break;
					}
					name = wr.except(LITERAL, "cu.except.fieldName").val();
				}
			}
		}
	}
	private void enumFields(JavaLexer wr, MyHashSet<String> names, LocalContext ctx) throws ParseException {
		assert fields.isEmpty();

		Type selfType = new Type(name);

		List<ExprNode> enumInit = new SimpleList<>();

		Word w = wr.next();
		while (w.type() == LITERAL) {
			String name = w.val();
			if (!names.add(name)) ctx.report(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);

			fieldIdx.add(wr.index);

			FieldNode f = new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM, name, selfType);

			// maybe should putin ParseTask?

			List<ExprNode> args = new SimpleList<>();
			args.add(Constant.valueOf(f.name()));
			args.add(Constant.valueOf(AnnVal.valueOf(fields.size())));

			int start1 = wr.current().pos();

			w = wr.next();
			if (w.type() == lParen) {
				int state = wr.setState(STATE_EXPR);

				while (true) {
					var expr1 = (ExprNode) ctx.ep.parse(ExprParser.STOP_RSB|ExprParser.STOP_COMMA|ExprParser.SKIP_COMMA|ExprParser._ENV_INVOKE|ExprParser._ENV_TYPED_ARRAY);
					// noinspection all
					if (expr1 == null || (args.add(expr1) & expr1.getClass().getName().endsWith("NamedParamList"))) {
						wr.except(rParen);
						w = wr.next();
						break;
					}
				}

				wr.state = state;
			}

			ExprNode expr = ctx.ep.newInvoke(selfType, ctx.ep.copyOf(args));
			expr.wordStart = start1;

			if (w.type() == lBrace) {
				modifier &= ~ACC_FINAL;

				wr.retractWord();
				assert wr.state == STATE_CLASS;

				var _type = newAnonymousClass(null);
				expr = ctx.ep.newAnonymousClass(expr, _type);

				if (ctx.classes.hasFeature(LavaFeatures.SEALED_ENUM)) {
					setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_17);

					var ps = (AttrClassList) attrByName("PermittedSubclasses");
					if (ps == null) putAttr(ps = new AttrClassList(Attribute.PermittedSubclasses));
					ps.value.add(_type.name);
				}

				w = wr.next();
			}
			// abstract enum 的检测在invoke里解决了

			enumInit.add(expr);
			fields.add(f);

			if (w.type() != comma) break;
			w = wr.next();
		}

		if (!enumInit.isEmpty()) {
			lazyTasks.add((lc) -> {
				var cw = getStaticInit();
				lc.setMethod(cw.mn);

				for (int i = 0; i < enumInit.size(); i++) {
					lc.lexer.index = fieldIdx.get(i);

					enumInit.get(i).resolve(lc).write(cw);
					cw.field(PUTSTATIC, this, i);

					finalFields.remove(fields.get(i));
				}

				cw.newArraySized(selfType, enumInit.size());
				cw.visitSizeMax(4, 0); // array array index value
				for (int i = 0; i < enumInit.size(); i++) {
					cw.one(DUP);
					cw.ldc(i);
					cw.field(GETSTATIC, this, i);
					cw.one(AASTORE);
				}

				cw.field(PUTSTATIC, this, miscFieldId/* $VALUES */);
			});
		}

		if (w.type() != semicolon) wr.retractWord();
	}
	private void fieldAcc(int acc, int mask) {if ((acc&mask) != 0) ctx.report(Kind.ERROR, "modifier.notAllowed", showModifiers(acc&mask, ACC_SHOW_METHOD));}
	private boolean isConstructor(String val) {
		int i = name.lastIndexOf('$');
		if (i < 0) i = name.lastIndexOf('/');
		return val.regionMatches(0, name, i+1, name.length() - i - 1);
	}

	public int _modifiers(JavaLexer wr, int mask) throws ParseException {
		if ((mask & _ACC_ANNOTATION) != 0) ctx.tmpAnnotations.clear();
		if ((mask & _ACC_JAVADOC) != 0) {
			// TODO javadoc
		}

		Word w;
		int acc = 0;
		while (true) {
			int f;
			w = wr.next();
			switch (w.type()) {
				case at -> {
					if ((mask & _ACC_ANNOTATION) == 0) ctx.report(Kind.ERROR, "modifier.annotation");
					_annotations(ctx.tmpAnnotations);
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
	private void commitJavadoc(Attributed node) {

	}

	public static final int TYPE_PRIMITIVE = 1, TYPE_GENERIC = 2, TYPE_NO_ARRAY = 4, TYPE_ALLOW_VOID = 8;
	private static final int GENERIC_INNER = 8, SKIP_TYPE_PARAM = 16;
	// this function only invoke on Stage 4
	public IType readType(@MagicConstant(flags = {TYPE_PRIMITIVE, TYPE_GENERIC, TYPE_NO_ARRAY, TYPE_ALLOW_VOID}) int flags) throws ParseException {
		IType type = readType(ctx.lexer, flags);
		if (currentNode != null && type instanceof LPGeneric g)
			return currentNode.applyTypeParam(g);
		return type;
	}
	/**
	 * 解析类型
	 */
	private IType readType(JavaLexer wr, int flags) throws ParseException {
		Word w = wr.next();
		IType type = FastPrimitive.get(w.type());

		if (type == null) {
			if (w.type() != LITERAL) throw wr.err("type.illegalType:"+w.val());
			wr.retractWord();

			CharList sb = ctx.tmpSb;
			readRef(wr, sb, false);
			String klass = sb.toString();

			if ((flags&TYPE_GENERIC) != 0) {
				int prev = wr.state;
				wr.state = STATE_TYPE;

				try {
					if (wr.nextIf(lss))
						return readGeneric(klass, flags&(TYPE_NO_ARRAY|GENERIC_INNER));
				} finally {
					wr.state = prev;
				}
			}

			type = currentNode == null || !currentNode.isTypeParam(klass) ? new Type(klass) : (flags&SKIP_TYPE_PARAM) != 0 ? new LPGeneric(klass) : new TypeParam(klass);
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

	private String readRef() throws ParseException {
		CharList sb = ctx.tmpSb;
		readRef(ctx.lexer, sb, false);
		return sb.toString();
	}
	/**
	 * 解析引用类型 (a.b.c)
	 * @param allowStar allow * (in import)
	 */
	private static void readRef(JavaLexer wr, CharList sb, boolean allowStar) throws ParseException {
		sb.clear();

		while (true) {
			Word w = wr.next();
			if (w.type() == LITERAL) {
				sb.append(w.val());
			} else if (allowStar && w.type() == mul) {
				sb.append("*");
				break;
			} else {
				throw wr.err("unexpected:"+w.val());
			}

			if (!wr.nextIf(dot)) break;
			sb.append('/');
		}
	}

	public IType genericTypePart(String type) throws ParseException {
		int prev = ctx.lexer.setState(STATE_TYPE);
		try {
			return readGeneric(type, 0);
		} finally {
			ctx.lexer.state = prev;
		}
	}
	/**
	 * 解析泛型部件 (从&lt;开始，不包括&lt;)
	 */
	private IType readGeneric(String type, int flags) throws ParseException {
		var wr = ctx.lexer;

		var g = new LPGeneric();
		g.wrPos = wr.index;
		g.owner = type;

		Word w = wr.next();

		if (w.type() == gtr) {
			// 钻石操作符<>
			if (g.children.isEmpty()) {
				if ((flags&GENERIC_INNER) != 0) ctx.report(Kind.ERROR, "type.illegalAnyType");
				else g.addChild(Asterisk.anyGeneric);
			}
		} else while (true) {
			byte extendType = 0;
			// <? extends|super Type
			if (w.type() == ask) {
				w = wr.next();
				switch (w.type()) {
					// A<? super
					case SUPER: extendType = Generic.EX_SUPER; break;
					case EXTENDS: extendType = Generic.EX_EXTENDS; break;
					// A<?,
					case comma: wr.retractWord(); g.addChild(Signature.any()); continue;
					default: wr.unexpected(w.val(), "type.except.afterAsk");
				}
			} else {
				wr.retractWord();
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
			if (w.type() != comma) break;
			w = wr.next();
		}

		if (w.type() != gtr) wr.unexpected(w.val(), "type.except.afterLss");
		w = wr.next();

		// GenericSub
		if (w.type() == dot) {
			IType sub = readGeneric(wr.except(LITERAL).val(), TYPE_NO_ARRAY);
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
		return g;
	}

	private LPSignature makeSignature() {
		if (currentNode == null) currentNode = new LPSignature(0);
		return currentNode;
	}
	private LPSignature finishSignature(LPSignature parent, byte kind, Attributed attr) {
		var sign = currentNode;
		if (sign == null) return null;
		currentNode = null;

		sign.parent = parent;
		sign.type = kind;

		sign.applyTypeParam(attr instanceof MethodNode mn ? mn : null);
		if (attr != null) attr.putAttr(sign);
		return sign;
	}

	// <T extends YYY<T, V> & ZZZ, V extends T & XXX>
	private void genericDecl(JavaLexer wr) throws ParseException {
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

	public List<AnnotationPrimer> _annotations(List<AnnotationPrimer> list) throws ParseException {
		JavaLexer wr = ctx.lexer;
		CharList tmp = ctx.tmpSb;

		while (true) {
			int pos = wr.index;
			readRef(wr, tmp, false);

			var a = new AnnotationPrimer(tmp.toString(), pos+1);
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

			if (w.type() != rParen) throw wr.err(a.valueOnly ? "cu.annotation.valueOnly" : "unexpected:"+w.val());

			w = wr.next();
			if (w.type() != at) {
				wr.retractWord();
				return list;
			}
		}
	}
	private void commitAnnotations(Attributed node) {
		if (!ctx.tmpAnnotations.isEmpty()) annoTask.put(node, new SimpleList<>(ctx.tmpAnnotations));
	}
	// endregion
	// region 阶段2 解析并验证类自身的引用
	public void S2_ResolveSelf() {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		// init TypeResolver
		tr.init(ctx);
		var wr = ctx.lexer;

		wr.index = classIdx;
		var s1 = signature;
		if (s1 != null) {
			currentNode = s1;
			s1.resolve(ctx);
		}

		for (int i = 0; i < fieldIdx.size(); i++) {
			wr.index = fieldIdx.get(i);
			var field = fields.get(i);
			var s = (LPSignature) field.attrByName("Signature");
			if (s != null) {
				currentNode = s;
				s.resolve(ctx);
				field.fieldType(s.typeParamToBound(field.fieldType()));

				ctx.disableRawTypeWarning = true;
			}
			field.fieldType(ctx.transformPseudoType(ctx.resolveType(field.fieldType())).rawType());

			ctx.disableRawTypeWarning = false;
		}
		for (int i = 0; i < methodIdx.size(); i++) {
			wr.index = methodIdx.get(i);
			var method = methods.get(i);
			var s = (LPSignature) method.attrByName("Signature");
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

		// extends
		var pInfo = tr.resolve(ctx, parent);
        if (pInfo == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", parent, name+".parent");
		} else {
            int acc = pInfo.modifier;
			if (0 != (acc & ACC_FINAL)) {
				ctx.report(Kind.ERROR, "cu.resolve.notInheritable", "cu.final", parent);
			} else if (0 != (acc & ACC_INTERFACE)) {
				ctx.report(Kind.ERROR, "cu.resolve.notInheritable", "cu.interface", parent);
			}

			parent(pInfo.name());
        }

		// implements
		var itfs = interfaces;
		for (int i = 0; i < itfs.size(); i++) {
			String iname = itfs.get(i).name().str();
			var info = tr.resolve(ctx, iname);
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
			var ps = (AttrClassList) attrByName("PermittedSubclasses");

			List<String> value = ps.value;
			for (int i = 0; i < value.size(); i++) {
				String type = value.get(i);
				var info = tr.resolve(ctx, type);

				if (info == null) {
					ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", type, name+".permits["+i+"]");
				} else {
					value.set(i, info.name);
				}
			}
		}

		// annotation
		for (var list : annoTask.values()) {
			for (int i = 0; i < list.size(); i++) {
				var a = list.get(i);
				resolveAnnotationType(ctx, a);

				for (Iterator<?> itr = a.values.values().iterator(); itr.hasNext(); ) {
					if (itr.next() instanceof AnnVal value) {
						if (value instanceof AnnValArray array) {
							for (AnnVal val : array.value) {
								var a1 = (AnnotationPrimer) ((AnnValAnnotation) val).value;
								resolveAnnotationType(ctx, a1);
							}
						} else {
							var a1 = (AnnotationPrimer) ((AnnValAnnotation) value).value;
							resolveAnnotationType(ctx, a1);
						}
					}
				}
			}
		}
	}
	private void resolveAnnotationType(LocalContext ctx, AnnotationPrimer a) {
		ctx.lexer.index = a.pos;
		var type = tr.resolve(ctx, a.type());
		if (type == null) {
			ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", a.type(), ctx.currentCodeBlockForReport());
		} else {
			a.setType(type.name());
		}
	}

	public void S2_ResolveRef() {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		ctx.lexer.index = classIdx;

		// 检测循环继承
		ctx.parentListOrReport(this);

		// 泛型异常
		if (signature != null && ctx.instanceOf(parent, "java/lang/Throwable")) {
			ctx.report(Kind.ERROR, "cu.genericException");
		}

		overridableMethods.methods = new SimpleList<>();
		overridableMethods.itf = Collections.emptyList();
		var names = ctx.tmpSet; names.clear();

		{
		String name = parent;
		int i = 0;
		var itfs = interfaces;
		while (true) {
			var info = ctx.classes.getClassInfo(name);

			ctx.assertAccessible(info);
			collectMethods(ctx, info, i != 0);

			// sealed
			var ps = info.parsedAttr(info.cp, Attribute.PermittedSubclasses);
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

		// use extraModifier so that anonymous class can disable it
		boolean autoInit = (extraModifier & ACC_INTERFACE) == 0;
		// 方法冲突在这里检查，因为stage0/1拿不到完整的rawDesc
		names.clear();
		List<MethodNode> methods = this.methods;
		for (int i = 0; i < methodIdx.size(); i++) {
			ctx.lexer.index = methodIdx.get(i);
			var method = methods.get(i);

			String par = method.rawDesc();
			if (!names.add(method.name()+par.substring(0, par.lastIndexOf(')')+1)))
				ctx.report(Kind.ERROR, "cu.nameConflict", this.name, "invoke.method", method.name());

			if ((modifier&ACC_ANNOTATION) != 0 && (method.modifier&ACC_STATIC) == 0) {
				checkAnnotationReturnType(method.returnType());
			}

			if (method.name().equals("<init>")) {
				autoInit = false;
			}

			// resolve异常
			AttrClassList exThrown = (AttrClassList) method.attrByName("Exceptions");
			if (exThrown != null) {
				List<String> classes = exThrown.value;
				for (int j = 0; j < classes.size(); j++) {
					IClass info = tr.resolve(ctx, classes.get(j));
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

		autoInit:
		if (autoInit) {
			if ((modifier&ACC_ENUM) != 0) {
				var cw = glinit = newWritableMethod(ACC_PUBLIC|ACC_SYNTHETIC, "<init>", "(Ljava/lang/String;I)V");
				cw.visitSize(3,3);
				cw.one(ALOAD_0);
				cw.one(ALOAD_1);
				cw.one(ILOAD_2);
				cw.invoke(INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V");
				break autoInit;
			}

			var pInfo = ctx.classes.getClassInfo(parent);

			int superInit = pInfo.getMethod("<init>", "()V");
			if (superInit < 0) {
				ctx.report(Kind.ERROR, "cu.noDefaultConstructor", parent);
				break autoInit;
			}

			var mn = pInfo.methods().get(superInit);
			ctx.checkAccessible(pInfo, mn, false, true);
			if (_parent != this) j11PrivateConstructor(mn);

			var cw = glinit = newWritableMethod(ACC_PUBLIC|ACC_SYNTHETIC, "<init>", "()V");
			cw.visitSize(1,1);
			cw.one(ALOAD_0);
			cw.invoke(INVOKESPECIAL, parent, "<init>", "()V");
		}

		// permits validate
		if ((extraModifier&_ACC_SEALED) != 0) {
			var ps = (AttrClassList) attrByName("PermittedSubclasses");

			List<String> value = ps.value;
			for (int i = 0; i < value.size(); i++) {
				String type = value.get(i);
				var info = ctx.classes.getClassInfo(type);

				ctx.assertAccessible(info);
				if (!info.parent.equals(name) && !info.interfaces().contains(name)) {
					ctx.report(Kind.ERROR, "cu.sealed.indirectInherit", type, name);
				}
			}
		}

		// enum default method
		if ((modifier&ACC_ENUM) != 0) {
			String arrayType_ = "[L"+name+";";
			int fid = miscFieldId = newField(ACC_PRIVATE|ACC_STATIC|ACC_FINAL/*|ACC_SYNTHETIC*/, "$VALUES", arrayType_);

			// T[] values()
			var w = newMethod(ACC_PUBLIC|ACC_STATIC, "values", "()".concat(arrayType_));
			w.visitSize(1, 1);
			w.field(GETSTATIC, this, fid);
			w.invoke(INVOKEVIRTUAL, arrayType_, "clone", "()Ljava/lang/Object;");
			w.clazz(CHECKCAST, arrayType_);
			w.one(ARETURN);
			w.finish();

			// T valueOf(String name)
			w = newMethod(ACC_PUBLIC|ACC_STATIC, "valueOf", "(Ljava/lang/String;)L"+name+";");
			w.visitSize(2, 1);
			w.ldc(cp.getClazz(name));
			w.one(ALOAD_0);
			w.invokeS("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
			w.clazz(CHECKCAST, name);
			w.one(ARETURN);
			w.finish();
		}
	}
	// 检测抽象方法，可以覆盖的方法和多个default
	private void collectMethods(LocalContext ctx, IClass info, boolean itf) {
		var d = ctx.tmpNat;
		MyHashSet<Desc> hasDefault = Helpers.cast(ctx.tmpSet);

		for (ComponentList list : ctx.classes.getResolveHelper(info).getMethods(ctx.classes).values()) {
			var list1 = list.getMethods();
			for (int i = 0; i < list1.size(); i++) {
				var node = list1.get(i);
				if ((node.modifier&ACC_PRIVATE) != 0) continue;
				if (node.name().startsWith("<")) continue;
				if (itf && node.owner.equals("java/lang/Object")) continue;

				String param = node.rawDesc();
				param = param.substring(0, param.lastIndexOf(')')+1);

				d.owner = node.owner;
				d.name = node.name();
				d.param = param;

				if ((node.modifier & ACC_ABSTRACT) != 0) {
					// 不检测权限，就像你没法继承ByteBuffer一样
					if ((modifier&ACC_ABSTRACT) == 0) {
						// 如果已经有实现，那就不处理抽象
						if (hasDefault.contains(d)) continue;

						// 虽然有点像下面的，但目的只是让owner更具体
						var d1 = abstractOrUnrelated.intern(d);
						if (d != d1) {
							if (ctx.parentListOrReport(info).containsValue(d1.owner)) {
								assert itf;
								d1.flags = (char) cacheMethod(node);
								d1.owner = d.owner;
							}
						} else {
							d.flags = (char) (cacheMethod(node) | (itf ? 0 : PARENT_MARKER));
							d = new NameAndType();
						}
					}
				} else if (itf && (node.modifier&ACC_STATIC) == 0) {
					// 这里也是相同的部分，不过处理相反的顺序：
					// 如果已经有实现，那就不处理抽象
					var existing = abstractOrUnrelated.find(d);
					if (existing != d && (existing.flags&(UNRELATED_MARKER|PARENT_MARKER)) == 0)
						abstractOrUnrelated.remove(d);

					var d1 = hasDefault.intern(d);
					if (d != d1) {
						if (ctx.parentListOrReport(info).containsValue(d1.owner)) {
							d1.flags = (char) (cacheMethod(node) | UNRELATED_MARKER);
							d1.owner = d.owner;
						} else if (!ctx.parentListOrReport(ctx.classes.getClassInfo(d1.owner)).containsValue(d.owner)) {
							abstractOrUnrelated.add(d1);
						}
					} else {
						d.flags = (char) (cacheMethod(node) | UNRELATED_MARKER);
						d = new NameAndType();
					}
				}

				if (ctx.checkAccessible(info, node, false, false)) {
					if (getMethod(node.name()) >= 0) {
						List<?> methods1 = overridableMethods.methods;
						methods1.add(Helpers.cast(node));
					}
				}
			}
		}

		ctx.tmpNat = d;
	}
	private int cacheMethod(MethodNode node) {
		var cache = methodCache;
		cache.add(node);
		return cache.size();
	}
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

	private MethodWriter newWritableMethod(int acc, String name, String desc) {
		var mn = new MethodNode(acc, this.name, name, desc);
		methods.add(mn);
		if ((acc & (ACC_ABSTRACT|ACC_NATIVE)) != 0) return Helpers.nonnull();
		var cw = ctx.classes.createMethodWriter(this, mn);
		mn.putAttr(new AttrCodeWriter(cp, mn, cw));
		return cw;
	}
	// endregion
	// region 阶段3 注解处理 MethodDefault
	void _setSign(Attributed merhod) {
		var sign = (LPSignature) merhod.attrByName("Signature");
		currentNode = sign != null ? sign : signature;
	}
	public void S3_Annotation() throws ParseException {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		// region 方法覆盖检测
		var ovrh = new ResolveHelper(overridableMethods);
		var parh = ctx.classes.getResolveHelper(ctx.classes.getClassInfo(parent));
		var d = ctx.tmpNat;

		int size = methods.size();
		for (int i = 0; i < size; i++) {
			var my = methods.get(i);
			var ml = ovrh.findMethod(ctx.classes, my.name());
			if (ml == null) continue;

			_setSign(my);
			var rm = ctx.inferrer.getGenericParameters(this, my, null);

			List<IType> params;
			IType myRt;
			if (rm.desc != null) {
				var list = SimpleList.asModifiableList(rm.desc);
				myRt = list.pop();
				params = list;
			} else {
				myRt = my.returnType();
				params = Helpers.cast(my.parameters());
			}

			ctx.inferrer.overrideMode = true;
			var ri = ml.findMethod(ctx, new Type(name), params, ComponentList.NO_REPORT);
			ctx.inferrer.overrideMode = false;
			if (ri == null) continue;

			ctx.lexer.index = methodIdx.get(i);

			var it = ri.method;

			String param = it.rawDesc();
			d.name = it.name();
			d.param = param.substring(0, param.lastIndexOf(')')+1);
			var d1 = abstractOrUnrelated.removeValue(d);

			if (d1 != null && (d1.flags&PARENT_MARKER) != 0 && !ri.method.owner.equals(parent)) {
				// 未能覆盖父类的方法——通常是权限不够
				ri = parh.findMethod(ctx.classes, my.name()).findMethod(ctx, params, 0);
				assert ri == null;
				continue;
			}

			// 检查覆盖静态或访问权限降低
			checkDowngrade(my, it, ctx);

			// 检测Override
			var annotations = annoTask.getOrDefault(my, Collections.emptyList());
			for (int j = 0; j < annotations.size(); j++) {
				AnnotationPrimer a = annotations.get(j);
				if (a.type().equals("java/lang/Override")) {
					a.values = Collections.emptyMap();
					break;
				}
			}

			IType itRt = ri.desc != null ? ri.desc[ri.desc.length-1] : it.returnType();

			// 返回值更精确而需要桥接，或更不精确而无法覆盖
			var cast = ctx.inferrer.overrideCast(myRt, itRt);
			if (cast.type != 0) {
				String inline = "\1cu.override.returnType:\1typeCast.error."+cast.type+':'+myRt+':'+itRt+"\0\0";
				ctx.report(Kind.ERROR, "cu.override.unable", name, it.owner, it, inline);
			}

			// 生成桥接方法 这里不检测泛型(主要是TypeParam)
			if (!it.rawDesc().equals(my.rawDesc())) {
				createDelegation((it.modifier&(ACC_PUBLIC|ACC_PROTECTED)) | ACC_FINAL | ACC_SYNTHETIC | ACC_BRIDGE, my, it, true, false);
			}

			// 声明相同或更少的异常
			var myThrows = rm.getExceptions(ctx);
			if (!myThrows.isEmpty()) {
				List<IType> itThrows = ri.getExceptions(ctx);
				outer:
				for (IType f : myThrows) {
					if (ctx.castTo(f, RUNTIME_EXCEPTION, TypeCast.E_NEVER).type == 0) continue;

					for (IType type : itThrows) {
						TypeCast.Cast c = ctx.castTo(f, type, TypeCast.E_NEVER);
						if (c.type == 0) continue outer;
					}

					String inline = "\1cu.override.thrown:"+f.owner().replace('/', '.')+'\0';
					ctx.report(Kind.ERROR, "cu.override.unable", name, it.owner.replace('/', '.'), it, inline);
				}
			}
		}
		// endregion

		ctx.lexer.index = classIdx;
		for (Desc method : abstractOrUnrelated) {
			if (!method.owner.equals(parent)) {
				// check parent implement
				var ml = parh.findMethod(ctx.classes, method.name);
				if (ml != null) {
					var mn = methodCache.get(method.flags&16383);
					//Signature sign = mn.parsedAttr(info.cp(), Attribute.SIGNATURE);

					var r = ml.findMethod(ctx, Helpers.cast(mn.parameters()), ComponentList.NO_REPORT);
					if (r != null) {
						checkDowngrade(r.method, mn, ctx);
						continue;
					}
				}
			}

			if (method.flags == UNRELATED_MARKER) {
				ctx.report(Kind.ERROR, "cu.unrelatedDefault", method, name.replace('/', '.'));
			} else {
				ctx.report(Kind.ERROR, "cu.override.noImplement", name, method.owner.replace('/', '.'), method.name);
			}
		}
		abstractOrUnrelated.clear();

		// check annotations
		MyHashMap<String, Object> dup = ctx.tmpMap1, extra = ctx.tmpMap2;
		var missed = ctx.tmpSet;
		for (var annotated : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			dup.clear();
			var list = annotated.getValue();
			for (int i = 0; i < list.size(); i++) {
				var a = list.get(i);
				ctx.lexer.index = a.pos;

				String type = a.type();
				var desc = ctx.classes.getAnnotationDescriptor(ctx.classes.getClassInfo(type));

				Object prev;
				if (!desc.stackable && (prev = dup.putIfAbsent(type, i)) != null) {
					if (desc.repeatOn == null) ctx.report(Kind.ERROR, "cu.annotation.noRepeat", type);
					else {
						List<AnnVal> values;
						if (prev instanceof Integer p) {
							var removable = list.get(p);

							values = new SimpleList<>();
							values.add(new AnnValAnnotation(removable));

							var repeat = new AnnotationPrimer(desc.repeatOn, removable.pos);
							repeat.values = Collections.singletonMap("value", new AnnValArray(values));
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

						values.add(new AnnValAnnotation(a));
						list.remove(i--);
					}
				} else {
					if (!applicableToNode(desc, annotated.getKey())) {
						ctx.report(Kind.ERROR, "cu.annotation.notApplicable", type, annotated.getKey().getClass());
					}

					if (desc.kind != AnnotationSelf.SOURCE) {
						if (desc.kind == AnnotationSelf.CLASS) {
							if (inv == null) {
								inv = new Annotations(false);
								annotated.getKey().putAttr(inv);
							}
							inv.annotations.add(a);
						} else {
							if (vis == null) {
								vis = new Annotations(true);
								annotated.getKey().putAttr(vis);
							}
							vis.annotations.add(a);
						}
					}
				}

				missed.clear();
				extra.clear(); extra.putAll(a.values);

				for (Map.Entry<String, Type> entry : desc.types.entrySet()) {
					String name = entry.getKey();

					Object node = Helpers.cast(extra.remove(name));
					if (node instanceof ExprNode expr) a.values.put(name, AnnotationPrimer.toAnnVal(ctx, expr, entry.getValue()));
					else if (node == null && !desc.values.containsKey(entry.getKey())) missed.add(name);
				}

				if (!extra.isEmpty()) ctx.report(Kind.ERROR, "cu.annotation.extra", type, extra.keySet());
				if (!missed.isEmpty()) ctx.report(Kind.ERROR, "cu.annotation.missing", type, missed);
			}

			ctx.classes.runAnnotationProcessor(this, annotated.getKey(), list);
		}
		annoTask.clear();
	}
	// 访问权限是否降级以及能否override 注：preCheck检测了必须有访问权限
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

			String inline = "\1cu.override.access:"+
				(itLevel==0?"\1package-private\0":showModifiers(itLevel, ACC_SHOW_METHOD))+":"+
				(myLevel==0?"\1package-private\0":showModifiers(myLevel, ACC_SHOW_METHOD))+"\0";
			ctx.report(Kind.ERROR, "cu.override.unable", my.owner.replace('/', '.'), it.owner.replace('/', '.'), it, inline);
		}
	}
	// 注解的适用类型
	private static boolean applicableToNode(AnnotationSelf ctx, Attributed key) {
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
		return (ctx.applicableTo&mask) != 0;
	}
	public CodeWriter createDelegation(int acc, MethodNode it, MethodNode my, boolean end, boolean newObject) {
		CodeWriter c = newMethod(acc, my.name(), my.rawDesc());
		int base = 1;

		if (newObject) {
			c.clazz(Opcodes.NEW, it.owner);
			c.one(DUP);
			c.visitSize(TypeHelper.paramSize(it.rawDesc())+2, TypeHelper.paramSize(my.rawDesc()));
		} else if ((it.modifier&ACC_STATIC) == 0) {
			c.one(ALOAD_0);
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
			c.one(my.returnType().shiftedOpcode(IRETURN));
			c.finish();
		}
		return c;
	}
	// endregion
	// region 阶段4 编译代码块
	private MethodWriter clinit, glinit;
	public MethodWriter getStaticInit() {
		if (clinit != null) return clinit;

		int v = getMethod("<clinit>");
		if (v < 0) {
			v = methods.size();
			methods.add(new MethodNode(ACC_PUBLIC|ACC_STATIC, name, "<clinit>", "()V"));
		}

		MethodNode node = methods.get(v);
		clinit = ctx.classes.createMethodWriter(this, node);
		node.putAttr(new AttrCodeWriter(cp, node, clinit));
		clinit.visitSizeMax(10,0);
		return clinit;
	}
	public MethodWriter getGlobalInit() {
		// 隐式构造器会主动设置这个，不再需要额外检测
		if (glinit != null) return glinit;

		SimpleList<ConstantData> _throws = new SimpleList<>();

		for (int i = 0, size = methods.size(); i < size; i++) {
			MethodNode method = methods.get(i);
			if (method.name().equals("<init>")) {

				var list = method.parsedAttr(cp, Attribute.Exceptions);
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
						var exParent = ctx.parentListOrReport(_throws.get(j));
						for (String s : list.value) {
							var self = ctx.classes.getClassInfo(s);

							if (ctx.parentListOrReport(self).containsValue(exParent.get(0))) {
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
			for (ConstantData data : _throws) strings[i++] = data.name;

			GlobalContext.debugLogger().info("Common parent of all constructor throws: "+Arrays.toString(strings));
			// common parent of all constructor throws'
			mn.putAttr(new AttrClassList(Attribute.Exceptions, SimpleList.asModifiableList(strings)));
		}
		return glinit = ctx.classes.createMethodWriter(this, mn);
	}
	private DynByteBuf glInitBytes;
	public void appendGlobalInit(CodeWriter target, LineNumberTable lines) {
		if (glinit != null) {
			if (glInitBytes == null) glInitBytes = glinit.writeTo();
			target.insertBefore(glInitBytes);
			if (glinit.lines != null) lines.list.addAll(glinit.lines.list);
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
		var bsm = (BootstrapMethods) attrByName("BootstrapMethods");
		if (bsm == null) putAttr(bsm = new BootstrapMethods());
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

		currentNode = signature;
		ctx.lexer.state = STATE_EXPR;

		// 优先级的定义写在ParseTask中
		lazyTasks.sort((o1, o2) -> Integer.compare(o1.priority(), o2.priority()));
		for (int i = 0; i < lazyTasks.size(); i++) lazyTasks.get(i).parse(ctx);
		lazyTasks.clear();

		if (clinit != null) clinit.one(Opcodes.RETURN);

		for (FieldNode field : finalFields) {
			ctx.lexer.index = fieldIdx.get(fields.indexOfAddress(field));
			ctx.report(Kind.ERROR, "cu.finalField.missed", field.name());
		}
		finalFields.clear();

		// 隐式构造器
		if (glinit != null && glInitBytes == null && extraModifier != (ACC_FINAL|ACC_INTERFACE)) {
			glinit.visitSizeMax(10,0);
			glinit.one(Opcodes.RETURN);
			glinit.finish();
		}

		// FIXME NOVERIFY
		if (name.equals("Test")) parent("java/lang/🔓_IL🐟");
	}
	// endregion
	//region 阶段5 序列化之前……
	private List<RawNode> noStore = Collections.emptyList();
	public void noStore(RawNode fn) {
		if (noStore.isEmpty()) noStore = new SimpleList<>();
		noStore.add(fn);
	}

	public void S5_noStore() {
		for (RawNode node : noStore) {
			fields.remove(node);
			methods.remove(node);
		}
	}
	//endregion

	public LocalContext lc() {return ctx;}
	public String getSourceFile() {return source;}
	public CharSequence getCode() {return code;}

	public void cancelTask(Attributed node) {
		// field method parseTask
		throw new UnsupportedOperationException("not implemented yet!");
	}

	public void j11PrivateConstructor(MethodNode method) {
		// package-private on demand
		if ((method.modifier&Opcodes.ACC_PRIVATE) != 0 && !ctx.classes.hasFeature(LavaFeatures.NESTED_MEMBER)) {
			method.modifier ^= Opcodes.ACC_PRIVATE;
		}
	}

	private int accessorId;
	public String getNextAccessorName() {return "`acc$"+accessorId++;}
}