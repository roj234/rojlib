package roj.compiler.context;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValArray;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asmx.AnnotationSelf;
import roj.asmx.mapper.util.NameAndType;
import roj.collect.*;
import roj.compiler.CompilerSpec;
import roj.compiler.JavaLexer;
import roj.compiler.asm.*;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.config.ParseException;
import roj.config.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;
import static roj.config.Word.*;

/**
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public final class CompileUnit extends ConstantData {
	public static final Type RUNTIME_EXCEPTION = new Type("java/lang/RuntimeException");
	private Object _next;

	static final int _ACC_DEFAULT = 1 << 16, _ACC_ANNOTATION = 1 << 17, _ACC_RECORD = 1 << 18, _ACC_STRUCT = 1 << 19, _ACC_SEALED = 1 << 20, _ACC_NON_SEALED = 1 << 21;
	static final int CLASS_ACC = ACC_PUBLIC|ACC_FINAL|ACC_ABSTRACT|ACC_STRICT|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED;
	static final int
		ACC_CLASS_ILLEGAL = ACC_NATIVE|ACC_TRANSIENT|ACC_VOLATILE|ACC_SYNCHRONIZED,
		ACC_METHOD_ILLEGAL = ACC_TRANSIENT|ACC_VOLATILE|_ACC_SEALED,
		ACC_FIELD_ILLEGAL = ACC_STRICT|ACC_NATIVE|ACC_ABSTRACT|_ACC_SEALED;

	private static final char UNRELATED_MARKER = 32768, PARENT_MARKER = 16384;

	private final String source;
	private final JavaLexer wr;

	private final TypeResolver tr;

	private int extraModifier;

	// 诊断的起始位置
	private int classIdx;
	private final IntList methodIdx = new IntList(), fieldIdx = new IntList();

	// S2前的缓存
	private MyHashSet<IType> toResolve = new MyHashSet<>();

	// Generic
	public SignaturePrimer signature, currentNode;

	// Supplementary
	private int recordFieldPos;
	public MyHashSet<FieldNode> finalFields = new MyHashSet<>(Hasher.identity());
	private final MyHashSet<Desc> abstractOrUnrelated = new MyHashSet<>();
	private final AccessData overridableMethods = new AccessData(null, 0, "_", null);
	private final List<MethodNode> methodCache = new SimpleList<>();

	// code block task
	private final List<ParseTask> lazyTasks = new SimpleList<>();
	private final MyHashMap<Attributed, List<AnnotationPrimer>> annoTask = new MyHashMap<>();

	// Inner Class
	private final CompileUnit _parent;
	public int _children;

	private LocalContext ctx;

	public void setMinimumBinaryCompatibility(int level) {
		this.version = Math.max(JavaVersion(level), version);
	}

	public CompileUnit(String name) {
		source = name;
		tr = new TypeResolver();

		_parent = this;

		LocalContext ctx = LocalContext.get();
		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_SOURCE_FILE))
			putAttr(new AttrString(Annotations.SourceFile, name));
		wr = ctx.classes.createLexer();
	}
	public CompileUnit(String name, InputStream in) throws IOException {
		this(name);
		wr.init(IOUtil.readString(in));
	}

	public GlobalContext ctx() {return ctx.classes;}
	public JavaLexer getLexer() {return wr;}
	public TypeResolver getTypeResolver() {return tr;}

	// region 文件中的其余类
	private CompileUnit(CompileUnit parent) {
		this.source = parent.source;

		LocalContext.depth(1);
		ctx = LocalContext.get();

		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_SOURCE_FILE))
			putAttr(parent.attrByName("SourceFile"));

		_parent = parent;

		wr = parent.wr;
		classIdx = wr.index;

		tr = parent.tr;
		toResolve = new MyHashSet<>();//parent.toResolve;
	}

	private CompileUnit _newHelper(int acc) throws ParseException {
		CompileUnit c = new CompileUnit(this);

		int i = name.lastIndexOf('/') + 1;
		c.name(i <= 0 ? "" : name.substring(0, i));
		c.header(acc);

		ctx.classes.addCompileUnit(c, true);
		LocalContext.depth(-1);
		return c;
	}
	private CompileUnit _newInner(int acc) throws ParseException {
		CompileUnit c = new CompileUnit(this);

		c.name(name.concat("$"));
		c.header(acc);
		acc = c.modifier;

		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_INNER_CLASS)) {
			addNestMember(c);

			if ((acc & (ACC_INTERFACE|ACC_ENUM|_ACC_RECORD)) != 0) acc |= ACC_STATIC;

			var desc = InnerClasses.InnerClass.innerClass(c.name, acc);
			this.innerClasses().add(desc);
			c.innerClasses().add(desc);
		}

		if ((acc&ACC_PROTECTED) != 0) {
			acc &= ~ACC_PROTECTED;
			acc |= ACC_PUBLIC;
		}
		c.modifier = (char) (acc&CLASS_ACC);

		ctx.classes.addCompileUnit(c, true);
		LocalContext.depth(-1);
		return c;
	}
	public CompileUnit newAnonymousClass(@Nullable MethodNode mn) throws ParseException {
		CompileUnit c = new CompileUnit(this);

		c.name(IOUtil.getSharedCharBuf().append(name).append("$").append(++_children).toString());
		c.modifier = ACC_FINAL|ACC_SUPER;

		c.body();

		if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_INNER_CLASS)) {
			addNestMember(c);

			var desc = InnerClasses.InnerClass.anonymous(c.name, c.modifier|ACC_PRIVATE);
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

		LocalContext.depth(-1);
		return c;
	}

	public void addNestMember(ConstantData c) {
		var that = _parent;
		c.putAttr(new AttrString("NestHost", that.name));
		AttrClassList nestMembers = (AttrClassList) that.attrByName("NestMembers");
		if (nestMembers == null) that.putAttr(nestMembers = new AttrClassList(Attribute.NestMembers));
		nestMembers.value.add(c.name);
	}
	public List<InnerClasses.InnerClass> innerClasses() {
		InnerClasses c = parsedAttr(cp, Attribute.InnerClasses);
		if (c == null) putAttr(c = new InnerClasses());
		return c.classes;
	}
	// endregion
	// region 阶段1非公共部分: package, import, package-info, module-info
	public boolean S1_Struct() throws ParseException {
		var ctx = this.ctx = LocalContext.get();
		ctx.setClass(this);

		JavaLexer wr = this.wr;
		CharList tmp = IOUtil.getSharedCharBuf();

		wr.state = STATE_CLASS;

		// 默认包""
		String pkg = "";
		Word w = wr.next();

		// 空
		if (w.type() == EOF) return false;

		boolean isNormalClass = !source.contains("-info");

		if (w.type() == at) {
			if (isNormalClass) report(Kind.ERROR, "package.annotation");

			ctx.tmpAnnotations.clear();
			_annotations(ctx.tmpAnnotations);
			w = wr.next();
		}

		boolean moduleIsOpen = w.val().equals("open");
		if (w.type() == MODULE || moduleIsOpen) {
			if (isNormalClass) report(Kind.ERROR, "module.unexpected");

			if (moduleIsOpen) wr.except(MODULE);

			wr.state = STATE_MODULE;
			parseModuleInfo(moduleIsOpen);
			return false;
		}

		if (w.type() == PACKAGE) {
			typeRef(wr, tmp, 0);
			pkg = tmp.append('/').toString();

			w = wr.optionalNext(semicolon);

			// package-info without helper classes
			if (w.type() == EOF) return false;
		}

		tr.clear();
		if (w.type() == PACKAGE_RESTRICTED) {
			tr.setRestricted(true);
			w = wr.optionalNext(semicolon);
		}

		while (w.type() == IMPORT) {
			boolean impField = wr.nextIf(STATIC);
			int unimport = wr.nextIf(sub) ? 0 : TYPE_STAR;

			typeRef(wr, tmp, unimport);

			importBlock:
			if (tmp.charAt(tmp.length()-1) == '*') {
				// import *
				if (tmp.length() == 1) {
					report(Kind.WARNING, "import.any");
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
				if (i < 0) report(Kind.SEVERE_WARNING, "import.unpackaged");

				MyHashMap<String, String> map = !impField ? tr.getImportClass() : tr.getImportStatic();
				String qualified = tmp.toString();

				if (unimport == 0) {
					map.put(qualified.substring(i+1), null);
					break importBlock;
				}

				String name = wr.nextIf(AS) ? wr.next().val() : qualified.substring(i+1);
				if ((qualified = map.put(name, qualified)) != null) {
					report(Kind.ERROR, "import.conflict", tmp, name, qualified);
				}
			}

			wr.optionalNext(semicolon);
		}

		wr.retractWord();
		name(pkg);

		tr.init(ctx);

		if (isNormalClass) {
			int acc = (char) _modifiers(wr, CLASS_ACC);
			header(acc);
			ctx.classes.addCompileUnit(this, false);
		} else name(pkg+"package-info");

		// 辅助类
		while (!wr.nextIf(EOF)) {
			int acc = (char) _modifiers(wr, CLASS_ACC);
			_newHelper(acc);
		}

		return isNormalClass;
	}
	private void parseModuleInfo(boolean moduleIsOpen) throws ParseException {
		setMinimumBinaryCompatibility(CompilerSpec.COMPATIBILITY_LEVEL_JAVA_9);
		modifier = ACC_MODULE;
		name("module-info");

		var a = new AttrModule(wr.except(LITERAL, "cu.name").val(), moduleIsOpen ? ACC_OPEN : 0);
		putAttr(a);

		var names = ctx.tmpSet; names.clear();

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
						String name = _type(wr, 0).owner();
						if (!names.add("R;"+name)) report(Kind.ERROR, "module.dup.requires", name);

						a.requires.add(new AttrModule.Module(name, access));

						w = wr.next();
					} while (w.type() == comma);
				}
				case EXPORTS, OPENS -> {
					boolean isExport = w.type() == EXPORTS;
					if (!isExport && moduleIsOpen) report(Kind.ERROR, "module.open");

					String from = _type(wr, 0).owner();

					var info = new AttrModule.Export(from);
					(isExport?a.exports:a.opens).add(info);

					if (!names.add((isExport?"E;":"O;")+from)) report(Kind.ERROR, "module.dup.exports", from);

					w = wr.next();
					if (w.type() == TO) {
						do {
							String to = _type(wr, 0).owner();
							if (!names.add((isExport?"E;":"O;")+from+';'+to)) report(Kind.ERROR, "module.dup.exports2", from, to);

							info.to.add(to);

							w = wr.next();
						} while (w.type() == comma);
					}
				}
				case USES -> {
					String spi = _type(wr, 0).owner();
					w = wr.next();

					if (!names.add("U;"+spi)) report(Kind.ERROR, "module.dup.uses", spi);
					a.uses.add(spi);
				}
				case PROVIDES -> {
					String spi = _type(wr, 0).owner();

					var info = new AttrModule.Provide(spi);
					a.provides.add(info);

					if (!names.add("P;"+spi)) report(Kind.ERROR, "module.dup.provides", spi);

					w = wr.next();
					if (w.type() == WITH) {
						do {
							String to = _type(wr, 0).owner();
							if (!names.add("P;"+spi+";"+to)) report(Kind.ERROR, "module.dup.provides2", spi, to);

							info.impl.add(to);

							w = wr.next();
						} while (w.type() == comma);
					}
				}
			}

			if (w.type() != semicolon) report(Kind.ERROR, "module.semicolon");
		}

		ctx.classes.setModule(a);
	}
	// endregion
	// region 阶段1 类的结构
	private void header(int acc) throws ParseException {
		JavaLexer wr = this.wr;

		// 修饰符和注解
		if (!ctx.tmpAnnotations.isEmpty()) annoTask.put(this, new SimpleList<>(ctx.tmpAnnotations));

		// 类型(class enum...)
		Word w = wr.next();
		switch (w.type()) {
			case INTERFACE: // interface
				if ((acc & (ACC_FINAL)) != 0) report(Kind.ERROR, "modifier.conflict:interface:final");
				acc |= ACC_ABSTRACT|ACC_INTERFACE;
			break;
			case AT_INTERFACE: // @interface
				if ((acc & (ACC_FINAL)) != 0) report(Kind.ERROR, "modifier.conflict:@interface:final");
				acc |= ACC_ANNOTATION|ACC_INTERFACE|ACC_ABSTRACT;
				addInterface("java/lang/annotation/Annotation");
			break;
			case ENUM: // enum
				if ((acc & (ACC_ABSTRACT)) != 0) report(Kind.ERROR, "modifier.conflict:enum:abstract");
				acc |= ACC_ENUM|ACC_FINAL;
				parent("java/lang/Enum");
			break;
			case RECORD:
				setMinimumBinaryCompatibility(CompilerSpec.COMPATIBILITY_LEVEL_JAVA_17);
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
		name(name.concat(w.val()));

		// 泛型参数和范围
		w = wr.next();
		if (w.type() == lss) { // <
			genericDecl(wr);
			w = wr.next();
		}

		// 继承
		checkExtends:
		if (w.type() == EXTENDS) {
			if ((acc & (ACC_ENUM|ACC_ANNOTATION|_ACC_RECORD)) != 0) report(Kind.ERROR, "cu.noInheritance", name, parent);
			if ((acc & ACC_INTERFACE) != 0) break checkExtends;

			IType type = _type(wr, TYPE_GENERIC|TYPE_LEVEL2);
			if (type.genericType() > 0 || currentNode != null) makeSignature()._add(type);
			parent(type.owner());

			w = wr.next();
		}

		structCheck:{
		// record
		recordCheck:
		if ((acc & _ACC_RECORD) != 0) {
			if (w.type() != lParen) {
				report(Kind.ERROR, "cu.record.header");
				break recordCheck;
			}

			do {
				_modifiers(wr, _ACC_ANNOTATION);

				IType type = _type(wr, TYPE_PRIMITIVE|TYPE_GENERIC);
				if (type.genericType() != 0) makeSignature().returns = type;

				String name = wr.except(LITERAL, "cu.name").val();

				FieldNode field = new FieldNode(ACC_PUBLIC|ACC_FINAL, name, type.rawType());

				if (!ctx.tmpAnnotations.isEmpty()) annoTask.put(field, new SimpleList<>(ctx.tmpAnnotations));
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

			if (w.type() != rParen) throw wr.err("unexpected_2:"+w.val()+":cu.except.record");
			w = wr.next();

			recordFieldPos = fields.size();

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
				IType type = _type(wr, TYPE_GENERIC|TYPE_LEVEL2);
				if (type.genericType() > 0 || currentNode != null) makeSignature()._impl(type);
				interfaces.add(new CstClass(type.owner()));

				w = wr.next();
			} while (w.type() == comma);
		}

		// 密封
		sealedCheck:
		if ((acc & _ACC_SEALED) != 0) {
			if (w.type() != PERMITS) {
				report(Kind.ERROR, "cu.sealed.noPermits");
				break sealedCheck;
			}

			var subclasses = new AttrClassList(Attribute.PermittedSubclasses);
			putAttr(subclasses);

			do {
				IType type = _type(wr, TYPE_LEVEL2);
				subclasses.value.add(type.owner());

				w = wr.next();
			} while (w.type() == comma);
		}

		wr.retractWord();

		signature = finishSignature(_parent == null ? null : _parent.signature, Signature.CLASS, this);
		for (int i = 0; i < recordFieldPos; i++) {
			var sign = (SignaturePrimer) fields.get(i).attrByName("Signature");
			if (sign != null) sign.parent = signature;
		}

		body();
		}
	}
	private void body() throws ParseException {
		LocalContext ctx = this.ctx;
		JavaLexer wr = this.wr;
		Word w;

		wr.except(lBrace);

		var names = ctx.tmpSet; names.clear();
		// for record
		for (int i = 0; i < fields.size(); i++) {
			String name = fields.get(i).name();
			if (!names.add(name)) ctx.classes.report(this, Kind.ERROR, fieldIdx.get(i), "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
		}

		// 枚举的字段
		if ((modifier & ACC_ENUM) != 0) {
			Type selfType = new Type(name);

			w = wr.next();
			while (w.type() == LITERAL) {
				String name = w.val();
				if (!names.add(name)) report(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);

				fieldIdx.add(w.pos());

				FieldNode f = new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM, name, selfType);
				lazyTasks.add(ParseTask.Enum(this, fields.size(), f));
				fields.add(f);

				if (w.type() != comma) break;
				w = wr.next();
			}

			if (w.type() != semicolon) wr.retractWord();
		}

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
			acc = ACC_PUBLIC|ACC_STATIC|ACC_STRICT|ACC_FINAL|ACC_ABSTRACT|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED;
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
				if ((acc & ACC_PRIVATE) != 0) {
					setMinimumBinaryCompatibility(CompilerSpec.COMPATIBILITY_LEVEL_JAVA_9);
				} else if ((acc & ACC_PROTECTED) != 0) {
					report(Kind.ERROR, "modifier.conflict:protected:public");
				} else {
					acc |= ACC_PUBLIC;
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
					if ((acc& ~(ACC_STATIC|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED)) != 0) report(Kind.ERROR, "modifier.conflict", showModifiers(acc & ~ACC_STATIC, ACC_SHOW_METHOD), "cu.except.initBlock");
					if ((acc & ACC_STATIC) == 0) {
						if ((acc & ACC_INTERFACE) != 0) report(Kind.ERROR, "cu.interfaceInit");
						lazyTasks.add(ParseTask.InstanceInitBlock(this));
					} else {
						// no interface checks 😄
						lazyTasks.add(ParseTask.StaticInitBlock(this));
					}
				continue;
				case CLASS, INTERFACE, ENUM, AT_INTERFACE, RECORD:
					wr.retractWord();
					if ((acc&ACC_CLASS_ILLEGAL) != 0) report(Kind.ERROR, "modifier.notAllowed", showModifiers(acc&ACC_CLASS_ILLEGAL, ACC_SHOW_METHOD));
					_newInner(acc);
				continue;
				default:
					if ((acc & (_ACC_SEALED|_ACC_NON_SEALED)) != 0) report(Kind.ERROR, "modifier.conflict", "sealed/non-sealed", "method/field");
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
						report(Kind.ERROR, "cu.interfaceInit");

					// 枚举必须是private构造器
					else if ((modifier & ACC_ENUM) != 0) {
						if ((acc & (ACC_PUBLIC|ACC_PROTECTED)) != 0) {
							report(Kind.ERROR, "cu.enumInit");
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
					Generic g = new Generic("roj/compiler/runtime/ReturnStack", 0, Generic.EX_NONE);
					type1 = g;
					do {
						g.addChild(_type(wr, TYPE_PRIMITIVE|TYPE_GENERIC));
						w = wr.next();
					} while (w.type() == comma);
					if (w.type() != rBracket) throw wr.err("unexpected_2:"+w.type()+":cu.except.multiArg");
				} else {
					wr.retractWord();
					type1 = _type(wr, TYPE_PRIMITIVE|TYPE_GENERIC);
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
				if ((acc&ACC_METHOD_ILLEGAL) != 0) report(Kind.ERROR, "modifier.notAllowed", showModifiers(acc&ACC_METHOD_ILLEGAL, ACC_SHOW_FIELD));
				if ((acc & ACC_ABSTRACT) != 0) {
					if ((acc&ACC_STRICT) != 0) report(Kind.ERROR, "modifier.conflict:strictfp:abstract");
					if ((modifier & ACC_ABSTRACT) == 0) report(Kind.ERROR, "cu.method.noAbstract", this.name, name);
				}

				MethodNode method = new MethodNode(acc, this.name, name, "()V");
				method.setReturnType(type);
				if (!ctx.tmpAnnotations.isEmpty()) annoTask.put(method, new SimpleList<>(ctx.tmpAnnotations));

				List<String> paramNames;

				w = wr.next();
				if (w.type() != rParen) {
					wr.retractWord();

					paramNames = new SimpleList<>();

					boolean lsVarargs = false;
					MethodParameters parAccName;
					if (ctx.classes.isSpecEnabled(CompilerSpec.ATTR_METHOD_PARAMETERS)) {
						parAccName = new MethodParameters();
						method.putAttr(parAccName);
					} else {
						parAccName = null;
					}

					do {
						if (lsVarargs) report(Kind.ERROR, "cu.method.paramVararg");

						int acc1 = _modifiers(wr, ACC_FINAL|_ACC_ANNOTATION);

						IType parType = _type(wr, TYPE_PRIMITIVE|TYPE_GENERIC);
						if (parType.genericType() != 0) makeSignature()._add(paramNames.size(), (Generic) parType);

						w = wr.next();
						if (w.type() == varargs) {
							lsVarargs = true;
							w = wr.next();
						}

						List<Type> p = method.parameters();
						p.add(parType.rawType());
						if (parType.rawType().type == VOID) report(Kind.ERROR, "cu.method.paramVoid");
						if (p.size() > 255) report(Kind.ERROR, "cu.method.paramCount");

						if (!ctx.tmpAnnotations.isEmpty()) {
							Attributed node = new ParamAnnotationRef(method, w.pos(), paramNames.size());
							annoTask.put(node, new SimpleList<>(ctx.tmpAnnotations));
						}

						if (w.type() == LITERAL) {
							if (paramNames.contains(w.val())) report(Kind.ERROR, "cu.method.paramConflict");
							paramNames.add(w.val());
						} else {
							throw wr.err("unexpected:" + w.val());
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
				} else {
					paramNames = Collections.emptyList();
				}

				method.modifier = (char) acc;
				methods.add(method);

				w = wr.next();
				// throws XX异常
				if (w.type() == THROWS) {
					if ((modifier & ACC_ANNOTATION) != 0) {
						report(Kind.ERROR, "cu.method.annotationThrow");
					}

					var excList = new AttrClassList(Attribute.Exceptions);
					method.putAttr(excList);

					do {
						IType type1 = _type(wr, TYPE_GENERIC);
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
								report(Kind.ERROR, "modifier.notAllowed:final");
							}
							break noMethodBody;
						}
					}

					if (w.type() != lBrace) {
						report(Kind.ERROR, "cu.method.mustHasBody");
						break noMethodBody;
					}

					lazyTasks.add(ParseTask.Method(this, method, paramNames));
					continue;
				}

				if ((acc & ACC_NATIVE) == 0) method.modifier |= ACC_ABSTRACT;
				if (w.type() != semicolon) throw wr.err("cu.method.mustNotBody");
			} else {
				// field
				if ((acc&ACC_FIELD_ILLEGAL) != 0) report(Kind.ERROR, "modifier.notAllowed", showModifiers(acc&ACC_FIELD_ILLEGAL, ACC_SHOW_METHOD));
				// 接口的字段必须是静态的
				if ((modifier & ACC_INTERFACE) != 0) {
					if ((acc & (ACC_PRIVATE|ACC_PROTECTED)) != 0) {
						setMinimumBinaryCompatibility(CompilerSpec.COMPATIBILITY_LEVEL_LAVA_1);
						//report(Kind.ERROR, "cu.interfacePrivateNonstatic");
						report(Kind.INCOMPATIBLE, "modifier.superset", acc);
					}

					acc |= ACC_STATIC;
				}

				wr.retractWord();

				Signature s = finishSignature(signature, Signature.FIELD, null);

				List<AnnotationPrimer> list = ctx.tmpAnnotations.isEmpty() ? null : new SimpleList<>(ctx.tmpAnnotations);

				while (true) {
					FieldNode field = new FieldNode(acc, name, type);
					if (!names.add(name)) {
						report(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
					}

					if (list != null) annoTask.put(field, list);
					if (s != null) field.putAttr(s);

					fields.add(field);
					if ((acc & ACC_FINAL) != 0) finalFields.add(field);
					fieldIdx.add(w.pos());

					w = wr.next();
					if (w.type() == assign) lazyTasks.add(ParseTask.Field(this, field));

					if (w.type() != comma) {
						assert w.type() == semicolon;
						break;
					}
					name = wr.except(LITERAL, "cu.except.fieldName").val();
				}
			}
		}
	}
	private boolean isConstructor(String val) {
		int i = name.lastIndexOf('$');
		if (i < 0) i = name.lastIndexOf('/');
		return val.regionMatches(0, name, i+1, name.length() - i - 1);
	}

	public int _modifiers(JavaLexer wr, int mask) throws ParseException {
		if ((mask & _ACC_ANNOTATION) != 0) ctx.tmpAnnotations.clear();

		Word w;
		int acc = 0;
		while (true) {
			int f;
			w = wr.next();
			switch (w.type()) {
				case at -> {
					if ((mask & _ACC_ANNOTATION) == 0) report(Kind.ERROR, "modifier.annotation");
					_annotations(ctx.tmpAnnotations);
					acc |= _ACC_ANNOTATION;
					continue;
				}
				// n << 22 => conflict mask
				case PUBLIC -> 		f = (        1 << 22) | ACC_PUBLIC;
				case PROTECTED -> 	f = (        1 << 22) | ACC_PROTECTED;
				case PRIVATE -> 	f = (     0b11 << 22) | ACC_PRIVATE;
				case NATIVE -> 		f = (    0b100 << 22) | ACC_NATIVE;
				case SYNCHRONIZED ->f = (   0b1000 << 22) | ACC_SYNCHRONIZED;
				case FINAL -> 		f = (  0b10000 << 22) | ACC_FINAL;
				case STATIC -> 		f = ( 0b100000 << 22) | ACC_STATIC;
				case CONST -> 		f = ( 0b110001 << 22) | ACC_PUBLIC | ACC_STATIC | ACC_FINAL;
				case DEFAULT -> 	f = ( 0b110110 << 22) | _ACC_DEFAULT;
				case ABSTRACT -> 	f = ( 0b111110 << 22) | ACC_ABSTRACT;
				case SEALED ->      f = (  0b10000 << 22) | _ACC_SEALED;
				case NON_SEALED ->  f = (  0b10000 << 22) | _ACC_NON_SEALED;
				case STRICTFP -> 	f = ACC_STRICT; // on method, cannot be used with abstract
				case VOLATILE -> 	f = ACC_VOLATILE;
				case TRANSIENT -> 	f = ACC_TRANSIENT;
				default -> {
					wr.retractWord();
					return acc & ((1<<22) - 1);
				}
			}

			if ((f & mask) == 0) {
				report(Kind.ERROR, "modifier.notAllowed", w.val());
				continue;
			}

			if ((acc & f) != 0) {
				report(Kind.ERROR, "modifier.conflict", w.val(), showModifiers(acc, ACC_SHOW_METHOD));
				continue;
			}
			acc |= f;
		}
	}

	public static final int TYPE_PRIMITIVE = 1, TYPE_OPTIONAL = 2, TYPE_GENERIC = 4, TYPE_STAR = 8, TYPE_LEVEL2 = 16;
	/**
	 * 获取类 (a.b.c)
	 * @param flags 0 | {@link #TYPE_STAR} allow * (in import)
	 */
	private static void typeRef(JavaLexer wr, CharList sb, int flags) throws ParseException {
		sb.clear();

		while (true) {
			Word w = wr.next();
			if (w.type() == mul) {
				if ((flags&TYPE_STAR) == 0) throw wr.err("unexpected:*");
				sb.append("*");
				break;
			} else if (w.type() == LITERAL) {
				sb.append(w.val());
			} else {
				throw wr.err("unexpected:"+w.val());
			}

			w = wr.next();
			if (w.type() != dot) {
				if (sb.length() == 0) throw wr.err("empty_type");
				wr.retractWord();
				break;
			}
			sb.append('/');
		}
	}
	/**
	 * 获取类型
	 * @param flags {@link #TYPE_PRIMITIVE} bit set
	 */
	private IType _type(JavaLexer wr, int flags) throws ParseException {
		IType type;

		Word w = wr.next();
		int std = FastPrimitive.getOrDefaultInt(w.type(), -1);
		if (std >= 0) {
			type = new Type(std, 0);
		} else {
			if (w.type() != LITERAL) {
				if ((flags&TYPE_OPTIONAL) == 0) throw wr.err("type.illegalType:"+w.val());
				return Helpers.nonnull();
			}

			wr.retractWord();

			CharList sb = ctx.tmpSb;
			typeRef(wr, sb, 0);

			if ((flags&TYPE_GENERIC) != 0) {
				CharSequence bound = null;

				w = wr.next();
				wr.retractWord();

				if (w.type() == lss ||
					(currentNode != null && sb != (bound = currentNode.getTypeBound(sb))) ||
					(signature != null && sb != (bound = signature.getTypeBound(sb)))) {

					type = _genericRef(sb, (flags&TYPE_LEVEL2) != 0 ? GENERIC_SUBCLASS : 0);

					if (bound != null) {
						sb.clear();
						sb.append(bound);
					}
					return type;
				}
			}

			type = new Type(sb.toString());
		}

		if ((flags & TYPE_LEVEL2) == 0) {
			int arrLen = 0;
			w = wr.next();

			while (w.type() == lBracket) {
				arrLen++;

				wr.except(rBracket);
				w = wr.next();
			}

			if (w.type() == varargs) arrLen++;

			if (arrLen > 0) {
				if (arrLen > 255) throw wr.err("type.arrayDepth");
				type.setArrayDim(arrLen);
			} else {
				if (type.rawType().owner == null && (flags&TYPE_PRIMITIVE) == 0) throw wr.err("type.primitiveNotAllowed");
			}

			wr.retractWord();
			return toResolve.intern(type);
		}

		return type;
	}
	private static final Int2IntMap FastPrimitive = new Int2IntMap();
	static {
		String s = "ICBZSDJFV";
		for (int i = 0; i < s.length(); i++) {
			FastPrimitive.putInt(JavaLexer.byName(Type.toString((byte) s.charAt(i))), s.charAt(i));
		}
	}

	private SignaturePrimer makeSignature() {
		if (currentNode == null) currentNode = new SignaturePrimer(0);
		return currentNode;
	}
	private SignaturePrimer finishSignature(SignaturePrimer parent, byte kind, Attributed attr) {
		var sign = currentNode;
		if (sign == null) return null;
		currentNode = null;

		sign.parent = parent;
		sign.type = kind;

		if (attr != null) {
			if (attr instanceof MethodNode mn) sign.commit(mn);
			attr.putAttr(sign);
		}
		return sign;
	}

	// <T extends YYY<T, V> & ZZZ, V extends T & XXX>
	private void genericDecl(JavaLexer wr) throws ParseException {
		SignaturePrimer s = makeSignature();
		Word w;
		do {
			w = wr.next();
			if (w.type() != LITERAL) throw wr.err("type.error.illegalAnyType");

			s.addTypeParam(w.val());

			short id = EXTENDS;
			while (wr.hasNext()) {
				w = wr.next();
				if (w.type() != id) break;

				s.addBound(_genericRef(null, 0));

				id = and;
			}
		} while (w.type() == comma);

		if (w.type() != gtr) wr.unexpected(w.val(), "type.except.afterLss");
	}

	private static final int GENERIC_INNER = 1, GENERIC_SUBCLASS = 2;
	public IType _genericRef(CharSequence type, int flag) throws ParseException {
		GenericPrimer g = new GenericPrimer();
		IType result = null;

		JavaLexer wr = this.wr;
		if (type != null) {
			g.owner = type.toString();
		} else {
			Word w = wr.next();
			// A<?
			if (w.type() == ask) {
				if ((flag & GENERIC_INNER) == 0) {
					if ((flag&TYPE_OPTIONAL) != 0) return Helpers.maybeNull();
					wr.unexpected(w.val());
				}

				g.owner = "*";

				int prev = wr.state;
				wr.state = STATE_TYPE;

				w = wr.next();
				switch (w.type()) {
					// A<? super
					case SUPER: g.extendType = Generic.EX_SUPER; break;
					case EXTENDS: g.extendType = Generic.EX_EXTENDS; break;
					// A<?
					case gtr, comma:
						wr.retractWord();
						return Signature.any();
					default:
						if ((flag&TYPE_OPTIONAL) != 0) return Helpers.maybeNull();
						wr.unexpected(w.val(), "type.except.afterAsk");
				}

				wr.state = prev;
			} else {
				// A<B
				wr.retractWord();
			}

			// resolve B
			if ((flag & GENERIC_INNER) != 0) {
				Type pt = (Type) _type(wr, TYPE_PRIMITIVE|TYPE_OPTIONAL);
				if (pt == null) {
					wr.retractWord();
					// 钻石操作符<>
					return Asterisk.anyGeneric;
				} else {
					if (pt.owner == null) result = pt;
					else g.owner = pt.owner;
				}
			}
		}

		Word w = wr.next();
		if (w.type() == lss) {
			int prev = wr.state;
			seg:
			while (true) {
				// <<或者>>
				wr.state = STATE_TYPE;
				IType child = _genericRef(null, flag&TYPE_OPTIONAL|GENERIC_INNER);
				wr.state = prev;

				g.addChild(child);
				w = wr.next();

				if (child == Asterisk.anyGeneric) {
					// check for <>
					if ((flag&GENERIC_INNER) != 0 || w.type() != gtr) {
						report(Kind.ERROR, "type.error.illegalAnyType");
					}
				}

				switch (w.type()) {
					case comma: continue;
					case gtr: break seg;
					default:
						if ((flag&TYPE_OPTIONAL) != 0) return Helpers.maybeNull();
						wr.unexpected(w.val(), "type.except.afterLss");
				}
			}

			w = wr.next();
		}

		if (w.type() == dot) {
			IType sub1 = _genericRef(wr.except(LITERAL).val(), flag&TYPE_OPTIONAL|GENERIC_SUBCLASS);
			if (sub1 == null) return Helpers.maybeNull();
			// 无法从参数化的类型中选择静态类
			if (sub1.genericType() != IType.GENERIC_TYPE) report(Kind.ERROR, "type.error.partialGenericSub", sub1.owner());
			else g.sub = ((GenericPrimer) sub1).toGenericSub();

			w = wr.next();
		}

		if ((flag & GENERIC_SUBCLASS) == 0) {
			int al = 0;
			while (w.type() == lBracket) {
				al++;
				wr.except(rBracket);
				w = wr.next();
			}
			g.setArrayDim(al);
		}
		wr.retractWord();

		if (result != null) {
			if (g.isRealGeneric()) report(Kind.ERROR, "type.error.primitiveGeneric");
			return result;
		}

		result = g.resolve(null);
		return toResolve.intern(result);
	}

	public List<AnnotationPrimer> _annotations(List<AnnotationPrimer> list) throws ParseException {
		JavaLexer wr = this.wr;
		CharList tmp = ctx.tmpSb;

		while (true) {
			int pos = wr.index;
			typeRef(wr, tmp, 0);

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
				a.newEntry("value", ParseTask.Annotation(this, a, "value"));

				w = wr.next();
			} else {
				wr.skip();

				while (true) {
					a.newEntry(name, ParseTask.Annotation(this, a, name));

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
	// endregion
	// region 阶段2 解析并验证类自身的引用
	public void S2_ResolveSelf() {
		LocalContext ctx = this.ctx = LocalContext.get();
		ctx.setClass(this);

		// region Type扩展
		wr.index = -2;

		// 自动处理在Stage0/1阶段产生的
		//  class, method param/return, field type
		//  和他们的泛型
		// TODO Typeparam subclass merged?
		for (IType type : toResolve) ctx.resolveType(type);
		toResolve.clear();

		if (signature != null) signature.resolve();

		for (int i = 0; i < methods.size(); i++) {
			SignaturePrimer s = (SignaturePrimer) methods.get(i).attrByName("Signature");
			if (s != null) s.resolve();
		}

		// endregion
		wr.index = classIdx;

		// extends
		var pInfo = tr.resolve(ctx, parent);
        if (pInfo == null) {
			report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", parent, name+".parent");
		} else {
            int acc = pInfo.modifier;
			if (0 != (acc & ACC_FINAL)) {
				report(Kind.ERROR, "cu.resolve.notInheritable", "cu.final", parent);
			} else if (0 != (acc & ACC_INTERFACE)) {
				report(Kind.ERROR, "cu.resolve.notInheritable", "cu.interface", parent);
			}

			parent(pInfo.name());
        }

		// implements
		var itfs = interfaces;
		for (int i = 0; i < itfs.size(); i++) {
			String iname = itfs.get(i).name().str();
			var info = tr.resolve(ctx, iname);
            if (info == null) {
				report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", iname, name+".interface["+i+"]");
			} else {
                int acc = info.modifier;
                if (0 == (acc & ACC_INTERFACE)) {
					report(Kind.ERROR, "cu.resolve.notInheritable", "cu.class", info.name());
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
					report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", type, name+".permits["+i+"]");
				} else {
					value.set(i, info.name);
				}
			}
		}
	}
	public void S2_ResolveRef() {
		LocalContext ctx = this.ctx = LocalContext.get();
		ctx.setClass(this);

		wr.index = classIdx;

		// 检测循环继承
		ctx.parentListOrReport(this);

		// 泛型异常
		if (signature != null && ctx.instanceOf(parent, "java/lang/Throwable")) {
			report(Kind.ERROR, "cu.genericException");
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
					report(Kind.ERROR, "cu.sealed.missing");
				}

				if (!ps.value.contains(this.name)) {
					report(Kind.ERROR, i == 0 ? "cu.sealed.unlisted.c" : "cu.sealed.unlisted.i", name, ps.value);
				}
			}

			if (i == itfs.size()) break;
			name = itfs.get(i++).name().str();
		}
		}

		boolean autoInit = (modifier & ACC_INTERFACE) == 0;
		// 方法冲突在这里检查，因为stage0/1拿不到完整的rawDesc
		names.clear();
		List<MethodNode> methods = this.methods;
		for (int i = 0; i < methodIdx.size(); i++) {
			wr.index = methodIdx.get(i);
			var method = methods.get(i);

			String par = method.rawDesc();
			if (!names.add(method.name()+par.substring(0, par.lastIndexOf(')')+1)))
				report(Kind.ERROR, "cu.nameConflict", this.name, "invoke.method", method.name());

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
						report(Kind.ERROR, "symbol.error.noSuchClass", classes.get(i));
					} else {
						ctx.assertAccessible(info);

						if (!ctx.instanceOf(info.name(), "java/lang/Throwable"))
							report(Kind.ERROR, "cu.throwException", classes.get(i));

						classes.set(j, info.name());
					}
				}
			}
		}

		autoInit:
		if (autoInit) {
			var pInfo = ctx.classes.getClassInfo(parent);

			int superInit = pInfo.getMethod("<init>", "()V");
			if (superInit < 0) {
				report(Kind.ERROR, "cu.noDefaultConstructor");
				break autoInit;
			}

			ctx.checkAccessible(pInfo, pInfo.methods().get(superInit), false, true);

			CodeWriter cw = newMethod(ACC_PUBLIC|ACC_SYNTHETIC, "<init>", "()V");
			cw.visitSize(1,1);
			cw.one(ALOAD_0);
			cw.invoke(INVOKESPECIAL, this.parent, "<init>", "()V");
			cw.one(Opcodes.RETURN);
			cw.finish();
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
					report(Kind.ERROR, "cu.sealed.noPermits", type, name);
				}
			}
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
	// endregion
	void _setSign(Attributed merhod) {
		var sign = (SignaturePrimer) merhod.attrByName("Signature");
		currentNode = sign != null ? sign : signature;
	}
	// region 阶段3 可能执行多次 注解处理 MethodDefault
	public void S3_Annotation() throws ParseException {
		LocalContext ctx = this.ctx = LocalContext.get();
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

			wr.index = methodIdx.get(i);

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
				IClass info = tr.resolve(ctx, a.type());
				if (info == null) continue;
				if (info.name().equals("java/lang/Override")) {
					annotations.remove(j);
					if (annotations.isEmpty()) annoTask.remove(my);
				}
				break;
			}

			IType itRt = ri.desc != null ? ri.desc[ri.desc.length-1] : it.returnType();

			// 返回值更精确而需要桥接，或更不精确而无法覆盖
			var cast = ctx.inferrer.overrideCast(myRt, itRt);
			if (cast.type != 0) {
				String inline = "\1cu.override.returnType:\1typeCast.error."+cast.type+':'+myRt+':'+itRt+"\0\0";
				report(Kind.ERROR, "cu.override.unable", name, my, it.owner, it, inline);
			}

			// 生成桥接方法 这里不检测泛型(主要是TypeParam)
			if (!it.rawDesc().equals(my.rawDesc())) {
				createDelegation((it.modifier&(ACC_PUBLIC|ACC_PROTECTED)) | ACC_FINAL | ACC_SYNTHETIC | ACC_BRIDGE, my, it);
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
					report(Kind.ERROR, "cu.override.unable", name, my, it.owner.replace('/', '.'), it, inline);
				}
			}
		}
		// endregion

		wr.index = classIdx;
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
				report(Kind.ERROR, "cu.unrelatedDefault", method, name.replace('/', '.'));
			} else {
				report(Kind.ERROR, "cu.override.noImplement", name, method.owner.replace('/', '.'), method.name);
			}
		}
		abstractOrUnrelated.clear();

		var missed = ctx.tmpSet;
		for (Map.Entry<Attributed, List<AnnotationPrimer>> annotated : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			List<AnnotationPrimer> list = annotated.getValue();
			for (int i = 0; i < list.size(); i++) {
				AnnotationPrimer a = list.get(i);
				wr.index = a.pos;

				IClass inst = tr.resolve(ctx, a.type());
				if (inst == null) {
					report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", a.type(), ctx.currentCodeBlockForReport());
					continue;
				}
				a.setType(inst.name());

				AnnotationSelf ad = ctx.classes.getAnnotationDescriptor(inst);

				if (!applicableToNode(ad, annotated.getKey())) report(Kind.ERROR, "cu.annotation.notApplicable", inst, annotated.getKey().getClass());

				missed.clear();
				for (Map.Entry<String, AnnVal> entry : ad.values.entrySet()) {
					String name = entry.getKey();

					Object task = Helpers.cast(a.values.remove(name));
					if (task instanceof ParseTask t) t.parse();
					else if (task == null && entry.getValue() == null) missed.add(name);
				}

				if (!a.values.isEmpty()) report(Kind.ERROR, "cu.annotation.extra", inst, a.values.keySet());
				if (!missed.isEmpty()) report(Kind.ERROR, "cu.annotation.missing", inst, missed);

				switch (ad.kind()) {
					case AnnotationSelf.SOURCE: break; // discard
					case AnnotationSelf.CLASS:
						if (inv == null) {
							inv = new Annotations(false);
							annotated.getKey().putAttr(inv);
						}
						inv.annotations.add(a);
						break;
					case AnnotationSelf.RUNTIME:
						if (vis == null) {
							vis = new Annotations(true);
							annotated.getKey().putAttr(vis);
						}
						vis.annotations.add(a);
						break;
				}
			}

			ctx.classes.invokeAnnotationProcessor(this, annotated.getKey(), list);
		}
		annoTask.clear();
	}
	// 访问权限是否降级以及能否override 注：preCheck检测了必须有访问权限
	private void checkDowngrade(MethodNode my, MethodNode it, LocalContext ctx) {
		if ((my.modifier&ACC_STATIC) != (it.modifier&ACC_STATIC)) {
			String inline = "cu.override.static."+((my.modifier&ACC_STATIC) != 0 ? "self" : "other");
			report(Kind.ERROR, "cu.override.unable", my.owner.replace('/', '.'), my, it.owner.replace('/', '.'), it, inline);
		}

		int myLevel = my.modifier&(ACC_PUBLIC|ACC_PROTECTED);
		int itLevel = it.modifier&(ACC_PUBLIC|ACC_PROTECTED);
		pass: {
			if (myLevel == ACC_PUBLIC || myLevel == itLevel) break pass;
			if (itLevel == 0 && myLevel == ACC_PROTECTED) break pass;

			String inline = "\1cu.override.access:"+
				(itLevel==0?"\1package-private\0":showModifiers(itLevel, ACC_SHOW_METHOD))+":"+
				(myLevel==0?"\1package-private\0":showModifiers(myLevel, ACC_SHOW_METHOD))+"\0";
			report(Kind.ERROR, "cu.override.unable", my.owner.replace('/', '.'), my, it.owner.replace('/', '.'), it, inline);
		}
	}
	// 注解的试用类型
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
		} else if (key instanceof IType) {
			// TYPE_PARAMETER, TYPE_USE
			throw new ResolveException("unsupported case");
		} else if (key instanceof ParamAnnotationRef) {
			mask = AnnotationSelf.PARAMETER;
		} else if (key instanceof Variable) {
			mask = AnnotationSelf.LOCAL_VARIABLE;
		} else {
			throw new ResolveException("unsupported case");
		}
		return (ctx.applicableTo&mask) != 0;
	}
	public void createDelegation(int acc, MethodNode my, MethodNode it) {
		CodeWriter c = newMethod(acc, it.name(), it.rawDesc());
		int size = (1 + TypeHelper.paramSize(it.rawDesc()));
		c.visitSize(size, size);
		c.one(ALOAD_0);

		List<Type> myPar = my.parameters();
		List<Type> itPar = it.parameters();

		for (int i = 0; i < myPar.size(); i++) {
			Type castFrom = itPar.get(i), cTo = myPar.get(i);
			c.varLoad(cTo, i+1);

			ctx.castTo(castFrom, cTo, TypeCast.E_DOWNCAST).write(c);
		}

		c.invoke(my.name().startsWith("<") ? INVOKESPECIAL : INVOKEVIRTUAL, my);

		ctx.castTo(my.returnType(), it.returnType(), TypeCast.E_DOWNCAST).write(c);
		c.one(it.returnType().shiftedOpcode(IRETURN));
		c.finish();
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
		clinit = ctx.classes.createMethodPoet(this, node);
		node.putAttr(new AttrCodeWriter(cp, node, clinit));
		clinit.visitSize(10,10);
		return clinit;
	}
	public MethodWriter getGlobalInit() {
		if (glinit != null) return glinit;

		SimpleList<ConstantData> _throws = new SimpleList<>();
		for (MethodNode method : methods) {
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
					for (int i = _throws.size()-1; i >= 0; i--) {
						var exParent = ctx.parentListOrReport(_throws.get(i));
						for (String s : list.value) {
							var self = ctx.classes.getClassInfo(s);

							if (ctx.parentListOrReport(self).containsValue(exParent.get(0))) {
								if (!_throws.contains(self)) _throws.add(self);
							} else if (exParent.containsValue(s)) continue loop;
						}

						_throws.remove(i);
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
		return glinit = ctx.classes.createMethodPoet(this, mn);
	}
	public void appendGlobalInit(MethodWriter target) {if (glinit != null) glinit.writeTo(target);}

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

	public void S4_Code() throws ParseException {
		LocalContext ctx = this.ctx = LocalContext.get();
		ctx.setClass(this);

		// 类型参数的更新在LocalContext#setMethod里，有点想移走
		currentNode = signature;
		wr.state = STATE_EXPR;

		// 优先级的定义写在ParseTask中
		lazyTasks.sort((o1, o2) -> Integer.compare(o1.priority(), o2.priority()));
		for (int i = 0; i < lazyTasks.size(); i++) lazyTasks.get(i).parse();
		lazyTasks.clear();

		if (clinit != null) clinit.one(Opcodes.RETURN);

		for (FieldNode field : finalFields) {
			wr.index = fieldIdx.get(fields.indexOfAddress(field));
			report(Kind.ERROR, "cu.finalField.missed", field.name());
		}
		finalFields.clear();

		if (name.equals("Test")) {
			// NOVERIFY
			parent("java/lang/🔓_IL🐟");
		}
	}
	// endregion

	public String getSourceFile() {return source;}

	public final void report(Kind kind, String code) {ctx.classes.report(this, kind, wr.index, code);}
	public final void report(Kind kind, String code, Object ... args) {ctx.classes.report(this, kind, wr.index, code, args);}

	// this function only invoke on Stage 4
	public IType readType(@MagicConstant(flags = {TYPE_PRIMITIVE, TYPE_OPTIONAL, TYPE_STAR, TYPE_GENERIC, TYPE_LEVEL2}) int flag) throws ParseException {
		IType type = _type(wr, flag);
		// TODO maybe not full suitable (resolve later)
		if (type instanceof GenericPrimer g) return g.resolve(currentNode);
		return type;
	}

	public void cancelTask(Attributed node) {
		// field method parseTask
		throw new UnsupportedOperationException("not implemented yet!");
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
}