package roj.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;
import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.ClassListAttribute;
import roj.asm.attr.InnerClasses;
import roj.asm.cp.*;
import roj.asm.insn.Label;
import roj.asm.type.*;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.*;
import roj.compiler.api.Compiler;
import roj.compiler.api.MethodDefault;
import roj.compiler.api.Types;
import roj.compiler.api.ValueBased;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.MethodParser;
import roj.compiler.ast.expr.*;
import roj.compiler.diagnostic.Diagnostic;
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

import static roj.compiler.JavaTokenizer.assign;
import static roj.compiler.LavaCompiler.*;

/**
 * CompileContext 解析(Parse/Resolve)环境 线程本地+递归
 * 1. 错误处理
 * 2. 类型转换，解析类型（扩展到全限定名，然后变成ASM数据，同时做权限检查）  (代理TypeCast)
 * 3. 解析符号引用 Class Field Method  （代理TypeResolver和Inferrer）
 * 4. instanceof，getCommonParent 最近共同祖先
 * 5. 访问权限检查和final赋值检查
 */
public class CompileContext {
	public final LavaCompiler compiler;

	public final ExprParser ep;
	public final MethodParser bp;
	public final JavaTokenizer lexer;

	public final TypeCast caster = new TypeCast();
	public final Inferrer inferrer = new Inferrer(this);

	private ImportList importList;
	public final HashMap<String, ClassNode> importCache = new HashMap<>(), importCacheMethod = new HashMap<>();
	public final HashMap<String, Object[]> importCacheField = new HashMap<>();

	public CompileUnit file;
	public MethodNode method;
	// 功能应该自解释了
	// 后两个：如果没显式调用super就插入默认构造器，如果没调用this就插入GlobalInit
	public boolean inStatic, inConstructor, noCallConstructor, thisConstructor;
	// 递归构造器检查
	private final HashMap<MethodNode, MethodNode> constructorChain = HashMap.withCustomHasher(Hasher.identity());

	// stage2.1解析方法和字段的基础类型时不生成警告
	public boolean reportPseudoType;

	/**
	 * 是否在return语句中
	 * @see Invoke#write(MethodWriter, TypeCast.Cast) Tail Call Elimination 尾递归优化
	 * @see NewArray#resolve(CompileContext) MultiReturn语法
	 */
	public boolean inReturn;
	/**
	 * this的槽位 暂时用不上
	 */
	public final int thisSlot = 0;
	/**
	 * expressionBeforeThisOrSuper()检查
	 * 只有在未使用this类型前才能调用this()或super()
	 * 不要求必须是构造器的第一个语句
	 * 同时用于lambda的自动静态化
	 * * 具有传递性
	 */
	public boolean thisUsed;
	/**
	 * 跳过枚举的构造器调用检测，抽象枚举类的构造器不是private的
	 * @see Invoke#resolve(CompileContext) int check_flag
	 */
	public boolean enumConstructor;
	/**
	 * 全局构造器插入点的segmentId
	 */
	public int globalInitInsertTo;

	// lambda方法的序号
	protected int syntheticMethodId;
	public String lambdaName() { return method.name()+"^lmd^"+(syntheticMethodId++);}
	public String accessorName() {return "access^"+(syntheticMethodId++);}

	// lambda用到，阻止预定义ID的变量被直接序列化入SolidBlock
	public boolean isArgumentDynamic;

	// stage3 constant resolution
	public boolean fieldDFS;

	// 宽容的泛型转换检查 - 可能在运行时造成问题
	public boolean lenientGenericCast;

	public CompileContext(LavaCompiler ctx) {
		this.compiler = ctx;
		this.lexer = ctx.createLexer();
		this.ep = new ExprParser(this);
		this.bp = ctx.createMethodParser(this);
		this.variables = bp.getVariables();
		this.caster.context = ctx;
	}

	public MethodWriter createMethodWriter(ClassNode file, MethodNode node) {return new MethodWriter(file, node, compiler.hasFeature(Compiler.EMIT_LOCAL_VARIABLES), this);}

	@NotNull public ToIntMap<String> getHierarchyList(ClassDefinition info) {return compiler.getHierarchyList(info);}
	@NotNull public ComponentList getFieldList(ClassDefinition info, String name) {return compiler.getFieldList(info, name);}
	@NotNull public ComponentList getMethodList(ClassDefinition info, String name) {return compiler.getMethodList(info, name);}

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
		this.reportedType.clear();
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
				constructorFields = new HashSet<>(Hasher.identity());
				constructorFields.addAll(file.finalFields);
			} else {
				constructorFields = file.finalFields;
			}
		}
		method = node;

		inReturn = false;
		thisUsed = false;

		syntheticMethodId = 0;
		globalInitInsertTo = 0;

		thisConstructor = false;

		hasError = false;
		errorReportIndex = -1;
	}

	public String currentCodeBlockForReport() {return "[symbol.type,\" \","+file.name()+"]";}

	//region 错误报告

	// 当前MethodNode是否编译失败
	protected boolean hasError;
	public boolean hasError() {return hasError;}

	public BiConsumer<String, Object[]> errorCapture;
	public int errorReportIndex;
	private Set<Object> reportedType = new HashSet<>();

	public final void report(Kind kind, String message) {report(kind, message, ArrayCache.OBJECTS);}
	public final void report(Kind kind, String message, Object... args) {
		if (errorReportIndex >= 0) report(errorReportIndex, kind, message, args);
		else report(lexer.current().pos(), lexer.index, kind, message, args);
	}

	public final void report(Expr pos, Kind kind, String message) {report(pos, kind, message, ArrayCache.OBJECTS);}
	public void report(Expr pos, Kind kind, String message, Object... args) {
		if (checkArgument(args) || checkCapture(message, args)) return;
		int start = pos.getWordStart();
		int end = pos.getWordEnd();
		if (end == 0) start = end = lexer.index;
		hasError |= compiler.report(new Diagnostic(file, kind, start, end, message, args));
	}
	public void report(int start, int end, Kind kind, String message, Object... args) {
		hasError |= compiler.report(new Diagnostic(file, kind, start, end, message, args));
	}

	public void report(int pos, Kind kind, String message, Object... args) {
		if (checkArgument(args) || checkCapture(message, args)) return;
		hasError |= compiler.report(new Diagnostic(file, kind, pos, pos, message, args));
	}

	private static boolean checkArgument(Object[] args) {
		for (Object arg : args) {
			if (arg == NaE.UNRESOLVABLE) return true;
		}
		return false;
	}

	private boolean checkCapture(String message, Object[] args) {
		if (errorCapture != null) {errorCapture.accept(message, args);return true;}
		return false;
	}

	public final void reportNoSuchType(Kind kind, Object owner) {
		if (reportedType.add(owner))
			report(kind, "symbol.noSuchSymbol", "symbol.type", owner, currentCodeBlockForReport());}
	public final void reportNoSuchType(Expr node, Kind kind, Object owner) {
		if (reportedType.add(owner))
			report(node, kind, "symbol.noSuchSymbol", "symbol.type", owner, currentCodeBlockForReport());
	}
	//endregion
	// region 访问权限和final字段赋值检查
	@SuppressWarnings("fallthrough")
	public void assertAccessible(ClassDefinition type) {
		if (type == file) return;

		int modifier = type.modifier();
		InnerClasses.Item item = compiler.getInnerClassInfo(type).get(type.name());
		if (item != null) {
			modifier = item.modifier;
			checkAccessModifier(modifier, type, null, "symbol.accessDenied.type", true);
		} else {
			switch ((modifier&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE))) {
				default: report(Kind.ERROR, "semantic.resolution.illegalModifier", Integer.toHexString(modifier));
				case Opcodes.ACC_PUBLIC: return;
				case 0: if (!ClassUtil.arePackagesSame(type.name(), file.name()))
					report(Kind.ERROR, "symbol.accessDenied.type", type.name(), "package-private", file.name());
			}
		}
	}
	// 这里不会检测某些东西 (override, static has been written等)
	public boolean checkAccessible(ClassDefinition type, Member node, boolean staticEnv, boolean report) {
		if (staticEnv && (node.modifier()&Opcodes.ACC_STATIC) == 0) {
			if (report) report(Kind.ERROR, "symbol.nonStatic.symbol", type.name(), node.name(), node instanceof FieldNode ? "symbol.field" : "invoke.method");
			return false;
		}

		return type == file || checkAccessModifier(node.modifier(), type, node.name(), "symbol.accessDenied.symbol", report);
	}
	private boolean checkAccessModifier(int flag, ClassDefinition type, String node, String message, boolean report) {
		String modifier;
		switch ((flag & (Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE))) {
			default: throw ResolveException.ofIllegalInput("semantic.resolution.illegalModifier", Integer.toHexString(flag));
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

	private HashSet<FieldNode> constructorFields;
	public final void checkSelfField(FieldNode node, boolean write) {
		boolean constructor = inConstructor;
		if (inStatic) {
			if ((node.modifier()&Opcodes.ACC_STATIC) == 0)
				report(Kind.ERROR, "symbol.nonStatic.symbol", file.name(), node.name(), "symbol.field");
		} else if ((node.modifier()&Opcodes.ACC_STATIC) != 0) {
			constructor = false;
		}

		if ((node.modifier()&Opcodes.ACC_FINAL) != 0) {
			if (write) {
				if (constructor) {
					if (!constructorFields.remove(node)) {
						report(Kind.ERROR, "symbol.field.writeAfterWrite", file.name(), node.name());
					}
				} else {
					report(Kind.ERROR, "symbol.field.writeFinal", file.name(), node.name());
				}
			} else {
				if (constructor) {
					if (constructorFields.contains(node)) {
						report(Kind.ERROR, "symbol.field.readBeforeWrite", file.name(), node.name());
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
		if (!callee.owner().equals(file.name())) return;

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
	public ComponentList getMethodListOrReport(ClassDefinition type, String name, Expr callerForDebug) {
		var list = compiler.getMethodList(type, name);
		if (list == ComponentList.NOT_FOUND) {
			int argc = callerForDebug instanceof Invoke a ? a.getArgumentCount() : Integer.MAX_VALUE;
			report(callerForDebug, Kind.ERROR, "symbol.noSuchSymbol", name.equals("<init>") ? "invoke.constructor" : "invoke.method", name, "[symbol.type,\" \","+type.name()+"]", reportSimilarMethod(type, name, argc));
		}
		return list;
	}
	private String reportSimilarMethod(ClassDefinition type, String method, int argc) {
		var similar = new ArrayList<String>();
		loop:
		for (var entry : compiler.link(type).getMethods(compiler).entrySet()) {
			for (MethodNode node : entry.getValue().getMethods()) {
				int parSize = node.parameters().size();
				if (argc < parSize - ((node.modifier & Opcodes.ACC_VARARGS) != 0 ? 1 : 0)) continue loop;
			}

			checkSimilarity(method, entry.getKey(), similar);
		}

		if (similar.isEmpty()) return "";

		var sb = getTmpSb().append("symbol.similar:[invoke.method,\"");
		sb.append(TextUtil.join(similar, "\n    "));
		return sb.append("\"]").toString();
	}
	private String reportSimilarField(ClassDefinition type, String field) {
		var similar = new ArrayList<String>();
		for (var entry : compiler.link(type).getFields(compiler).entrySet()) {
			checkSimilarity(field, entry.getKey(), similar);
		}

		if (similar.isEmpty()) return "";

		var sb = getTmpSb().append("symbol.similar:[symbol.field,\"");
		sb.append(TextUtil.join(similar, "\n    "));
		return sb.append("\"]").toString();
	}

	//Pattern WORD = Pattern.compile("_+|(?>[A-Z])[a-z]+");
	private static void checkSimilarity(String method, String candidate, ArrayList<String> similar) {
		int len = method.length();
		int editThreshold = Math.max(2, (len + 1) / 2);

		// 条件1：检查编辑距离
		if (TextUtil.editDistance(method, candidate) <= editThreshold) {
			similar.add(candidate);
		}

		// 条件2：加权连续匹配相似度（解决换位问题）
		else if (len >= 3) {
			double weightedSimilarity = TextUtil.weightedContiguousSimilarity(method, candidate);
			if (weightedSimilarity > 0.65) similar.add(candidate);
		}
	}

	/**
	 * 将一个全限定名称或短名称解析到其类型实例.
	 * 通过{@link ImportList#resolve(CompileContext, String)}，
	 * 并进行导入限制的检查
	 * @param klass 短名称
	 */
	@Nullable
	public final ClassNode resolve(String klass) { return importList.resolve(this, klass); }
	/**
	 * 进行导入限制的完整检查, 防止通过返回值或字段类型引入不允许的类
	 * @param type 类名
	 */
	public void checkTypeRestriction(@Nullable String type) {
		// 检查type是否已导入
		if (type != null && importList.isRestricted() && importList.checkRestriction(compiler.resolve(type)) == null) {
			// FIXME 这个检查和别名导入有冲突
			report(Kind.ERROR, "semantic.feature.import.restricted", type);
		}
	}

	/**
	 * 将(已经resolveType的)类型表示解析到其类型实例，支持数组
	 * @param type 类型
	 */
	@Nullable
	public final ClassNode resolve(IType type) { return type.array() > 0 ? compiler.resolveArray(type) : compiler.resolve(type.owner()); }
	/**
	 * 解析一个类型表示, 包括将它的名称解析为全限定名称, 对泛型参数作出限制, 实现虚拟泛型等
	 * @return 大部分时间和输入参数相同 有些Magic会被特殊处理
	 */
	public final IType resolveType(IType type) {
		if (type.genericType() == 0 ? type.rawType().type != Type.CLASS : type.genericType() != 1) return type;

		// 不预先检查全限定名，适配package-restricted模式
		var info = resolve(type.owner());
		if (info == null) {
			reportNoSuchType(Kind.ERROR, type.owner());
			return type;
		}
		type.owner(info.name());

		if (reportPseudoType && info.parent() == null && !info.name().equals("java/lang/Object")) {
			var vb = (ValueBased) info.getAttribute(ValueBased.NAME);
			if (vb != null) {
				report(Kind.WARNING, "pseudoType.cast");
				return vb.exactType;
			} else {
				report(Kind.ERROR, "pseudoType.pseudo");
				return type;
			}
		}

		Signature sign = info.getAttribute(info.cp(), Attribute.SIGNATURE);
		int count = sign == null ? 0 : sign.typeParams.size();

		int typeFlag = compiler.getTypeFlag(info);

		if (type.array() != 0 && (typeFlag&TF_NOARRAY) != 0) {
			report(Kind.ERROR, "expr.newArray.generic");
		}

		if (type.genericType() == IType.GENERIC_TYPE) {
			Generic type1 = (Generic) type;
			List<IType> params = type1.children;

			if (params.size() != count && (typeFlag & TF_ANYARGC) == 0) {
				if (count == 0) report(Kind.ERROR, "symbol.generic.paramCount.0", type.rawType());
				else if (params.size() != 1 || params.get(0) != Asterisk.anyGeneric) {
					report(Kind.ERROR, "symbol.generic.paramCount", type.rawType(), params.size(), count);
					return type;
				}
			}

			if (sign == null) return type;
			var itr = sign.typeParams.values().iterator();

			for (int i = 0; i < params.size(); i++) {
				IType param = resolveType(params.get(i));
				if ((typeFlag & TF_ANYARGC) != 0) continue;

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
				} else if (param.isPrimitive() && (typeFlag & TF_PRIMITIVE) == 0) {
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
					report(Kind.ERROR, "当前版本暂时未实现基本类型泛型");
				}
			}

			//TODO not tested yet
			// HashMap<K,V>.Entry<Z>
			// HashMap.Entry<K,V>
			// HashMap<K,V>.Entry.SomeClass<Z>
			// class G1<T> { class G2 { class G3<T2> {} } }
			GenericSub x = type1.sub;
			while (x != null) {
				var ic = compiler.getInnerClassInfo(info).get(x.owner);
				if (ic == null || (info = compiler.resolve(ic.self)) == null) {
					report(Kind.ERROR, "symbol.noSuchSymbol", "symbol.type", x.owner, "[symbol.type,\""+type1+"\"]");
					break;
				}

				if ((ic.modifier &Opcodes.ACC_STATIC) != 0) {
					report(Kind.ERROR, "type.error.staticGenericSub", type1, ic.name);
				}

				sign = info.getAttribute(info.cp(), Attribute.SIGNATURE);
				count = sign == null ? 0 : sign.typeParams.size();

				if (x.children.size() != count) {
					if (count == 0) report(Kind.ERROR, "symbol.generic.paramCount.0", ic.self);
					else report(Kind.ERROR, "symbol.generic.paramCount", ic.self, x.children.size(), count);
				}

				x = x.sub;
			}

			// 250819 This is MAGIC!!!
			if (type.owner().equals("roj/compiler/api/Union")) {
				List<IType> children = type1.children;
				for (int i = 0; i < children.size(); i++) {
					var child = getHierarchyList(resolve(children.get(i)));

					for (int j = i+1; j < children.size(); j++) {
						if (child.containsKey(children.get(j).owner())) {
							report(Kind.ERROR, "block.switch.collisionType", children.get(i), children.get(j));
							break;
						}
					}
				}
				return Asterisk.oneOf(type1.children);
			}
		} else if (count > 0) {
			report((typeFlag & TF_NORAW) != 0 ? Kind.ERROR : Kind.WARNING, "symbol.generic.rawTypes", type);
		}
		return type;
	}
	//endregion
	//region DotGet字符串解析
	private final CharList frTemp = new CharList();
	private ClassDefinition _frBegin;
	private final ArrayList<FieldNode> _frChain = new ArrayList<>();
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
					var clz = compiler.resolve(sb);
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

				return "symbol.expression:[\""+desc+"\"]";
			}
			return "symbol.noSuchSymbol:[symbol.field,\""+desc+"\","+currentCodeBlockForReport()+"]";
		}
		return anySuccess;
	}
	/**
	 * 将(partial)DotGet格式的字符串, 解析为字段访问的组合
	 * @param clz 起始类型，即上个javadoc中的类型前缀部分
	 * @param generic 起始类型的泛型参数
	 * @param desc DotGet格式的字段访问字符串
	 */
	public final String resolveField(ClassDefinition clz, IType generic, CharList desc) { return resolveField(clz, generic, desc, 0); }

	private String resolveField(ClassDefinition clz, IType fieldType, CharList desc, int prevI) {
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
				return "symbol.noSuchSymbol:[symbol.field,"+name+",[symbol.type,\" \","+clz.name()+"],"+reportSimilarField(clz, name)+"]";
			}

			Signature cSign = clz.getAttribute(clz.cp(), Attribute.SIGNATURE);
			block:{
			if (cSign != null) {
				Signature fSign = field.getAttribute(clz.cp(), Attribute.SIGNATURE);
				if (fSign != null) {
					Map<String, List<IType>> tpBounds = cSign.typeParams;
					HashMap<String, IType> knownTps = new HashMap<>(cSign.typeParams.size());

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
				return "symbol.derefPrimitive:"+type;
			}

			clz = resolve(type);
			if (clz == null) return "symbol.noSuchClass:".concat(type.toString());
		}
	}

	public final ClassDefinition get_frBegin() {return _frBegin;}
	public final ArrayList<FieldNode> get_frChain() {return _frChain;}
	public final IType get_frType() {return _frType;}
	// optional chaining offset
	public final int get_frOffset() {return _frOffset;}
	//endregion
	//region [可重载] 静态导入 内部类
	public static final class Import {
		public ClassDefinition owner;
		public String name;
		public Object parent;
		public boolean isStatic;

		public static Import replace(@NotNull Expr node) {return new Import(null, null, Objects.requireNonNull(node));}
		public static Import staticCall(@NotNull ClassDefinition owner, @NotNull String name) {return new Import(Objects.requireNonNull(owner), Objects.requireNonNull(name), null);}
		public static Import virtualCall(@NotNull ClassDefinition owner, @NotNull String name, @Nullable Expr prev) {return new Import(Objects.requireNonNull(owner), Objects.requireNonNull(name), prev);}
		public static Import constructor(@NotNull ClassDefinition owner, @NotNull String name, @Nullable Expr prev) {return new Import(Objects.requireNonNull(owner), "<init>", Type.klass(owner.name()));}

		public Import(ClassDefinition owner, String name, Object parent) {
			this.owner = owner;
			this.name = name;
			this.parent = parent;
		}

		public Import(ClassDefinition owner, String name, boolean isStatic) {
			this.owner = owner;
			this.name = name;
			this.isStatic = isStatic;
		}

		public Expr parent() {return (Expr) parent;}

		public Import setName(ClassNode owner, String name, boolean isStatic) {
			this.owner = owner;
			this.name = name;
			this.isStatic = isStatic;
			return this;
		}
	}

	// 动态导入对象
	@Nullable
	public Function<String, Import> dynamicMethodImport, dynamicFieldImport;

	// 这个对象在递归解析中继承
	ArrayList<NestContext> enclosing = new ArrayList<>();

	/**
	 * 实用函数，获取导入一个类中所有字段的DynamicImport
	 * @param info 类型实例
	 * @param ref 如果不为空，那么用它作为基础导入非静态字段
	 * @param prev 之前的动态导入对象
	 */
	@NotNull
	public final Function<String, Import> getFieldDFI(ClassDefinition info, Variable ref, Function<String, Import> prev) {
		return name -> {
			var cl = getFieldList(info, name);
			if (cl != ComponentList.NOT_FOUND) {
				FieldResult result = cl.findField(this, ref == null ? ComponentList.IN_STATIC : 0);
				if (result.error == null) return Import.virtualCall(info, result.field.name(), ref == null ? null : new LocalVariable(ref));
			}

			return prev == null ? null : prev.apply(name);
		};
	}

	public String firstError;
	private Object[] firstErrorArgs;
	public void clearFirstError() {
		firstError = null;
		firstErrorArgs = null;
	}
	public void setImportError(String code, Object... args) {
		if (firstError != null) return;
		firstError = code;
		firstErrorArgs = args;
	}
	public void reportFirstError(Expr position) {
		report(position, Kind.ERROR, firstError, firstErrorArgs);
		clearFirstError();
	}

	/**
	 * 处理字段的静态导入
	 * @param name 导入名称
	 * @return '导入'对象
	 */
	@Nullable
	public Import tryImportField(String name) {
		clearFirstError();

		if (dynamicFieldImport != null) {
			Import result = dynamicFieldImport.apply(name);
			if (result != null) return result;
		}

		var nestHost = enclosing;
		for (int i = nestHost.size()-1; i >= 0; i--) {
			Import result = nestHost.get(i).resolveField(this, name);
			if (result != null) {
				for (int j = i+1; j < nestHost.size(); j++) {
					if (result.isStatic) break;
					result = nestHost.get(j).transferInto(this, result, name);
				}
				return result;
			}
		}

		_frChain.clear();
		return importList.resolveField(this, name, _frChain);
	}

	/**
	 * 处理方法的静态导入
	 * @param name 导入名称
	 * @param args 参数，子类的重载可能用到
	 * @return '导入'对象
	 */
	@Nullable
	public Import tryImportMethod(String name, List<Expr> args) {
		clearFirstError();

		if (dynamicMethodImport != null) {
			Import result = dynamicMethodImport.apply(name);
			if (result != null) return result;
		}

		var nestHost = enclosing;
		for (int i = nestHost.size()-1; i >= 0; i--) {
			Import result = nestHost.get(i).resolveMethod(this, name, args);
			if (result != null) {
				for (int j = i+1; j < nestHost.size(); j++) {
					if (result.isStatic) break;
					result = nestHost.get(j).transferInto(this, result, name);
				}
				return result;
			}
		}

		return importList.resolveMethod(this, name);
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
		if (compiler.hasFeature(Compiler.OMIT_CHECKED_EXCEPTION)) return;

		var exceptions = (ClassListAttribute) method.getAttribute("Exceptions");
		if (exceptions != null) {
			if (type.genericType() == 0) {
				var parents = getHierarchyList(compiler.resolve(type.owner()));
				for (String s : exceptions.value) {
					if (parents.containsKey(s)) return;
				}
			} else {
				for (String s : exceptions.value) {
					if (caster.checkCast(type, Type.klass(s)).type >= 0) return;
				}
			}
		}
		report(Kind.ERROR, "lc.unReportedException", type);
	}

	public final Map<String, Variable> variables;
	public final Variable getVariable(String name) {return variables.get(name);}
	public final void loadVar(Variable v) {bp.loadVar(v);}
	public final void storeVar(Variable v) {bp.storeVar(v);}
	public final void assignVar(Variable v, Object constant) {bp.assignVar(v, constant);}
	public final Variable createTempVariable(IType type) {return bp.tempVar(type);}
	//endregion
	//region 类型转换和推断
	public MethodResult inferGeneric(IType typeInst, MethodNode method) {
		List<IType> resolvedGeneric = inferGeneric(typeInst, method.owner());
		return inferrer.getGenericParameters(compiler.resolve(method.owner()), method,
				resolvedGeneric == null ? Type.klass(method.owner()) : new Generic(method.owner(), resolvedGeneric));
	}
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
	 *     <td>{@link TypeCast#IMPLICIT}</td> <td>-1</td> <td>数值窄化（但是在JVM里都是int的情况）（如 int → char）</td></tr>
	 * <tr><td>{@link TypeCast#LOSSY}</td><td>-2</td><td>数值窄化（如 double → float）</td></tr>
	 * <tr><td>{@link TypeCast#DOWNCAST}</td>     <td>-3</td> <td>向下转型（如 Collection → ArrayList）</td></tr>
	 * <tr><td>致命错误</td>
	 *     <td>{@link TypeCast#TO_PRIMITIVE}等</td>    <td>≤-4</td><td>不可恢复的类型错误（如 String → int）</td></tr>
	 * </table>
	 *
	 * <p><b>转换代价（distance）规则 *主要用于{@link Inferrer}的方法重载优先级计算</b>：
	 * <ul>
	 *   <li>仅在成功转换时有效（type ≥ 0）</li>
	 *   <li>计算方式：继承链层级差 + 装箱/拆箱次数 + 数值精度转换次数</li>
	 *   <li>示例：{@code byte → Integer} 转换代价为 2（1次装箱 + 1次数值宽化）</li>
	 * </ul>
	 * <b>本方法的返回值移除了Identity类型的转换代价以降低GC压力</b>
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
	 *                      <li>{@link TypeCast#IMPOSSIBLE}：允许任何转换，即便不合法</li>
	 *                      <li>{@link TypeCast#DOWNCAST}：允许生成可行的转换</li>
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
		return cast.intern();
	}

	/**
	 * @see LavaCompiler#inferGeneric(IType, String)
	 */
	@Nullable
	public final List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType) {return compiler.inferGeneric(typeInst, targetType, tmpMap1);}
	/**
	 * @see LavaCompiler#instanceOf(String, String)
	 */
	public final boolean instanceOf(String testClass, String instClass) {return compiler.instanceOf(testClass, instClass);}
	/**
	 * @see LavaCompiler#getCommonAncestor(IType, IType)
	 */
	public final IType getCommonParent(IType a, IType b) {return compiler.getCommonAncestor(a, b);}
	/**
	 * @see LavaCompiler#getCommonAncestor(ClassDefinition, ClassDefinition)
	 */
	public final String getCommonParent(ClassDefinition infoA, ClassDefinition infoB) {return compiler.getCommonAncestor(infoA, infoB);}
	//endregion

	// Cast | Expr
	public Object autoCast(Expr expr, IType toType) {
		var rType = expr.type();

		var allow = expr.isConstant() && toType.getActualType() == Type.CHAR;
		var cast = castTo(rType, toType, -8);
		if (cast.type < 0) {
			if (!rType.equals(rType = expr.minType())) {
				cast = castTo(rType, toType, allow ? TypeCast.IMPLICIT : 0);
			} else {
				var override = getOperatorOverride(expr, toType, assign);
				if (override != null) return override;

				report(Kind.ERROR, "typeCast.error."+cast.type, rType, toType);
			}
		}

		if (allow && cast.type == TypeCast.IMPLICIT) {
			var number = ((roj.config.data.CEntry)expr.constVal()).asInt();
			if (number < 0 || number > 65535)
				report(Kind.ERROR, "typeCast.error.-2", rType, toType);
		}

		return cast;
	}

	/**
	 * 统一函数：对常量进行范围内的自动窄化转换
	 * @see roj.compiler.ast.expr.Literal#write(MethodWriter, TypeCast.Cast)
	 */
	public IType writeCast(MethodWriter cw, Expr expr, IType toType) {
		var result = autoCast(expr, toType);

		if (result instanceof Expr override) {
			override.write(cw);
			return override.type();
		}

		var cast = (TypeCast.Cast) result;
		if (cast.type >= TypeCast.IMPLICIT) {
			expr.write(cw, cast);
		}

		return toType;
	}

	public IType writeVarCast(MethodWriter cw, Expr expr) {
		var type = expr.type();
		var cast = TypeCast.Cast.IDENTITY;

		if (type.genericType() == IType.CONCRETE_ASTERISK_TYPE) {
			var newType = ((Asterisk) type).getBound();
			// FIXME 在这里需要进行类型擦除吗，还是仅仅这样checkcast
			cast = caster.checkCast(type, newType);
		}

		// 将菱形泛型，例如 new ArrayList<>()，擦除到边界
		if (type instanceof Generic g && g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric) {
			ClassNode info = compiler.resolve(type.owner());
			g.children = info.getAttribute(info.cp, Attribute.SIGNATURE).getBounds();
		}

		expr.write(cw, cast);
		return type;
	}

	// region 辅助函数，允许但通常无需重载
	/**
	 * 获取ASM节点的注解
	 * @param type 类型
	 * @param node 节点，可能等于类型
	 * @param annotation 注解类型
	 * @param rt 是否为运行时可见注解
	 * @return 注解对象
	 */
	public Annotation getAnnotation(ClassDefinition type, Attributed node, String annotation, boolean rt) {
		return Annotation.find(Annotations.getAnnotations(type.cp(), node, rt), annotation);
	}

	private static final IntMap<String> EMPTY = new IntMap<>(0);
	static {Attribute.addCustomAttribute(MethodDefault.ID, MethodDefault::new);}
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
	public IntMap<String> getDefaultArguments(ClassDefinition klass, MethodNode method) {
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
			tmpList = new ArrayList<>();
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
	 * @param operator  运算符标识（一元和二元运算符列表见{@link JavaTokenizer}）
	 *                  <p>特例
	 *                  <ul>
	 *                    <li>{@link #UNARY_PRE 一元运算符}：前缀运算 ++a</li>
	 *                    <li>{@link #UNARY_POST} + 一元运算符：后缀运算 a++</li>
	 *                    <li>{@link JavaTokenizer#lBracket}：数组下标访问 a[b]</li>
	 *                    <li>{@link JavaTokenizer#lParen}：类型转换 (Type) expr</li>
	 *                    <li>{@link JavaTokenizer#rParen}：变量赋值 a = b</li>
	 *                  </ul>
	 * @return 自定义的表达式树，返回null表示报错
	 */
	@Nullable
	public Expr getOperatorOverride(@NotNull Expr e1, @Nullable Object e2, int operator) {return compiler.getOperatorOverride(this, e1, e2, operator);}

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
	public ClassDefinition getPrimitiveMethod(IType type) {
		assert type.isPrimitive();
		return compiler.resolve(TypeCast.getWrapper(type).owner());
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
	public Expr getConstantValue(ClassDefinition klass, FieldNode field, IType fieldType) {
		if (fieldDFS && klass != file && klass instanceof CompileUnit cu) {
			ArrayList<NestContext> bak = enclosing;
			try {
				push();
				next.enclosing = new ArrayList<>();
				cu.S3_DFSField();
			} catch (ParseException e) {
				Helpers.athrow(e);
			} finally {
				next.enclosing = bak;
				pop();
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
	private CompileContext prev, next;

	private static final ThreadLocal<CompileContext> FTL = new ThreadLocal<>();

	public static void set(@Nullable CompileContext ctx) {
		if (ctx != null) ctx.next = null;
		FTL.set(ctx);
	}

	public static CompileContext get() {return FTL.get();}

	public static CompileContext push() {
		var ctx = FTL.get();
		CompileContext next = ctx.next;
		if (next == null) {
			ctx.next = next = ctx.compiler.createContext();
			next.enclosing = ctx.enclosing;
			next.prev = ctx;
		}

		FTL.set(next);
		return next;
	}
	public static void pop() {set(Objects.requireNonNull(get().prev, "stack is empty"));}

	public void pushNestContext(NestContext context) {if (enclosing.size() > 10)report(Kind.INCOMPATIBLE, "lc.nestTooDeep");enclosing.add(context);}
	public void popNestContext() {enclosing.pop().onPop();}

	@UnmodifiableView public ArrayList<NestContext> enclosingContext() {return enclosing;}
	public int pushEnclosingContext() {return enclosing.size();}
	public void popEnclosingContext(int backup) {enclosing.removeRange(backup, enclosing.size());}
	//endregion
	// region 缓存
	// GenericDecl, Invoke临时, 参数名称
	public ArrayList<String> tmpList = new ArrayList<>();
	// 暂存Switch的标签
	public final ArrayList<String> tmpList2 = new ArrayList<>();
	// 注解暂存
	public final ArrayList<AnnotationPrimer> tmpAnnotations = new ArrayList<>();
	// S2实现检查临时, 注解检查临时, 模块重复检查临时
	public final HashSet<String> tmpSet = new HashSet<>();
	public HashSet<String> getTmpSet() {tmpSet.clear();return tmpSet;}

	// 注解检查临时
	public final HashMap<String, Object> tmpMap1 = new HashMap<>(), tmpMap2 = new HashMap<>();

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