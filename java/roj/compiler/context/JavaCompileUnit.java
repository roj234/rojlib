package roj.compiler.context;

import org.jetbrains.annotations.Nullable;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.attr.*;
import roj.asm.attr.MethodParameters.MethodParam;
import roj.asm.cp.CstClass;
import roj.asm.type.*;
import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.LavaFeatures;
import roj.compiler.api.Types;
import roj.compiler.asm.LPSignature;
import roj.compiler.asm.ParamAnnotationRef;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.GeneratorUtil;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.diagnostic.Kind;
import roj.compiler.doc.Javadoc;
import roj.compiler.doc.JavadocProcessor;
import roj.compiler.jpp.NativeStruct;
import roj.compiler.resolve.NestContext;
import roj.config.ParseException;
import roj.config.Word;
import roj.io.IOUtil;
import roj.util.ArrayUtil;

import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;
import static roj.config.Word.*;

/**
 * Lava Compiler - 类结构Parser (Stage1 for JavaLike)<p>
 * Parser levels: <ol>
 *     <li><b><i>Class Parser</i></b></li>
 *     <li>{@link ParseTask Segment Parser}</li>
 *     <li>{@link BlockParser Method Parser}</li>
 *     <li>{@link ExprParser Expression Parser}</li>
 * </ol>
 * @author Roj234
 * @since 2025/2/15 3:42
 */
public final class JavaCompileUnit extends CompileUnit {
	public static JavaCompileUnit create(String className, String code) {return new JavaCompileUnit(className, code);}

	public JavaCompileUnit(String name, String code) {super(name, code);}
	// region 文件中的其余类
	private JavaCompileUnit(JavaCompileUnit parent, boolean helperClass) {super(parent, helperClass);}

	private JavaCompileUnit _newHelper(int acc) throws ParseException {
		JavaCompileUnit c = new JavaCompileUnit(this, true);

		int i = name.lastIndexOf('/') + 1;
		c.name = i <= 0 ? "" : name.substring(0, i);
		c.header(acc);

		ctx.classes.addCompileUnit(c, true);
		return c;
	}
	private JavaCompileUnit _newInner(int acc) throws ParseException {
		JavaCompileUnit c = new JavaCompileUnit(this, false);

		c.name = name.concat("$");
		c.header(acc|_ACC_INNER_CLASS);
		acc = c.modifier;

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_INNER_CLASS)) {
			if ((acc & (ACC_INTERFACE|ACC_ENUM|_ACC_RECORD)) != 0) acc |= ACC_STATIC;

			var desc = InnerClasses.Item.innerClass(c.name, acc);
			this.innerClasses().add(desc);
			c.innerClasses().add(desc);
		}

		if (ctx.classes.getMaximumBinaryCompatibility() >= LavaFeatures.JAVA_11)
			addNestMember(c);

		if ((acc&ACC_PROTECTED) != 0) {
			acc &= ~ACC_PROTECTED;
			acc |= ACC_PUBLIC;
		}
		c.modifier = (char) (acc&~(ACC_PRIVATE|ACC_STATIC));

		ctx.classes.addCompileUnit(c, true);
		return c;
	}
	public JavaCompileUnit newAnonymousClass(@Nullable MethodNode mn) throws ParseException {
		JavaCompileUnit c = new JavaCompileUnit(this, false);

		c.name(c.name = IOUtil.getSharedCharBuf().append(name).append("$").append(++_children).toString());
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
			c.addAttribute(ownerMethod);
		}

		if (ctx.classes.getMaximumBinaryCompatibility() >= LavaFeatures.JAVA_11)
			addNestMember(c);

		return c;
	}
	// endregion
	// region 阶段1非公共部分: package, import, package-info, module-info
	public boolean S1_Struct() throws ParseException {
		var ctx = LocalContext.get();
		ctx.setClass(this);

		var wr = ctx.lexer;

		wr.index = 0;
		wr.state = STATE_CLASS;
		wr.javadoc = null;

		// 默认包""
		String pkg = "";
		Word w = wr.next();

		// 空
		if (w.type() == EOF) return false;

		boolean isNormalClass = !source.contains("-info");
		boolean packageAnnotation = w.type() == at;
		if (packageAnnotation) {
			attachJavadoc(this);
			ctx.tmpAnnotations.clear();
			readAnnotations(ctx.tmpAnnotations);
			commitAnnotations(this);
			w = wr.next();
		}

		boolean moduleIsOpen = w.val().equals("open");
		if (w.type() == MODULE || moduleIsOpen) {
			if (isNormalClass) ctx.report(Kind.ERROR, "module.unexpected");

			if (moduleIsOpen) wr.except(MODULE);

			wr.state = STATE_MODULE;
			parseModuleInfo(wr, moduleIsOpen);
			return packageAnnotation;
		}

		if (w.type() == PACKAGE) {
			if (packageAnnotation && isNormalClass) ctx.report(Kind.ERROR, "package.annotation");

			pkg = readRef(false).append('/').toString();

			w = wr.optionalNext(semicolon);

			// package-info without helper classes
			if (w.type() == EOF) return packageAnnotation;
		}

		if (ctx.classes.hasFeature(LavaFeatures.ATTR_SOURCE_FILE))
			addAttribute(new StringAttribute(Annotations.SourceFile, source));

		importList.clear();
		if (w.type() == PACKAGE_RESTRICTED) {
			importList.setRestricted(true);
			w = wr.optionalNext(semicolon);
		}

		var tmp = ctx.getTmpSb();
		while (w.type() == IMPORT) {
			boolean impField = wr.nextIf(STATIC);
			boolean unimport = !wr.nextIf(sub);

			readRef(unimport);

			importBlock:
			if (tmp.charAt(tmp.length()-1) == '*') {
				// import *
				if (tmp.length() == 1) {
					tmp.clear();
					importList.setImportAny(true);
				} else {
					tmp.setLength(tmp.length()-2);
				}

				List<String> list = !impField ? importList.getImportPackage() : importList.getImportStaticClass();
				// noinspection all
				if (!list.contains(tmp)) list.add(tmp.toString());
			} else {
				int i = tmp.lastIndexOf("/");
				if (i < 0) ctx.report(Kind.SEVERE_WARNING, "import.unpackaged");

				MyHashMap<String, String> map = !impField ? importList.getImportClass() : importList.getImportStatic();
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
		name = pkg;

		if (isNormalClass) {
			int acc = readModifiers(wr, CLASS_ACC);
			header(acc);
		} else {
			name(name = pkg.concat("package-info"));
			attachJavadoc(this);
		}

		boolean shouldAdd = isNormalClass || packageAnnotation;
		if (shouldAdd) ctx.classes.addCompileUnit(this, false);

		// 辅助类
		while (!wr.nextIf(EOF)) {
			int acc = readModifiers(wr, CLASS_ACC);
			_newHelper(acc);
		}

		return shouldAdd;
	}
	private void parseModuleInfo(JavaLexer wr, boolean moduleIsOpen) throws ParseException {
		setMinimumBinaryCompatibility(LavaFeatures.JAVA_9);
		modifier = ACC_MODULE;
		name(name = "module-info");

		var a = new ModuleAttribute(readModuleName(), moduleIsOpen ? ACC_OPEN : 0);
		addAttribute(a);
		wr.except(lBrace);

		var names = ctx.getTmpSet();

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
				case rBrace -> {break loop;}
				case REQUIRES -> {
					int access = 0;
					if (wr.nextIf(TRANSITIVE)) access |= ACC_TRANSITIVE;
					if (wr.nextIf(STATIC)) access |= ACC_STATIC_PHASE;
					do {
						String name = readModuleName();
						if (!names.add("R;"+name)) ctx.report(Kind.ERROR, "module.dup.requires", name);

						a.requires.add(new ModuleAttribute.Module(name, access));

						w = wr.next();
					} while (w.type() == comma);
				}
				case EXPORTS, OPENS -> {
					boolean isExport = w.type() == EXPORTS;
					if (!isExport && moduleIsOpen) ctx.report(Kind.ERROR, "module.open");

					String from = readRef();

					var info = new ModuleAttribute.Export(from);
					(isExport?a.exports:a.opens).add(info);

					if (!names.add((isExport?"E;":"O;")+from)) ctx.report(Kind.ERROR, "module.dup.exports", from);

					w = wr.next();
					if (w.type() == TO) {
						do {
							String to = readModuleName();
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

					var info = new ModuleAttribute.Provide(spi);
					a.provides.add(info);

					if (!names.add("P;"+spi)) ctx.report(Kind.ERROR, "module.dup.provides", spi);

					w = wr.next();
					if (w.type() == WITH) {
						do {
							String to = readModuleName();
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
	private String readModuleName() throws ParseException {return readRef().replace('/', '.');}

	// endregion
	// region 阶段1 类的结构
	private void header(int acc) throws ParseException {
		JavaLexer wr = ctx.lexer;

		// 虽然看起来很怪，但是确实满足了this inner和helper正确的javadoc提交逻辑
		_parent.attachJavadoc(this);
		// 修饰符和注解
		commitAnnotations(this);

		// 类型(class enum...)
		Word w = wr.next();
		switch (w.type()) {
			case INTERFACE -> {
				if ((acc & (ACC_FINAL)) != 0) ctx.report(Kind.ERROR, "modifier.conflict", "final", "interface");
				acc |= ACC_ABSTRACT|ACC_INTERFACE;
			}
			case AT_INTERFACE -> {
				if ((acc & (ACC_FINAL)) != 0) ctx.report(Kind.ERROR, "modifier.conflict", "final", "@interface");
				notSealable(acc);
				acc |= ACC_ANNOTATION|ACC_INTERFACE|ACC_ABSTRACT;
				addInterface("java/lang/annotation/Annotation");
			}
			case ENUM -> {
				notSealable(acc);
				acc |= ACC_ENUM|ACC_FINAL;
				parent("java/lang/Enum");
			}
			case RECORD -> {
				notSealable(acc);
				setMinimumBinaryCompatibility(LavaFeatures.JAVA_17);
				acc |= ACC_FINAL|_ACC_RECORD;
				parent("java/lang/Record");
			}
			case STRUCT -> {
				notSealable(acc);
				acc |= ACC_FINAL|_ACC_RECORD|_ACC_STRUCT;
				parent("roj/compiler/runtime/Struct");
			}
			case CLASS -> acc |= ACC_SUPER;
			default -> throw wr.err("unexpected_2\1"+w.val()+"\0\1cu.except.type\0");
		}
		modifier = (char)acc;
		extraModifier = acc;

		// 名称
		w = wr.except(LITERAL, "cu.name");
		classIdx = w.pos()+1;

		if (ctx.classes.hasFeature(LavaFeatures.VERIFY_FILENAME) && (acc&ACC_PUBLIC) != 0 && _parent == this && !IOUtil.fileName(source).equals(w.val())) {
			ctx.report(Kind.SEVERE_WARNING, "cu.filename", source);
		}

		name(name = name.concat(w.val()));

		// 泛型参数和范围
		w = wr.next();
		if (w.type() == lss) { // <
			genericDecl(wr);

			// 泛型非静态类
			if ((acc & _ACC_INNER_CLASS) != 0) {
				var t = _parent;
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
			if ((acc & (ACC_ENUM|ACC_ANNOTATION|_ACC_RECORD)) != 0) ctx.report(Kind.ERROR, "cu.noInheritance", name, parent());
			if ((acc & ACC_INTERFACE) != 0) break checkExtends;

			IType type = readType(wr, TYPE_GENERIC|TYPE_NO_ARRAY);
			if (type.genericType() > 0 || currentNode != null) makeSignature()._add(type);
			parent(type.owner());

			w = wr.next();
		} else if ((acc & ACC_ENUM) != 0) {
			makeSignature()._add(new Generic("java/lang/Enum", Collections.singletonList(Type.klass(name))));
		} else if (currentNode != null) {
			currentNode._add(Types.OBJECT_TYPE);
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

			var attr = new RecordAttribute();
			addAttribute(attr);

			if (!wr.nextIf(rParen)) do {
				readModifiers(wr, _ACC_ANNOTATION);

				IType type = readType(wr, TYPE_PRIMITIVE|TYPE_GENERIC|SKIP_TYPE_PARAM);
				if (type.genericType() != 0) makeSignature().returns = type;

				String name = wr.except(LITERAL, "cu.name").val();

				var field = new FieldNode(((acc & _ACC_STRUCT) != 0)
						? ACC_PUBLIC
						: (ctx.classes.hasFeature(LavaFeatures.PUBLIC_RECORD_FIELD) ? ACC_PUBLIC : ACC_PRIVATE) | ACC_FINAL,
					name, type.rawType());

				commitAnnotations(field);
				finishSignature(null, Signature.FIELD, field);

				fields.add(field);
				if ((acc & _ACC_STRUCT) == 0) finalFields.add(field);
				fieldIdx.add(wr.index);

				w = wr.next();
				if ((acc&_ACC_STRUCT) != 0 && w.type() == lBracket) {
					// C-style array definition
					field.modifier |= ACC_NATIVE; // SPECIAL PROCESSING

					IntList list = new IntList();
					do {
						list.add(wr.except(INTEGER).asInt());
						wr.except(rBracket);
						w = wr.next();
					} while (w.type() == lBracket);

					var clone = (Type) TypeHelper.arrayTypeNC(field.fieldType());
					clone.setArrayDim(list.size());
					field.fieldType(clone);

					field.addAttribute(new NativeStruct(list));
				}

				attr.fields.add(RecordAttribute.Field.link(field));
			} while (w.type() == comma);

			currentNode = classSelf;

			if (w.type() != rParen) throw wr.err("unexpected_2\1"+w.val()+"\0\1cu.except.record\0");

			miscFieldId = fields.size();

			if ((acc & _ACC_STRUCT) != 0) {
				modifier |= ACC_NATIVE;
				wr.except(semicolon);
				break structCheck;
			}

			w = wr.next();
		}

		// 实现
		if (w.type() == ((acc & ACC_INTERFACE) != 0 ? EXTENDS : IMPLEMENTS)) {
			var interfaces = itfList();
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

			var subclasses = new ClassListAttribute(Attribute.PermittedSubclasses);
			addAttribute(subclasses);

			do {
				IType type = readType(wr, TYPE_NO_ARRAY);
				subclasses.value.add(type.owner());

				w = wr.next();
			} while (w.type() == comma);
		}

		signature = finishSignature(_parent == this ? null : _parent.signature, Signature.CLASS, this);
		for (int i = 0; i < miscFieldId; i++) {
			var sign = (LPSignature) fields.get(i).getRawAttribute("Signature");
			if (sign != null) sign.parent = signature;
		}

		if (w.type() == semicolon) return;
		wr.retractWord();

		body();
		}
	}
	private void notSealable(int acc) {
		if ((acc & (_ACC_SEALED|_ACC_NON_SEALED)) != 0) ctx.report(Kind.ERROR, "modifier.conflict", "sealed", "final");
	}
	private void body() throws ParseException {
		LocalContext ctx = this.ctx;
		JavaLexer wr = ctx.lexer;
		Word w;

		wr.except(lBrace);

		var names = ctx.getTmpSet();
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
			acc = ACC_PUBLIC|ACC_STATIC|ACC_STRICT|ACC_FINAL|ACC_ABSTRACT|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED|_ACC_ASYNC;
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
			acc = readModifiers(wr, acc);

			if ((modifier & ACC_INTERFACE) != 0) {
				if ((acc & ACC_PRIVATE) == 0) {
					acc |= ACC_PUBLIC;
				} else {
					setMinimumBinaryCompatibility(LavaFeatures.JAVA_9);
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
					if ((acc& ~(ACC_STATIC|_ACC_ANNOTATION|_ACC_SEALED|_ACC_NON_SEALED)) != 0) ctx.report(Kind.ERROR, "modifier.conflict", showModifiers(acc & ~ACC_STATIC, ACC_SHOW_METHOD), "cu.except.initBlock");
					if ((acc & ACC_STATIC) == 0) {
						if ((acc & ACC_INTERFACE) != 0) ctx.report(Kind.ERROR, "cu.interfaceInit");
						addParseTask(ParseTask.InstanceInitBlock(this));
					} else {
						// no interface checks 😄
						addParseTask(ParseTask.StaticInitBlock(this));
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
					type = Type.primitive(Type.VOID);

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
					if (w.type() != rBracket) throw wr.err("unexpected_2\1"+w.type()+"\0\1cu.except.multiArg\0");
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
				attachJavadoc(method);
				method.setReturnType(type);
				commitAnnotations(method);

				List<String> paramNames = ctx.tmpList; paramNames.clear();

				if (name.equals("<init>")) {
					if ((modifier&ACC_INTERFACE) != 0) ctx.report(Kind.ERROR, "cu.interfaceInit");
					else if ((modifier & ACC_ENUM) != 0) {
						paramNames.add("@name");
						paramNames.add("@ordinal");
						var par = method.parameters();
						par.add(Types.STRING_TYPE);
						par.add(Type.primitive(Type.INT));
					}
					else if (_parent != this && (extraModifier & ACC_STATIC) == 0) {
						paramNames.add(NestContext.InnerClass.FIELD_HOST_REF);
						var par = method.parameters();
						par.add(Type.klass(_parent.name()));
					}
				}

				w = wr.next();
				if (w.type() != rParen) {
					wr.retractWord();

					boolean lsVarargs = false;
					MethodParameters parAccName;
					if (ctx.classes.hasFeature(LavaFeatures.ATTR_METHOD_PARAMETERS)) {
						parAccName = new MethodParameters();
						method.addAttribute(parAccName);
						if (name.equals("<init>") && (modifier&ACC_ENUM) != 0) {
							parAccName.flags.add(new MethodParam(null, ACC_SYNTHETIC));
							parAccName.flags.add(new MethodParam(null, ACC_SYNTHETIC));
						}
					} else {
						parAccName = null;
					}

					do {
						if (lsVarargs) ctx.report(Kind.ERROR, "cu.method.paramVararg");

						int acc1 = readModifiers(wr, ACC_FINAL | _ACC_ANNOTATION);

						IType parType = readType(wr, TYPE_PRIMITIVE|TYPE_GENERIC|SKIP_TYPE_PARAM);
						if (parType.genericType() != 0) makeSignature()._add(paramNames.size(), (Generic) parType);

						w = wr.next();
						if (w.type() == varargs) {
							lsVarargs = true;
							w = wr.next();
							parType = TypeHelper.arrayTypeNC(parType);
						}

						List<Type> p = method.parameters();
						p.add(parType.rawType());
						if (parType.rawType().type == VOID) ctx.report(Kind.ERROR, "cu.method.paramVoid");
						if (p.size() > 255) ctx.report(Kind.ERROR, "cu.method.paramCount");

						if (!ctx.tmpAnnotations.isEmpty()) {
							commitAnnotations(new ParamAnnotationRef(method, w.pos(), paramNames.size()));
						}

						if (w.type() == LITERAL) {
							var pname = w.val();
							if (pname.equals("_")) {
								pname = "@skip";
							} else {
								if (paramNames.contains(pname)) ctx.report(Kind.ERROR, "cu.method.paramConflict");
							}
							paramNames.add(pname);
							if (parAccName != null) {
								parAccName.flags.add(new MethodParam(pname, (char) acc1));
							}
						} else {
							throw wr.err("unexpected\1"+w.val()+"\0");
						}

						w = wr.next();
						if (w.type() == assign) {
							ParseTask.MethodDefault(this, method, paramNames.size());
							w = wr.next();
						}
					} while (w.type() == comma);

					if (w.type() != rParen) throw wr.err("unexpected\1"+w.val()+"\0");

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

					var excList = new ClassListAttribute(Attribute.Exceptions);
					method.addAttribute(excList);

					do {
						IType type1 = readType(wr, TYPE_GENERIC|TYPE_NO_ARRAY);
						excList.value.add(type1.owner());
						if (type1.genericType() != 0) makeSignature().exceptions.add(type1);

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
							addParseTask(ParseTask.AnnotationDefault(this, method));
							continue;
						}

						break noMethodBody;
					}

					//   是接口且没有default且不是static
					if ((modifier & ACC_INTERFACE) != 0) {
						if ((acc & (ACC_STATIC|_ACC_DEFAULT|ACC_PRIVATE)) == 0) {
							if ((acc & ACC_FINAL) != 0) {
								// 接口不能加final
								ctx.report(Kind.ERROR, "modifier.notAllowed", "final");
							}
							break noMethodBody;
						}

						// 接口中的非抽象方法在Java8引入
						setMinimumBinaryCompatibility(LavaFeatures.JAVA_8);
					}

					if (w.type() != lBrace) {
						ctx.report(Kind.ERROR, "cu.method.mustHasBody");
						break noMethodBody;
					}

					addParseTask((acc & _ACC_ASYNC) != 0
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

				JavadocProcessor processor;
				Javadoc javadoc = ctx.lexer.javadoc;
				if (javadoc != null) {
					ctx.lexer.javadoc = null;
					processor = ctx.classes.createJavadocProcessor(javadoc, this);
				} else {
					processor = null;
				}

				while (true) {
					var field = new FieldNode(acc, name, type);

					if (ctx.lexer.javadoc != null) processor = null;
					if (processor == null) attachJavadoc(field);
					else processor.attach(field);

					if (!names.add(name)) {
						ctx.report(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
					}

					if (list != null) addAnnotations(field, list);
					if (s != null) field.addAttribute(s);

					fields.add(field);
					if ((acc & ACC_FINAL) != 0) finalFields.add(field);
					fieldIdx.add(w.pos());

					w = wr.next();
					if (w.type() == assign) addParseTask(ParseTask.Field(this, field));

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

		Type selfType = Type.klass(name);

		List<ExprNode> enumInit = new SimpleList<>();

		Word w = wr.next();
		while (w.type() == LITERAL) {
			String name = w.val();
			if (!names.add(name)) ctx.report(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);

			fieldIdx.add(wr.index);

			FieldNode f = new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL|ACC_ENUM, name, selfType);
			attachJavadoc(f);

			// maybe should putin ParseTask?

			List<ExprNode> args = new SimpleList<>();
			args.add(ExprNode.valueOf(f.name()));
			args.add(ExprNode.valueOf(fields.size()));

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

				if (ctx.classes.getMaximumBinaryCompatibility() >= LavaFeatures.JAVA_17) {
					setMinimumBinaryCompatibility(LavaFeatures.JAVA_17);

					var ps = (ClassListAttribute) getRawAttribute("PermittedSubclasses");
					if (ps == null) addAttribute(ps = new ClassListAttribute(Attribute.PermittedSubclasses));
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
			addParseTask((lc) -> {
				var cw = getStaticInit();
				lc.setMethod(cw.mn);
				lc.enumConstructor = true;

				for (int i = 0; i < enumInit.size(); i++) {
					lc.errorReportIndex = fieldIdx.get(i);

					enumInit.get(i).resolve(lc).write(cw);
					cw.field(PUTSTATIC, this, i);

					finalFields.remove(fields.get(i));
				}
				lc.errorReportIndex = -1;

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
	// endregion
}