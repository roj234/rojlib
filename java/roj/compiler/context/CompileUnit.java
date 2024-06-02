package roj.compiler.context;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.visitor.CodeWriter;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.CompilerSpec;
import roj.compiler.JavaLexer;
import roj.compiler.asm.*;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.TypeCast;
import roj.compiler.resolve.TypeResolver;
import roj.config.ParseException;
import roj.config.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static roj.asm.Opcodes.*;
import static roj.compiler.JavaLexer.*;
import static roj.config.Word.EOF;
import static roj.config.Word.LITERAL;

/**
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public final class CompileUnit extends ConstantData {
	public static final Type RUNTIME_EXCEPTION = new Type("java/lang/RuntimeException");
	private Object _next;

	static final int ACC_METHOD_INACCEPTABLE = ACC_TRANSIENT|ACC_VOLATILE;
	static final int ACC_FIELD_INACCEPTABLE = ACC_STRICT|ACC_NATIVE;
	static final int _ACC_DEFAULT = 1 << 17, _ACC_ANNOTATION = 1 << 18, _ACC_RECORD = 1 << 19, _ACC_INNERCLASS = 0x0040, _ACC_ANONYMOUS = 0x0080;

	private JavaLexer wr;

	final TypeResolver tr;

	SimpleList<AnnotationPrimer> annotations;

	// S0之后的诊断需要用到的起始位置等
	int classIdx;
	IntList methodIdx = new IntList(), fieldIdx = new IntList();

	private List<String> interfacesPre = Helpers.cast(interfaces);
	private Signature signature;

	// S2前的缓存
	private SignaturePrimer currentNode;

	private MyHashSet<IType> toResolve_unc = new MyHashSet<>();
	private MyHashSet<IType> genericPending = new MyHashSet<>();

	// code block task
	private final List<ParseTask> lazyTasks = new SimpleList<>();
	private final MyHashMap<Attributed, List<AnnotationPrimer>> annoTask = new MyHashMap<>();

	// Inner Class
	private CompileUnit _parent;
	public int _children;

	private String path;
	private LocalContext ctx;

	public CompileUnit(String name) {
		path = name;

		ctx = LocalContext.get();
		if (ctx.classes.isSpecEnabled(CompilerSpec.SOURCE_FILE)) putAttr(new AttrString(AttrString.SOURCE,name));

		tr = new TypeResolver();
		wr = new JavaLexer();
	}
	public CompileUnit(String name, InputStream in) throws IOException {
		this(name);
		wr.init(IOUtil.readString(in));
	}

	public TypeResolver getTypeResolver() {return tr;}

	// region 文件中的其余类

	private CompileUnit(CompileUnit parent) {
		this.path = parent.path;

		LocalContext.depth(1);
		ctx = LocalContext.get();

		if (ctx.classes.isSpecEnabled(CompilerSpec.SOURCE_FILE))
			putAttr(parent.attrByName(Attribute.SourceFile.name));

		_parent = parent;
		access = 0;

		wr = parent.wr;
		classIdx = wr.index;

		tr = parent.tr;
	}

	private static CompileUnit _newHelper(CompileUnit p) throws ParseException {
		CompileUnit c = new CompileUnit(p);

		int i = p.name.lastIndexOf('/') + 1;
		c.name(i <= 0 ? "" : p.name.substring(0, i));
		c.S1_Struct();

		LocalContext.depth(-1);
		return c;
	}

	public CompileUnit newAnonymousClass(@Nullable MethodNode mn) throws ParseException {
		CompileUnit c = new CompileUnit(this);

		// X(123) {
		//     void op() {}
		// }
		//           =>
		// class Anonymous$1 extends X {
		//    Anonymous$1(int a) {
		// 		  super(a);
		//    }
		//    void op() {}
		// }

		c.name(IOUtil.getSharedCharBuf().append(name).append("$").append(++_children).toString());
		c.access = ACC_SYNTHETIC|ACC_FINAL|ACC_SUPER|_ACC_ANONYMOUS;

		c.copyTmpFromCache();
		ctx.classes.addCompileUnit(c);
		c.body();

		InnerClasses.InnerClass desc = InnerClasses.InnerClass.anonymous(c.name, c.access);
		_innerClass(this).add(desc);
		_innerClass(c).add(desc);

		EnclosingMethod ownerAttr = new EnclosingMethod();
		ownerAttr.owner = name;
		if (mn != null) {
			ownerAttr.name = mn.name();
			ownerAttr.parameters = mn.parameters();
			ownerAttr.returnType = mn.returnType();
		}
		c.putAttr(ownerAttr);

		LocalContext.depth(-1);
		return c;
	}

	private static CompileUnit _newInner(CompileUnit p, int flag) throws ParseException {
		CompileUnit c = new CompileUnit(p);

		c.name(p.name.concat("$"));
		c.access = (char) (flag|_ACC_INNERCLASS);

		InnerClasses.InnerClass desc = InnerClasses.InnerClass.innerClass(c.name, 0);
		_innerClass(p).add(desc);
		_innerClass(c).add(desc);

		c.S1_Struct();

		LocalContext.depth(-1);
		return c;
	}

	private static List<InnerClasses.InnerClass> _innerClass(CompileUnit parent) {
		InnerClasses c = parent.parsedAttr(parent.cp, Attribute.InnerClasses);
		if (c == null) parent.putAttr(c = new InnerClasses());
		return c.classes;
	}

	// endregion

	public String getSimpleName() {
		int m = name.lastIndexOf('$');
		if (m < 0) m = name.lastIndexOf('/');
		if (m < 0) return name;
		return name.substring(m + 1);
	}

	public JavaLexer getLexer() {return wr;}
	public GlobalContext ctx() {return ctx.classes;}

	// region 阶段0 package、import、同时检查package-info
	public boolean S0_Init() throws ParseException {
		JavaLexer wr = this.wr;// = (JavaLexer) new JavaLexer().init(IOUtil.readUTF(in));
		CharList tmp = IOUtil.getSharedCharBuf();

		wr.env = CAT_HEADER|CAT_MODIFIER|CAT_TYPE_TYPE;

		// 默认包""
		String pkg = "";
		Word w = wr.next();
		if (w.type() == PACKAGE) {
			_klass(wr, tmp, 0);
			pkg = tmp.append('/').toString();

			w = wr.optionalNext(semicolon);
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

			_klass(wr, tmp, unimport);

			importBlock:
			if (tmp.charAt(tmp.length() - 1) == '*') {
				// import *
				if (tmp.length() == 1) {
					fireDiagnostic(Kind.WARNING, "import.any");
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
				if (i < 0) fireDiagnostic(Kind.SEVERE_WARNING, "import.unpackaged");

				MyHashMap<String, String> map = !impField ? tr.getImportClass() : tr.getImportStatic();
				String qualified = tmp.toString();

				if (unimport == 0) {
					map.put(qualified.substring(i+1), null);
					break importBlock;
				}

				String name = wr.nextIf(AS) ? wr.next().val() : qualified.substring(i+1);
				if ((qualified = map.put(name, qualified)) != null) {
					fireDiagnostic(Kind.ERROR, "import.conflict", tmp, name, qualified);
				}
			}

			wr.optionalNext(semicolon);
		}

		wr.retractWord();
		wr.env = CAT_MODIFIER|CAT_TYPE_TYPE;

		name(pkg);
		return true;
	}

	// endregion
	// region 阶段1 类的结构
	public CharList copyTmpFromCache() {
		LocalContext cache = ctx;

		toResolve_unc = cache.toResolve_unc;
		toResolve_unc.clear();

		annotations = cache.annotationTmp;
		annotations.clear();

		CharList tmp = cache.tmpList; tmp.clear();
		return tmp;
	}

	public void S1_Struct() throws ParseException {
		LocalContext ctx = this.ctx = LocalContext.get();

		tr.init(ctx);
		copyTmpFromCache();

		JavaLexer wr = this.wr;

		// 修饰符和注解
		int acc = (char) _modifier(wr, ACC_PUBLIC|ACC_FINAL|ACC_ABSTRACT|_ACC_ANNOTATION);
		if (!annotations.isEmpty()) addAnnotation(this, new SimpleList<>(annotations));

		wr.env = CAT_TYPE|CAT_MODIFIER|CAT_TYPE_TYPE|CAT_METHOD;

		// 类型(class enum...)
		Word w = wr.next();
		switch (w.type()) {
			case CLASS: // class
				acc |= ACC_SUPER;
			break;
			case INTERFACE: // interface
				if ((acc & (ACC_FINAL)) != 0) fireDiagnostic(Kind.ERROR, "modifier.conflict:interface:final");
				acc |= ACC_ABSTRACT|ACC_INTERFACE;
			break;
			case ENUM: // enum
				if ((acc & (ACC_ABSTRACT)) != 0) fireDiagnostic(Kind.ERROR, "modifier.conflict:enum:abstract");
				acc |= ACC_ENUM|ACC_FINAL;
				parent("java/lang/Enum");
			break;
			case AT_INTERFACE: // @interface
				if ((acc & (ACC_FINAL)) != 0) fireDiagnostic(Kind.ERROR, "modifier.conflict:@interface:final");
				acc |= ACC_ANNOTATION|ACC_INTERFACE|ACC_ABSTRACT;
				interfacesPre.add("java/lang/annotation/Annotation");
			break;
			case RECORD:
				acc |= ACC_FINAL|_ACC_RECORD;
				parent("java/lang/Record");
			break;
			default: throw wr.err("unexpected:"+w.val()+":cu.except.type");
		}
		access = (char)acc;

		if ((acc&_ACC_INNERCLASS) != 0) {
			List<InnerClasses.InnerClass> c = _innerClass(this);
			if ((acc & (ACC_INTERFACE|ACC_ENUM)) != 0) acc |= ACC_STATIC;
			c.get(c.size()-1).flags = (char) acc;
		}

		// 名称
		w = wr.except(LITERAL, "cu.except.name");
		classIdx = w.pos();
		name(name.concat(w.val()));

		// 泛型参数和范围
		w = wr.next();
		if (w.type() == lss) { // <
			_genericDeclare(wr, _signature());
			w = wr.next();
		}

		// 继承
		checkExtends:
		if (w.type() == EXTENDS) {
			if ((acc & (ACC_ENUM|ACC_ANNOTATION|_ACC_RECORD)) != 0) fireDiagnostic(Kind.ERROR, "cu.noInheritance", name, parent);
			if ((acc & ACC_INTERFACE) != 0) break checkExtends;

			IType type = _type(wr, ctx.tmpList, 0);
			if (type.genericType() > 0 || currentNode != null) _signature()._add(type);
			parent(type.owner());

			w = wr.next();
		}

		// record
		if (w.type() == lParen && (acc & _ACC_RECORD) != 0) {
			do {
				_modifier(wr, _ACC_ANNOTATION);

				IType type = _type(wr, ctx.tmpList, TYPE_PRIMITIVE|TYPE_GENERIC);
				if (type.genericType() != 0) _signature().returns = (Generic) type;

				String name = wr.except(LITERAL, "cu.name").val();

				FieldNode field = new FieldNode(ACC_PUBLIC|ACC_FINAL, name, type.rawType());

				if (!annotations.isEmpty()) addAnnotation(field, new SimpleList<>(annotations));
				Signature s = _signatureCommit((SignaturePrimer) signature, Signature.FIELD, null);
				if (s != null) field.putAttr(s);

				fields.add(field);
				fieldIdx.add(w.pos());

				w = wr.next();
			} while (w.type() == comma);

			if (w.type() != rParen) ctx.report(Kind.ERROR, "cu.except.record");
			w = wr.next();

			recordFieldPos = fields.size();
		}

		// 实现
		if (w.type() == ((acc & ACC_INTERFACE) != 0 ? EXTENDS : IMPLEMENTS)) {
			List<String> itfs = interfacesPre;
			do {
				IType type = _type(wr, ctx.tmpList, 0);
				if (type.genericType() > 0 || currentNode != null) _signature()._impl(type);
				itfs.add(type.owner());

				w = wr.next();
			} while (w.type() == comma);
		}

		wr.retractWord();

		signature = _signatureCommit(_parent == null ? null : (SignaturePrimer) _parent.signature, Signature.CLASS, null);
		if (signature != null) putAttr(signature);

		body();

		// 辅助类 (把文件读完)
		if (_parent == null) while (wr.hasNext()) {
			w = wr.next();
			if (w.type() != EOF) {
				wr.retractWord();
				_newHelper(this);
			}
		}
	}

	private void body() throws ParseException {
		JavaLexer wr = this.wr;
		Word w;
		CharList tmp = ctx.tmpList;

		wr.except(lBrace);

		MyHashSet<String> names = ctx.tmpSet; names.clear();
		// for record
		for (int i = 0; i < fields.size(); i++) {
			String name = fields.get(i).name();
			if (!names.add(name)) ctx.classes.report(this, Kind.ERROR, fieldIdx.get(i), "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
		}

		// 枚举的字段
		if ((access & ACC_ENUM) != 0) {
			Type selfType = new Type(name);

			w = wr.next();
			while (w.type() == LITERAL) {
				String name = w.val();
				if (!names.add(name)) fireDiagnostic(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);

				fieldIdx.add(w.pos());

				FieldNode f = new FieldNode(ACC_PUBLIC|ACC_STATIC|ACC_FINAL, name, selfType);
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
			acc = ACC_PUBLIC|ACC_STATIC|ACC_STRICT|ACC_FINAL|ACC_ABSTRACT|_ACC_ANNOTATION;
			switch (access & (ACC_INTERFACE|ACC_ANNOTATION)) {
				case ACC_INTERFACE:
					acc |= _ACC_DEFAULT;
					break;
				case ACC_INTERFACE|ACC_ANNOTATION:
					break;
				case 0:
					acc |= ACC_NATIVE|ACC_PRIVATE|ACC_PROTECTED|ACC_TRANSIENT|ACC_VOLATILE|ACC_SYNCHRONIZED;
					break;
			}
			acc = _modifier(wr, acc);

			// ## 3.2 泛型参数<T>
			w = wr.next();
			if (w.type() == lss) {
				_genericDeclare(wr, _signature());
				w = wr.next();
			}

			// ## 3.3.1 初始化和内部类
			switch (w.type()) {
				case lBrace: // static initializator
					if ((acc& ~ACC_STATIC) != 0) throw wr.err("modifier.conflict:"+acc+":cu.except.initBlock");
					lazyTasks.add((acc & ACC_STATIC) == 0 ? ParseTask.InstanceInitBlock(this) : ParseTask.StaticInitBlock(this));
				continue;
				case CLASS, INTERFACE, ENUM, AT_INTERFACE, RECORD:
					wr.retractWord();
					_newInner(this, acc);
				continue;
			}

			String name;
			Type type;

			// 3.3.2 方法、字段、构造器
			mof: {
				constructor:
				if (w.val().equals(getSimpleName())) {
					wr.mark();
					if (wr.next().type() != lParen) {
						wr.retract();
						break constructor;
					}
					wr.skip();

					name = "<init>";
					type = Type.std(Type.VOID);

					// 接口不能有构造器
					if ((access & ACC_INTERFACE) != 0)
						fireDiagnostic(Kind.ERROR, "cu.noConstructor:interface");

					// 枚举必须是private构造器
					else if ((access & ACC_ENUM) != 0) {
						if ((acc & (ACC_PUBLIC|ACC_PROTECTED)) != 0) {
							fireDiagnostic(Kind.ERROR, "cu.noConstructor.enum");
						} else {
							acc |= ACC_PRIVATE;
						}
					}
					break mof;
				}
				wr.retractWord();

				// ## 5.1.3 类型
				IType type1 = _type(wr, tmp, TYPE_PRIMITIVE|TYPE_GENERIC);
				if (type1.genericType() != 0)
					_signature().returns = (Generic) type1;
				type = type1.rawType();

				// method or field
				name = wr.except(LITERAL, "cu.name").val();

				w = wr.next();
			}

			if (w.type() == lParen) { // method
				methodIdx.add(w.pos());
				if ((acc & ACC_METHOD_INACCEPTABLE) != 0) fireDiagnostic(Kind.ERROR, "modifier.notAllowed", acc);

				MethodNode method = new MethodNode(acc, this.name, name, "()V");
				method.setReturnType(type);
				if (!annotations.isEmpty()) addAnnotation(method, new SimpleList<>(annotations));

				List<String> paramNames;

				w = wr.next();
				if (w.type() != rParen) {
					wr.retractWord();

					paramNames = new SimpleList<>();

					boolean lsVarargs = false;
					MethodParameters parAccName;
					if (ctx.classes.isSpecEnabled(CompilerSpec.KEEP_PARAMETER_NAME)) {
						parAccName = new MethodParameters();
						method.putAttr(parAccName);
					} else {
						parAccName = null;
					}

					do {
						if (lsVarargs) {
							fireDiagnostic(Kind.ERROR, "cu.method.paramVararg");
						}

						int acc1 = _modifier(wr, ACC_FINAL|_ACC_ANNOTATION);

						IType parType = _type(wr, tmp, TYPE_PRIMITIVE|TYPE_GENERIC);
						if (parType.genericType() != 0) _signature()._add(paramNames.size(), (Generic) parType);

						w = wr.next();
						if (w.type() == varargs) {
							lsVarargs = true;
							w = wr.next();
						}

						List<Type> p = method.parameters();
						p.add(parType.rawType());
						if (parType.rawType().type == VOID) fireDiagnostic(Kind.ERROR, "cu.method.paramVoid");
						if (p.size() > 255) fireDiagnostic(Kind.ERROR, "cu.method.paramCount");

						if (!annotations.isEmpty()) {
							addAnnotation(new ParamAnnotationRef(method, w.pos(), paramNames.size()), new SimpleList<>(annotations));
						}

						if (w.type() == LITERAL) {
							if (paramNames.contains(w.val())) fireDiagnostic(Kind.ERROR, "cu.method.paramConflict");
							paramNames.add(w.val());
						} else {
							throw wr.err("unexpected:" + w.val());
						}

						if (parAccName != null) {
							parAccName.flags.add(new MethodParam(w.val(), (char) acc1));
						}

						w = wr.next();
						if (w.type() == equ) {
							lazyTasks.add(ParseTask.MethodDefault(this, method, paramNames.size()));
						}
					} while (w.type() == comma);

					if (w.type() != rParen) throw wr.err("unexpected:"+w.val());

					if (lsVarargs) method.modifier |= ACC_VARARGS;
				} else {
					paramNames = Collections.emptyList();
				}

				if (!names.add(method.name() + method.rawDesc()))
					fireDiagnostic(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "invoke.method", method.name());
				method.modifier = (char) acc;
				methods.add(method);

				w = wr.next();
				// throws XX异常
				if (w.type() == THROWS) {
					if ((access & ACC_ANNOTATION) != 0) {
						fireDiagnostic(Kind.ERROR, "cu.method.annotationThrow");
					}

					AttrClassList excList = new AttrClassList(AttrClassList.EXCEPTIONS);
					method.putAttr(excList);

					do {
						IType type1 = _type(wr, tmp, TYPE_GENERIC);
						excList.value.add(type1.owner());
						if (type1.genericType() != 0) _signature().Throws.add(type1);

						w = wr.next();
					} while (w.type() == comma);
				}

				SignaturePrimer sign = _signatureCommit((SignaturePrimer) signature, Signature.METHOD, method);
				if (sign != null) method.putAttr(sign);

				// 不能包含方法体:
				noMethodBody: {
					//   被abstract或native修饰
					if ((acc & (ACC_ABSTRACT|ACC_NATIVE)) != 0) break noMethodBody;

					//   是注解且没有default
					//   注解不允许静态方法
					if ((access & ACC_ANNOTATION) != 0) {
						// 注解的default
						if (w.type() == DEFAULT) {
							lazyTasks.add(ParseTask.AnnotationDefault(this, method));
							continue;
						}

						break noMethodBody;
					}

					//   是接口且没有default且不是static
					if ((access & ACC_INTERFACE) != 0) {
						if ((acc & (ACC_STATIC|_ACC_DEFAULT)) == 0) {
							if ((acc & ACC_FINAL) != 0) {
								// 接口不能加final
								fireDiagnostic(Kind.ERROR, "modifier.notAllowed:final");
							}
							break noMethodBody;
						}
					}

					if (w.type() != lBrace) throw wr.err("cu.method.mustHasBody");

					lazyTasks.add(ParseTask.Method(this, method, paramNames));
					continue;
				}

				if ((acc & ACC_NATIVE) == 0) method.modifier |= ACC_ABSTRACT;
				if (w.type() != semicolon) throw wr.err("cu.method.mustNotBody");
			} else {
				// field
				if ((acc & ACC_FIELD_INACCEPTABLE) != 0) fireDiagnostic(Kind.ERROR, "modifier.notAllowed", acc);
				// 接口的字段必须是静态的
				if ((access & ACC_INTERFACE) != 0) {
					if ((acc & (ACC_PRIVATE|ACC_PROTECTED)) != 0) {
						fireDiagnostic(Kind.SEVERE_WARNING, "modifier.superset", acc);
					}

					acc |= ACC_STATIC;
				}

				wr.retractWord();

				Signature s = _signatureCommit((SignaturePrimer) signature, Signature.FIELD, null);

				List<AnnotationPrimer> list = annotations.isEmpty() ? null : new SimpleList<>(annotations);

				while (true) {
					FieldNode field = new FieldNode(acc, name, type);
					if (!names.add(name)) {
						fireDiagnostic(Kind.ERROR, "cu.nameConflict", ctx.currentCodeBlockForReport(), "symbol.field", name);
					}

					if (list != null) addAnnotation(field, list);
					if (s != null) field.putAttr(s);

					fields.add(field);
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

	public int _modifier(JavaLexer wr, int mask) throws ParseException {
		if ((mask & _ACC_ANNOTATION) != 0) annotations.clear();

		Word w;
		int acc = 0;
		while (true) {
			int f;
			w = wr.next();
			switch (w.type()) {
				case at -> {
					if ((mask & _ACC_ANNOTATION) == 0) fireDiagnostic(Kind.ERROR, "modifier.annotation");
					_annotations(annotations);
					acc |= _ACC_ANNOTATION;
					continue;
				}
				// n << 20 => conflict mask
				case PUBLIC -> 		f = (        1 << 20) | ACC_PUBLIC;
				case PROTECTED -> 	f = (        1 << 20) | ACC_PROTECTED;
				case PRIVATE -> 	f = (     0b11 << 20) | ACC_PRIVATE;
				case NATIVE -> 		f = (    0b100 << 20) | ACC_NATIVE;
				case SYNCHRONIZED ->f = (   0b1000 << 20) | ACC_SYNCHRONIZED;
				case FINAL -> 		f = (  0b10000 << 20) | ACC_FINAL;
				case STATIC -> 		f = ( 0b100000 << 20) | ACC_STATIC;
				case CONST -> 		f = ( 0b110001 << 20) | ACC_PUBLIC | ACC_STATIC | ACC_FINAL;
				case DEFAULT -> 	f = ( 0b110110 << 20) | _ACC_DEFAULT;
				case STRICTFP -> 	f = (0b1000000 << 20) | ACC_STRICT;
				case ABSTRACT -> 	f = (0b1111110 << 20) | ACC_ABSTRACT;
				case VOLATILE -> 	f = ACC_VOLATILE;
				case TRANSIENT -> 	f = ACC_TRANSIENT;
				default -> {
					wr.retractWord();
					return acc & ((1<<20) - 1);
				}
			}

			if ((f & mask) == 0) {
				fireDiagnostic(Kind.ERROR, "modifier.notAllowed", w.val());
				continue;
			}

			if ((acc & f) != 0) {
				fireDiagnostic(Kind.ERROR, "modifier.conflict", w.val(), showModifiers(acc, ACC_SHOW_METHOD));
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
	private static void _klass(JavaLexer wr, CharList sb, int flags) throws ParseException {
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
	private IType _type(JavaLexer wr, CharList sb, int flags) throws ParseException {
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
			_klass(wr, sb, 0);

			if ((flags&TYPE_GENERIC) != 0) {
				CharSequence bound = null;

				w = wr.next();
				wr.retractWord();

				if (w.type() == lss ||
					(currentNode != null && sb != (bound = currentNode.getTypeBound(sb))) ||
					(signature != null && sb != (bound = ((SignaturePrimer) signature).getTypeBound(sb)))) {

					type = _genericUse(sb, (flags&TYPE_LEVEL2) != 0 ? GENERIC_SUBCLASS : 0);

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
			return toResolve_unc.intern(type);
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

	private SignaturePrimer _signature() {
		if (currentNode == null) currentNode = new SignaturePrimer(0);
		return currentNode;
	}
	private SignaturePrimer _signatureCommit(SignaturePrimer parent, int kind, MethodNode mn) {
		SignaturePrimer s = currentNode;
		if (s == null) return null;
		currentNode = null;

		s.parent = parent;
		s.type = (byte) kind;
		s.initS0(mn);
		return s;
	}

	// <T extends YYY<T, V> & ZZZ, V extends T & XXX>
	private void _genericDeclare(JavaLexer wr, SignaturePrimer s) throws ParseException {
		Word w;
		do {
			w = wr.next();
			if (w.type() != LITERAL) throw wr.err("type.error.illegalAnyType");

			s.addTypeParam(w.val());

			short id = EXTENDS;
			while (wr.hasNext()) {
				w = wr.next();
				if (w.type() != id) break;

				s.addBound(_genericUse(null, 0));

				id = and;
			}
		} while (w.type() == comma);

		if (w.type() != gtr) wr.unexpected(w.val(), "type.except.afterLss");
	}

	private static final int GENERIC_INNER = 1, GENERIC_SUBCLASS = 2;
	public IType _genericUse(CharSequence type, int flag) throws ParseException {
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
			} else {
				// A<B
				wr.retractWord();
			}

			// resolve B
			if ((flag & GENERIC_INNER) != 0) {
				Type pt = (Type) _type(wr, IOUtil.getSharedCharBuf(), TYPE_PRIMITIVE|TYPE_OPTIONAL);
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
			seg:
			while (true) {
				// <<或者>>
				wr.env &= ~CAT_METHOD;
				IType child = _genericUse(null, flag&TYPE_OPTIONAL|GENERIC_INNER);
				g.addChild(child);
				w = wr.next();

				if (child == Asterisk.anyGeneric) {
					//TODO WHICH FLAG?
					if ((flag&GENERIC_INNER) != 0 || w.type() != gtr) {
						fireDiagnostic(Kind.ERROR, "type.error.illegalAnyType");
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
			wr.env |= CAT_METHOD;

			w = wr.next();
		}

		if (w.type() == dot) {
			IType sub1 = _genericUse(wr.except(LITERAL).val(), flag&TYPE_OPTIONAL|GENERIC_SUBCLASS);
			if (sub1 == null) return Helpers.maybeNull();
			// 无法从参数化的类型中选择静态类
			if (sub1.genericType() != IType.GENERIC_TYPE) fireDiagnostic(Kind.ERROR, "type.error.partialGenericSub", sub1.owner());
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
			if (g.isRealGeneric()) fireDiagnostic(Kind.ERROR, "type.error.primitiveGeneric");
			return result;
		}

		result = g.toRealType(null);
		//TODO generic resolve
		genericPending.add(result);
		return toResolve_unc.intern(result);
	}

	public List<AnnotationPrimer> _annotations(List<AnnotationPrimer> list) throws ParseException {
		JavaLexer wr = this.wr;
		CharList tmp = IOUtil.getSharedCharBuf();

		while (true) {
			_klass(wr, tmp, 0);

			AnnotationPrimer a = new AnnotationPrimer(tmp.toString(), wr.index);
			// 允许忽略注解
			if (list != Collections.EMPTY_LIST) list.add(a);

			Word w = wr.next();
			if (w.type() != lParen) {
				if (w.type() == at) continue;

				wr.retractWord();
				return list;
			}

			do {
				if (a.assertValueOnly) fireDiagnostic(Kind.ERROR, "cu.annotation.valueOnly");

				wr.mark();
				String name = wr.next().val();

				w = wr.next();
				if (w.type() != assign) {
					wr.retract();

					a.assertValueOnly = true;
					a.newEntry("value", ParseTask.Annotation(this, a, "value"));
				} else {
					wr.skip();

					a.newEntry(name, ParseTask.Annotation(this, a, name));
				}
			} while (w.type() == comma);

			if (w.type() != rParen) throw wr.err("unexpected:"+w.val());

			w = wr.next();
			if (w.type() != at) {
				wr.retractWord();
				return list;
			}
		}
	}
	private void addAnnotation(Attributed node, List<AnnotationPrimer> anno) {annoTask.put(node, anno);}

	// endregion
	// region 阶段2 解析并验证类自身的引用
	public List<IType> getGenericEnv(CharSequence sb) {
		CharSequence bound;
		if ((currentNode != null && sb != (bound = currentNode.getTypeBound(sb))) ||
			(signature != null && sb != (bound = ((SignaturePrimer) signature).getTypeBound(sb)))) {
			return Collections.singletonList(new Type(bound.toString())); // TODO type bounds
		}
		return null;
	}

	public int recordFieldPos;
	public MyHashSet<FieldNode> finalStaticField = new MyHashSet<>(), finalObjectField = new MyHashSet<>();

	public void createDelegation(int acc, MethodNode mn) {
		CodeWriter c = newMethod(acc, mn.name(), mn.rawDesc());
		int size = (1 + TypeHelper.paramSize(mn.rawDesc()));
		c.visitSize(size, size);
		c.one(ALOAD_0);
		List<Type> pars = mn.parameters();
		for (int i = 0; i < pars.size(); i++) {
			c.varLoad(pars.get(i), i);
		}
		c.invoke(mn.name().startsWith("<") ? INVOKESPECIAL : INVOKEVIRTUAL, mn);
		c.finish();
	}
	public void S2_Parse() {
		LocalContext ctx = this.ctx = LocalContext.get();
		ctx.setClass(this);
		// region 继承可行性

		wr.index = classIdx;

		IClass pInfo = tr.resolve(ctx, parent);
        if (pInfo == null) {
			fireDiagnostic(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", parent, name+".parent");
			// TODO better fallback
			pInfo = GlobalContext.anyArray();
		} else {
            int acc = pInfo.modifier();
			if (0 != (acc & ACC_FINAL)) {
				fireDiagnostic(Kind.ERROR, "cu.resolve.notInheritable", "cu.final", parent);
			} else if (0 != (acc & ACC_INTERFACE)) {
				fireDiagnostic(Kind.ERROR, "cu.resolve.notInheritable", "cu.interface", parent);
			}

			ctx.assertAccessible(pInfo);
			parent(pInfo.name());
        }

		if (signature != null && ctx.instanceOf(parent, "java/lang/Throwable")) {
			fireDiagnostic(Kind.ERROR, "cu.genericException");
		}

		List<String> itfs = interfacesPre;
		interfacesPre = null;

		for (int i = 0; i < itfs.size(); i++) {
			IClass info = tr.resolve(ctx, itfs.get(i));
            if (info == null) {
				fireDiagnostic(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", itfs.get(i), name+".interface["+i+"]");
			} else {
                int acc = info.modifier();
                if (0 == (acc & ACC_INTERFACE)) {
					fireDiagnostic(Kind.ERROR, "cu.resolve.notInheritable", "cu.class", info.name());
                }

				ctx.assertAccessible(info);
				interfaces.set(i, cp.getClazz(info.name()));
            }
		}

		boolean autoInit = (access & ACC_INTERFACE) == 0;
		List<MethodNode> methods = this.methods;
		for (int i = 0; i < methods.size(); i++) {
			wr.index = methodIdx.get(i);
			MethodNode m = methods.get(i);

			if (m.name().equals("<init>")) {
				autoInit = false;
			}

			// resolve异常
			AttrClassList exThrown = (AttrClassList) m.attrByName("Exceptions");
			if (exThrown != null) {
				List<String> classes = exThrown.value;
				for (int j = 0; j < classes.size(); j++) {
					IClass info = tr.resolve(ctx, classes.get(j));
					if (info == null) {
						fireDiagnostic(Kind.ERROR, "symbol.error.noSuchClass", classes.get(i));
					} else {
						ctx.assertAccessible(info);

						if (!ctx.instanceOf(info.name(), "java/lang/Throwable"))
							fireDiagnostic(Kind.ERROR, "cu.throwException", classes.get(i));

						classes.set(j, info.name());
					}
				}
			}
		}

		autoInit:
		if (autoInit) {
			int superInit = pInfo.getMethod("<init>", "()V");
			if (superInit < 0) {
				fireDiagnostic(Kind.ERROR, "cu.noDefaultConstructor");
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

		// endregion
		// region Type扩展
		wr.index = -2;

		// Depreacted
		for (IType type : toResolve_unc)
			ctx.resolveType(type);
		toResolve_unc.clear();

		if (signature != null) ((SignaturePrimer) signature).resolveS2();

		for (int i = 0; i < methods.size(); i++) {
			SignaturePrimer s = (SignaturePrimer) methods.get(i).attrByName("Signature");
			if (s != null) s.resolveS2();
		}

		// endregion
	}
	// endregion
	// region 阶段3 可能执行多次 注解处理
	public void S3_Annotation() throws ParseException {
		MyHashSet<String> missed = ctx.tmpSet;
		for (Map.Entry<Attributed, List<AnnotationPrimer>> annotated : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			List<AnnotationPrimer> list = annotated.getValue();
			for (int i = 0; i < list.size(); i++) {
				AnnotationPrimer a = list.get(i);
				wr.index = a.pos;

				IClass inst = tr.resolve(ctx, a.type());
				if (inst == null) {
					fireDiagnostic(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", a.type(), ctx.currentCodeBlockForReport());
					continue;
				}
				a.setType(inst.name());

				AnnotationSelf ad = ctx.classes.getAnnotationDescriptor(inst);

				if (!ctx.classes.applicableToNode(ad, annotated.getKey())) fireDiagnostic(Kind.ERROR, "cu.annotation.notApplicable", inst.name(), annotated.getKey().getClass());

				missed.clear();
				for (Map.Entry<String, AnnVal> entry : ad.values.entrySet()) {
					String name = entry.getKey();

					ParseTask task = Helpers.cast(a.values.remove(name));
					if (task != null) task.parse();
					else if (entry.getValue() == null) missed.add(name);
				}

				if (!a.values.isEmpty()) fireDiagnostic(Kind.ERROR, "cu.annotation.extra", inst.name(), a.values.keySet());
				if (!missed.isEmpty()) fireDiagnostic(Kind.ERROR, "cu.annotation.missing", inst.name(), missed);

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
	// endregion
	// region 阶段4 编译代码块
	private MethodWriter clinit, glinit;
	public MethodWriter getStaticInit() {
		if (clinit != null) return clinit;

		int v = getMethod("<clinit>");
		if (v < 0) {
			v = methods.size();
			methods.add(new MethodNode(ACC_PUBLIC|ACC_STATIC|ACC_SYNTHETIC, name, "<clinit>", "()V"));
		}

		return clinit = ctx.classes.createMethodPoet(this, methods.get(v));
	}

	public MethodWriter getGlobalInit() {
		if (glinit != null) return glinit;

		MethodNode mn = new MethodNode(ACC_PUBLIC | ACC_SYNTHETIC, name, "<glinit>", "()V");

		return glinit = ctx.classes.createMethodPoet(this, mn);
	}

	public void S4_Code() throws ParseException {
		currentNode = (SignaturePrimer) signature;

		// todo setCurrentNode, createGlobalInit, updateCurrentNode
		for (int i = 0; i < lazyTasks.size(); i++) {
			lazyTasks.get(i).parse();
		}
		lazyTasks.clear();

		if (glinit != null) {

		}
	}
	// endregion

	public String getFilePath() {return path;}

	public final void fireDiagnostic(Kind kind, String code) {ctx.classes.report(this, kind, wr.index, code);}
	public final void fireDiagnostic(Kind kind, String code, Object ... args) {ctx.classes.report(this, kind, wr.index, code, args);}

	public ByteList getBytes() {return Parser.toByteArrayShared(this);}

	public IType readType(@MagicConstant(flags = {TYPE_PRIMITIVE, TYPE_OPTIONAL, TYPE_STAR, TYPE_GENERIC, TYPE_LEVEL2}) int flag) throws ParseException {
		IType type = _type(wr, ctx.tmpList, flag);
		// TODO maybe not full suitable (resolve later)
		if (type instanceof GenericPrimer g) return g.toRealType(currentNode);
		return type;
	}
}