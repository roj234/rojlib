package roj.lavac.parser;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.frame.MethodPoet;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttrHelper;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.collect.*;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.lavac.CompilerConfig;
import roj.lavac.asm.AnnotationPrimer;
import roj.lavac.asm.GenericPrimer;
import roj.lavac.asm.MethodParamAnno;
import roj.lavac.asm.SignaturePrimer;
import roj.lavac.block.ParseTask;
import roj.mapper.MapUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nullable;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static roj.config.word.Word.EOF;
import static roj.config.word.Word.LITERAL;
import static roj.lavac.parser.JavaLexer.*;

/**
 * @author solo6975
 * @since 2020/12/31 17:34
 */
public final class CompileUnit extends ConstantData {
	static final int NON_FIELD_ACC = AccessFlag.TRANSIENT | AccessFlag.VOLATILE;
	static final int NON_METHOD_ACC = AccessFlag.STRICTFP | AccessFlag.NATIVE;
	static final int _ACC_DEFAULT = 1 << 17, _ACC_ANNOTATION = 1 << 18, _ACC_RECORD = 1 << 19,
		_ACC_INNERCLASS = 0x0040, _ACC_ANONYMOUS = 0x0080;

	InputStream in;
	private JavaLexer wr;

	// 导入
	private final MyHashMap<String, String> importCls, importStc;
	private final List<String> importPkg, importStcPkg;

	//
	CompileContext ctx;
	int diagPos = -1;
	SimpleList<AnnotationPrimer> annotations;

	// S0之后的诊断需要用到的起始位置等
	int classIdx;
	IntList methodIdx = new IntList(), fieldIdx = new IntList();

	// 自己是注解时的信息
	AnnotationClass annoInfo;
	MyHashMap<String, AnnVal> annoDef;

	private List<String> interfacesPre = Helpers.cast(interfaces);
	private Signature signature;

	// S2前的缓存
	private List<GenericPrimer> genericDeDup;
	private SignaturePrimer currentNode;

	private final SimpleList<IType> toResolve = new SimpleList<>();
	private MyHashSet<IType> toResolve_unc;

	// code block task
	private final MyHashMap<Attributed, ParseTask> exprTask = new MyHashMap<>();
	private final List<ParseTask> initTask = new SimpleList<>(), clInitTask = new SimpleList<>();
	private final MyHashMap<Attributed, List<AnnotationPrimer>> annoTask = new MyHashMap<>();

	private MyHashSet<String> allNodeNames;

	// Inner Class
	private CompileUnit _parent;
	private List<CompileUnit> _children = Collections.emptyList();

	private String path;
	private CompileLocalCache Cache;

	public CompileUnit(String name, InputStream in, CompileContext ctx) {
		this.in = in;
		this.path = name;
		this.ctx = ctx;
		if (ctx.isSpecEnabled(CompilerConfig.SOURCE_FILE)) putAttr(AttrUTF.Source(name));

		importCls = new MyHashMap<>();
		importPkg = new SimpleList<>();
		importStc = new MyHashMap<>();
		importStcPkg = new SimpleList<>();
		Cache = CompileLocalCache.get();
	}

	// region 文件中的其余类

	private CompileUnit(CompileUnit parent) {
		this.ctx = parent.ctx;
		this.path = parent.path;
		if (ctx.isSpecEnabled(CompilerConfig.SOURCE_FILE))
			putAttr(parent.attrByName(AttrUTF.SOURCE));

		_parent = parent;
		access = 0;

		wr = parent.wr;
		classIdx = wr.index;

		importCls = parent.importCls;
		importPkg = parent.importPkg;
		importStc = parent.importStc;
		importStcPkg = Collections.emptyList();

		CompileLocalCache.depth(1);
		Cache = CompileLocalCache.get();
	}

	private static CompileUnit _newHelper(CompileUnit p) throws ParseException {
		CompileUnit c = new CompileUnit(p);

		int i = p.name.lastIndexOf('/') + 1;
		c.name(i <= 0 ? "" : p.name.substring(0, i));
		c._header();
		c.S1_Struct();

		CompileLocalCache.depth(-1);
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
		c.access = AccessFlag.SYNTHETIC | AccessFlag.FINAL | AccessFlag.SUPER | _ACC_ANONYMOUS;
		c.parent(parent.owner());

		c.copyTmpFromCache();
		ctx.addCompileUnit(c);
		c.body();

		paramTypes.add(Type.std(Type.VOID));

		String rawDesc = TypeHelper.getMethod(paramTypes);
		CodeWriter code = c.newMethod(AccessFlag.SYNTHETIC, "<init>", rawDesc);
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

		CompileLocalCache.depth(-1);
		return c;
	}

	private static CompileUnit _newInner(CompileUnit p, int flag) throws ParseException {
		CompileUnit c = new CompileUnit(p);

		c.name(p.name.concat("$"));
		c.access = (char) (flag | _ACC_INNERCLASS);

		InnerClasses.InnerClass desc = InnerClasses.InnerClass.innerClass(c.name, 0);
		_innerClass(p).add(desc);
		_innerClass(c).add(desc);

		if (p._children.isEmpty()) p._children = new SimpleList<>();
		p._children.add(c);

		c.S1_Struct();

		CompileLocalCache.depth(-1);
		return c;
	}

	private static List<InnerClasses.InnerClass> _innerClass(CompileUnit parent) {
		InnerClasses c = (InnerClasses) parent.attrByName(InnerClasses.NAME);
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

	public void ctx(CompileContext ctx) {
		this.ctx = ctx;
	}
	public CompileContext ctx() {
		return ctx;
	}

	public MethodPoet mw;

	// region 阶段0 读取文件和import

	public boolean S0_Init() throws IOException, ParseException {
		importCls.clear();
		importPkg.clear();
		importStc.clear();

		JavaLexer wr = this.wr = (JavaLexer) new JavaLexer().init(IOUtil.readUTF(in));
		CharList tmp = IOUtil.getSharedCharBuf();

		wr.env = CAT_HEADER | CAT_MODIFIER;

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
			if (i < 0) fireDiagnostic(Diagnostic.Kind.MANDATORY_WARNING, "unpackage_import");

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
					fireDiagnostic(Diagnostic.Kind.ERROR, "duplicate_compile_unit:" + val + ":" + tmp);
				}
			}

			expect(wr, semicolon);
			w = wr.next();
		}

		wr.retractWord();
		wr.emptyWordCache();
		wr.env = CAT_MODIFIER | CAT_TYPE_TYPE;

		name(pkg);
		_header();
		return wr.hasNext();
	}

	private void _header() throws ParseException {
		annotations = Cache.annotationTmp;
		annotations.clear();

		// 修饰符和注解
		access = (char) _modifier(wr, AccessFlag.PUBLIC | AccessFlag.FINAL | AccessFlag.ABSTRACT | _ACC_ANNOTATION);
		if (!annotations.isEmpty()) addAnnotation(this, new SimpleList<>(annotations));
	}

	// endregion
	// region 阶段1 类文件结构

	private CharList copyTmpFromCache() {
		CompileLocalCache cache = Cache;

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
		wr.env = CAT_TYPE | CAT_MODIFIER | CAT_TYPE_TYPE | CAT_GENERIC_EXT;

		int acc = _classModifier(wr, access);
		access = (char)acc;

		if ((access &_ACC_INNERCLASS) != 0) {
			List<InnerClasses.InnerClass> c = _innerClass(this);
			if ((acc & (AccessFlag.INTERFACE|AccessFlag.ENUM)) != 0) acc |= AccessFlag.STATIC;
			c.get(c.size()-1).flags = (char) acc;
		}

		// 名称
		Word w = wr.next();
		if (w.type() != LITERAL) {
			throw wr.err("unexpected:" + w.val() + ":CLASS_NAME");
		}
		classIdx = w.pos();
		name(name.concat(w.val()));

		if ((access & AccessFlag.PUBLIC) != 0) {
			//if (!path.substring(0, path.length()-6).endsWith(w.val()))
			//    fireDiagnostic(Diagnostic.Kind.ERROR, "public_class_name_wrong");
		}

		ctx.addCompileUnit(this);

		if (_parent != null) {
			importCls.put(name.substring(name.lastIndexOf('$') + 1), name);
		}

		// 泛型参数和范围
		w = wr.next();
		if (w.type() == lss) { // <
			_genericDeclare(wr, _signature());
			w = wr.next();
		}

		// 继承和实现

		String parent = (acc & AccessFlag.ENUM) != 0 ? "java/lang/Enum" : "java/lang/Object";
		if ((acc & AccessFlag.ANNOTATION) != 0) interfacesPre.add("java/lang/annotation/Annotation");

		checkExtends:
		if (w.type() == EXTENDS) {
			if ((acc & (AccessFlag.ENUM | AccessFlag.ANNOTATION)) != 0) fireDiagnostic(Diagnostic.Kind.ERROR, "no_inherit_allowed");
			if ((acc & AccessFlag.INTERFACE) != 0) break checkExtends;

			IType type = _genericUse(null, 0);
			if (type.genericType() > 0) _signature()._add(type);
			parent = type.owner();

			w = wr.next();
		}
		parent(parent);

		if (w.type() == ((acc & AccessFlag.INTERFACE) != 0 ? EXTENDS : IMPLEMENTS)) {
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
					case left_l_bracket:
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

		expect(wr, left_l_bracket);

		MyHashSet<String> names = Cache.names; names.clear();

		// ## 2.9 枚举的处理
		if ((access & AccessFlag.ENUM) != 0) {
			Type selfType = new Type(name);

			w = wr.next();
			while (w.type() == LITERAL) {
				String name = w.val();
				if (!names.add(name)) throw wr.err("duplicate_enum_name");

				fieldIdx.add(w.pos());

				Field f = new Field(AccessFlag.PUBLIC | AccessFlag.STATIC | AccessFlag.FINAL, name, selfType);
				exprTask.put(f, ParseTask.EnumVal(fields.size(), wr.index, skipCode(wr, BRACKET_ENUM)));
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
				case right_l_bracket: break mfsdcLoop;
				case EOF: throw wr.err("eof_early");
			}

			// ## 3.1 acc
			acc = AccessFlag.PUBLIC|AccessFlag.STATIC|AccessFlag.STRICTFP|AccessFlag.FINAL|AccessFlag.ABSTRACT|_ACC_ANNOTATION;
			switch (access & (AccessFlag.INTERFACE | AccessFlag.ANNOTATION)) {
				case AccessFlag.INTERFACE:
					acc |= _ACC_DEFAULT;
					break;
				case AccessFlag.INTERFACE | AccessFlag.ANNOTATION:
					break;
				case 0:
					acc |= AccessFlag.NATIVE|AccessFlag.PRIVATE|AccessFlag.PROTECTED|AccessFlag.TRANSIENT|AccessFlag.VOLATILE|AccessFlag.SYNCHRONIZED;
					break;
			}

			acc = _modifier(wr, acc);

			w = wr.next();
			if (w.type() == lss) {
				_genericDeclare(wr, _signature());
				w = wr.next();
			}

			switch (w.type()) {
				case left_l_bracket: // static initializator
					if ((acc & AccessFlag.STATIC) == 0) {
						initTask.add(ParseTask.GlobalInit(wr.index, skipCode(wr, BRACKET_METHOD)));
					} else {
						clInitTask.add(ParseTask.StaticInit(wr.index, skipCode(wr, BRACKET_METHOD)));
					}
					continue;
				case CLASS:
				case INTERFACE:
				case ENUM:
				case AT_INTERFACE:
				case RECORD:
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
					if (lsb.type() != left_s_bracket) {
						wr.index = pos;
						break constructor;
					}
					// constructor
					name = "<init>";
					type = Type.std(Type.VOID);

					if ((access & AccessFlag.INTERFACE) != 0)
						fireDiagnostic(Diagnostic.Kind.ERROR, "interface_constructor");
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

			if (w.type() == left_s_bracket) { // method
				methodIdx.add(w.pos());
				if ((acc & NON_FIELD_ACC) != 0) fireDiagnostic(Diagnostic.Kind.ERROR, "illegal_modifier_compound");

				Method method = new Method(acc, this, name, "()V");
				method.setReturnType(type);
				if (!annotations.isEmpty()) addAnnotation(method, new SimpleList<>(annotations));

				List<String> paramNames;

				w = wr.next();
				if (w.type() != right_s_bracket) {
					wr.retractWord();

					paramNames = new SimpleList<>();

					boolean lsVarargs = false;
					MethodParameters parAccName = null;
					ParameterAnnotations parAnno = null;

					label:
					while (wr.hasNext()) {
						if (lsVarargs) {
							fireDiagnostic(Diagnostic.Kind.ERROR, "vararg_not_last");
						}

						int acc1 = _modifier(wr, AccessFlag.FINAL | _ACC_ANNOTATION);

						IType parType = _type(wr, tmp, TYPE_PRIMITIVE | TYPE_GENERIC);
						if (parType.genericType() != 0)
							_signature()._add(paramNames.size(), (Generic) parType);

						w = wr.next();
						if (w.type() == varargs) {
							lsVarargs = true;
							w = wr.next();
						}

						List<Type> p = method.parameters();
						p.add(parType.rawType());
						if (parType.rawType().type == VOID) fireDiagnostic(Diagnostic.Kind.ERROR, "param_is_void");
						if (p.size() > 255) fireDiagnostic(Diagnostic.Kind.ERROR, "what_ruizhi_have_256_parameters");

						MethodParamAnno paramNode = new MethodParamAnno(method, w.pos(), w.val(), paramNames.size());
						if (!annotations.isEmpty()) addAnnotation(paramNode, new SimpleList<>(annotations));

						if (w.type() == LITERAL) {
							if (paramNames.contains(w.val()))
								fireDiagnostic(Diagnostic.Kind.ERROR, "dup_param_name");
							paramNames.add(w.val());
						} else {
							throw wr.err("unexpected:" + w.val());
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
								fireDiagnostic(Diagnostic.Kind.ERROR, "disabled_spec:default_value");
							int start = wr.index;
							int end = skipCode(wr, BRACKET_ANNOTATION);
							exprTask.put(paramNode, ParseTask.AnnotationConst(start, end));
						}
						switch (w.type()) {
							case right_s_bracket:
								if (lsVarargs) method.access |= AccessFlag.TRANSIENT;
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

				if (!names.add(method.name + method.rawDesc())) fireDiagnostic(Diagnostic.Kind.ERROR, "duplicate_method:" + method.name);
				method.access = (char) acc;
				methods.add(method);

				w = wr.next();
				if (w.type() == THROWS) {
					if ((access & AccessFlag.ANNOTATION) != 0) {
						fireDiagnostic(Diagnostic.Kind.ERROR, "@interface should not have throws");
					}

					AttrStringList excList = new AttrStringList(AttrStringList.EXCEPTIONS, 1);
					method.putAttr(excList);

					while (wr.hasNext()) {
						IType type1 = _type(wr, tmp, TYPE_GENERIC);
						excList.classes.add(type1.owner());
						if (type1.genericType() != 0)
							_signature().Throws.add(type1);

						w = wr.next();
						if (w.type() != comma) break;
					}
				}

				// 不能包含方法体:
				//   被abstract或native修饰
				//   是接口且没有default且不是static
				if ((acc & (AccessFlag.ABSTRACT | AccessFlag.NATIVE)) != 0 ||
					(0 != (access & AccessFlag.INTERFACE) && (acc & (AccessFlag.STATIC | _ACC_DEFAULT)) == 0)) {
					if ((acc & NATIVE) == 0) method.access = (char) (acc | AccessFlag.ABSTRACT);

					if ((access & AccessFlag.ANNOTATION) != 0) {
						// 注解的default
						if (w.type() == DEFAULT) {
							exprTask.put(method, ParseTask.AnnotationConst(wr.index, skipCode(wr, BRACKET_EXPRESSION)));
							continue;
						} else {
							method.access |= AccessFlag.ABSTRACT;
						}
					}

					if (w.type() != semicolon) throw wr.err("不能包含方法体");
				} else {
					if (w.type() != left_l_bracket) throw wr.err("必须包含方法体");

					method.setCode(new AttrCode(method));
					exprTask.put(method, ParseTask.Method(paramNames, wr.index, skipCode(wr, BRACKET_METHOD)));
				}
			} else { // field
				if ((acc & NON_METHOD_ACC) != 0) fireDiagnostic(Diagnostic.Kind.ERROR, "illegal_modifier_compound");
				wr.retractWord();

				Signature s = _signatureCommit((SignaturePrimer) signature, Signature.FIELD, null);

				List<AnnotationPrimer> list = annotations.isEmpty() ? null : new SimpleList<>(annotations);

				sameTypeField:
				do {
					Field field = new Field(acc, name, type);
					if (!names.add(field.name)) fireDiagnostic(Diagnostic.Kind.ERROR, "duplicate_field:" + field.name);
					if (list != null) addAnnotation(field, list);
					if (s != null) field.putAttr(s);

					fields.add(field);
					fieldIdx.add(w.pos());

					w = wr.next();
					if (w.type() == assign) {
						// have to execute task now
						exprTask.put(field, ParseTask.FieldVal(wr.index, skipCode(wr, BRACKET_EXPRESSION)));
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
						fireDiagnostic(Diagnostic.Kind.ERROR, "annotation_should_not_be_here");
					_annotations(annotations);
					acc |= _ACC_ANNOTATION;
					continue;
				case PUBLIC:
					f = (1 << 20) | AccessFlag.PUBLIC;
					break;
				case PROTECTED:
					f = (1 << 20) | AccessFlag.PROTECTED;
					break;
				case PRIVATE: // private - abstract
					f = (1 << 20) | (1 << 21) | AccessFlag.PRIVATE;
					break;
				case ABSTRACT: // abstract - final
					f = (1 << 22) | (1 << 21) | AccessFlag.ABSTRACT;
					break;
				case STATIC:
					f = AccessFlag.STATIC;
					break;
				case CONST: // public static final
					if (!ctx.isSpecEnabled(CompilerConfig.CONST))
						throw wr.err("disabled_spec:const");
					f = (1 << 21) | (1 << 20) |
						AccessFlag.PUBLIC | AccessFlag.STATIC | AccessFlag.FINAL;
					break;
				case STRICTFP:
					f = AccessFlag.STRICTFP;
					break;
				case VOLATILE:
					f = AccessFlag.VOLATILE;
					break;
				case TRANSIENT:
					f = AccessFlag.TRANSIENT;
					break;
				case NATIVE:
					f = AccessFlag.NATIVE;
					break;
				case SYNCHRONIZED:
					f = AccessFlag.SYNCHRONIZED;
					break;
				case FINAL:
					f = (1 << 22) | AccessFlag.FINAL;
					break;
				case DEFAULT:
					f = _ACC_DEFAULT;
					break;
				default:
					wr.retractWord();
					return acc;
			}

			if ((kind & (f >>> 20)) != 0) {
				fireDiagnostic(Diagnostic.Kind.ERROR, "illegal_modifiers:" + w.val());
				continue;
			}
			kind |= f >>> 20;

			if ((f & mask) == 0) {
				fireDiagnostic(Diagnostic.Kind.ERROR, "unsupported_modifier:" + w.val());
				continue;
			}

			if ((acc & f) != 0) fireDiagnostic(Diagnostic.Kind.MANDATORY_WARNING, "duplicate_modifier:" + w.val());
			acc |= f;
		}
	}
	private static int _classModifier(JavaLexer wr, int acc) throws ParseException {
		Word w;
		w = wr.next();
		switch (w.type()) {
			case CLASS: // class
				acc |= AccessFlag.SUPER;
				break;
			case INTERFACE: // interface
				if ((acc & (AccessFlag.FINAL)) != 0) throw wr.err("illegal_modifier:interface:final");
				acc |= AccessFlag.ABSTRACT | AccessFlag.INTERFACE;
				break;
			case ENUM: // enum
				if ((acc & (AccessFlag.ABSTRACT)) != 0) throw wr.err("illegal_modifier:enum:abstract");
				acc |= AccessFlag.ENUM | AccessFlag.FINAL;
				break;
			case AT_INTERFACE: // @interface
				if ((acc & (AccessFlag.FINAL)) != 0) throw wr.err("illegal_modifier:@interface:final");
				acc |= AccessFlag.ANNOTATION | AccessFlag.INTERFACE | AccessFlag.ABSTRACT;
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

	public static final int TYPE_PRIMITIVE = 1, TYPE_OPTIONAL = 2, TYPE_AUTO = 4, TYPE_STAR = 8, TYPE_GENERIC = 16, TYPE_LEVEL2 = 32;
	/**
	 * 获取类型
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
	 */
	private IType _type(JavaLexer wr, CharList sb, int flags) throws ParseException {
		IType type;

		Word w = wr.next();
		int std = toType_1.getOrDefaultInt(w.type(), -1);
		if (std >= 0) {
			type = Type.std(std);
		} else {
			if (w.type() != LITERAL) {
				if ((flags&TYPE_OPTIONAL) == 0) throw wr.err("empty_type"+w);
				return null;
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

					type = _genericUse(sb, 0);

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

			while (w.type() == left_m_bracket) {
				if (++arrLen > 255) throw wr.err("array_dimension_overflow");

				expect(wr, right_m_bracket);
				w = wr.next();
			}

			if (w.type() == varargs) arrLen++;

			if (arrLen > 0) {
				if (type.genericType() == 0 && type.rawType().owner == null) type = new Type(((Type)type).type, arrLen);
				else type.setArrayDim(arrLen);
			} else {
				if (type.rawType().owner == null && (flags&TYPE_PRIMITIVE) == 0) throw wr.err("unexpected:"+w.val());
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
	private IType _genericUse(CharList type, int flag) throws ParseException {
		GenericPrimer g = new GenericPrimer();
		IType result = null;

		JavaLexer wr = this.wr;
		if (type != null) {
			g.owner = type.toString();
		} else {
			Word w = wr.next();
			// A<?
			if (w.type() == ask) {
				if ((flag & GENERIC_INNER) == 0) wr.unexpected(w.val());
				g.owner = "*";
				w = wr.next();
				switch (w.type()) {
					// A<? super
					case SUPER: g.extendType = Generic.EX_SUPER; break;
					case EXTENDS: g.extendType = Generic.EX_EXTENDS; break;
					// A<?
					case gtr: case comma:
						wr.retractWord();
						return g;
					default: wr.unexpected(w.val(), "'>' ',' 'extends' 'super'");
				}
			} else {
				// A<B
				wr.retractWord();
			}

			Type type1 = (Type) _type(wr, IOUtil.getSharedCharBuf(), TYPE_PRIMITIVE);
			if (type1.owner == null) result = type1;
			else g.owner = type1.owner;
		}

		Word w = wr.next();
		if (w.type() == lss) {
			g.real = true;
			seg:
			while (true) {
				// <<或者>>
				wr.env &= ~CAT_GENERIC_EXT;
				g.addChild(_genericUse(null, GENERIC_INNER));
				w = wr.next();
				switch (w.type()) {
					case comma: continue;
					case gtr: break seg;
					default: throw wr.err("unexpected:", w);
				}
			}
			wr.env |= CAT_GENERIC_EXT;

			w = wr.next();
		}

		if (w.type() == dot) {
			g.sub1 = _genericUse(null, GENERIC_SUBCLASS);
		}

		if ((flag & GENERIC_SUBCLASS) == 0) {
			int al = 0;
			while (w.type() == left_m_bracket) {
				al++;
				expect(wr, right_m_bracket);
				w = wr.next();
			}
			g.setArrayDim(al);
		}
		wr.retractWord();

		if (result != null) {
			if (g.isRealGeneric()) fireDiagnostic(Diagnostic.Kind.ERROR, "generic_primitive");
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
			if (w.type() != left_s_bracket) {
				if (w.type() == at) {
					continue;
				} else {
					wr.retractWord();
					return list;
				}
			}

			while (wr.hasNext()) {
				Word nameOpt = wr.next().copy();
				w = wr.next();
				if (w.type() != assign) {
					// values
					a.assertValueOnly = true;
					a.newEntry("value", ParseTask.FieldVal(wr.index = nameOpt.pos(), skipCode(wr, BRACKET_ANNOTATION)));
				} else {
					if (a.assertValueOnly) throw wr.err("assert value only");
					a.newEntry(nameOpt.val(), ParseTask.FieldVal(wr.index, skipCode(wr, BRACKET_ANNOTATION)));
				}

				w = wr.next();
				switch (w.type()) {
					case right_s_bracket:
						w = wr.next();
						if (w.type() == at) {
							continue readMore;
						} else {
							wr.retractWord();
							return list;
						}
					case comma:
						break;
					default:
						throw wr.err("unexpected:" + w.val());
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
		int L = 0, M = 0, S = 0;
		cyl:
		while (wr.hasNext()) {
			Word w = wr.next();
			switch (w.type()) {
				case left_l_bracket: L++; break;
				case left_m_bracket: M++; break;
				case left_s_bracket: S++; break;
				case right_l_bracket: L--;
					if (L < 0) {
						if ((type&_E_LSB) != 0) break cyl;
						throw wr.err("invalid_bracket:" + w.val());
					}
					break;
				case right_m_bracket: M--;
					if (M < 0) throw wr.err("invalid_bracket:" + w.val());
					break;
				case right_s_bracket: S--;
					if (S < 0) {
						if ((type&_E_SSB) != 0){
							wr.retractWord();
							break cyl;
						}
						throw wr.err("invalid_bracket:" + w.val());
					}
					break;
				case semicolon: if ((type & _E_SEM) == 0) continue;
				case comma: if (w.type() == comma && (type & _E_COM) == 0) continue;
				case EOF:
					if ((L|M|S) == 0) break cyl;
					if (w.type() == EOF) throw wr.err("unclosed_bracket");
			}
		}
		return wr.index;
	}

	// endregion
	// region 阶段2 验证和解析类文件

	public void S2_Parse() throws ParseException {
		// region 继承可行性

		diagPos = classIdx;

		IClass pInfo = resolve(parent);
        if (pInfo == null) {
			fireDiagnostic(Diagnostic.Kind.ERROR, "unable_resolve:PARENT:" + parent);
			return;
		} else {
            int acc = pInfo.modifier();
            if (0 == (acc & AccessFlag.SUPER)) {
                if (0 != (acc & AccessFlag.ANNOTATION)) {
                    fireDiagnostic(Diagnostic.Kind.NOTE, "inherit:annotation");
                } else if (0 != (acc & AccessFlag.ENUM)) {
                    fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:enum");
                } else if (0 != (acc & AccessFlag.FINAL)) {
                    fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:final");
                } else if (0 != (acc & AccessFlag.INTERFACE)) {
                    fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:interface");
                }
            }
            if (0 == (acc & AccessFlag.PUBLIC)) {
                if (!MapUtil.arePackagesSame(name, parent)) {
                    fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:package-private");
                }
            }
        }

		// 禁止泛型异常
		if (signature != null && ctx.canInstanceOf("java/lang/Throwable", parent, -1)) {
			fireDiagnostic(Diagnostic.Kind.ERROR, "generic_exception");
		}

		List<String> itfs = this.interfacesPre;
		interfacesPre = null;

		for (int i = 0; i < itfs.size(); i++) {
			IClass info = resolve(itfs.get(i));
            if (info == null) {
				fireDiagnostic(Diagnostic.Kind.ERROR, "unable_resolve:INTERFACE:" + itfs.get(i));
			} else {
                int acc = info.modifier();
                if (0 == (acc & AccessFlag.INTERFACE)) {
                    fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:non_interface");
                }
                if (0 == (acc & AccessFlag.PUBLIC)) {
                    if (!MapUtil.arePackagesSame(name, info.name())) {
                        fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:package-private");
                    }
                }
				interfaces.set(i, cp.getClazz(info.name()));
            }
		}

		allNodeNames = new MyHashSet<>(methods.size()+fields.size());

		List<Field> fields = Helpers.cast(this.fields);
		for (int i = 0; i < fields.size(); i++) {
			Field f = fields.get(i);
			if ((access & AccessFlag.INTERFACE) != 0) {
				int acc = f.access;
				if ((acc & (AccessFlag.PRIVATE|AccessFlag.PROTECTED)) != 0) {
					if (ctx.isSpecEnabled(CompilerConfig.INTERFACE_INACCESSIBLE_FIELD)) {
						if ((acc & AccessFlag.PRIVATE) != 0)
							fireDiagnostic(Diagnostic.Kind.WARNING, "inaccessible_interface_field");
					} else {
						fireDiagnostic(Diagnostic.Kind.ERROR, "modifier_not_allowed:"+acc);
					}
				}
				f.access = (char) (AccessFlag.STATIC | acc);
			}

			allNodeNames.add(f.name);
		}

		boolean autoInit = (access & AccessFlag.INTERFACE) == 0;
		List<MethodNode> methods = this.methods;
		for (int i = 0; i < methods.size(); i++) {
			diagPos = methodIdx.get(i);

			Method m = (Method) methods.get(i);
			if (m.name.equals("<init>")) {
				autoInit = false;
				if ((access & AccessFlag.ENUM) != 0) {
					if ((m.access & (AccessFlag.PUBLIC | AccessFlag.PROTECTED)) != 0) {
						fireDiagnostic(Diagnostic.Kind.ERROR, "enum_public_protected_constructor");
					} else if ((m.access & AccessFlag.PRIVATE) == 0) {
						m.access |= AccessFlag.PRIVATE;
					}
					// not necessary to check
					continue;
				}
			}
			allNodeNames.add(m.name+m.rawDesc());

			// todo: check interface method flags
			if ((access & AccessFlag.INTERFACE) != 0 && (m.access & (AccessFlag.STATIC | AccessFlag.FINAL)) == AccessFlag.FINAL) {
				fireDiagnostic(Diagnostic.Kind.ERROR, "final_interface_method");
			}

			AttrStringList list = (AttrStringList) m.attrByName("Exceptions");
			if (list != null) {
				List<String> classes = list.classes;
				for (int j = 0; j < classes.size(); j++) {
					IClass info = resolve(classes.get(j));
					if (info == null) {
						fireDiagnostic(Diagnostic.Kind.ERROR, "unable_resolve:EXCEPTION:"+classes.get(i));
					} else {
						int acc = info.modifier();
						if (0 == (acc & AccessFlag.PUBLIC)) {
							if (!MapUtil.arePackagesSame(name, info.name())) {
								fireDiagnostic(Diagnostic.Kind.ERROR, "inherit:package-private");
							}
						}
						if (!ctx.canInstanceOf("java/lang/Throwable", info.name(), -1)) {
							fireDiagnostic(Diagnostic.Kind.ERROR, "excepting_exception:"+classes.get(i));
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
							fireDiagnostic(Diagnostic.Kind.ERROR, "constructor_inaccessible");
							break;
						}
					} else if (parentNoPar == 0) {
						parentNoPar = -1;
					}
				}
			}

			if (parentNoPar < 0) {
				fireDiagnostic(Diagnostic.Kind.ERROR, "not_empty_constructor");
			}

			CodeWriter cw = newMethod(AccessFlag.PUBLIC | AccessFlag.SYNTHETIC, "<init>", "()V");
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
		if ((access & AccessFlag.ANNOTATION) != 0) {
			List<AnnotationPrimer> list = annoTask.get(this);
			if (list != null) annoInfo = AttrHelper.getAnnotationInfo(list);
			if (annoInfo == null) fireDiagnostic(Diagnostic.Kind.ERROR, "annotation_error:class");
		}
		// endregion
		// region 注解预处理
		for (Map.Entry<Attributed, List<AnnotationPrimer>> entry : annoTask.entrySet()) {
			Annotations inv = null, vis = null;

			List<AnnotationPrimer> list = entry.getValue();
			for (int i = 0; i < list.size(); i++) {
				AnnotationPrimer a = list.get(i);

				IClass type1 = resolve(a.clazz);
				if (type1 == null) {
					fireDiagnostic(Diagnostic.Kind.ERROR, "unable_resolve:ANNOTATION:" + a.clazz);
				}
				a.clazzInst = type1;

				AnnotationClass ad = CompileContext.getAnnotationDescriptor(type1);
				if (!ctx.applicableToNode(ad, entry.getKey())) {
					fireDiagnostic(Diagnostic.Kind.ERROR, "not_applicable_to:" + entry.getKey().getClass().getSimpleName());
				}

				switch (ad.kind()) {
					case AnnotationClass.SOURCE:
						break;// discard
					case AnnotationClass.CLASS:
						if (inv == null) {
							inv = new Annotations(Annotations.INVISIBLE);
							entry.getKey().putAttr(inv);
						}
						inv.annotations.add(a);
						break;
					case AnnotationClass.RUNTIME:
						if (vis == null) {
							vis = new Annotations(Annotations.VISIBLE);
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
			fireDiagnostic(Diagnostic.Kind.ERROR, "unable_resolve:"+kind+":" + t.owner());
		} else if (!_accessible(type1, AccessFlag.PUBLIC)) {
			fireDiagnostic(Diagnostic.Kind.ERROR, "unable_access:"+kind+":" + t.owner());
		} else t.owner(type1.name());
	}

	@SuppressWarnings("fallthrough")
	private boolean _accessible(IClass target, char acc) {
		if (this == target) return true;

		boolean pkg = false;
		if ((target.modifier() & AccessFlag.PUBLIC) == 0) {
			if (!MapUtil.arePackagesSame(name, target.name())) return false;
			pkg = true;
		}
		switch (acc & (AccessFlag.PUBLIC | AccessFlag.PROTECTED | AccessFlag.PRIVATE)) {
			case AccessFlag.PUBLIC: return true;
			case AccessFlag.PROTECTED: if (ctx.canInstanceOf(name, target.name(), 0)) return true;
			case 0: return pkg || MapUtil.arePackagesSame(name, target.name());
			case AccessFlag.PRIVATE: return false;
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

		return newMethod(AccessFlag.PUBLIC | AccessFlag.STATIC | AccessFlag.SYNTHETIC, "<clinit>", "()V");
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

				List<? extends MoFNode> methods = a.clazzInst.methods();
				for (int j = 0; j < methods.size(); j++) {
					MethodNode m = (MethodNode) methods.get(j);
					if ((m.modifier() & AccessFlag.STATIC) != 0) continue;
					if (m.attrByName(AnnotationDefault.NAME) == null) {
						if (!a.values.containsKey(m.name())) {
							missed.add(m.name());
							continue;
						}
					}

					ParseTask task = Helpers.cast(a.values.remove(m.name()));
					if (task != null) task.parse(this, m.returnType());
				}

				ctx.invokePartialAnnotationProcessor(this, a, missed);

				if (!a.values.isEmpty()) {
					fireDiagnostic(Diagnostic.Kind.ERROR, "annotation_value_left:" + a.values.keySet());
				}

				if (!missed.isEmpty()) {
					fireDiagnostic(Diagnostic.Kind.ERROR, "annotation_value_miss:" + missed);
				}
			}
			ctx.invokeAnnotationProcessor(this, entry.getKey(), list);
		}
		annoTask.clear();
		// endregion
		// region todo 表达式解析

		SignaturePrimer root = (SignaturePrimer) signature;
		currentNode = root;

		if (!clInitTask.isEmpty()) {
			CodeWriter clinit = getClinit();
			for (int i = 0; i < clInitTask.size(); i++) {
				clInitTask.get(i).parse(this, clinit);
			}
			clInitTask.clear();
		}

		if (!initTask.isEmpty()) {
			CodeWriter init = newMethod(AccessFlag.PUBLIC | AccessFlag.STATIC | AccessFlag.SYNTHETIC, "<init>", "()V");
			for (int i = 0; i < initTask.size(); i++) {
				initTask.get(i).parse(this, init);
			}
			// todo append to init last
			initTask.clear();
		}

		for (Map.Entry<Attributed, ParseTask> entry : exprTask.entrySet()) {
			Attributed k = entry.getKey();
			if (k instanceof Method) {
				SignaturePrimer s1 = (SignaturePrimer) k.attrByName("Signature");
				currentNode = s1 == null ? root : s1;
			} else {
				currentNode = root;
			}
			entry.getValue().parse(this, k);
		}
		exprTask.clear();

		// endregion

	}

	// endregion

	public String getFilePath() {
		return path;
	}

	public String getContext() {
		return wr == null ? "~IO 错误~" : wr.getText().toString();
	}

	private void fireDiagnostic(Diagnostic.Kind kind, String code) {
		if (diagPos == -2) {
			ctx.report(this, kind, -1, code);
			return;
		}
		if (diagPos < 0) {
			ctx.report(this, kind, wr.index, code);
		} else {
			ctx.report(this, kind, diagPos, code);
		}
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

	public IType resolveType(int flag) throws ParseException {
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
}
