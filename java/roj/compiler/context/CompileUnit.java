package roj.compiler.context;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.tree.*;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.util.ClassUtil;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.asmx.AnnotationSelf;
import roj.collect.*;
import roj.compiler.CompilerConfig;
import roj.compiler.JavaLexer;
import roj.compiler.asm.*;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.diagnostic.Kind;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.compiler.JavaLexer.*;
import static roj.config.word.Word.EOF;
import static roj.config.word.Word.LITERAL;

/**
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public final class CompileUnit extends ConstantData {
	private Object _next;

	static final int NON_FIELD_ACC = Opcodes.ACC_TRANSIENT|Opcodes.ACC_VOLATILE;
	static final int NON_METHOD_ACC = Opcodes.ACC_STRICT|Opcodes.ACC_NATIVE;
	static final int _ACC_DEFAULT = 1 << 17, _ACC_ANNOTATION = 1 << 18, _ACC_RECORD = 1 << 19,
		_ACC_INNERCLASS = 0x0040, _ACC_ANONYMOUS = 0x0080;

	InputStream in;
	private JavaLexer wr;

	// 导入
	private final MyHashMap<String, String> importCls, importStc;
	private final List<String> importPkg, importStcPkg;

	public MyHashMap<String, String> getImportCls() {
		return importCls;
	}

	public MyHashMap<String, String> getImportStc() {
		return importStc;
	}

	public List<String> getImportPkg() {
		return importPkg;
	}

	public List<String> getImportStcPkg() {
		return importStcPkg;
	}

	//
	@Deprecated
	ClassContext ctx;
	@Deprecated
	CompileContext ctx1;
	int diagPos = -1;
	SimpleList<AnnotationPrimer> annotations;

	// S0之后的诊断需要用到的起始位置等
	int classIdx;
	IntList methodIdx = new IntList(), fieldIdx = new IntList();

	// 自己是注解时的信息
	public AnnotationSelf annoInfo;

	private List<String> interfacesPre = Helpers.cast(interfaces);
	private Signature signature;

	// S2前的缓存
	private List<GenericPrimer> genericDeDup;
	private SignaturePrimer currentNode;

	private final SimpleList<IType> toResolve = new SimpleList<>();
	private MyHashSet<IType> toResolve_unc;

	// code block task
	private final List<ParseTask> lazyTasks = new SimpleList<>();
	private final MyHashMap<Attributed, List<AnnotationPrimer>> annoTask = new MyHashMap<>();

	private MyHashSet<String> allNodeNames;

	// Inner Class
	private CompileUnit _parent;
	private List<CompileUnit> _children = Collections.emptyList();

	private String path;
	private CompileContext Cache;

	public CompileUnit(String name, InputStream in, ClassContext ctx) {
		this.in = in;
		this.path = name;
		this.ctx = ctx;
		if (ctx.isSpecEnabled(CompilerConfig.SOURCE_FILE)) putAttr(new AttrString(AttrString.SOURCE,name));

		importCls = new MyHashMap<>();
		importPkg = new SimpleList<>();
		importStc = new MyHashMap<>();
		importStcPkg = new SimpleList<>();
		Cache = CompileContext.get();
	}
	public CompileUnit(String name, ClassContext ctx) {
		this(name, null, ctx);
		this.wr = new JavaLexer();
	}

	// region 文件中的其余类

	private CompileUnit(CompileUnit parent) {
		this.ctx = parent.ctx;
		this.path = parent.path;
		if (ctx.isSpecEnabled(CompilerConfig.SOURCE_FILE))
			putAttr(parent.attrByName(Attribute.SourceFile.name));

		_parent = parent;
		access = 0;

		wr = parent.wr;
		classIdx = wr.index;

		importCls = parent.importCls;
		importPkg = parent.importPkg;
		importStc = parent.importStc;
		importStcPkg = Collections.emptyList();

		CompileContext.depth(1);
		Cache = CompileContext.get();
	}

	private static CompileUnit _newHelper(CompileUnit p) throws ParseException {
		CompileUnit c = new CompileUnit(p);

		int i = p.name.lastIndexOf('/') + 1;
		c.name(i <= 0 ? "" : p.name.substring(0, i));
		c._header();
		c.S1_Struct();

		CompileContext.depth(-1);
		return c;
	}

	public CompileUnit newAnonymousClass(@Nullable MethodNode mn, IType parent, List<Type> paramTypes) throws ParseException {
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

		c.name(IOUtil.getSharedCharBuf().append(name).append("$").append(_children.size() + 1).toString());
		c.access = Opcodes.ACC_SYNTHETIC|Opcodes.ACC_FINAL|Opcodes.ACC_SUPER|_ACC_ANONYMOUS;
		c.parent(parent.owner());

		c.copyTmpFromCache();
		ctx.addCompileUnit(c);
		c.body();

		paramTypes.add(Type.std(Type.VOID));

		String rawDesc = TypeHelper.getMethod(paramTypes);
		CodeWriter code = c.newMethod(Opcodes.ACC_SYNTHETIC, "<init>", rawDesc);
		int size = (1 + TypeHelper.paramSize(rawDesc));
		code.visitSize(size, size);
		// todo write !?

		c.S2_Parse();

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

		if (_children.isEmpty()) _children = new SimpleList<>();
		_children.add(c);

		CompileContext.depth(-1);
		return c;
	}

	private static CompileUnit _newInner(CompileUnit p, int flag) throws ParseException {
		CompileUnit c = new CompileUnit(p);

		c.name(p.name.concat("$"));
		c.access = (char) (flag|_ACC_INNERCLASS);

		InnerClasses.InnerClass desc = InnerClasses.InnerClass.innerClass(c.name, 0);
		_innerClass(p).add(desc);
		_innerClass(c).add(desc);

		if (p._children.isEmpty()) p._children = new SimpleList<>();
		p._children.add(c);

		c.S1_Struct();

		CompileContext.depth(-1);
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

	public JavaLexer getLexer() {
		return wr;
	}

	public CharSequence getText() {
		return wr.getText();
	}

	public void ctx(ClassContext ctx) {
		this.ctx = ctx;
	}
	public ClassContext ctx() {
		return ctx;
	}

	// region 阶段0 读取文件和import

	public boolean S0_Init() throws IOException, ParseException {
		importCls.clear();
		importPkg.clear();
		importStc.clear();

		JavaLexer wr = this.wr = (JavaLexer) new JavaLexer().init(IOUtil.readUTF(in));
		CharList tmp = IOUtil.getSharedCharBuf();

		wr.env = CAT_HEADER|CAT_MODIFIER;

		// 默认包""
		String pkg = "";
		Word w = wr.next();
		if (w.type() == PACKAGE) {
			_klass(wr, tmp, 0);
			expect(wr, semicolon);
			pkg = tmp.append('/').toString();

			w = wr.next();
		}

		while (w.type() == IMPORT) {
			int impField = 0;
			if (wr.next().type() == STATIC) {
				impField = 1;
			} else {
				wr.retractWord();
			}
			_klass(wr, tmp, TYPE_STAR);

			int i = tmp.lastIndexOf("/");
			if (i < 0) fireDiagnostic(Kind.SEVERE_WARNING, "unpackage_import");

			if (tmp.charAt(tmp.length() - 1) == '*') {
				tmp.setLength(tmp.length() - 1);

				List<String> list = impField == 0 ? importPkg : importStcPkg;
				// noinspection all
				if (!list.contains(tmp)) list.add(tmp.toString());
			} else {
				String key, val = tmp.toString();

				w = wr.next();
				if (w.type() == AS) {
					key = wr.next().val();
				} else {
					wr.retractWord();
					key = val.substring(i + 1);
				}

				MyHashMap<String, String> map = impField == 0 ? importCls : importStc;
				if ((val = map.put(key, val)) != null) {
					fireDiagnostic(Kind.ERROR, "duplicate_compile_unit:"+val+":"+tmp);
				}
			}

			expect(wr, semicolon);
			w = wr.next();
		}

		wr.retractWord(); // TODO This method changed
		wr.env = CAT_MODIFIER|CAT_TYPE_TYPE;

		name(pkg);
		_header();
		return wr.hasNext();
	}

	private void _header() throws ParseException {
		annotations = Cache.annotationTmp;
		annotations.clear();

		// 修饰符和注解
		access = (char) _modifier(wr, Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL|Opcodes.ACC_ABSTRACT|_ACC_ANNOTATION);
		if (!annotations.isEmpty()) addAnnotation(this, new SimpleList<>(annotations));
	}

	// endregion
	// region 阶段1 类文件结构

	public CharList copyTmpFromCache() {
		CompileContext cache = Cache;

		genericDeDup = cache.genericDeDup;
		genericDeDup.clear();

		toResolve_unc = cache.toResolve_unc;
		toResolve_unc.clear();

		annotations = cache.annotationTmp;
		annotations.clear();

		CharList tmp = cache.tmpList; tmp.clear();
		return tmp;
	}

	public void S1_Struct() throws ParseException {
		copyTmpFromCache();
		JavaLexer wr = this.wr;
		wr.env = CAT_TYPE|CAT_MODIFIER|CAT_TYPE_TYPE|CAT_METHOD;

		int acc = _classModifier(wr, access);
		access = (char)acc;

		if ((access &_ACC_INNERCLASS) != 0) {
			List<InnerClasses.InnerClass> c = _innerClass(this);
			if ((acc & (Opcodes.ACC_INTERFACE|Opcodes.ACC_ENUM)) != 0) acc |= Opcodes.ACC_STATIC;
			c.get(c.size()-1).flags = (char) acc;
		}

		// 名称
		Word w = wr.next();
		if (w.type() != LITERAL) {
			throw wr.err("unexpected:" + w.val() + ":CLASS_NAME");
		}
		classIdx = w.pos();
		name(name.concat(w.val()));

		if ((access & Opcodes.ACC_PUBLIC) != 0) {
			//if (!path.substring(0, path.length()-6).endsWith(w.val()))
			//    fireDiagnostic(Diagnostic.Kind.ERROR, "public_class_name_wrong");
		}

		ctx.addCompileUnit(this);

		if (_parent != null) {
			importCls.put(name.substring(name.lastIndexOf('$')+1), name);
		}

		// 泛型参数和范围
		w = wr.next();
		if (w.type() == lss) { // <
			_genericDeclare(wr, _signature());
			w = wr.next();
		}

		// 继承和实现

		String parent = (acc & Opcodes.ACC_ENUM) != 0 ? "java/lang/Enum" : "java/lang/Object";
		if ((acc & Opcodes.ACC_ANNOTATION) != 0) interfacesPre.add("java/lang/annotation/Annotation");

		checkExtends:
		if (w.type() == EXTENDS) {
			if ((acc & (Opcodes.ACC_ENUM|Opcodes.ACC_ANNOTATION)) != 0) fireDiagnostic(Kind.ERROR, "no_inherit_allowed");
			if ((acc & Opcodes.ACC_INTERFACE) != 0) break checkExtends;

			IType type = _genericUse(null, 0);
			if (type.genericType() > 0) _signature()._add(type);
			parent = type.owner();

			w = wr.next();
		}
		parent(parent);

		if (w.type() == ((acc & Opcodes.ACC_INTERFACE) != 0 ? EXTENDS : IMPLEMENTS)) {
			List<String> itfs = this.interfacesPre;
			out:
			while (true) {
				IType type = _genericUse(null, 0);
				if (type.genericType() > 0) _signature()._impl(type);
				itfs.add(type.owner());

				w = wr.next();
				switch (w.type()) {
					case comma:
						break;
					case lBrace:
						wr.retractWord();
						break out;
					default:
						throw wr.err("unexpected:" + w.val() + ":, or {");
				}
			}
		} else {
			wr.retractWord();
		}

		signature = _signatureCommit(_parent == null ? null : (SignaturePrimer) _parent.signature, Signature.CLASS, null);
		if (signature != null) putAttr(signature);

		body();

		if (_parent != null) return;

		// helper class
		while (wr.hasNext()) {
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
		CharList tmp = Cache.tmpList;

		expect(wr, lBrace);

		MyHashSet<String> names = Cache.names; names.clear();

		// ## 2.9 枚举的处理
		if ((access & Opcodes.ACC_ENUM) != 0) {
			Type selfType = new Type(name);

			w = wr.next();
			while (w.type() == LITERAL) {
				String name = w.val();
				if (!names.add(name)) throw wr.err("duplicate_enum_name");

				fieldIdx.add(w.pos());

				FieldNode f = new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_FINAL, name, selfType);
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
				default: wr.retractWord(); break;
				case rBrace: break mfsdcLoop;
				case EOF: throw wr.err("eof_early");
			}

			// ## 3.1 acc
			acc = Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_STRICT|Opcodes.ACC_FINAL|Opcodes.ACC_ABSTRACT|_ACC_ANNOTATION;
			switch (access & (Opcodes.ACC_INTERFACE|Opcodes.ACC_ANNOTATION)) {
				case Opcodes.ACC_INTERFACE:
					acc |= _ACC_DEFAULT;
					break;
				case Opcodes.ACC_INTERFACE|Opcodes.ACC_ANNOTATION:
					break;
				case 0:
					acc |= Opcodes.ACC_NATIVE|Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED|Opcodes.ACC_TRANSIENT|Opcodes.ACC_VOLATILE|Opcodes.ACC_SYNCHRONIZED;
					break;
			}

			acc = _modifier(wr, acc);

			w = wr.next();
			if (w.type() == lss) {
				_genericDeclare(wr, _signature());
				w = wr.next();
			}

			switch (w.type()) {
				case lBrace: // static initializator
					lazyTasks.add((acc & Opcodes.ACC_STATIC) == 0 ? ParseTask.InstanceInitBlock(this) : ParseTask.StaticInitBlock(this));
				continue;
				case CLASS: case INTERFACE: case ENUM:
				case AT_INTERFACE: case RECORD:
					wr.retractWord();
					_newInner(this, acc);
				continue;
			}

			String name;
			Type type;

			mof: {
				constructor:
				if (w.val().equals(getSimpleName())) {
					int pos = wr.index;
					Word lsb = wr.readWord();
					if (lsb.type() != lParen) {
						wr.index = pos;
						break constructor;
					}
					// constructor
					name = "<init>";
					type = Type.std(Type.VOID);

					if ((access & Opcodes.ACC_INTERFACE) != 0)
						fireDiagnostic(Kind.ERROR, "interface_constructor");
					break mof;
				}
				wr.retractWord();

				// ## 5.1.3 类型
				IType type1 = _type(wr, tmp, TYPE_PRIMITIVE|TYPE_GENERIC);
				type = type1.rawType();
				if (type1.genericType() != 0)
					_signature().returns = (Generic) type1;

				// method or field
				name = wr.except(LITERAL, "name").val();

				w = wr.next();
			}

			if (w.type() == lParen) { // method
				methodIdx.add(w.pos());
				if ((acc & NON_FIELD_ACC) != 0) fireDiagnostic(Kind.ERROR, "illegal_modifier_compound");

				MethodNode method = new MethodNode(acc, this.name, name, "()V");
				method.setReturnType(type);
				if (!annotations.isEmpty()) addAnnotation(method, new SimpleList<>(annotations));

				List<String> paramNames;

				w = wr.next();
				if (w.type() != rParen) {
					wr.retractWord();

					paramNames = new SimpleList<>();

					boolean lsVarargs = false;
					MethodParameters parAccName = null;
					ParameterAnnotations parAnno = null;

					label:
					while (wr.hasNext()) {
						if (lsVarargs) {
							fireDiagnostic(Kind.ERROR, "vararg_not_last");
						}

						int acc1 = _modifier(wr, Opcodes.ACC_FINAL|_ACC_ANNOTATION);

						IType parType = _type(wr, tmp, TYPE_PRIMITIVE|TYPE_GENERIC);
						if (parType.genericType() != 0)
							_signature()._add(paramNames.size(), (Generic) parType);

						w = wr.next();
						if (w.type() == varargs) {
							lsVarargs = true;
							w = wr.next();
						}

						List<Type> p = method.parameters();
						p.add(parType.rawType());
						if (parType.rawType().type == VOID) fireDiagnostic(Kind.ERROR, "param_is_void");
						if (p.size() > 255) fireDiagnostic(Kind.ERROR, "what_ruizhi_have_256_parameters");

						MethodParamAnno paramNode = new MethodParamAnno(method, w.pos(), w.val(), paramNames.size());
						if (!annotations.isEmpty()) addAnnotation(paramNode, new SimpleList<>(annotations));

						if (w.type() == LITERAL) {
							if (paramNames.contains(w.val()))
								fireDiagnostic(Kind.ERROR, "dup_param_name");
							paramNames.add(w.val());
						} else {
							throw wr.err("unexpected:"+w.val());
						}

						if (acc1 != 0 || ctx.isSpecEnabled(CompilerConfig.KEEP_PARAMETER_NAME)) {
							if (parAccName == null) {
								parAccName = new MethodParameters();
								method.putAttr(parAccName);
							}
							parAccName.flags.add(new MethodParam(w.val(), (char) acc1));
						}

						w = wr.next();
						if (w.type() == equ) {
							// 参数默认值
							if (!ctx.isSpecEnabled(CompilerConfig.DEFAULT_VALUE))
								fireDiagnostic(Kind.ERROR, "disabled_spec:default_value");
							lazyTasks.add(ParseTask.MethodDefault(this, paramNode));
						}
						switch (w.type()) {
							case rParen:
								if (lsVarargs) method.modifier |= Opcodes.ACC_TRANSIENT;
								break label;
							case comma: continue;
							default: throw wr.err("unexpected:" + w.val());
						}
					}
				} else {
					paramNames = Collections.emptyList();
				}

				SignaturePrimer sign = _signatureCommit((SignaturePrimer) signature, Signature.METHOD, method);
				if (sign != null) method.putAttr(sign);

				if (!names.add(method.name() + method.rawDesc())) fireDiagnostic(Kind.ERROR, "duplicate_method:" + method.name());
				method.modifier = (char) acc;
				methods.add(method);

				w = wr.next();
				if (w.type() == THROWS) {
					if ((access & Opcodes.ACC_ANNOTATION) != 0) {
						fireDiagnostic(Kind.ERROR, "@interface should not have throws");
					}

					AttrClassList excList = new AttrClassList(AttrClassList.EXCEPTIONS);
					method.putAttr(excList);

					while (wr.hasNext()) {
						IType type1 = _type(wr, tmp, TYPE_GENERIC);
						excList.value.add(type1.owner());
						if (type1.genericType() != 0)
							_signature().Throws.add(type1);

						w = wr.next();
						if (w.type() != comma) break;
					}
				}

				// 不能包含方法体:
				//   被abstract或native修饰
				//   是接口且没有default且不是static
				if ((acc & (Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE)) != 0 ||
					(0 != (access & Opcodes.ACC_INTERFACE) && (acc & (Opcodes.ACC_STATIC|_ACC_DEFAULT)) == 0)) {
					if ((acc & NATIVE) == 0) method.modifier = (char) (acc|Opcodes.ACC_ABSTRACT);

					if ((access & Opcodes.ACC_ANNOTATION) != 0) {
						// 注解的default
						if (w.type() == DEFAULT) {
							lazyTasks.add(ParseTask.AnnotationDefault(this, method));
							continue;
						} else {
							method.modifier |= Opcodes.ACC_ABSTRACT;
						}
					}

					if (w.type() != semicolon) throw wr.err("不能包含方法体");
				} else {
					if (w.type() != lBrace) throw wr.err("必须包含方法体");

					lazyTasks.add(ParseTask.Method(this, method, paramNames));
				}
			} else { // field
				if ((acc & NON_METHOD_ACC) != 0) fireDiagnostic(Kind.ERROR, "illegal_modifier_compound");
				wr.retractWord();

				Signature s = _signatureCommit((SignaturePrimer) signature, Signature.FIELD, null);

				List<AnnotationPrimer> list = annotations.isEmpty() ? null : new SimpleList<>(annotations);

				sameTypeField:
				do {
					FieldNode field = new FieldNode(acc, name, type);
					if (!names.add(field.name())) {
						fireDiagnostic(Kind.ERROR, "duplicate_field:"+field.name());
					}
					if (list != null) addAnnotation(field, list);
					if (s != null) field.putAttr(s);

					fields.add(field);
					fieldIdx.add(w.pos());

					w = wr.next();
					if (w.type() == assign) {
						// have to execute task now
						lazyTasks.add(ParseTask.Field(this, field));
					}

					switch (w.type()) {
						case semicolon: break sameTypeField;
						case comma: w = wr.next(); name = w.val();
					}
				} while (wr.hasNext());

			}
		}
	}

	public int _modifier(JavaLexer wr, int mask) throws ParseException {
		if ((mask & _ACC_ANNOTATION) != 0) annotations.clear();

		Word w;
		int acc = 0, kind = 0;
		while (true) {
			int f;
			w = wr.next();
			switch (w.type()) {
				case at:
					if ((mask & _ACC_ANNOTATION) == 0)
						fireDiagnostic(Kind.ERROR, "annotation_should_not_be_here");
					_annotations(annotations);
					acc |= _ACC_ANNOTATION;
					continue;
				case PUBLIC:
					f = (1 << 20) | Opcodes.ACC_PUBLIC;
					break;
				case PROTECTED:
					f = (1 << 20) | Opcodes.ACC_PROTECTED;
					break;
				case PRIVATE: // private - abstract
					f = (1 << 20)|(1 << 21) | Opcodes.ACC_PRIVATE;
					break;
				case ABSTRACT: // abstract - final
					f = (1 << 22) | (1 << 21) | Opcodes.ACC_ABSTRACT;
					break;
				case STATIC:
					f = Opcodes.ACC_STATIC;
					break;
				case CONST: // public static final
					if (!ctx.isSpecEnabled(CompilerConfig.CONST))
						throw wr.err("disabled_spec:const");
					f = (1 << 21) | (1 << 20) |
						Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;
					break;
				case STRICTFP:
					f = Opcodes.ACC_STRICT;
					break;
				case VOLATILE:
					f = Opcodes.ACC_VOLATILE;
					break;
				case TRANSIENT:
					f = Opcodes.ACC_TRANSIENT;
					break;
				case NATIVE:
					f = Opcodes.ACC_NATIVE;
					break;
				case SYNCHRONIZED:
					f = Opcodes.ACC_SYNCHRONIZED;
					break;
				case FINAL:
					f = (1 << 22) | Opcodes.ACC_FINAL;
					break;
				case DEFAULT:
					f = _ACC_DEFAULT;
					break;
				default:
					wr.retractWord();
					return acc;
			}

			if ((kind & (f >>> 20)) != 0) {
				fireDiagnostic(Kind.ERROR, "illegal_modifiers:" + w.val());
				continue;
			}
			kind |= f >>> 20;

			if ((f & mask) == 0) {
				fireDiagnostic(Kind.ERROR, "unsupported_modifier:" + w.val());
				continue;
			}

			if ((acc & f) != 0) fireDiagnostic(Kind.SEVERE_WARNING, "duplicate_modifier:" + w.val());
			acc |= f;
		}
	}
	private static int _classModifier(JavaLexer wr, int acc) throws ParseException {
		Word w;
		w = wr.next();
		switch (w.type()) {
			case CLASS: // class
				acc |= Opcodes.ACC_SUPER;
				break;
			case INTERFACE: // interface
				if ((acc & (Opcodes.ACC_FINAL)) != 0) throw wr.err("illegal_modifier:interface:final");
				acc |= Opcodes.ACC_ABSTRACT|Opcodes.ACC_INTERFACE;
				break;
			case ENUM: // enum
				if ((acc & (Opcodes.ACC_ABSTRACT)) != 0) throw wr.err("illegal_modifier:enum:abstract");
				acc |= Opcodes.ACC_ENUM|Opcodes.ACC_FINAL;
				break;
			case AT_INTERFACE: // @interface
				if ((acc & (Opcodes.ACC_FINAL)) != 0) throw wr.err("illegal_modifier:@interface:final");
				acc |= Opcodes.ACC_ANNOTATION|Opcodes.ACC_INTERFACE|Opcodes.ACC_ABSTRACT;
				break;
			case RECORD:
				//javaVersion();
				if (true)
					throw new UnsupportedOperationException("RECORD not implemented");
				break;
			default: throw wr.err("unexpected:" + w.val() + ":TYPE_TYPE");
		}
		return acc;
	}

	public static final int TYPE_PRIMITIVE = 1, TYPE_OPTIONAL = 2, TYPE_STAR = 8, TYPE_GENERIC = 4, TYPE_LEVEL2 = 16;
	/**
	 * 获取类 (a.b.c)
	 * @param flags 0 | {@link #TYPE_STAR} allow * (in import)
	 */
	private static void _klass(JavaLexer wr, CharList sb, int flags) throws ParseException {
		sb.clear();

		Word w = wr.except(LITERAL, "name");
		sb.append(w.val());

		while (true) {
			w = wr.next();
			if (w.type() != dot) {
				if (sb.length() == 0) throw wr.err("empty_type");
				wr.retractWord();
				break;
			}
			sb.append('/');

			w = wr.next();
			if (w.type() == mul) {
				if ((flags&TYPE_STAR) == 0) throw wr.err("unexpected:*");
				sb.append("*");
				break;
			} else if (w.type() == LITERAL) {
				sb.append(w.val());
			} else {
				throw wr.err("unexpected:"+w.val());
			}
		}
	}
	/**
	 * 获取类型
	 * @param flags {@link #TYPE_PRIMITIVE} bit set
	 */
	private IType _type(JavaLexer wr, CharList sb, int flags) throws ParseException {
		IType type;

		Word w = wr.next();
		int std = toType_1.getOrDefaultInt(w.type(), -1);
		if (std >= 0) {
			type = new Type(std, 0);
		} else {
			if (w.type() != LITERAL) {
				if ((flags&TYPE_OPTIONAL) == 0) throw wr.err("empty_type:"+w.val());
				return Helpers.nonnull();
			}

			wr.retractWord();
			_klass(wr, sb, 0);

			if ((flags&TYPE_GENERIC) != 0) {
				CharSequence bound = null;

				w = wr.next();
				wr.retractWord();

				if (w.type() == lss ||
					(currentNode != null && sb != (bound = currentNode.getTypeBoundary(sb))) ||
					(signature != null && sb != (bound = ((SignaturePrimer) signature).getTypeBoundary(sb)))) {

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
				if (++arrLen > 255) throw wr.err("array_dimension_overflow");

				expect(wr, rBracket);
				w = wr.next();
			}

			if (w.type() == varargs) arrLen++;

			if (arrLen > 0) {
				type.setArrayDim(arrLen);
			} else {
				if (type.rawType().owner == null && (flags&TYPE_PRIMITIVE) == 0) throw wr.err("unexpected_primitive:"+w.val());
			}

			wr.retractWord();
			return toResolve_unc.intern(type);
		}

		return type;
	}

	private static final Int2IntMap toType_1 = new Int2IntMap();
	static {
		String s = "ICBZSDJFV";
		for (int i = 0; i < s.length(); i++) {
			toType_1.putInt(JavaLexer.byName(Type.toString((byte) s.charAt(i))), s.charAt(i));
		}
	}

	private SignaturePrimer _signature() {
		if (currentNode == null) currentNode = new SignaturePrimer(0);
		return currentNode;
	}
	private SignaturePrimer _signatureCommit(SignaturePrimer parent, int kind, MethodNode mn) {
		SignaturePrimer s = currentNode;
		if (s == null) return null;
		s.parent = parent;
		s.type = (byte) kind;
		currentNode = null;
		s.initS0(mn);

		for (int i = genericDeDup.size() - 1; i >= 0; i--) {
			GenericPrimer g = genericDeDup.get(i);

			if (g.resolveS1(s)) toResolve_unc.remove(g);
		}
		genericDeDup.clear();

		toResolve.addAll(toResolve_unc);
		toResolve_unc.clear();

		return s;
	}

	private void _genericDeclare(JavaLexer wr, SignaturePrimer s) throws ParseException {
		generic:
		while (wr.hasNext()) {
			Word w = wr.next();
			if (w.type() == LITERAL) {
				s.addTypeParam(w.val());

				short id = EXTENDS;
				while (wr.hasNext()) {
					w = wr.next();
					if (w.type() == id) {
						s.addBound(_genericUse(null, 0));
					} else {
						break;
					}

					id = and;
				}
			}
			switch (w.type()) {
				case gtr: break generic;
				case comma: break;
				default: throw wr.err("unexpected", w);
			}
		}

		// <T extends YYY<T, V> & ZZZ, V extends T & XXX>
	}

	// todo 支持放int，但是处理还没有ok
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
					if ((flag&TYPE_OPTIONAL) != 0) return null;
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
						if ((flag&TYPE_OPTIONAL) != 0) return null;
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
					// FIXME incorrect
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
					if ((flag&GENERIC_SUBCLASS) != 0 || w.type() != gtr) {
						fireDiagnostic(Kind.ERROR, "type.error.illegalAnyType");
					}
				}

				switch (w.type()) {
					case comma: continue;
					case gtr: break seg;
					default:
						if ((flag&TYPE_OPTIONAL) != 0) return null;
						wr.unexpected(w.val(), "type.except.afterLss");
				}
			}
			wr.env |= CAT_METHOD;

			w = wr.next();
		}

		if (w.type() == dot) {
			IType sub1 = _genericUse(wr.except(LITERAL).val(), flag&TYPE_OPTIONAL|GENERIC_SUBCLASS);
			if (sub1 == null) return null;
			// 无法从参数化的类型中选择静态类
			if (sub1.genericType() != IType.GENERIC_TYPE) fireDiagnostic(Kind.ERROR, "type.error.partialGenericSub", sub1.owner());
			else g.sub = ((GenericPrimer) sub1).toGenericSub();

			w = wr.next();
		}

		if ((flag & GENERIC_SUBCLASS) == 0) {
			int al = 0;
			while (w.type() == lBracket) {
				al++;
				expect(wr, rBracket);
				w = wr.next();
			}
			g.setArrayDim(al);
		}
		wr.retractWord();

		if (result != null) {
			if (g.isRealGeneric()) fireDiagnostic(Kind.ERROR, "type.error.primitiveGeneric");
			return result;
		}

		result = g.isRealGeneric() ? g : g.rawType();

		IType entry = toResolve_unc.intern(result);
		if (entry != result) return entry;
		else if (g.isRealGeneric()) genericDeDup.add(g);

		return result;
	}

	public List<AnnotationPrimer> _annotations(List<AnnotationPrimer> list) throws ParseException {
		JavaLexer wr = this.wr;
		CharList tmp = IOUtil.getSharedCharBuf();

		readMore:
		while (true) {
			_klass(wr, tmp, 0);

			AnnotationPrimer a = new AnnotationPrimer(tmp.toString(), wr.index);
			// 允许你忽略注解
			if (list != Collections.EMPTY_LIST) list.add(a);

			Word w = wr.next();
			if (w.type() != lParen) {
				if (w.type() == at) {
					continue;
				} else {
					wr.retractWord();
					return list;
				}
			}

			while (wr.hasNext()) {
				// TODO previous is lastWordPos
				int index = wr.prevIndex;
				String val = wr.next().val();

				w = wr.next();
				if (w.type() != assign) {
					wr.index = index;
					// values
					a.assertValueOnly = true;
					a.newEntry("value", ParseTask.Annotation(this, a, "value"));
				} else {
					if (a.assertValueOnly) throw wr.err("assert value only");
					a.newEntry(val, ParseTask.Annotation(this, a, val));
				}

				switch (w.type()) {
					case rParen:
						w = wr.next();
						if (w.type() == at) continue readMore;
						wr.retractWord();
						return list;
					case comma: break;
					default: throw wr.err("unexpected:" + w.val());
				}
			}
		}
	}

	private void addAnnotation(Attributed node, List<AnnotationPrimer> anno) {
		annoTask.put(node, anno);
	}

	static final int _E_LSB = 1, _E_SSB = 2, _E_SEM = 4, _E_COM = 8;
	static final int BRACKET_EXPRESSION = _E_SEM|_E_COM,
		BRACKET_METHOD = _E_LSB,
		BRACKET_ANNOTATION = _E_SEM|_E_COM|_E_SSB,
		BRACKET_ENUM = _E_LSB|_E_SEM|_E_COM|_E_SSB;

	@SuppressWarnings("fallthrough")
	private static int skipCode(JavaLexer wr, int type) throws ParseException {
		int L = 0, M = 0, S = 0, G = 0;
		cyl:
		while (wr.hasNext()) {
			Word w = wr.next();
			switch (w.type()) {
				case lss: G++; break;
				case lBrace: L++; break;
				case lBracket: M++; break;
				case lParen: S++; break;
				case rBrace: L--;
					if (L < 0) {
						if ((type&_E_LSB) != 0) break cyl;
						throw wr.err("invalid_bracket:" + w.val());
					}
				break;
				case rBracket: M--;
					if (M < 0) throw wr.err("invalid_bracket:" + w.val());
				break;
				case rParen: S--;
					if (S < 0) {
						if ((type&_E_SSB) != 0) break cyl;
						throw wr.err("invalid_bracket:" + w.val());
					}
				break;
				case gtr: G--;
					if (G < 0)throw wr.err("invalid_bracket:" + w.val());
				break;
				case semicolon: if ((type & _E_SEM) == 0) continue;
				case comma: if (w.type() == comma && (type & _E_COM) == 0) continue;
				case EOF:
					if ((L|M|S|G) == 0) break cyl;
					if (w.type() == EOF) throw wr.err("unclosed_bracket");
			}
		}
		return wr.index;
	}

	// endregion
	// region 阶段2 验证和解析类文件
	public List<IType> getGenericEnv(CharSequence sb) {
		CharSequence bound;
		if ((currentNode != null && sb != (bound = currentNode.getTypeBoundary(sb))) ||
			(signature != null && sb != (bound = ((SignaturePrimer) signature).getTypeBoundary(sb)))) {
			return Collections.singletonList(new Type(bound.toString())); // TODO
		}
		return null;
	}

	public void S2_Parse() throws ParseException {
		// region 继承可行性

		diagPos = classIdx;

		IClass pInfo = resolve(parent);
        if (pInfo == null) {
			fireDiagnostic(Kind.ERROR, "unable_resolve:PARENT:" + parent);
			return;
		} else {
            int acc = pInfo.modifier();
            if (0 == (acc & Opcodes.ACC_SUPER)) {
                if (0 != (acc & Opcodes.ACC_ANNOTATION)) {
                    fireDiagnostic(Kind.NOTE, "inherit:annotation");
                } else if (0 != (acc & Opcodes.ACC_ENUM)) {
                    fireDiagnostic(Kind.ERROR, "inherit:enum");
                } else if (0 != (acc & Opcodes.ACC_FINAL)) {
                    fireDiagnostic(Kind.ERROR, "inherit:final");
                } else if (0 != (acc & Opcodes.ACC_INTERFACE)) {
                    fireDiagnostic(Kind.ERROR, "inherit:interface");
                }
            }
            if (0 == (acc & Opcodes.ACC_PUBLIC)) {
                if (!ClassUtil.arePackagesSame(name, parent)) {
                    fireDiagnostic(Kind.ERROR, "inherit:package-private");
                }
            }
        }

		// 禁止泛型异常
		if (signature != null && ctx1.instanceOf("java/lang/Throwable", parent)) {
			fireDiagnostic(Kind.ERROR, "generic_exception");
		}

		List<String> itfs = this.interfacesPre;
		interfacesPre = null;

		for (int i = 0; i < itfs.size(); i++) {
			IClass info = resolve(itfs.get(i));
            if (info == null) {
				fireDiagnostic(Kind.ERROR, "unable_resolve:INTERFACE:" + itfs.get(i));
			} else {
                int acc = info.modifier();
                if (0 == (acc & Opcodes.ACC_INTERFACE)) {
                    fireDiagnostic(Kind.ERROR, "inherit:non_interface");
                }
                if (0 == (acc & Opcodes.ACC_PUBLIC)) {
                    if (!ClassUtil.arePackagesSame(name, info.name())) {
                        fireDiagnostic(Kind.ERROR, "inherit:package-private");
                    }
                }
				interfaces.set(i, cp.getClazz(info.name()));
            }
		}

		allNodeNames = new MyHashSet<>(methods.size()+fields.size());

		List<FieldNode> fields = Helpers.cast(this.fields);
		for (int i = 0; i < fields.size(); i++) {
			FieldNode f = fields.get(i);
			if ((access & Opcodes.ACC_INTERFACE) != 0) {
				int acc = f.modifier;
				if ((acc & (Opcodes.ACC_PRIVATE|Opcodes.ACC_PROTECTED)) != 0) {
					if (ctx.isSpecEnabled(CompilerConfig.INTERFACE_INACCESSIBLE_FIELD)) {
						if ((acc & Opcodes.ACC_PRIVATE) != 0)
							fireDiagnostic(Kind.WARNING, "inaccessible_interface_field");
					} else {
						fireDiagnostic(Kind.ERROR, "modifier_not_allowed:"+acc);
					}
				}
				f.modifier = (char) (Opcodes.ACC_STATIC|acc);
			}

			allNodeNames.add(f.name());
		}

		boolean autoInit = (access & Opcodes.ACC_INTERFACE) == 0;
		List<MethodNode> methods = this.methods;
		for (int i = 0; i < methods.size(); i++) {
			diagPos = methodIdx.get(i);

			MethodNode m = methods.get(i);
			if (m.name().equals("<init>")) {
				autoInit = false;
				if ((access & Opcodes.ACC_ENUM) != 0) {
					if ((m.modifier & (Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED)) != 0) {
						fireDiagnostic(Kind.ERROR, "enum_public_protected_constructor");
					} else if ((m.modifier & Opcodes.ACC_PRIVATE) == 0) {
						m.modifier |= Opcodes.ACC_PRIVATE;
					}
					// not necessary to check
					continue;
				}
			}
			allNodeNames.add(m.name() +m.rawDesc());

			// todo: check interface method flags
			if ((access & Opcodes.ACC_INTERFACE) != 0 && (m.modifier & (Opcodes.ACC_STATIC|Opcodes.ACC_FINAL)) == Opcodes.ACC_FINAL) {
				fireDiagnostic(Kind.ERROR, "final_interface_method");
			}

			AttrClassList list = (AttrClassList) m.attrByName("Exceptions");
			if (list != null) {
				List<String> classes = list.value;
				for (int j = 0; j < classes.size(); j++) {
					IClass info = resolve(classes.get(j));
					if (info == null) {
						fireDiagnostic(Kind.ERROR, "unable_resolve:EXCEPTION:"+classes.get(i));
					} else {
						int acc = info.modifier();
						if (0 == (acc & Opcodes.ACC_PUBLIC)) {
							if (!ClassUtil.arePackagesSame(name, info.name())) {
								fireDiagnostic(Kind.ERROR, "inherit:package-private");
							}
						}
						if (!ctx1.instanceOf("java/lang/Throwable", info.name())) {
							fireDiagnostic(Kind.ERROR, "excepting_exception:"+classes.get(i));
						}
						classes.set(j, info.name());
					}
				}
			}
		}

		if (autoInit) {
			List<MethodNode> m1 = Helpers.cast(pInfo.methods());
			int parentNoPar = 0;
			for (int i = 0; i < m1.size(); i++) {
				MethodNode m = m1.get(i);
				if (m.name().equals("<init>")) {
					if (m.rawDesc().equals("()V")) {
						if (_accessible(pInfo, m.modifier())) {
							parentNoPar = 1;
						} else {
							fireDiagnostic(Kind.ERROR, "constructor_inaccessible");
							break;
						}
					} else if (parentNoPar == 0) {
						parentNoPar = -1;
					}
				}
			}

			if (parentNoPar < 0) {
				fireDiagnostic(Kind.ERROR, "not_empty_constructor");
			}

			CodeWriter cw = newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_SYNTHETIC, "<init>", "()V");
			cw.visitSize(1,1);
			cw.one(Opcodes.ALOAD_0);
			cw.invoke(Opcodes.INVOKESPECIAL, this.parent, "<init>", "()V");
			cw.one(Opcodes.RETURN);
			cw.finish();
		}

		// endregion
		// region Type扩展
		diagPos = -2;

		for (IType type : toResolve_unc) {
			if (type.owner() != null) {
				toResolve.add(type);
			}
		}
		toResolve_unc.clear();

		for (int i = 0; i < toResolve.size(); i++) {
			_resolve(toResolve.get(i), "");
		}
		toResolve.clear();

		if (signature != null) ((SignaturePrimer) signature).initS1(this);
		for (int i = 0; i < methods.size(); i++) {
			SignaturePrimer s = (SignaturePrimer) methods.get(i).attrByName("Signature");
			if (s != null) s.initS1(this);
		}

		// endregion
		// region 自己是注解
		if ((access & Opcodes.ACC_ANNOTATION) != 0) {
			List<AnnotationPrimer> list = annoTask.get(this);
			if (list != null) annoInfo = ClassContext.getAnnotationInfo(list);
			if (annoInfo == null) fireDiagnostic(Kind.ERROR, "annotation_error:class");
		}
		// endregion
		// region 注解预处理
		for (Map.Entry<Attributed, List<AnnotationPrimer>> entry : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			List<AnnotationPrimer> list = entry.getValue();
			for (int i = 0; i < list.size(); i++) {
				AnnotationPrimer a = list.get(i);

				IClass type1 = resolve(a.type);
				if (type1 == null) {
					fireDiagnostic(Kind.ERROR, "unable_resolve:ANNOTATION:" + a.type);
				}
				a.clazzInst = type1;

				AnnotationSelf ad = ctx.getAnnotationDescriptor(type1);
				if (!ctx.applicableToNode(ad, entry.getKey())) {
					fireDiagnostic(Kind.ERROR, "not_applicable_to:" + entry.getKey().getClass().getSimpleName());
				}

				switch (ad.kind()) {
					case AnnotationSelf.SOURCE:
						break;// discard
					case AnnotationSelf.CLASS:
						if (inv == null) {
							inv = new Annotations(false);
							entry.getKey().putAttr(inv);
						}
						inv.annotations.add(a);
						break;
					case AnnotationSelf.RUNTIME:
						if (vis == null) {
							vis = new Annotations(true);
							entry.getKey().putAttr(vis);
						}
						vis.annotations.add(a);
						break;
				}
			}
		}
		// endregion
	}

	public void _resolve(IType t, String kind) {
		IClass type1 = resolve(t.owner());
		if (type1 == null) {
			fireDiagnostic(Kind.ERROR, "unable_resolve:"+kind+":" + t.owner());
		} else if (!_accessible(type1, Opcodes.ACC_PUBLIC)) {
			fireDiagnostic(Kind.ERROR, "unable_access:"+kind+":" + t.owner());
		} else t.owner(type1.name());
	}

	@SuppressWarnings("fallthrough")
	private boolean _accessible(IClass target, char acc) {
		if (this == target) return true;

		boolean pkg = false;
		if ((target.modifier() & Opcodes.ACC_PUBLIC) == 0) {
			if (!ClassUtil.arePackagesSame(name, target.name())) return false;
			pkg = true;
		}
		switch (acc & (Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE)) {
			case Opcodes.ACC_PUBLIC: return true;
			case Opcodes.ACC_PROTECTED: if (ctx1.instanceOf(name, target.name())) return true;
			case 0: return pkg || ClassUtil.arePackagesSame(name, target.name());
			case Opcodes.ACC_PRIVATE: return false;
		}
		return false;
	}

	public IClass resolve(String name) {
		// search full-name / short-name
		IClass c = ctx.getClassInfo(name = importCls.getOrDefault(name, name));
		if (c != null) return c;

		int lastSlash = name.lastIndexOf('/');
		if (lastSlash >= 0) {
			if (name.lastIndexOf('$') > lastSlash) return null;

			CharList tmp = IOUtil.getSharedCharBuf();
			tmp.append(name, 0, lastSlash).append('$').append(name, lastSlash + 1, name.length());
			return resolve(tmp.toString());
		}

		MyHashMap<String, IClass> importCache = Cache.importCache;

		c = importCache.get(name);
		if (c != null) return c;

		CharList tmp = IOUtil.getSharedCharBuf();

		// search this package
		c = ctx.getClassInfo(tmp.append(this.name, 0, this.name.lastIndexOf('/') + 1).append(name));
		if (c != null) {
			importCache.put(name, c);
			return c;
		}

		// search other package
		for (int i = 0; i < importPkg.size(); i++) {
			tmp.clear();

			String pkg = importPkg.get(i); // java/util/
			c = ctx.getClassInfo(tmp.append(pkg).append(name));
			if (c != null) {
				importCache.put(name, c);
				return c;
			}
		}

		tmp.clear();

		c = ctx.getClassInfo(tmp.append("java/lang/").append(name));
		if (c != null) {
			importCache.put(name, c);
			return c;
		}
		return null;
	}

	// endregion
	// region 阶段3 编译代码块

	public CodeWriter getClinit() {
		int v = getMethod("<clinit>");
		if (v >= 0) return ((AttrCodeWriter)methods.get(v).attrByName("Code")).cw;

		return newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC, "<clinit>", "()V");
	}

	public void S3_Code() throws ParseException {
		// region 注解
		MyHashSet<String> missed = Cache.annotationMissed;

		for (Map.Entry<Attributed, List<AnnotationPrimer>> entry : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			List<AnnotationPrimer> list = entry.getValue();
			for (int i = 0; i < list.size(); i++) {
				missed.clear();

				AnnotationPrimer a = list.get(i);
				diagPos = a.idx;

				List<? extends RawNode> methods = a.clazzInst.methods();
				for (int j = 0; j < methods.size(); j++) {
					MethodNode m = (MethodNode) methods.get(j);
					if ((m.modifier() & Opcodes.ACC_STATIC) != 0) continue;
					if (m.attrByName(Attribute.AnnotationDefault.name) == null) {
						if (!a.values.containsKey(m.name())) {
							missed.add(m.name());
							continue;
						}
					}

					ParseTask task = Helpers.cast(a.values.remove(m.name()));
					if (task != null) task.parse();
				}

				ctx.invokePartialAnnotationProcessor(this, a, missed);

				if (!a.values.isEmpty()) {
					fireDiagnostic(Kind.ERROR, "annotation_value_left:" + a.values.keySet());
				}

				if (!missed.isEmpty()) {
					fireDiagnostic(Kind.ERROR, "annotation_value_miss:" + missed);
				}
			}
			ctx.invokeAnnotationProcessor(this, entry.getKey(), list);
		}
		annoTask.clear();
		// endregion
		// region 表达式解析

		currentNode = (SignaturePrimer) signature;

		// todo setCurrentNode, createGlobalInit
		for (int i = 0; i < lazyTasks.size(); i++) {
			lazyTasks.get(i).parse();
		}
		lazyTasks.clear();
		// endregion
	}

	// endregion

	public String getFilePath() {
		return path;
	}

	public String getContext() {
		return wr == null ? "~IO 错误~" : wr.getText().toString();
	}

	public final void fireDiagnostic(Kind kind, String code) {
		if (diagPos == -2) {
			ctx.report(this, kind, -1, code);
			return;
		}
		if (diagPos < 0) {
			ctx.report(this, kind, wr.prevIndex, code);
		} else {
			ctx.report(this, kind, diagPos, code);
		}
	}
	public final void fireDiagnostic(Kind kind, String code, Object ... args) {
		fireDiagnostic(kind, code+":"+TextUtil.join(Arrays.asList(args), ":"));
	}

	public ByteList getBytes() {
		return Parser.toByteArrayShared(this);
	}

	private static void expect(JavaLexer wr, short k) throws ParseException {
		Word w = wr.next();
		if (w.type() != k) {
			throw wr.err("unexpected:" + w.val() + ':' + byId(k));
		}
	}

	public IType readType(@MagicConstant(flags = {TYPE_PRIMITIVE, TYPE_OPTIONAL, TYPE_STAR, TYPE_GENERIC, TYPE_LEVEL2}) int flag) throws ParseException {
		IType type = _type(wr, Cache.tmpList, flag);
		if (type instanceof GenericPrimer) {
			GenericPrimer g = (GenericPrimer) type;
			if (!g.isRealGeneric()) return g.rawType();

			if (!g.resolveS1(currentNode)) {
				System.out.println("!g.resolveS1(currentNode)");
				//genericDeDup.add(g);
				//g.resolveS2(this, "EXPRESSION");
			}
		}
		return type;
	}

	public CharSequence objectPath() throws ParseException {
		_klass(wr, Cache.tmpList, 0);
		return Cache.tmpList;
	}

	public JavaLexer lex() {
		return wr;
	}

	public MethodNode parseLambda() {
		return null;
	}
}