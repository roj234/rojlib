package roj.compiler.context;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrClassList;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.*;
import roj.asm.util.ClassUtil;
import roj.asm.visitor.Label;
import roj.asmx.mapper.util.NameAndType;
import roj.collect.*;
import roj.compiler.JavaLexer;
import roj.compiler.LavaFeatures;
import roj.compiler.api.MethodDefault;
import roj.compiler.api.Types;
import roj.compiler.api.ValueBased;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.Variable;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.LocalVariable;
import roj.compiler.ast.expr.NaE;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.config.ParseException;
import roj.config.data.CInt;
import roj.reflect.GetCallerArgs;
import roj.text.CharList;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * LocalContext 解决(resolve)环境 每线程一个
 * 1. 错误处理
 * 2. 类型转换，解析类型（扩展到全限定名，然后变成ASM数据，同时做权限检查）  (代理TypeCast)
 * 3. 解析符号引用 Class Field Method  （代理TypeResolver和Inferrer）
 * 4. instanceof，getCommonParent 最近共同祖先
 * 5. 也许能通过import或通过继承CompileContext你可以控制的功能
 *   常量值获取
 *   检查第一个Literal是不是import static 字段、方法， 或本地变量
 *   禁用cast优化/不可能的“动态类型” 设计目的：Mixin
 *   操作符重载
 * 6. 权限限制，以及clinit等的引用限制
 */
public class LocalContext {
	public final GlobalContext classes;

	public final ExprParser ep;
	public final BlockParser bp;
	public final JavaLexer lexer;

	public final TypeCast caster = new TypeCast();
	public final Inferrer inferrer = new Inferrer(this);

	protected TypeResolver tr;
	public final MyHashMap<String, ConstantData> importCache = new MyHashMap<>(), importCacheMethod = new MyHashMap<>();
	public final MyHashMap<String, Object[]> importCacheField = new MyHashMap<>();

	public CompileUnit file;
	public MethodNode method;
	public boolean in_static, in_constructor, not_invoke_constructor;
	// CompileUnit S2 resovler
	public boolean disableRawTypeWarning;

	// Tailrec,MultiReturn等使用
	public boolean inReturn;
	// This(AST)使用 也许能换成callback 目前主要给Generator用
	public int thisSlot;
	public boolean thisUsed;

	public LocalContext(GlobalContext ctx) {
		this.classes = ctx;
		this.lexer = ctx.createLexer();
		this.ep = ctx.createExprParser(this);
		this.bp = ctx.createBlockParser(this);
		this.variables = bp.getVariables();
		this.caster.context = ctx;
	}

	public boolean disableConstantValue;
	public BiConsumer<String, Object[]> errorCapture;
	public int errorReportIndex;

	public void report(Kind kind, String message) {
		if (errorCapture != null) {
			errorCapture.accept(message, ArrayCache.OBJECTS);
			return;
		}

		Object caller = GetCallerArgs.INSTANCE.getCallerInstance();
		if (caller instanceof ExprNode node) {
			classes.report(file, kind, node.getWordStart(), node.getWordEnd(), message, ArrayCache.OBJECTS);
			return;
		}

		classes.report(file, kind, errorReportIndex >= 0 ? errorReportIndex : lexer.index, message);
	}
	public void report(Kind kind, String message, Object... args) {
		for (Object arg : args) {
			if (arg == NaE.UNRESOLVABLE) return;
		}

		if (errorCapture != null) {
			errorCapture.accept(message, args);
			return;
		}

		Object caller = GetCallerArgs.INSTANCE.getCallerInstance();
		if (caller instanceof ExprNode node) {
			classes.report(file, kind, node.getWordStart(), node.getWordEnd(), message, args);
			return;
		}

		classes.report(file, kind, errorReportIndex >= 0 ? errorReportIndex : lexer.index, message, args);
	}
	public void report(int knownPos, Kind kind, String message, Object... args) {
		if (errorCapture != null) errorCapture.accept(message, args);
		else classes.report(file, kind, knownPos,message, args);
	}

	public TypeCast.Cast castTo(@NotNull IType from, @NotNull IType to, @Range(from = -8, to = 0) int lower_limit) {
		var cast = caster.checkCast(from, to);
		if (cast.type < lower_limit) report(Kind.ERROR, "typeCast.error."+cast.type, from, to);
		return cast;
	}

	// region 权限管理
	private final ToIntMap<String> flagCache = new ToIntMap<>();
	public void assertAccessible(IClass type) {
		if (type == file) return;

		int flag = flagCache.getOrDefault(type.name(), -1);
		if (flag < 0) {
			flag = 0;
			InnerClasses ics = type.parsedAttr(type.cp(), Attribute.InnerClasses);
			if (ics != null) {
				List<InnerClasses.Item> list = ics.classes;
				for (int i = 0; i < list.size(); i++) {
					InnerClasses.Item ic = list.get(i);
					if (ic.self.equals(type.name())) {
						flag = ic.flags;
						break;
					}
				}
			}
			flagCache.putInt(type.name(), flag);
		}

		if (flag > 0) {
			if ((flag&Opcodes.ACC_STATIC) == 0) {
				// todo non static class & hook
			}

			checkAccessModifier(flag, type, null, "symbol.error.accessDenied.type", true);
		} else {
			switch ((type.modifier()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED|Opcodes.ACC_PRIVATE))) {
				default: throw ResolveException.ofIllegalInput("lc.illegalModifier", Integer.toHexString(type.modifier()));
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
	public void checkSelfField(FieldNode node, boolean write) {
		boolean constructor = in_constructor;
		if (in_static) {
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
	// endregion

	public void clear() {
		this.file = null;
		this.tr = null;
		this.importCache.clear();
		this.importCacheMethod.clear();
		this.importCacheField.clear();
		this.flagCache.clear();
		this.lexer.init("");
	}
	public void setClass(CompileUnit file) {
		this.errorReportIndex = -1;
		this.file = file;
		this.tr = file.getTypeResolver();
		this.importCache.clear();
		this.importCacheMethod.clear();
		this.importCacheField.clear();
		this.flagCache.clear();
		int pos = this.lexer.index;
		this.lexer.init(file.getCode());
		this.lexer.index = pos;

		file.ctx = this;
	}
	public void setMethod(MethodNode node) {
		file._setSign(node);
		//TODO test
		caster.typeParamsL = file.currentNode != null ? file.currentNode.typeParams : null;

		in_static = (node.modifier&Opcodes.ACC_STATIC) != 0;
		not_invoke_constructor = in_constructor = node.name().startsWith("<");
		if (in_constructor) {
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
		thisUsed = in_constructor;
	}

	@NotNull public IntBiMap<String> getParentList(IClass info) {return classes.getParentList(info);}
	@NotNull public ComponentList getFieldList(IClass info, String name) {return classes.getFieldList(info, name);}
	@NotNull public ComponentList getMethodList(IClass info, String name) {return classes.getMethodList(info, name);}

	/**
	 * 将泛型类型typeInst继承链上的targetType擦除到具体类型.
	 * 若有 A&lt;K extends Number, V> extends B&lt;String> implements C&lt;K>
	 * 第一个判断按顺序检测它们
	 *     inferGeneric (A&lt;x, y>, D) = null     // D不在继承链上，或D不是泛型类型
	 *     inferGeneric (A&lt;x, y>, B) = [String] // 具体类型，不存在IType
	 * 第二个判断检测它们
	 *     inferGeneric (A, C) = [Number]          // raw类型，会返回C的泛型边界
	 *     inferGeneric (A&lt;>, C) = [Number]     // anyGeneric类型，同上
	 * 最后的动态解析处理它
	 *     inferGeneric (A&lt;x, y>, C) = [x]      // 变量类型，需要解析
	 *
	 * @param typeInst 泛型实例
	 * @param targetType 需要擦除到的具体类型
	 *
	 * @return targetType在typeInst中的实际类型
	 */
	@Nullable
	public List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType) {
		var info = classes.getClassInfo(typeInst.owner());

		List<IType> bounds = null;
		try {
			bounds = classes.getTypeParamOwner(info, targetType);
		} catch (ClassNotFoundException e) {
			report(Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
		}

		if (bounds == null || bounds.getClass() == SimpleList.class) return bounds;

		if (!(typeInst instanceof Generic g) || (g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric)) {
			var sign = info.parsedAttr(info.cp, Attribute.SIGNATURE);

//

			MyHashMap<String, IType> realType = Helpers.cast(tmpMap1);
			Inferrer.fillDefaultTypeParam(sign.typeParams, realType);

			bounds = new SimpleList<>(bounds);
			for (int i = 0; i < bounds.size(); i++) {
				bounds.set(i, Inferrer.clearTypeParam(bounds.get(i), realType, sign.typeParams));
			}

			return bounds;
		}

		// 含有类型参数，需要动态解析
		return inferGeneric0(classes.getClassInfo(g.owner), g.children, targetType);
	}
	// B <T1, T2> extends A <T1> implements Z<T2>
	// C <T3> extends B <String, T3>
	// 假设我要拿A的类型，那就要先通过已知的C（params）推断B，再推断A
	private List<IType> inferGeneric0(ConstantData typeInst, List<IType> params, String target) {
		Map<String, IType> visType = new MyHashMap<>();

		int depthInfo = getParentList(typeInst).getValueOrDefault(target, -1);
		if (depthInfo == -1) throw new IllegalStateException("无法从"+typeInst.name+"<"+params+">推断"+target);

		loop:
		for(;;) {
			var g = typeInst.parsedAttr(typeInst.cp, Attribute.SIGNATURE);

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
				depthInfo = getParentList(typeInst).getValueOrDefault(target, -1);
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

	// region 解析符号引用 Class Field Method
	@Nullable
	public ConstantData getClassOrArray(IType type) { return type.array() > 0 ? classes.getArrayInfo(type) : classes.getClassInfo(type.owner()); }

	public ConstantData resolveType(String klass) { return tr.resolve(this, klass); }
	@Contract("_ -> param1")
	public IType resolveType(IType type) {
		if (type.genericType() == 0 ? type.rawType().type != Type.CLASS : type.genericType() != 1) return type;

		// 不预先检查全限定名，适配package-restricted模式
		var info = resolveType(type.owner());
		if (info == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", type.owner());
			return type;
		}
		type.owner(info.name());

		Signature sign = info.parsedAttr(info.cp(), Attribute.SIGNATURE);
		int count = sign == null ? 0 : sign.typeParams.size();

		if (type.genericType() == IType.GENERIC_TYPE) {
			Generic type1 = (Generic) type;
			List<IType> gp = type1.children;

			boolean genericIgnore = info.interfaces().contains("roj/compiler/runtime/GenericIgnore");
			if (!genericIgnore && gp.size() != count) {
				if (count == 0) report(Kind.ERROR, "symbol.error.generic.paramCount.0", type.rawType());
				else if (gp.size() != 1 || gp.get(0) != Asterisk.anyGeneric) report(Kind.ERROR, "symbol.error.generic.paramCount", type.rawType(), gp.size(), count);
			}

			if (sign == null) return type;
			var itr = sign.typeParams.values().iterator();

			for (int i = 0; i < gp.size(); i++) {
				IType type2 = resolveType(gp.get(i));
				if (genericIgnore) continue;

				// skip if is AnyType (?)
				if (type2.genericType() <= 2) {
				// TODO test
					caster.typeParamsR = sign.typeParams;
					for (IType iType : itr.next()) castTo(type2, iType, 0);
					caster.typeParamsR = null;
				}

				if (type2 instanceof Generic g && g.canBeAny()) {
					gp.set(i, Signature.any());
				} else if (type2.isPrimitive()) {
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
				var ic = classes.getInnerClassFlags(info).get(x.owner);
				if (ic == null || (info = classes.getClassInfo(ic.self)) == null) {
					report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", x.owner, "\1symbol.type\0 "+type1);
					break;
				}

				if ((ic.flags&Opcodes.ACC_STATIC) != 0) {
					report(Kind.ERROR, "type.error.staticGenericSub", type1, ic.name);
				}

				sign = info.parsedAttr(info.cp(), Attribute.SIGNATURE);
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

	private final CharList fieldResolveTmp = new CharList();

	private IClass _frBegin;
	private final SimpleList<FieldNode> _frChain = new SimpleList<>();
	private IType _frType;
	private int _frOffset;

	/**
	 * 将这种格式的字符串 net/minecraft/client/Minecraft/fontRender/FONT_HEIGHT
	 * 解析为类与字段的组合
	 * @return 错误码,null为成功
	 */
	public String resolveDotGet(CharList desc, boolean allowClassExpr) {
		ConstantData directClz = null;
		String anySuccess = "";
		_frOffset = 0;

		int slash = desc.indexOf("/");
		if (slash >= 0) {
			directClz = tr.resolve(this, desc.substring(0, slash++));
			if (directClz != null) {
				String error = resolveField(directClz, null, desc, slash);
				if (error == null) return null;

				anySuccess = error;
			}

			var sb = fieldResolveTmp;
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
			if (directClz == null) directClz = resolveType(desc.toString());
			if (directClz != null) {
				if (allowClassExpr) {
					_frBegin = directClz;
					_frChain.clear();
					_frType = null;
					return "";
				}

				return "symbol.error.expression:".concat(desc.toString());
			}
			return "symbol.error.noSuchSymbol::"+desc+":"+currentCodeBlockForReport();
		}
		return anySuccess;
	}

	/**
	 * @param clz 前一个字段的owner
	 * @param generic 前一个字段的类型
	 * @param desc dot splitted field mapping
	 */
	public String resolveField(IClass clz, IType generic, CharList desc) { return resolveField(clz, generic, desc, 0); }
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
				return "symbol.error.noSuchSymbol:symbol.field:"+name+":\1symbol.type\0 "+clz.name();
			}

			Signature cSign = clz.parsedAttr(clz.cp(), Attribute.SIGNATURE);
			block:{
			if (cSign != null) {
				Signature fSign = field.parsedAttr(clz.cp(), Attribute.SIGNATURE);
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

			clz = getClassOrArray(type);
			if (clz == null) return "symbol.error.noSuchClass:".concat(type.toString());
		}
	}
	public IClass get_frBegin() {return _frBegin;}
	public SimpleList<FieldNode> get_frChain() {return _frChain;}
	public IType get_frType() {return _frType;}
	// optional chaining offset
	public int get_frOffset() {return _frOffset;}
	// endregion

	public void checkType(String owner) {
		// 检查type是否已导入
		if (owner != null && tr.isRestricted() && null == tr.resolve(this, owner.substring(owner.lastIndexOf('/')+1))) {
			report(Kind.ERROR, "lc.packageRestricted", owner);
		}
	}

	public IType transformPseudoType(IType type) {
		if (type.owner() != null && !"java/lang/Object".equals(type.owner())) {
			var info = classes.getClassInfo(type.owner());
			if (info.parent == null) {
				var vb = (ValueBased) info.attrByName(ValueBased.NAME);
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

	public void addException(IType type) {
		var cast = caster.checkCast(type, Types.RUNTIME_EXCEPTION);
		if (cast.type >= 0) return;
		if (bp.addException(type)) return;

		var exceptions = (AttrClassList) method.attrByName("Exceptions");
		if (exceptions != null) {
			if (type.genericType() == 0) {
				var parents = getParentList(classes.getClassInfo(type.owner()));
				for (String s : exceptions.value) {
					if (parents.containsValue(s)) return;
				}
			} else {
				for (String s : exceptions.value) {
					if (caster.checkCast(type, new Type(s)).type >= 0) return;
				}
			}
		}
		report(classes.hasFeature(LavaFeatures.NO_CHECKED_EXCEPTION) ? Kind.WARNING : Kind.ERROR, "lc.unReportedException", type);
	}

	// this should inherit, see #parent for details
	public SimpleList<NestContext> enclosing = new SimpleList<>();

	// Read by BlockParser
	public final MyHashMap<String, Variable> variables;
	public Variable getVariable(String name) {return variables.get(name);}
	public void loadVar(Variable v) {bp.loadVar(v);}
	public void storeVar(Variable v) {bp.storeVar(v);}
	public void assignVar(Variable v, Object constant) {bp.assignVar(v, constant);}
	public Variable createTempVariable(IType type) {return bp.tempVar(type);}

	public static final class Import {
		public final IClass owner;
		public final String method;
		public final Object prev;

		// 替换
		public Import(ExprNode node) {
			this.owner = null;
			this.method = null;
			this.prev = Objects.requireNonNull(node);
		}

		// 静态方法
		public Import(IClass owner, String method) {
			this.owner = Objects.requireNonNull(owner);
			this.method = Objects.requireNonNull(method);
			this.prev = null;
		}

		// 动态方法
		public Import(IClass owner, String method, ExprNode prev) {
			this.owner = Objects.requireNonNull(owner);
			this.method = Objects.requireNonNull(method);
			this.prev = prev;
		}

		// 构造器
		public Import(IClass owner) {
			this.owner = Objects.requireNonNull(owner);
			this.method = "<init>";
			this.prev = new Type(owner.name());
		}

		public ExprNode parent() {return (ExprNode) prev;}
	}
	public Function<String, Import> dynamicMethodImport, dynamicFieldImport;

	@NotNull
	public Function<String, Import> getFieldDFI(IClass info, Variable ref, Function<String, Import> prev) {
		return name -> {
			var cl = getFieldList(info, name);
			if (cl != ComponentList.NOT_FOUND) {
				FieldResult result = cl.findField(this, ref == null ? ComponentList.IN_STATIC : 0);
				if (result.error == null) return new Import(info, result.field.name(), ref == null ? null : new LocalVariable(ref));
			}

			return prev == null ? null : prev.apply(name);
		};
	}

	/**
	 * @param name DotGet name
	 * @param args arguments
	 *
	 * @return [owner, name, Nullable Expr]
	 */
	public Import tryImportMethod(String name, List<ExprNode> args) {
		if (dynamicMethodImport != null) {
			Import result = dynamicMethodImport.apply(name);
			if (result != null) return result;
		}

		if (enclosing != null) {
			Import imp = NestContext.tryMethodRef(enclosing, this, name);
			if (imp != null) return imp;
		}

		var owner = tr.resolveMethod(this, name);
		if (owner == null) return null;
		return new Import(owner, name);
	}
	public Import tryImportField(String name) {
		if (dynamicFieldImport != null) {
			Import result = dynamicFieldImport.apply(name);
			if (result != null) return result;
		}

		if (enclosing != null) {
			Import imp = NestContext.tryFieldRef(enclosing, this, name);
			if (imp != null) return imp;
		}

		_frChain.clear();
		var begin = tr.resolveField(this, name, _frChain);
		if (begin == null) return null;
		return new Import(begin, name);
	}

	public boolean instanceOf(String testClass, String instClass) {
		IClass info = classes.getClassInfo(testClass);
		if (info == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", testClass);
			return false;
		}

		return classes.getParentList(info).containsValue(instClass);
	}

	public IType getCommonParent(IType a, IType b) {
		if (a.equals(b)) return a;

		if (a.genericType() >= IType.ASTERISK_TYPE) {
			a = ((Asterisk) a).getBound();
			if (a == null) return b;
		}
		if (b.genericType() >= IType.ASTERISK_TYPE) {
			b = ((Asterisk) b).getBound();
			if (b == null) return a;
		}

		int capa = TypeCast.getDataCap(a.getActualType());
		int capb = TypeCast.getDataCap(b.getActualType());
		// 双方都是数字
		if ((capa&7) != 0 && (capb&7) != 0) return new Type("java/lang/Number");
		// 没有任何一方是对象 (boolean | void)
		if ((capa|capb) < 8) return Types.OBJECT_TYPE;

		if (a.isPrimitive()) a = TypeCast.getWrapper(a);
		if (b.isPrimitive()) b = TypeCast.getWrapper(b);

		// noinspection all
		IClass infoA = classes.getClassInfo(a.owner());
		if (infoA == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", a);
			return a;
		}
		// noinspection all
		IClass infoB = classes.getClassInfo(b.owner());
		if (infoB == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", b);
			return a;
		}

		// CP(List<String>, SimpleList<String>) => List<String>
		// CP(List<?>, SimpleList<String>) => List<?>
		// CP(List<String>, SimpleList<?>) => List<?>
		List<IType> typeParams = Collections.emptyList();
		int extendType = 0;
		if (a.genericType() == 1 & b.genericType() == 1) {
			// noinspection all
			Generic ga = (Generic) a, gb = (Generic) b;
			if (ga.children.size() != gb.children.size()) {
				report(Kind.ERROR, "symbol.error.generic.paramCount", ga.children.size(), gb.children.size());
				return a;
			}
			// TODO => GenericSub
			typeParams = SimpleList.asModifiableList(new IType[ga.children.size()]);
			for (int i = 0; i < ga.children.size(); i++) {
				IType aa = ga.children.get(i);
				IType bb = gb.children.get(i);
				typeParams.set(i, getCommonParent(aa, bb));
			}

			// java未定义这些行为！
			// CP(List<String>, List<? extends String>) => List<? extends String>
			// CP(List<? super String>, List<String>) => List<String>
			// CP(List<? super String>, List<? extends String>) => List<String>

			// CP(List<String>, List<? extends Integer>) => List<Object>
			// CP(List<String>, List<? super Integer>) => List<Object>
			// CP(List<? super String>, List<? extends Integer>) => List<Object>
			if (ga.extendType != gb.extendType) {
				if (ga.extendType == Generic.EX_SUPER || gb.extendType == Generic.EX_SUPER) {
					extendType = Generic.EX_NONE;
				} else if (ga.extendType == Generic.EX_EXTENDS || gb.extendType == Generic.EX_EXTENDS) {
					extendType = Generic.EX_EXTENDS;
				}
			} else {
				extendType = ga.extendType;
			}
		} else {
			// TODO check primitive generic
		}

		if (infoA == infoB) return a;

		String commonParent = getCommonParent(infoA, infoB);
		assert commonParent != null;
		if (typeParams.isEmpty() && extendType == 0) return new Type(commonParent);

		Generic generic = new Generic(commonParent, a.array(), (byte) extendType);
		generic.children = typeParams;
		return generic;
	}
	public String getCommonParent(IClass infoA, IClass infoB) {
		IntBiMap<String> tmp,
			listA = getParentList(infoA),
			listB = getParentList(infoB);

		if (listA.size() > listB.size()) {
			tmp = listA;
			listA = listB;
			listB = tmp;
		}

		String commonParent = infoA.name();
		int minIndex = listB.size();
		for (var entry : listA.selfEntrySet()) {
			String klass = entry.getValue();

			int val = listB.getValueOrDefault(klass, minIndex)&0x7FFF_FFFF;
			int j = val&0xFFFF;
			if (j < minIndex || (j == minIndex && val < minIndex)) {
				commonParent = klass;
				minIndex = j;
			}
		}
		return commonParent;
	}

	// region 也许应该放在GlobalContext中的一些实现特殊语法的函数
	public Annotation getAnnotation(IClass type, Attributed node, String annotation, boolean rt) {
		return Annotation.find(Annotation.getAnnotations(type.cp(), node, rt), annotation);
	}

	private static final IntMap<ExprNode> EMPTY = new IntMap<>(0);
	/**
	 * 方法默认值的统一处理
	 */
	public IntMap<ExprNode> getDefaultValue(IClass klass, MethodNode method) {
		MethodDefault attr = method.parsedAttr(klass.cp(), MethodDefault.METHOD_DEFAULT);
		if (attr != null) {
			IntMap<String> value = attr.defaultValue;
			IntMap<ExprNode> def = new IntMap<>();

			var tmpPrev = tmpList;
			tmpList = new SimpleList<>();
			try {
				for (IntMap.Entry<String> entry : value.selfEntrySet()) {
					def.putInt(entry.getIntKey(), ep.deserialize(entry.getValue()).resolve(this));
				}
				return def;
			} catch (ParseException|ResolveException e) {
				e.printStackTrace();
			} finally {
				tmpList = tmpPrev;
			}
		}
		return EMPTY;
	}

	public static final int UNARY_PRE = 0, UNARY_POST = 65536;
	/**
	 * 操作符重载的统一处理
	 * 注意：重载是次低优先级，仅高于报错，所以无法覆盖默认行为
	 * @param e1 左侧
	 * @param e2 右侧， 当operator属于一元运算符时为null
	 * @param operator JavaLexer中的tokenId,可以是一元运算符、一元运算符|UNARY_POST、二元运算符、lBracket(数组取值)、lParen(cast表达式)、rParen(变量赋值)
	 * @return 替代的表达式
	 */
	@Nullable
	public ExprNode getOperatorOverride(@NotNull ExprNode e1, @Nullable Object e2, int operator) {return null;}

	public IClass getPrimitiveMethod(IType type, ExprNode caller, List<ExprNode> args) {
		if (type.getActualType() == Type.INT) {
			var type1 = new ConstantData();
			type1.name("java/lang/Integer");
			type1.newMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "toHexString", "(I)Ljava/lang/String;");
			return type1;
		}
		return null;
	}

	/**
	 * 常量传播的统一处理
	 * @param fieldType field的泛型类型 可能为空
	 * @return klass.field的常量值，若有
	 */
	@Nullable
	public ExprNode getConstantValue(IClass klass, FieldNode field, @Nullable IType fieldType) {
		assert klass.fields().contains(field);

		var cv = field.parsedAttr(klass.cp(), Attribute.ConstantValue);
		if (cv == null) return null;

		var c = switch (cv.c.type()) {
			case Constant.INT -> AnnVal.valueOf(((CstInt) cv.c).value);
			case Constant.FLOAT -> AnnVal.valueOf(((CstFloat) cv.c).value);
			case Constant.LONG -> AnnVal.valueOf(((CstLong) cv.c).value);
			case Constant.DOUBLE -> AnnVal.valueOf(((CstDouble) cv.c).value);
			case Constant.CLASS -> cv.c;
			case Constant.STRING -> cv.c.getEasyCompareValue();
			default -> throw new IllegalArgumentException("Illegal ConstantValue "+cv.c);
		};
		return new roj.compiler.ast.expr.Constant(fieldType == null ? field.fieldType() : fieldType, c);
	}

	// endregion
	// region CompileUnit用到的
	private static final ThreadLocal<List<Object>> FTL = new ThreadLocal<>();

	public static void set(LocalContext cache) {
		SimpleList<Object> list = new SimpleList<>(2);
		list.add(new CInt(1));
		list.add(cache);
		FTL.set(list);
	}

	public static LocalContext get() {
		List<Object> list = FTL.get();
		return list == null ? Helpers.maybeNull() : (LocalContext) list.get(((CInt) list.get(0)).value);
	}

	public static LocalContext next() {
		List<Object> list = FTL.get();
		int v = ++((CInt) list.get(0)).value;
		if (v >= list.size()) {
			LocalContext first = (LocalContext) list.get(1);
			LocalContext next = first.classes.createLocalContext();
			next.enclosing = first.enclosing;
			list.add(next);
		}
		return (LocalContext) list.get(v);
	}
	public static void prev() {((CInt) FTL.get().get(0)).value--;}

	public SimpleList<String> tmpList = new SimpleList<>();
	public final SimpleList<AnnotationPrimer> tmpAnnotations = new SimpleList<>();
	public final MyHashSet<String> tmpSet = new MyHashSet<>();
	public final MyHashMap<String, Object> tmpMap1 = new MyHashMap<>(), tmpMap2 = new MyHashMap<>();
	public final CharList tmpSb = new CharList();
	public NameAndType tmpNat = new NameAndType();

	public MyHashSet<String> getTmpSet() {tmpSet.clear();return tmpSet;}
	public CharList getTmpSb() {tmpSb.clear();return tmpSb;}

	private Label[] tmpLabels = new Label[8];
	public Label[] getTmpLabels(int i) {
		if (i > tmpLabels.length) return tmpLabels = new Label[i];
		return tmpLabels;
	}

	public String currentCodeBlockForReport() {return "\1symbol.type\0 "+file.name;}
	// endregion
}