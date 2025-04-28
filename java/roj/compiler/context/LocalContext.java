package roj.compiler.context;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.ClassListAttribute;
import roj.asm.attr.InnerClasses;
import roj.asm.cp.*;
import roj.asm.insn.Label;
import roj.asm.type.*;
import roj.asm.util.ClassUtil;
import roj.collect.*;
import roj.compiler.LavaFeatures;
import roj.compiler.Tokens;
import roj.compiler.api.MethodDefault;
import roj.compiler.api.Types;
import roj.compiler.api.ValueBased;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.LocalVariable;
import roj.compiler.ast.expr.NaE;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.config.ParseException;
import roj.config.data.CEntry;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * LocalContext 解析(Parse/Resolve)环境 线程本地+递归
 * 1. 错误处理
 * 2. 类型转换，解析类型（扩展到全限定名，然后变成ASM数据，同时做权限检查）  (代理TypeCast)
 * 3. 解析符号引用 Class Field Method  （代理TypeResolver和Inferrer）
 * 4. instanceof，getCommonParent 最近共同祖先
 * 5. 访问权限检查和final赋值检查
 */
public class LocalContext {
	public final GlobalContext classes;

	public final ExprParser ep;
	public final BlockParser bp;
	public final Tokens lexer;

	public final TypeCast caster = new TypeCast();
	public final Inferrer inferrer = new Inferrer(this);

	private ImportList importList;
	public final MyHashMap<String, ClassNode> importCache = new MyHashMap<>(), importCacheMethod = new MyHashMap<>();
	public final MyHashMap<String, Object[]> importCacheField = new MyHashMap<>();

	public CompileUnit file;
	public MethodNode method;
	// 功能应该自解释了
	// 后两个：如果没显式调用super就插入默认构造器，如果没调用this就插入GlobalInit
	public boolean inStatic, inConstructor, noCallConstructor, thisConstructor;
	// 递归构造器检查
	private final MyHashMap<MethodNode, MethodNode> constructorChain = MyHashMap.withCustomHasher(Hasher.identity());

	// stage2.1解析方法和字段的基础类型时不生成警告
	public boolean disableRawTypeWarning;

	// Tail Call Elimination, MultiReturn等使用
	public boolean inReturn;
	// This(AST节点)使用 也许能换成callback 目前只有Generator不是0
	public int thisSlot;
	// expressionBeforeThisOrSuper()检查
	// 只有在未使用this类型前才能调用this或super
	// 不要求必须是构造器的第一个语句
	public boolean thisUsed;
	// 绕过枚举的构造器检测，有子类的枚举构造器不是private的，所以不能省去
	public boolean enumConstructor;

	// lambda方法的序号
	public int nameIndex;
	// lambda用到，阻止预定义ID的变量被直接序列化入SolidBlock
	public boolean isArgumentDynamic;

	// stage3 constant resolution
	public boolean fieldDFS;

	public LocalContext(GlobalContext ctx) {
		this.classes = ctx;
		this.lexer = createLexer();
		this.ep = createExprParser();
		this.bp = createBlockParser();
		this.variables = bp == null ? Collections.emptyMap() : bp.getVariables();
		this.caster.context = ctx;
		this.caster.ctx = this;
	}

	protected Tokens createLexer() {return new Tokens();}
	protected ExprParser createExprParser() {return new ExprParser(this);}
	protected BlockParser createBlockParser() {return new BlockParser(this);}
	public MethodWriter createMethodWriter(ClassNode file, MethodNode node) {return new MethodWriter(file, node, classes.hasFeature(LavaFeatures.ATTR_LOCAL_VARIABLES), this);}

	@NotNull public ToIntMap<String> getHierarchyList(IClass info) {return classes.getHierarchyList(info);}
	@NotNull public ComponentList getFieldList(IClass info, String name) {return classes.getFieldList(info, name);}
	@NotNull public ComponentList getMethodList(IClass info, String name) {return classes.getMethodList(info, name);}

	public void clear() {
		this.file = null;
		this.importList = null;
		this.importCache.clear();
		this.importCacheMethod.clear();
		this.importCacheField.clear();
		this.lexer.init("");
		this.constructorChain.clear();
	}
	public void setClass(CompileUnit file) {
		this.errorReportIndex = -1;
		this.file = file;
		this.importList = file.getImportList();
		this.importCache.clear();
		this.importCacheMethod.clear();
		this.importCacheField.clear();
		int pos = this.lexer.index;
		this.lexer.init(file.getCode());
		this.lexer.index = pos;
		this.caster.typeParams = file.signature == null ? Collections.emptyMap() : file.signature.typeParams;
		this.fieldDFS = false;
		this.constructorChain.clear();

		file.ctx = this;
	}
	public void setupFieldDFS() {
		constructorFields = file.finalFields;
		inStatic = true;
		inConstructor = true;
		fieldDFS = true;
	}
	public void setMethod(MethodNode node) {
		file._setSign(node);
		// LPSignature的typeParams具有继承功能
		caster.typeParams = file.currentNode == null ? Collections.emptyMap() : file.currentNode.typeParams;

		inStatic = (node.modifier&Opcodes.ACC_STATIC) != 0;
		noCallConstructor = inConstructor = node.name().startsWith("<");
		if (inConstructor) {
			if (node.name().equals("<init>")) {
				constructorFields = new MyHashSet<>(Hasher.identity());
				constructorFields.addAll(file.finalFields);
			} else {
				constructorFields = file.finalFields;
			}
		}
		method = node;

		inReturn = false;
		thisSlot = 0;
		thisUsed = false;

		nameIndex = 0;

		thisConstructor = false;
	}

	public String currentCodeBlockForReport() {return "[symbol.type,\" \","+file.name()+"]";}

	/**
	 * 伪类型检查，WIP
	 */
	public IType transformPseudoType(IType type) {
		if (type.owner() != null && !"java/lang/Object".equals(type.owner())) {
			var info = classes.getClassInfo(type.owner());
			if (info.parent() == null) {
				var vb = (ValueBased) info.getRawAttribute(ValueBased.NAME);
				if (vb != null) {
					report(Kind.WARNING, "pseudoType.cast");
					return vb.exactType;
				} else {
					report(Kind.ERROR, "pseudoType.pseudo");
				}
			}
		}

		return type;
	}

	//region 错误报告
	public BiConsumer<String, Object[]> errorCapture;
	public int errorReportIndex;

	public void report(Kind kind, String message) {
		if (errorCapture != null) {errorCapture.accept(message, ArrayCache.OBJECTS);return;}
		classes.report(file, kind, errorReportIndex >= 0 ? errorReportIndex : lexer.index, message);
	}
	public void report(Kind kind, String message, Object... args) {
		for (Object arg : args) {
			if (arg == NaE.UNRESOLVABLE) return;
		}

		if (errorCapture != null) {errorCapture.accept(message, args);return;}
		classes.report(file, kind, errorReportIndex >= 0 ? errorReportIndex : lexer.index, message, args);
	}
	public void report(Expr node, Kind kind, String message) {
		if (errorCapture != null) {errorCapture.accept(message, ArrayCache.OBJECTS);return;}
		classes.report(file, kind, node.getWordStart(), node.getWordEnd(), message, ArrayCache.OBJECTS);
	}
	public void report(Expr node, Kind kind, String message, Object... args) {
		for (Object arg : args) {
			if (arg == NaE.UNRESOLVABLE) return;
		}

		if (errorCapture != null) {errorCapture.accept(message, args);return;}
		classes.report(file, kind, node.getWordStart(), node.getWordEnd(), message, args);
	}
	public void report(int knownPos, Kind kind, String message, Object... args) {classes.report(file, kind, knownPos, message, args);}
	//endregion
	// region 访问权限和final字段赋值检查
	@SuppressWarnings("fallthrough")
	public void assertAccessible(IClass type) {
		if (type == file) return;

		int modifier = type.modifier();
		InnerClasses.Item item = classes.getInnerClassInfo(type).get(type.name());
		if (item != null) {
			modifier = item.modifier;
			checkAccessModifier(modifier, type, null, "symbol.error.accessDenied.type", true);
		} else {
			switch ((modifier&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE))) {
				default: report(Kind.ERROR, "lc.illegalModifier", Integer.toHexString(modifier));
				case Opcodes.ACC_PUBLIC: return;
				case 0: if (!ClassUtil.arePackagesSame(type.name(), file.name()))
					report(Kind.ERROR, "symbol.error.accessDenied.type", type.name(), "package-private", file.name());
			}
		}
	}
	// 这里不会检测某些东西 (override, static has been written等)
	public boolean checkAccessible(IClass type, RawNode node, boolean staticEnv, boolean report) {
		if (type == file) return true;

		if (!checkAccessModifier(node.modifier(), type, node.name(), "symbol.error.accessDenied.symbol", report)) {
			return false;
		}

		if (staticEnv && (node.modifier()&Opcodes.ACC_STATIC) == 0) {
			if (report) report(Kind.ERROR, "symbol.error.nonStatic.symbol", type.name(), node.name(), node instanceof FieldNode ? "symbol.field" : "invoke.method");
			return false;
		}
		return true;
	}
	private boolean checkAccessModifier(int flag, IClass type, String node, String message, boolean report) {
		String modifier;
		switch ((flag & (Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE))) {
			default: throw ResolveException.ofIllegalInput("lc.illegalModifier", Integer.toHexString(flag));
			case Opcodes.ACC_PUBLIC: return true;
			case Opcodes.ACC_PROTECTED:
				if (ClassUtil.arePackagesSame(type.name(), file.name()) || instanceOf(file.name(), type.name())) return true;
				modifier = "protected";
				break;
			case Opcodes.ACC_PRIVATE:
				// 同一个类可以互相访问
				// 条件: a/b仅比较$之前的部分
				// 除了桥接方法, NestHost属性也可以达成该目的
				if (ClassUtil.canAccessPrivate(type.name(), file.name())) return true;
				modifier = "private";
				break;
			case 0:
				if (ClassUtil.arePackagesSame(type.name(), file.name())) return true;
				modifier = "package-private";
				break;
		}
		if (report) report(Kind.ERROR, message, type.name()+(node!=null?"."+node:""), modifier, file.name());
		return false;
	}

	private MyHashSet<FieldNode> constructorFields;
	public final void checkSelfField(FieldNode node, boolean write) {
		boolean constructor = inConstructor;
		if (inStatic) {
			if ((node.modifier()&Opcodes.ACC_STATIC) == 0)
				report(Kind.ERROR, "symbol.error.nonStatic.symbol", file.name(), node.name(), "symbol.field");
		} else if ((node.modifier()&Opcodes.ACC_STATIC) != 0) {
			constructor = false;
		}

		if ((node.modifier()&Opcodes.ACC_FINAL) != 0) {
			if (write) {
				if (constructor) {
					if (!constructorFields.remove(node)) {
						report(Kind.ERROR, "symbol.error.field.writeAfterWrite", file.name(), node.name());
					}
				} else {
					report(Kind.ERROR, "symbol.error.field.writeFinal", file.name(), node.name());
				}
			} else {
				if (constructor) {
					if (constructorFields.contains(node)) {
						report(Kind.ERROR, "symbol.error.field.readBeforeWrite", file.name(), node.name());
					}
				}
			}
		}
	}

	/**
	 * 递归构造器检查
	 */
	public void onCallConstructor(MethodNode callee) {
		noCallConstructor = false;
		if (!callee.owner.equals(file.name())) return;

		thisConstructor = true;
		constructorChain.putIfAbsent(method, callee);
		MethodNode node = constructorChain.get(callee);
		while (node != null) {
			if (node == method) {
				report(Kind.ERROR, "lc.recursiveConstructor");
				break;
			}
			node = constructorChain.get(node);
		}
	}
	// endregion
	// region 解析 符号引用 类型表示 类型实例
	@NotNull
	public ComponentList getMethodListOrReport(IClass type, String name, Expr caller) {
		var list = classes.getMethodList(type, name);
		if (list == ComponentList.NOT_FOUND) {
			int argc = 0;
			report(caller, Kind.ERROR, "symbol.error.noSuchSymbol", name.equals("<init>") ? "invoke.constructor" : "invoke.method", name, "[symbol.type,\" \","+type.name()+"]", reportSimilarMethod(type, name, argc));
		}
		return list;
	}
	private String reportSimilarMethod(IClass type, String method, int argc) {
		var similar = new SimpleList<String>();
		loop:
		for (var entry : classes.getResolveHelper(type).getMethods(classes).entrySet()) {
			for (MethodNode node : entry.getValue().getMethods()) {
				int parSize = node.parameters().size();
				/*if ((node.modifier & Opcodes.ACC_VARARGS) != 0 ? argc < parSize - 1 : argc != parSize) {
					continue loop;
				}*/
			}
			if (TextUtil.editDistance(method, entry.getKey()) < (method.length()+1)/2) {
				similar.add(entry.getKey());
			}
		}
		if (similar.isEmpty()) return "";

		var sb = getTmpSb().append("[symbol.similar,[invoke.method,\"");
		sb.append(TextUtil.join(similar, "\n    "));
		return sb.append("\"]]").toString();
	}

	/**
	 * 将一个全限定名称或短名称解析到其类型实例.
	 * 通过{@link ImportList#resolve(LocalContext, String)}，
	 * 并进行导入限制的检查
	 * @param klass 短名称
	 */
	@Nullable
	public final ClassNode resolve(String klass) { return importList.resolve(this, klass); }
	/**
	 * 进行导入限制的完整检查, 防止通过返回值或字段类型引入不允许的类
	 * @param owner 类名
	 */
	public void checkTypeRestriction(String owner) {
		// 检查type是否已导入
		if (owner != null && importList.isRestricted() && null == importList.resolve(this, owner.substring(owner.lastIndexOf('/')+1))) {
			report(Kind.ERROR, "lc.packageRestricted", owner);
		}
	}

	/**
	 * 将(已经resolveType的)类型表示解析到其类型实例，支持数组
	 * @param type 类型
	 */
	@Nullable
	public final ClassNode resolve(IType type) { return type.array() > 0 ? classes.getArrayInfo(type) : classes.getClassInfo(type.owner()); }
	/**
	 * 解析一个类型表示, 包括将它的名称解析为全限定名称, 对泛型参数作出限制, 实现虚拟泛型等
	 * @return 和输入参数相同
	 */
	@Contract("_ -> param1")
	public final IType resolveType(IType type) {
		if (type.genericType() == 0 ? type.rawType().type != Type.CLASS : type.genericType() != 1) return type;

		// 不预先检查全限定名，适配package-restricted模式
		var info = resolve(type.owner());
		if (info == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", type.owner());
			return type;
		}
		type.owner(info.name());

		Signature sign = info.getAttribute(info.cp(), Attribute.SIGNATURE);
		int count = sign == null ? 0 : sign.typeParams.size();

		if (type.genericType() == IType.GENERIC_TYPE) {
			Generic type1 = (Generic) type;
			List<IType> params = type1.children;

			boolean genericIgnore = isGenericIgnore(info);
			if (!genericIgnore && params.size() != count) {
				if (count == 0) report(Kind.ERROR, "symbol.error.generic.paramCount.0", type.rawType());
				else if (params.size() != 1 || params.get(0) != Asterisk.anyGeneric) report(Kind.ERROR, "symbol.error.generic.paramCount", type.rawType(), params.size(), count);
			}

			if (sign == null) return type;
			var itr = sign.typeParams.values().iterator();

			for (int i = 0; i < params.size(); i++) {
				IType param = resolveType(params.get(i));
				if (genericIgnore) continue;

				// skip if is AnyType (?)
				if (param.genericType() <= 2) {
					/*var tmp = new MyHashMap<String, IType>();
					for (Map.Entry<String, List<IType>> entry : sign.typeParams.entrySet()) {
						List<IType> bound = entry.getValue();
						tmp.put(entry.getKey(), bound.get(bound.get(0).genericType() == IType.PLACEHOLDER_TYPE ? 1 : 0));
					}*/

					caster.typeParamsForTargetType = sign.typeParams;
					for (IType bound : itr.next()) {
						//bound = Inferrer.clearTypeParam(bound, tmp, sign.typeParams);
						castTo(param, bound, 0);
					}
					caster.typeParamsForTargetType = null;
				}

				if (param instanceof Generic g && g.canBeAny()) {
					params.set(i, Signature.any());
				} else if (param.isPrimitive()) {
					// TODO generate template class
					// 这里是实现的关键……不再处理什么基本类型泛型的保存读取这个那个
					// 从这里，把每一次带有基本类型
					// roj.collect.MyHashMap<int, Object>
					// 都换成类名为 ;PGEN;I;L;roj.collect.MyHashMap
					// 泛型为 <Object>

					// PGEN: Primitive Generic (Generator)
					// I: int
					// L: object

					//return resolvePrimitiveGeneric(type);

					// 吗？
					// bushi(
					// 为此，我们还需要生成一个;PGEN;int，作为T的内部类型
					// 它不继承Object，这样就不会被作为普通对象处理
					// 它仅提供hashCode和equals通过Evaluable接口，可以把他们inline成对应的比较
					// 除此之外，传递给BlockParser的方法参数也需要是PGEN类型
					// 真实的方法参数、fieldList类型、以及BlockParser生成变量的类型，依然是基本类型
					report(Kind.ERROR, "当前版本暂时未实现这个功能");
				}
			}

			//TODO not tested yet
			// MyHashMap<K,V>.Entry<Z>
			// MyHashMap.Entry<K,V>
			// MyHashMap<K,V>.Entry.SomeClass<Z>
			// class G1<T> { class G2 { class G3<T2> {} } }
			GenericSub x = type1.sub;
			while (x != null) {
				var ic = classes.getInnerClassInfo(info).get(x.owner);
				if (ic == null || (info = classes.getClassInfo(ic.self)) == null) {
					report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", x.owner, "[symbol.type,\""+type1+"\"]");
					break;
				}

				if ((ic.modifier &Opcodes.ACC_STATIC) != 0) {
					report(Kind.ERROR, "type.error.staticGenericSub", type1, ic.name);
				}

				sign = info.getAttribute(info.cp(), Attribute.SIGNATURE);
				count = sign == null ? 0 : sign.typeParams.size();

				if (x.children.size() != count) {
					if (count == 0) report(Kind.ERROR, "symbol.error.generic.paramCount.0", ic.self);
					else report(Kind.ERROR, "symbol.error.generic.paramCount", ic.self, x.children.size(), count);
				}

				x = x.sub;
			}
		} else if (count > 0 && !disableRawTypeWarning) {
			report(Kind.WARNING, "symbol.warn.generic.rawTypes", type);
		}
		return type;
	}
	//endregion
	//region DotGet字符串解析
	private final CharList frTemp = new CharList();
	private IClass _frBegin;
	private final SimpleList<FieldNode> _frChain = new SimpleList<>();
	private IType _frType;
	private int _frOffset;

	/**
	 * 将(full)DotGet格式的字符串, 例如 {@code net/minecraft/client/Minecraft/fontRender/FONT_HEIGHT} 解析为类与字段访问的组合
	 * @param allowClassExpr 允许仅解析到类而不包含字段，比如 java/lang/String
	 * @return 错误码,返回null时成功
	 */
	@Nullable
	public final String resolveDotGet(CharList desc, boolean allowClassExpr) {
		ClassNode directClz = null;
		String anySuccess = "";
		_frOffset = 0;

		int slash = desc.indexOf("/");
		if (slash >= 0) {
			directClz = importList.resolve(this, desc.substring(0, slash++));
			if (directClz != null) {
				String error = resolveField(directClz, null, desc, slash);
				if (error == null) return null;

				anySuccess = error;
			}

			var sb = frTemp;
			for(;;) {
				slash = desc.indexOf("/", slash);
				if (slash < 0) break;

				_frOffset++;
				sb.clear(); sb.append(desc, 0, slash);

				int dollar = slash++;
				for(;;) {
					var clz = classes.getClassInfo(sb);
					if (clz != null) {
						String error = resolveField(clz, null, desc, slash);
						if (error == null) return null;

						anySuccess = error;
					}

					dollar = sb.lastIndexOf("/", dollar);
					if (dollar < 0) break;
					sb.set(dollar, '$');
				}
			}
		}

		if (anySuccess.isEmpty()) {
			if (directClz == null) directClz = resolve(desc.toString());
			if (directClz != null) {
				if (allowClassExpr) {
					_frBegin = directClz;
					_frChain.clear();
					_frType = null;
					return "";
				}

				return "symbol.error.expression:".concat(desc.toString());
			}
			return "symbol.error.noSuchSymbol:[symbol.field,"+desc+","+currentCodeBlockForReport()+"]";
		}
		return anySuccess;
	}
	/**
	 * 将(partial)DotGet格式的字符串, 解析为字段访问的组合
	 * @param clz 起始类型，即上个javadoc中的类型前缀部分
	 * @param generic 起始类型的泛型参数
	 * @param desc DotGet格式的字段访问字符串
	 */
	public final String resolveField(IClass clz, IType generic, CharList desc) { return resolveField(clz, generic, desc, 0); }

	private String resolveField(IClass clz, IType fieldType, CharList desc, int prevI) {
		_frBegin = clz;
		List<FieldNode> result = _frChain; result.clear();

		int i = desc.indexOf("/", prevI);
		while (true) {
			String name = desc.substring(prevI, i < 0 ? desc.length() : i);

			FieldNode field;
			block: {
				var fields = getFieldList(clz, name);
				if (fields != ComponentList.NOT_FOUND) {
					var fr = fields.findField(this, 0);
					if (fr.error != null) return fr.error;

					field = fr.field;
					break block;
				}
				return "symbol.error.noSuchSymbol:[symbol.field,"+name+",[symbol.type,\" \","+clz.name()+"]]";
			}

			Signature cSign = clz.getAttribute(clz.cp(), Attribute.SIGNATURE);
			block:{
			if (cSign != null) {
				Signature fSign = field.getAttribute(clz.cp(), Attribute.SIGNATURE);
				if (fSign != null) {
					Map<String, List<IType>> tpBounds = cSign.typeParams;
					MyHashMap<String, IType> knownTps = new MyHashMap<>(cSign.typeParams.size());

					if (fieldType instanceof Generic gType) {
						assert gType.children.size() == tpBounds.size();
						Iterator<String> itr = tpBounds.keySet().iterator();
						for (IType child : gType.children) knownTps.put(itr.next(), child);
					} else {
						Inferrer.fillDefaultTypeParam(tpBounds, knownTps);
					}

					fieldType = Inferrer.clearTypeParam(fSign.values.get(0), knownTps, tpBounds);
					break block;
				}
			}
			fieldType = field.fieldType();
			}
			result.add(field);

			if (i < 0) {
				_frType = fieldType;
				return null;
			}
			prevI = i+1;
			i = desc.indexOf("/", prevI);

			Type type = field.fieldType();
			if (type.isPrimitive()) {
				if (i < 0) {
					_frType = fieldType;
					return null;
				}
				// 不能解引用基本类型
				return "symbol.error.derefPrimitive:"+type;
			}

			clz = resolve(type);
			if (clz == null) return "symbol.error.noSuchClass:".concat(type.toString());
		}
	}
	public final IClass get_frBegin() {return _frBegin;}
	public final SimpleList<FieldNode> get_frChain() {return _frChain;}
	public final IType get_frType() {return _frType;}
	// optional chaining offset
	public final int get_frOffset() {return _frOffset;}
	//endregion
	//region [可重载] 静态导入 内部类
	public static final class Import {
		public final IClass owner;
		public final String method;
		public final Object prev;

		public static Import replace(@NotNull Expr node) {return new Import(null, null, Objects.requireNonNull(node));}
		public static Import staticCall(@NotNull IClass owner, @NotNull String name) {return new Import(Objects.requireNonNull(owner), Objects.requireNonNull(name), null);}
		public static Import virtualCall(@NotNull IClass owner, @NotNull String name, @Nullable Expr prev) {return new Import(Objects.requireNonNull(owner), Objects.requireNonNull(name), prev);}
		public static Import constructor(@NotNull IClass owner, @NotNull String name, @Nullable Expr prev) {return new Import(Objects.requireNonNull(owner), "<init>", Type.klass(owner.name()));}

		public Import(IClass owner, String method, Object prev) {
			this.owner = owner;
			this.method = method;
			this.prev = prev;
		}

		public Expr parent() {return (Expr) prev;}
	}

	// 动态导入对象
	@Nullable
	public Function<String, Import> dynamicMethodImport, dynamicFieldImport;

	// 这个对象在递归解析中继承
	public SimpleList<NestContext> enclosing = new SimpleList<>();

	/**
	 * 实用函数，获取导入一个类中所有字段的DynamicImport
	 * @param info 类型实例
	 * @param ref 如果不为空，那么用它作为基础导入非静态字段
	 * @param prev 之前的动态导入对象
	 */
	@NotNull
	public final Function<String, Import> getFieldDFI(IClass info, Variable ref, Function<String, Import> prev) {
		return name -> {
			var cl = getFieldList(info, name);
			if (cl != ComponentList.NOT_FOUND) {
				FieldResult result = cl.findField(this, ref == null ? ComponentList.IN_STATIC : 0);
				if (result.error == null) return Import.virtualCall(info, result.field.name(), ref == null ? null : new LocalVariable(ref));
			}

			return prev == null ? null : prev.apply(name);
		};
	}

	/**
	 * 处理字段的静态导入
	 * @param name 导入名称
	 * @return '导入'对象
	 */
	@Nullable
	public Import tryImportField(String name) {
		if (dynamicFieldImport != null) {
			Import result = dynamicFieldImport.apply(name);
			if (result != null) return result;
		}

		var nestHost = enclosing;
		for (int i = nestHost.size()-1; i >= 0; i--) {
			Import result = nestHost.get(i).resolveField(this, nestHost, i, name);
			if (result != null) return result;
		}

		_frChain.clear();
		var begin = importList.resolveField(this, name, _frChain);
		if (begin == null) return null;
		return Import.staticCall(begin, name);
	}

	/**
	 * 处理方法的静态导入
	 * @param name 导入名称
	 * @param args 参数，子类的重载可能用到
	 * @return '导入'对象
	 */
	@Nullable
	public Import tryImportMethod(String name, List<Expr> args) {
		if (dynamicMethodImport != null) {
			Import result = dynamicMethodImport.apply(name);
			if (result != null) return result;
		}

		var nestHost = enclosing;
		for (int i = nestHost.size()-1; i >= 0; i--) {
			Import result = nestHost.get(i).resolveMethod(this, nestHost, i, name);
			if (result != null) return result;
		}

		var owner = importList.resolveMethod(this, name);
		if (owner == null) return null;
		return Import.staticCall(owner, name);
	}
	// endregion
	//region BlockParser API
	/**
	 * 添加方法抛出的异常, 并报告错误
	 * @param type 异常类型
	 */
	public final void addException(IType type) {
		if (caster.checkCast(type, Types.RUNTIME_EXCEPTION).type >= 0) return;
		if (caster.checkCast(type, Types.ERROR).type >= 0) return;
		if (bp.addException(type)) return;

		var exceptions = (ClassListAttribute) method.getRawAttribute("Exceptions");
		if (exceptions != null) {
			if (type.genericType() == 0) {
				var parents = getHierarchyList(classes.getClassInfo(type.owner()));
				for (String s : exceptions.value) {
					if (parents.containsValue(s)) return;
				}
			} else {
				for (String s : exceptions.value) {
					if (caster.checkCast(type, Type.klass(s)).type >= 0) return;
				}
			}
		}
		report(classes.hasFeature(LavaFeatures.DISABLE_CHECKED_EXCEPTION) ? Kind.WARNING : Kind.ERROR, "lc.unReportedException", type);
	}

	public final Map<String, Variable> variables;
	public final Variable getVariable(String name) {return variables.get(name);}
	public final void loadVar(Variable v) {bp.loadVar(v);}
	public final void storeVar(Variable v) {bp.storeVar(v);}
	public final void assignVar(Variable v, Object constant) {bp.assignVar(v, constant);}
	public final Variable createTempVariable(IType type) {return bp.tempVar(type);}
	//endregion
	//region 类型转换和推断
	/**
	 * 执行类型转换语义检查并生成对应的中间表示（IR）
	 *
	 * <p>该方法功能如下：
	 * <ul>
	 *   <li>当转换类型等级 {@code cast.type} 低于 {@code lowest_limit} 时，触发编译错误</li>
	 *   <li>支持自动装箱/拆箱、数值精度转换等隐式转换规则</li>
	 *   <li>若涉及泛型类型，会进行类型擦除和通配符捕获检查（如 {@code List<String> → List<? extends CharSequence>}）</li>
	 * </ul>
	 *
	 * <p><b>转换类型等级</b>（完整定义见{@link TypeCast}）：
	 * <table>
	 * <tr><th>类型</th>        <th>常量</th>               <th>值</th> <th>语义</th></tr>
	 * <tr><td rowspan="4">成功</td>
	 *     <td>{@link TypeCast#UPCAST}</td>         <td>0</td> <td>类/接口直接向上转型</td></tr>
	 * <tr><td>{@link TypeCast#NUMBER_UPCAST}</td> <td>1</td> <td>数值宽化（如 int → long）</td></tr>
	 * <tr><td>{@link TypeCast#UNBOXING}</td>      <td>2</td> <td>拆箱操作（如 Integer → int）</td></tr>
	 * <tr><td>{@link TypeCast#BOXING}</td>        <td>3</td> <td>装箱操作（如 int → Integer）</td></tr>
	 * <tr><td rowspan="3">可恢复错误</td>
	 *     <td>{@link TypeCast#E_EXPLICIT_CAST}</td> <td>-1</td> <td>数值窄化（但是在JVM里都是int的情况）（如 int → char）</td></tr>
	 * <tr><td>{@link TypeCast#E_NUMBER_DOWNCAST}</td><td>-2</td><td>数值窄化（如 double → float）</td></tr>
	 * <tr><td>{@link TypeCast#E_DOWNCAST}</td>     <td>-3</td> <td>向下转型（如 Collection → ArrayList）</td></tr>
	 * <tr><td>致命错误</td>
	 *     <td>{@link TypeCast#E_OBJ2INT}等</td>    <td>≤-4</td><td>不可恢复的类型错误（如 String → int）</td></tr>
	 * </table>
	 *
	 * <p><b>转换代价（distance）规则 *主要用于{@link Inferrer}的方法重载优先级计算</b>：
	 * <ul>
	 *   <li>仅在成功转换时有效（type ≥ 0）</li>
	 *   <li>计算方式：继承链层级差 + 装箱/拆箱次数 + 数值精度转换次数</li>
	 *   <li>示例：{@code byte → Integer} 转换代价为 2（1次装箱 + 1次数值宽化）</li>
	 * </ul>
	 *
	 * <p><b>示例</b>：
	 * <pre>{@code
	 * // 自动装箱+数值转换
	 * castTo(byte, Integer) → BOXING(distance=2)
	 * // 向上转型+泛型
	 * castTo(List<String>, Collection<Object>) → UPCAST(distance=1)
	 * // 注意这里没有计算泛型内部参数的distance，因为不允许这种重载所以没必要
	 * // 好像给我加锅了，Stage3那边挺麻烦的？
	 *
	 * // 强制向下转型
	 * castTo(Object, String) → E_DOWNCAST
	 * // 强制数字窄化转换
	 * castTo(long, int) → E_NUMBER_DOWNCAST
	 * // 源类型小于等于int，实际类型小于源类型
	 * castTo(int, byte) → E_EXPLICIT_CAST
	 * // 致命错误
	 * castTo(String, int) → E_OBJ2INT
	 * }</pre>
	 *
	 * @param lowest_limit 允许的最低转换等级
	 *                    <ul>
	 *                      <li>{@link TypeCast#E_NEVER}：允许任何转换，即便不合法</li>
	 *                      <li>{@link TypeCast#E_DOWNCAST}：允许生成可行的转换</li>
	 *                      <li>{@link TypeCast#UPCAST}：仅允许向上转换</li>
	 *                    </ul>
	 * @return 转换描述对象，包含：
	 * <ul>
	 *   <li>{@link TypeCast.Cast#type}：转换类型代码</li>
	 *   <li>{@link TypeCast.Cast#distance}：转换代价（越小越优先）</li>
	 *   <li>{@link TypeCast.Cast#write(roj.asm.insn.CodeWriter)}：生成字节码的方法</li>
	 *   <li>{@link TypeCast.Cast#getType1()}：获取泛型目标类型（用于类型推断）</li>
	 * </ul>
	 *
	 * @see TypeCast.Cast 查看转换描述对象的完整结构
	 */
	public final TypeCast.Cast castTo(@NotNull IType from, @NotNull IType to, @Range(from = -8, to = 0) int lowest_limit) {
		var cast = caster.checkCast(from, to);
		if (cast.type < lowest_limit) report(Kind.ERROR, "typeCast.error."+cast.type, from, to);
		return cast;
	}

	private static final AbstractList<IType> ANY_GENERIC_LIST = new AbstractList<>() {
		@Override public IType get(int index) {return Asterisk.anyType;}
		@Override public int size() {return 0;}
	};
	/**
	 * 解析泛型实例继承关系，获取目标类型在泛型上下文中的实际类型参数.
	 *
	 * <p>当{@code targetType}出现在{@code typeInst}的继承链（父类或接口）中时，返回其实际绑定的类型参数。
	 * 该方法的处理逻辑遵循以下优先级：
	 * <ol>
	 *   <li><b>继承链检查</b> - 若{@code targetType}不在继承链中，或目标类型非泛型，返回{@code null}
	 *     <pre>{@code inferGeneric(A<x,y>, D) → null}</pre></li>
	 *   <li><b>具体类型解析</b> - 若目标类型参数已具化，返回具体类型列表
	 *     <pre>{@code inferGeneric(A<x,y>, B) → [String]}</pre></li>
	 *   <li><b>原始类型处理</b> - 若{@code typeInst}为原始类型或无泛型参数（如A或A&lt;>），返回目标类型参数的上界
	 *     <pre>{@code inferGeneric(A, C) → [Number]}</pre></li>
	 *   <li><b>动态变量解析</b> - 若目标类型参数依赖{@code typeInst}的泛型变量，返回解析后的变量引用
	 *     <pre>{@code inferGeneric(A<x,y>, C) → [x]}</pre></li>
	 * </ol>
	 *
	 * <p><b>示例场景</b>：对于类型声明 {@code A<K extends Number, V> extends B<String> implements C<K>}
	 * <ul>
	 *   <li>若目标类型为父类{@code B}，返回具体类型参数 {@code [String]}</li>
	 *   <li>若目标类型为接口{@code C}且{@code typeInst}为原始类型，返回边界类型 {@code [Number]}</li>
	 *   <li>若{@code typeInst}携带泛型参数{@code A<x,y>}，返回变量引用 {@code [x]}</li>
	 * </ul>
	 *
	 * @param typeInst    待解析的泛型实例（支持具化泛型、原始类型、通配符泛型）
	 * @param targetType 需要解析的目标类型全限定名（如"java/util/List"）
	 *
	 * @return 目标类型的实际泛型参数列表。可能返回：
	 * <ul>
	 *   <li>{@code null} - 目标类型不存在于继承链或非泛型类型</li>
	 *   <li>具化类型列表（如 {@code [String, Integer]}）</li>
	 * </ul>
	 *
	 * @throws IllegalStateException 当继承关系数据异常时抛出（targetType并非typeInst的父类/接口）
	 * @implNote 若{@code targetType}在类路径中不存在，将通过{@link #report}方法抛出编译错误
	 */
	@Nullable
	public final List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType) {
		var info = classes.getClassInfo(typeInst.owner());

		List<IType> bounds = null;
		try {
			bounds = classes.getTypeArgumentsFor(info, targetType);
		} catch (ClassNotFoundException e) {
			report(Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
		}

		if (bounds == null || bounds.getClass() == SimpleList.class) return bounds;

		// bounds是SimpleList$1，代表其中含有类型参数，需要动态解析
		List<IType> children;
		if (typeInst instanceof Generic g) {
			children = g.children;

			if (g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric) {
				// 20250411 这里应该用目标类型……之前写错了，现在是擦除到上界，因为anyGeneric嘛
				info = classes.getClassInfo(targetType);
				var sign = info.getAttribute(info.cp, Attribute.SIGNATURE);

				MyHashMap<String, IType> realType = Helpers.cast(tmpMap1);
				Inferrer.fillDefaultTypeParam(sign.typeParams, realType);

				bounds = new SimpleList<>(bounds);
				for (int i = 0; i < bounds.size(); i++) {
					bounds.set(i, Inferrer.clearTypeParam(bounds.get(i), realType, sign.typeParams));
				}

				return bounds;
			}
		} else {
			children = ANY_GENERIC_LIST;
		}

		return inferGeneric0(info, children, targetType);
	}
	// B <T1, T2> extends A <T1> implements Z<T2>
	// C <T3> extends B <String, T3>
	// 假设我要拿A的类型，那就要先通过已知的C（params）推断B，再推断A
	private List<IType> inferGeneric0(ClassNode typeInst, List<IType> params, String target) {
		Map<String, IType> visType = new MyHashMap<>();

		int depthInfo = getHierarchyList(typeInst).getOrDefault(target, -1);
		if (depthInfo == -1) throw new IllegalStateException("无法从"+typeInst.name()+"<"+params+">推断"+target);

		loop:
		for(;;) {
			var g = typeInst.getAttribute(typeInst.cp, Attribute.SIGNATURE);

			int i = 0;
			visType.clear();
			for (String s : g.typeParams.keySet())
				visType.put(s, params.get(i++));

			// < 0 => flag[0x80000000] => 从接口的接口继承，而不是父类的接口
			i = depthInfo < 0 ? 1 : 0;
			for(;;) {
				IType type = g.values.get(i);
				if (target.equals(type.owner())) {
					// rawtypes
					if (type.genericType() == IType.STANDARD_TYPE) return Collections.emptyList();

					var rubber = (Generic) Inferrer.clearTypeParam(type, visType, g.typeParams);
					return rubber.children;
				}

				typeInst = classes.getClassInfo(type.owner());
				depthInfo = getHierarchyList(typeInst).getOrDefault(target, -1);
				if (depthInfo != -1) {
					var rubber = (Generic) Inferrer.clearTypeParam(type, visType, g.typeParams);
					params = rubber.children;
					continue loop;
				}

				i++;
				assert i < g.values.size();
			}
		}
	}

	/**
	 * 判断{@code instClass}是否属于{@code testClass}的子类（或自身）
	 *
	 * <p>该方法能处理接口和数组等，传入的参数为内部(/)全限定名称
	 */
	public final boolean instanceOf(String testClass, String instClass) {
		IClass info = classes.getClassInfo(testClass);
		if (info == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", testClass);
			return false;
		}

		return classes.getHierarchyList(info).containsValue(instClass);
	}

	/**
	 * 计算两个类型的最近公共父类型
	 *
	 * <p>算法支持各种类型，包括但不限于
	 * <ol>
	 *   <li><b>基本类型</b> - 优先返回数值公共父类（如int+double→Number）</li>
	 *   <li><b>数组</b> - 不同维度数组返回Cloneable/Object</li>
	 *   <li><b>泛型</b>：
	 *     <ul>
	 *       <li>泛型参数取交集（List&lt;String>+List&lt;Integer>→List&lt;?>）</li>
	 *       <li>通配符边界处理（? super T + ? extends S → Object）</li>
	 *     </ul>
	 *   </li>
	 * </ol>
	 *
	 * @return 最近公共父类型。特殊情形：
	 *         <ul>
	 *           <li>若存在原始类型 → 返回擦除后的原始类型</li>
	 *           <li>若存在不匹配的泛型参数 → 触发错误并返回首个类型</li>
	 *         </ul>
	 */
	public final IType getCommonParent(IType a, IType b) {
		if (a.equals(b)) return a;

		if (a.genericType() >= IType.ASTERISK_TYPE) {
			a = ((Asterisk) a).getBound();
			if (a == null) return b.isPrimitive() ? TypeCast.getWrapper(b) : b;
		}
		if (b.genericType() >= IType.ASTERISK_TYPE) {
			b = ((Asterisk) b).getBound();
			if (b == null) return a.isPrimitive() ? TypeCast.getWrapper(a) : a;
		}

		int capa = TypeCast.getDataCap(a.getActualType());
		int capb = TypeCast.getDataCap(b.getActualType());
		// 双方都是数字
		if ((capa&7) != 0 && (capb&7) != 0) return Type.klass("java/lang/Number");
		// 没有任何一方是对象 (boolean | void)
		if ((capa|capb) < 8) return Types.OBJECT_TYPE;

		if (a.isPrimitive()) a = TypeCast.getWrapper(a);
		if (b.isPrimitive()) b = TypeCast.getWrapper(b);

		// noinspection all
		if (a.array() != b.array()) {
			// common parent of Object[][] | Object[]
			return Math.min(a.array(), b.array()) == 0
				? Types.OBJECT_TYPE
				: Type.klass("java/lang/Cloneable");
		}

		IClass infoA = classes.getClassInfo(a.owner());
		if (infoA == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", a);
			return a;
		}
		IClass infoB = classes.getClassInfo(b.owner());
		if (infoB == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", b);
			return a;
		}

		String commonParent = getCommonParent(infoA, infoB);
		assert commonParent != null;

		var info = classes.getClassInfo(commonParent);

		int extendType = a instanceof Generic ga ? ga.extendType : Generic.EX_NONE;
		int extendType2 = b instanceof Generic gb ? gb.extendType : Generic.EX_NONE;
		if (extendType != extendType2) {
			boolean hasSuper = extendType == Generic.EX_SUPER || extendType2 == Generic.EX_SUPER;
			boolean hasExtends = extendType == Generic.EX_EXTENDS || extendType2 == Generic.EX_EXTENDS;

			// LCA(T, ? extends T) = ? extends T
			// LCA(T, ? super T) = ?
			// LCA(? extends, ? super T) = ?
			if (hasSuper) return Signature.any(); // 通配符类型
			if (hasExtends) extendType = Generic.EX_EXTENDS;
		}

		// 如果不是泛型类，那么直接返回
		if (extendType == Generic.EX_NONE && info.getRawAttribute("Signature") == null) {
			// 少创建对象
			if (info == infoA) return a;
			if (info == infoB) return b;
			return Type.klass(commonParent, a.array());
		}

		// 否则进行泛型推断
		// CP(List<String>, SimpleList<String>) => List<String>
		// CP(List<?>, SimpleList<String>) => List<?>
		// CP(List<String>, SimpleList<?>) => List<?>

		List<IType> aGeneric = inferGeneric(a, commonParent);
		List<IType> bGeneric = inferGeneric(b, commonParent);

		assert aGeneric != null && bGeneric != null && aGeneric.size() == bGeneric.size();

		//Arrays.asList也许也可以？
		List<IType> typeParams = SimpleList.asModifiableList(new IType[aGeneric.size()]);
		for (int i = 0; i < aGeneric.size(); i++) {
			IType aa = aGeneric.get(i);
			IType bb = bGeneric.get(i);
			typeParams.set(i, getCommonParent(aa, bb));
		}

		// TODO primitive generic
		// 	CP(ArrayList<int>, List<Integer>) => List<Integer>
		//  CP(List<int>, List<Integer>) => Collection<Integer>

		Generic generic = new Generic(commonParent, a.array(), extendType);
		generic.children = typeParams;
		return generic;
	}
	/**
	 * 返回ab两个类(Class)的共同父类
	 */
	public final String getCommonParent(IClass infoA, IClass infoB) {
		ToIntMap<String> tmp,
			listA = getHierarchyList(infoA),
			listB = getHierarchyList(infoB);

		if (listA.size() > listB.size()) {
			tmp = listA;
			listA = listB;
			listB = tmp;
		}

		String commonParent = infoA.name();
		int minIndex = listB.size();
		for (var entry : listA.selfEntrySet()) {
			String klass = entry.getKey();

			int val = listB.getOrDefault(klass, minIndex)&0x7FFF_FFFF;
			int j = val&0xFFFF;
			if (j < minIndex || (j == minIndex && val < minIndex)) {
				commonParent = klass;
				minIndex = j;
			}
		}
		return commonParent;
	}
	//endregion
	// region [可重载] 表达式语法扩展

	/**
	 * 解析时忽略这个类的泛型限制，用于某些编译器内部类，需要在泛型中保存可变参数
	 */
	protected boolean isGenericIgnore(ClassNode info) {return info.name().equals("roj/compiler/runtime/ReturnStack");}

	/**
	 * 获取ASM节点的注解
	 * @param type 类型
	 * @param node 节点，可能等于类型
	 * @param annotation 注解类型
	 * @param rt 是否为运行时可见注解
	 * @return 注解对象
	 */
	public Annotation getAnnotation(IClass type, Attributed node, String annotation, boolean rt) {
		return Annotation.find(Annotations.getAnnotations(type.cp(), node, rt), annotation);
	}

	private static final IntMap<String> EMPTY = new IntMap<>(0);
	static {Parser.registerCustomAttribute(MethodDefault.ID, MethodDefault::new);}
	/**
	 * 获取方法的默认参数索引表
	 *
	 * <p>映射参数位置到默认值表达式，用于支持如下语法：
	 * <pre>{@code
	 * void foo(int a, String b = "def") {}
	 * foo(1); // 使用b的默认值
	 * }</pre>
	 *
	 * @param klass  方法所属类
	 * @param method 目标方法
	 * @return 参数默认值索引表（参数位置 → 序列化表达式）
	 * @see #parseDefaultArgument(String) 反序列化方法
	 */
	public IntMap<String> getDefaultArguments(IClass klass, MethodNode method) {
		MethodDefault attr = method.getAttribute(klass.cp(), MethodDefault.ID);
		return attr != null ? attr.defaultValue : EMPTY;
	}
	/**
	 * 反序列化参数默认值
	 * @param defVal 序列化的ExprNode
	 * @return 反序列化结果，失败返回null
	 */
	@Nullable
	public Expr parseDefaultArgument(@Nullable String defVal) {
		if (defVal != null) {
			var tmpPrev = tmpList;
			tmpList = new SimpleList<>();
			try {
				return ep.deserialize(defVal).resolve(this);
			} catch (ParseException | ResolveException e) {
				e.printStackTrace();
			} finally {
				tmpList = tmpPrev;
			}
		}
		return null;
	}

	public static final int UNARY_PRE = 0, UNARY_POST = 65536;
	/**
	 * 操作符重载扩展点
	 *
	 * <p><b>重载优先级</b>：
	 * <ol>
	 *   <li>内置运算符语义（如数值运算）</li>
	 *   <li>用户重载实现（通过override此方法）</li>
	 *   <li>错误处理（类型不匹配）</li>
	 * </ol>
	 * 注意：重载是次低优先级，仅高于报错，无法覆盖默认行为！
	 *
	 * @param e1        左操作数表达式
	 * @param e2        右操作数表达式（一元运算符时为null）
	 * @param operator  运算符标识（一元和二元运算符列表见{@link Tokens}）
	 *                  <p>特例
	 *                  <ul>
	 *                    <li>{@link #UNARY_PRE 一元运算符}：前缀运算 ++a</li>
	 *                    <li>{@link #UNARY_POST} + 一元运算符：后缀运算 a++</li>
	 *                    <li>{@link Tokens#lBracket}：数组下标访问 a[b]</li>
	 *                    <li>{@link Tokens#lParen}：类型转换 (Type) expr</li>
	 *                    <li>{@link Tokens#rParen}：变量赋值 a = b</li>
	 *                  </ul>
	 * @return 自定义的表达式树，返回null表示报错
	 */
	@Nullable
	public Expr getOperatorOverride(@NotNull Expr e1, @Nullable Object e2, int operator) {return null;}

	/**
	 * 基本类型函数扩展点
	 *
	 * <p>用于为基本类型添加伪方法，例如：
	 * <pre>{@code
	 * getPrimitiveMethod(int.class, expr, args) → Integer#toHexString
	 * }</pre>
	 * 就可以使用{@code 4 .toHexString()}了
	 * 数字字面量后的一个空格是必须的，为了防止Tokenizer把小数点当成浮点数的一部分。
	 *
	 * @param type    基本类型（如int.class）
	 * @return 包含一些静态方法的类，这些方法的第一个参数必须是该基本类型，返回null表示报错
	 * @implNote 默认实现把基本类型包装类中的所有静态函数返回了
	 */
	@Nullable
	public IClass getPrimitiveMethod(IType type) {
		assert type.isPrimitive();
		return classes.getClassInfo(TypeCast.getWrapper(type).owner());
	}

	/**
	 * 常量传播扩展点
	 *
	 * <p>在编译期解析常量字段值，用于优化：
	 * <pre>{@code
	 * public static final int MAX = 100;
	 * int a = MAX; → 直接替换为100
	 * }</pre>
	 *
	 * @param klass      字段所属类
	 * @param field      目标字段
	 * @param fieldType  字段的类型（包含泛型信息）
	 * @return 常量表达式。
	 */
	@Nullable
	public Expr getConstantValue(IClass klass, FieldNode field, IType fieldType) {
		if (fieldDFS && klass != file && klass instanceof CompileUnit cu) {
			SimpleList<NestContext> bak = enclosing;
			try {
				next();
				next.enclosing = new SimpleList<>();
				cu.S3_DFSField();
			} catch (ParseException e) {
				Helpers.athrow(e);
			} finally {
				next.enclosing = bak;
				prev();
			}
		}

		var cv = field.getAttribute(klass.cp(), Attribute.ConstantValue);
		if (cv == null) return null;

		var c = switch (cv.c.type()) {
			case Constant.INT -> CEntry.valueOf(((CstInt) cv.c).value);
			case Constant.FLOAT -> CEntry.valueOf(((CstFloat) cv.c).value);
			case Constant.LONG -> CEntry.valueOf(((CstLong) cv.c).value);
			case Constant.DOUBLE -> CEntry.valueOf(((CstDouble) cv.c).value);
			case Constant.CLASS -> cv.c;
			case Constant.STRING -> cv.c.getEasyCompareValue();
			default -> throw new IllegalArgumentException("Illegal ConstantValue "+cv.c);
		};
		return Expr.constant(fieldType, c);
	}
	// endregion
	//region 递归状态管理
	private LocalContext prev, next;

	private static final ThreadLocal<LocalContext> FTL = new ThreadLocal<>();

	public static void set(@Nullable LocalContext ctx) {
		if (ctx != null) ctx.prev = ctx.next = null;
		FTL.set(ctx);
	}

	public static LocalContext get() {return FTL.get();}

	public static LocalContext next() {
		var ctx = FTL.get();
		if (ctx.next == null) {
			var next = ctx.classes.createLocalContext();
			next.enclosing = ctx.enclosing;

			ctx.next = next;
			next.prev = ctx;

			FTL.set(next);
			return next;
		}
		return ctx;
	}
	public static void prev() {set(Objects.requireNonNull(get().prev));}
	//endregion
	// region 缓存
	// GenericDecl, Invoke临时, 参数名称
	public SimpleList<String> tmpList = new SimpleList<>();
	// 暂存Switch的标签
	public final SimpleList<String> tmpList2 = new SimpleList<>();
	// 注解暂存
	public final SimpleList<AnnotationPrimer> tmpAnnotations = new SimpleList<>();
	// S2实现检查临时, 注解检查临时, 模块重复检查临时
	public final MyHashSet<String> tmpSet = new MyHashSet<>();
	public MyHashSet<String> getTmpSet() {tmpSet.clear();return tmpSet;}

	// 注解检查临时
	public final MyHashMap<String, Object> tmpMap1 = new MyHashMap<>(), tmpMap2 = new MyHashMap<>();

	// toClassRef, report, 等等
	private final CharList tmpSb = new CharList();
	public CharList getTmpSb() {tmpSb.clear();return tmpSb;}

	// S2实现检查临时
	public NameAndType tmpNat = new NameAndType();

	// try with resource临时
	private Label[] tmpLabels = new Label[8];
	public Label[] getTmpLabels(int i) {
		if (i > tmpLabels.length) return tmpLabels = new Label[i];
		return tmpLabels;
	}
	// endregion
}