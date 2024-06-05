package roj.compiler.context;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrClassList;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.*;
import roj.asm.util.Attributes;
import roj.asm.util.ClassUtil;
import roj.collect.*;
import roj.compiler.JavaLexer;
import roj.compiler.api_rt.MethodDefault;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.Variable;
import roj.compiler.ast.block.BlockParser;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.*;
import roj.config.ParseException;
import roj.config.data.CInt;
import roj.text.CharList;
import roj.text.TextUtil;
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
	public static final Type OBJECT_TYPE = new Type("java/lang/Object");

	public final GlobalContext classes;
	public CompileUnit file;
	public MethodNode method;
	public boolean in_static, in_constructor, not_invoke_constructor;

	public final TypeCast castCheck = new TypeCast();
	public final Inferrer inferrer = new Inferrer(this);

	public TypeResolver tr;
	public final MyHashMap<String, IClass> importCache = new MyHashMap<>(), importCacheMethod = new MyHashMap<>();
	public final MyHashMap<String, Object[]> importCacheField = new MyHashMap<>();

	public LocalContext(GlobalContext classes) {this.classes = classes;}

	public boolean disableConstantValue;
	public BiConsumer<String, Object[]> errorCapture;

	public void report(Kind kind, String message) {
		if (kind == Kind.ERROR && errorCapture != null) errorCapture.accept(message, ArrayCache.OBJECTS);
		else file.report(kind, message);
	}
	public void report(Kind kind, String message, Object... args) {
		if (errorCapture != null) errorCapture.accept(message, args);
		else file.report(kind, message+":"+TextUtil.join(Arrays.asList(args), ":"));
	}
	public void report(int knownPos, Kind kind, String message, Object... args) {
		if (errorCapture != null) errorCapture.accept(message, args);
		else classes.report(file, kind, knownPos,message+":"+TextUtil.join(Arrays.asList(args), ":"));
	}
	public List<?> tmpListForExpr2 = new SimpleList<>();

	public TypeCast.Cast castTo(@NotNull IType from, @NotNull IType to, int lower_limit) {
		TypeCast.Cast cast = castCheck.checkCast(from, to);

		// TODO change this
		if ((cast.type == TypeCast.E_DOWNCAST || cast.type == TypeCast.UPCAST) && isDynamicType(to)) {
			cast = TypeCast.RESULT(TypeCast.E_DOWNCAST, 99);
		}

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
				List<InnerClasses.InnerClass> list = ics.classes;
				for (int i = 0; i < list.size(); i++) {
					InnerClasses.InnerClass ic = list.get(i);
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
				default: throw new ResolveException("非法的修饰符组合:"+Integer.toHexString(type.modifier()));
				case Opcodes.ACC_PUBLIC: return;
				case 0: if (!ClassUtil.arePackagesSame(type.name(), file.name()))
					report(Kind.ERROR, "symbol.error.accessDenied.type", type.name(), "package-private", file.name());
			}
		}
	}
	// 这里不会检测某些东西 (override, static has been written等)
	public boolean checkAccessible(IClass type, RawNode node, boolean staticEnv, boolean report) {
		if (type == file) return true;

		if (!checkAccessModifier(node.modifier(), type, node.name(), "symbol.error.accessDenied.symbol", report) & !report) {
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
			default: throw new ResolveException("非法的修饰符组合: 0x"+Integer.toHexString(flag));
			case Opcodes.ACC_PUBLIC: return true;
			case Opcodes.ACC_PROTECTED:
				if (instanceOf(type.name(), file.name())) return true;
				modifier = "protected";
				break;
			case Opcodes.ACC_PRIVATE:
				// 同一个类可以互相访问
				// 条件: a/b仅比较$之前的部分
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

	public void setClass(CompileUnit file) {
		this.file = file;
		this.castCheck.context = classes;
		// TODO getGenericEnv应该随着方法变吧
		this.castCheck.genericResolver = file::getGenericEnv;

		this.tr = file.getTypeResolver();
		this.importCache.clear();
		this.importCacheMethod.clear();
		this.importCacheField.clear();
		this.flagCache.clear();
	}
	public void setMethod(MethodNode node) {
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
	}

	public ComponentList fieldListOrReport(IClass info, String name) {return classes.fieldList(info, name);}
	public ComponentList methodListOrReport(IClass info, String name) {return classes.methodList(info, name);}
	public IntBiMap<String> parentListOrReport(IClass info) {
		try {
			return classes.parentList(info);
		} catch (ClassNotFoundException e) {
			report(Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
			return new IntBiMap<>();
		}
	}

	// region 解析符号引用 Class Field Method
	@Nullable
	public IClass getClassOrArray(IType type) { return type.array() > 0 ? GlobalContext.anyArray() : classes.getClassInfo(type.owner()); }

	public IClass resolveType(String klass) { return tr.resolve(this, klass); }
	@Contract("_ -> param1")
	public IType resolveType(IType type) {
		if (type.genericType() == 0 ? type.rawType().type != Type.CLASS : type.genericType() != 1) return type;

		// 不预先检查全限定名，适配package-restricted模式
		IClass info = resolveType(type.owner());
		if (info == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", type);
			return type;
		}
		type.owner(info.name());

		Signature sign = info.parsedAttr(info.cp(), Attribute.SIGNATURE);
		int count = sign == null ? 0 : sign.typeParams.size();

		if (type.genericType() == IType.GENERIC_TYPE) {
			// TODO hack..
			boolean genericIgnore = info.interfaces().contains("roj/compiler/runtime/GenericIgnore");

			Generic type1 = (Generic) type;
			List<IType> gp = type1.children;

			if (!genericIgnore && gp.size() != count) {
				if (count == 0) report(Kind.ERROR, "symbol.error.generic.paramCount.0", type.rawType());
				else if (gp.size() != 1 || gp.get(0) != Asterisk.anyGeneric) report(Kind.ERROR, "symbol.error.generic.paramCount", type.rawType(), gp.size(), count);
			}

			for (int i = 0; i < gp.size(); i++) {
				IType type2 = resolveType(gp.get(i));
				if (genericIgnore) continue;

				if (type2 instanceof Generic g && g.isAnyType()) {
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

			if (type1.sub != null) {
				MyHashMap<String, InnerClasses.InnerClass> flags1 = classes.getInnerClassFlags(info);
				GenericSub x = type1.sub;
				while (x != null) {
					InnerClasses.InnerClass ic = flags1.get(x.owner);
					if (ic == null || (info = classes.getClassInfo(ic.self)) == null) {
						report(Kind.ERROR, "symbol.error.noSuchSymbol", "symbol.type", x.owner, "\1symbol.type\0 "+type1);
						break;
					}

					if ((ic.flags&Opcodes.ACC_STATIC) != 0) {
						report(Kind.ERROR, "type.error.staticGenericSub", type1, ic.name);
						break;
					}

					sign = info.parsedAttr(info.cp(), Attribute.SIGNATURE);
					count = sign == null ? 0 : sign.typeParams.size();

					if (x.children.size() != count) {
						if (count == 0) report(Kind.ERROR, "symbol.error.generic.paramCount.0", ic.self);
						else report(Kind.ERROR, "symbol.error.generic.paramCount", ic.self, x.children.size(), count);
					}

					x = x.sub;
				}
			}
		} else if (count > 0) {
			report(Kind.WARNING, "symbol.warn.generic.rawTypes", type);
		}
		return type;
	}

	private final CharList fieldResolveTmp = new CharList();

	private IClass _frBegin;
	private final SimpleList<FieldNode> _frChain = new SimpleList<>();
	private IType _frType;

	/**
	 * 将这种格式的字符串 net/minecraft/client/Minecraft/fontRender/FONT_HEIGHT
	 * 解析为类与字段的组合
	 * @return 错误码,null为成功
	 */
	public String resolveDotGet(CharList desc, boolean allowClassExpr) {
		CharList sb = fieldResolveTmp;

		IClass directClz = null;
		String anySuccess = "";
		int slash = desc.indexOf("/");
		if (slash >= 0) {
			directClz = tr.resolve(this, desc.substring(0, slash));
			if (directClz != null) {
				String error = resolveField(directClz, null, desc, slash+1);
				if (error == null) return null;

				anySuccess = error;
			}

			do {
				sb.clear(); sb.append(desc, 0, slash);

				int dollar = slash++;
				while (true) {
					IClass clz = classes.getClassInfo(sb);
					if (clz != null) {
						String error = resolveField(clz, null, desc, slash);
						if (error == null) return null;

						anySuccess = error;
					}

					dollar = sb.lastIndexOf("/", dollar);
					if (dollar < 0) break;
					sb.set(dollar, '$');
				}

				slash = desc.indexOf("/", slash);
			} while (slash >= 0);
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
				ComponentList fields = fieldListOrReport(clz, name);
				if (fields != null) {
					FieldResult fr = fields.findField(this, 0);
					if (fr.error == null) {
						field = fr.field;
						break block;
					}
					return fr.error;
				}
				return "symbol.error.noSuchSymbol:symbol.field:"+name+":"+currentCodeBlockForReport();
			}

			// TODO 泛型的处理可能没必要这么复杂
			Signature cSign = clz.parsedAttr(clz.cp(), Attribute.SIGNATURE);
			if (cSign != null) {
				Signature fSign = field.parsedAttr(clz.cp(), Attribute.SIGNATURE);
				if (fSign != null) {
					Map<String, List<IType>> tpBounds = cSign.typeParams;
					MyHashMap<String, IType> knownTps = new MyHashMap<>(cSign.typeParams.size());

					if (!(fieldType instanceof Generic gType)) {
						for (Map.Entry<String, List<IType>> entry : tpBounds.entrySet()) {
							List<IType> value = entry.getValue();
							knownTps.put(entry.getKey(), value.get(0).genericType() == IType.PLACEHOLDER_TYPE ? OBJECT_TYPE : value.get(0));
						}
					} else {
						Iterator<String> itr = tpBounds.keySet().iterator();
						assert gType.children.size() == tpBounds.size();
						for (IType child : gType.children) knownTps.put(itr.next(), child);
					}

					fieldType = Inferrer.clearTypeParams(fSign.values.get(0), knownTps, tpBounds);
				}
			} else {
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
				return "symbol.error.derefPrimitiveField";
			}

			clz = type.array() > 0 ? GlobalContext.anyArray() : classes.getClassInfo(type.owner);
			if (clz == null) return "symbol.error.noSuchClass:".concat(type.owner);
		}
	}
	public IClass get_frBegin() {return _frBegin;}
	public SimpleList<FieldNode> get_frChain() {return _frChain;}
	public IType get_frType() {return _frType;}
	// endregion

	public void checkType(String owner) {
		// 检查type是否已导入
		if (owner != null && tr.isRestricted() && null == tr.resolve(this, owner.substring(owner.lastIndexOf('/')+1))) {
			report(Kind.ERROR, "lc.packageRestricted", owner);
		}
	}

	public void addException(IType type) {
		TypeCast.Cast cast = castCheck.checkCast(type, CompileUnit.RUNTIME_EXCEPTION);
		if (cast.type >= 0) return;

		var exceptions = (AttrClassList) method.attrByName("Exceptions");
		if (exceptions != null) {
			for (String s : exceptions.value) {
				if (castCheck.checkCast(type, new Type(s)).type >= 0) return;
			}
		}
		report(Kind.ERROR, "lc.unReportedException", type);
	}

	// Assigned by BlockParser
	public MyHashMap<String, Variable> variables = new MyHashMap<>();
	public Variable tryVariable(String name) {return variables.get(name);}

	public static final class Import {
		public final IClass owner;
		public final String method;
		public final ExprNode prev;

		public Import(IClass owner, String method) {
			this.owner = owner;
			this.method = method;
			this.prev = null;
		}

		public Import(IClass owner, String method, ExprNode prev) {
			this.owner = owner;
			this.method = method;
			this.prev = prev;
		}
	}
	public Function<String, Import> dynamicMethodImport, dynamicFieldImport;

	/**
	 * @param name DotGet name
	 * @return [owner, name, Nullable Expr]
	 */
	public Import tryImportMethod(String name) {
		if (dynamicMethodImport != null) {
			Import result = dynamicMethodImport.apply(name);
			if (result != null) return result;
		}

		IClass owner = tr.resolveMethod(this, name);
		if (owner == null) return null;
		return new Import(owner, name);
	}
	public Import tryImportField(String name) {
		if (dynamicFieldImport != null) {
			Import result = dynamicFieldImport.apply(name);
			if (result != null) return result;
		}

		_frChain.clear();
		IClass begin = tr.resolveField(this, name, _frChain);
		if (begin == null) return null;
		return new Import(begin, name);
	}

	public boolean instanceOf(String testClass, String instClass) {
		IClass info = classes.getClassInfo(testClass);
		if (info == null) {
			report(Kind.ERROR, "symbol.error.noSuchClass", testClass);
			return false;
		}

		try {
			return classes.parentList(info).containsValue(instClass);
		} catch (ClassNotFoundException e) {
			report(Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
			return false;
		}
	}

	public IType getCommonParent(IType a, IType b) {
		assert a.genericType() < 2 && b.genericType() < 2 : "无法比较的类型:"+a+","+b;

		if (a.equals(b)) return a;

		int capa = TypeCast.getDataCap(a.getActualType());
		int capb = TypeCast.getDataCap(b.getActualType());
		// 双方都是数字
		if ((capa&7) != 0 && (capb&7) != 0) return new Type("java/lang/Number");
		// 没有任何一方是对象 (boolean | void)
		if ((capa|capb) < 8) return OBJECT_TYPE;

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

		try {
			IntBiMap<String> tmp,
				listA = classes.parentList(infoA),
				listB = classes.parentList(infoB);

			if (listA.size() > listB.size()) {
				tmp = listA;
				listA = listB;
				listB = tmp;
			}

			String commonParent = infoA.name();
			int minIndex = listB.size();
			for (IntBiMap.Entry<String> entry : listA.selfEntrySet()) {
				String klass = entry.getValue();

				int val = listB.getValueOrDefault(klass, minIndex);
				int j = val&0xFFFF;
				if (j < minIndex || (j == minIndex && val < minIndex)) {
					commonParent = klass;
					minIndex = j;
				}
			}

			assert commonParent != null;
			if (typeParams.isEmpty() && extendType == 0) {
				return new Type(commonParent);
			}

			Generic generic = new Generic(commonParent, a.array(), (byte) extendType);
			generic.children = typeParams;
			return generic;
		} catch (ClassNotFoundException e) {
			report(Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
			return a;
		}
	}
	// region 也许应该放在GlobalContext中的一些实现特殊语法的函数
	public Annotation getAnnotation(IClass type, Attributed node, String annotation, boolean rt) {
		return Attributes.getAnnotation(Attributes.getAnnotations(type.cp(), node, rt), annotation);
	}

	private static final IntMap<ExprNode> EMPTY = new IntMap<>(0);
	/**
	 * 方法默认值的统一处理
	 */
	public IntMap<ExprNode> getDefaultValue(IClass klass, MethodNode method) {
		MethodDefault attr = method.parsedAttr(klass.cp(), MethodDefault.METHOD_DEFAULT);
		if (attr != null) {
			IntMap<String> value = attr.defaultValue;
			IntMap<ExprNode> value1 = new IntMap<>();
			for (IntMap.Entry<String> entry : value.selfEntrySet()) {
				try {
					value1.putInt(entry.getIntKey(), ExprParser.deserialize(entry.getValue()).resolve(this));
				} catch (ParseException|ResolveException e) {
					e.printStackTrace();
					return null;
				}
			}
			return value1;
		} else {return EMPTY;}
	}

	// 被cast和intanceof调用，若返回true则禁用编译警告和化简
	public boolean isDynamicType(IType type) {return false;}

	public static final int UNARY_PRE = 0, UNARY_POST = 65536;
	/**
	 * 操作符重载的统一处理
	 * 注意：重载是次低优先级，仅高于报错，所以无法覆盖默认行为
	 * @param e1 左侧
	 * @param e2 右侧， 当operator属于一元运算符时为null
	 * @param operator JavaLexer中的tokenId,可以是一元运算符、一元运算符|UNARY_POST、二元运算符、lBracket(数组取值)
	 * @return 替代的表达式
	 */
	@Nullable
	public ExprNode getOperatorOverride(@NotNull ExprNode e1, @Nullable ExprNode e2, int operator) {
		IType left = e1.type(), right = e2 == null ? /*deal with null check*/Helpers.maybeNull() : e2.type();

		switch (operator) {
			case JavaLexer.mul -> {
				if (left.array() == 0 && "java/lang/String".equals(left.owner()) && right.getActualType() == Type.INT) {
					MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "repeat", "(I)Ljava/lang/String;");
					return Invoke.binaryAlt(mn, e1, e2);
				}
			}
			case JavaLexer.logic_not -> {
				if (left.array() == 0 && "java/lang/String".equals(left.owner())) {
					MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "isEmpty", "()Z");
					return Invoke.unaryAlt(mn, e1);
				}
			}
			case JavaLexer.lBracket -> {
				TypeCast.Cast cast = castTo(left, new Type("java/util/Map"), TypeCast.E_NEVER);
				if (cast.type == TypeCast.UPCAST) {
					MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

					List<IType> owner = null;
					if (cast.distance == 0) {
						owner = left.genericType() == IType.GENERIC_TYPE ? ((Generic) left).children : null;
					} else {
						try {
							owner = classes.getTypeParamOwner(classes.getClassInfo(left.owner()), new Type("java/util/Map"));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					if (owner != null) {
						System.out.println("GenType1="+owner.get(1));
					}

					return Invoke.interfaceMethod(mn, e1, e2);

				}
			}
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
		Object c;
		assert klass.fields().contains(field);
		ConstantValue cv = field.parsedAttr(klass.cp(), Attribute.ConstantValue);
		if (cv == null) {
			if ((klass.modifier() & field.modifier & Opcodes.ACC_ENUM) == 0) return null;
			c = new AnnValEnum(klass.name(), field.name());
		} else {
			switch (cv.c.type()) {
				case Constant.INT: c = AnnVal.valueOf(((CstInt) cv.c).value); break;
				case Constant.FLOAT: c = AnnVal.valueOf(((CstFloat) cv.c).value); break;
				case Constant.LONG: c = AnnVal.valueOf(((CstLong) cv.c).value); break;
				case Constant.DOUBLE: c = AnnVal.valueOf(((CstDouble) cv.c).value); break;
				case Constant.CLASS: c = cv.c; break;
				case Constant.STRING: c = cv.c.getEasyCompareValue(); break;
				default: return null;
			}
		}

		return new roj.compiler.ast.expr.Constant(fieldType == null ? field.fieldType() : fieldType, c);
	}

	// WIP
	public boolean setConstantValue(Variable var, roj.compiler.ast.expr.Constant val) {
		return false;
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
		return (LocalContext) list.get(((CInt) list.get(0)).value);
	}

	public static void depth(int ud) {
		List<Object> list = FTL.get();
		CInt mi = (CInt) list.get(0);
		int v = mi.value += ud;
		if (v >= list.size()) list.add(new LocalContext(((LocalContext) list.get(1)).classes));
	}

	public MyHashSet<IType> toResolve_unc = new MyHashSet<>();

	public MyHashSet<String> tmpSet = new MyHashSet<>();
	public SimpleList<AnnotationPrimer> annotationTmp = new SimpleList<>();
	public CharList tmpList = new CharList();

	public final ExprParser ep = new ExprParser();
	public final BlockParser bp = new BlockParser(this);

	public String currentCodeBlockForReport() {
		return "\1symbol.type\0 "+file.name;
	}
	// endregion
}