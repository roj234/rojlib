package roj.compiler.context;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.*;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.ConstantValue;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.util.Attributes;
import roj.asm.util.ClassUtil;
import roj.collect.*;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.GenericPrimer;
import roj.compiler.asm.Variable;
import roj.compiler.ast.block.BlockParser;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.resolve.*;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ArrayCache;

import javax.tools.Diagnostic;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class CompileContext {
	public static final Type OBJECT_TYPE = new Type("java/lang/Object");

	public final ClassContext classes;
	public CompileUnit file;
	public boolean in_static, in_constructor, first_statement;

	public boolean annotationEnv;
	private Function<String, IClass> globalClassEnv, classEnv;
	private Function<String, List<IType>> genericEnv;
	public final Inferrer inferrer = new Inferrer(this);

	public CompileContext(ClassContext classes) {
		this.classes = classes;
		this.globalClassEnv = classes::getClassInfo;
	}

	public boolean disableConstantValue;
	public BiConsumer<String, Object[]> errorCapture;

	public void report(Diagnostic.Kind kind, String message) {
		if (kind == Diagnostic.Kind.ERROR && errorCapture != null) errorCapture.accept(message, ArrayCache.OBJECTS);
		else file.fireDiagnostic(kind, message);
	}
	public void report(Diagnostic.Kind kind, String message, Object... args) {
		if (errorCapture != null) errorCapture.accept(message, args);
		else file.fireDiagnostic(kind, message+":"+TextUtil.join(Arrays.asList(args), ":"));
	}
	public List<?> tmpListForExpr2 = new SimpleList<>();

	public TypeCast.Cast castTo(IType from, IType to, int lower_limit) throws UnableCastException {
		TypeCast.Cast cast = TypeCast.checkCast(from, to, classEnv, genericEnv);
		if (cast.rawType) report(Diagnostic.Kind.WARNING, "typeCast.warn.rawTypes", from, to);

		// TODO change this
		if ((cast.type == TypeCast.E_DOWNCAST || cast.type == TypeCast.UPCAST) && isDynamicType(to)) {
			cast = TypeCast.RESULT(TypeCast.E_DOWNCAST, 99);
		}

		if (cast.type < lower_limit) report(Diagnostic.Kind.ERROR, "typeCast.error."+cast.type, from, to);
		return cast;
	}

	// region 权限管理
	private ToIntMap<String> flagCache = new ToIntMap<>();
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
					report(Diagnostic.Kind.ERROR, "symbol.error.accessDenied.type", type.name(), "package-private", file.name());
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
			if (report) report(Diagnostic.Kind.ERROR, "symbol.error.nonStatic.symbol", type.name(), node.name(), node instanceof FieldNode ? "symbol.field" : "invoke.method");
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
				if (instanceOf(type.name(), file.name(), -1)) return true;
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
		if (report) report(Diagnostic.Kind.ERROR, message, type.name()+(node!=null?"."+node:""), modifier, file.name());
		return false;
	}

	private final MyHashSet<String> constructorFields = new MyHashSet<>();
	public void checkSelfField(FieldNode node, boolean write) {
		boolean constructor = in_constructor;
		if (in_static) {
			if ((node.modifier()&Opcodes.ACC_STATIC) == 0)
				report(Diagnostic.Kind.ERROR, "symbol.error.nonStatic.symbol", file.name(), node.name(), "symbol.field");
		} else if ((node.modifier()&Opcodes.ACC_STATIC) != 0) {
			constructor = false;
		}

		if ((node.modifier()&Opcodes.ACC_FINAL) != 0) {
			if (write) {
				if (constructor) {
					if (!constructorFields.add(node.name())) {
						report(Diagnostic.Kind.ERROR, "symbol.error.field.writeAfterWrite", file.name(), node.name());
					}
				} else {
					report(Diagnostic.Kind.ERROR, "symbol.error.field.writeFinal", file.name(), node.name());
				}
			} else {
				if (constructor) {
					if (!constructorFields.contains(node.name())) {
						report(Diagnostic.Kind.ERROR, "symbol.error.field.readBeforeWrite", file.name(), node.name());
					}
				}
			}
		}
	}
	// endregion

	public Function<String, IClass> GlobalClassEnv() { return globalClassEnv; }

	public void setClass(CompileUnit file) {
		this.file = file;
		this.classEnv = file::resolve;
		// TODO 这玩意应该随着方法变吧
		this.genericEnv = file::getGenericEnv;
	}
	public void setMethod(MethodNode node) {
		constructorFields.clear();
		in_static = (node.modifier &Opcodes.ACC_STATIC) != 0;
		in_constructor = node.name().equals("<init>") || node.name().equals("<clinit>");
		first_statement = true;
	}

	// region 解析符号引用 Class Field Method
	@Nullable
	public IClass getClassOrArray(IType type) { return type.array() > 0 ? ClassContext.anyArray() : classes.getClassInfo(type.owner()); }

	public IClass resolveType(String klass) { return this.file.resolve(klass); }
	@Contract("_ -> param1")
	public IType resolveType(IType type) {
		if (type.genericType() == 0 ? type.rawType().type != Type.CLASS : type.genericType() != 1) return type;

		IClass info = classes.getClassInfo(type.owner());
		if (info == null) {
			info = resolveType(type.owner());
			if (info == null) {
				report(Diagnostic.Kind.ERROR, "symbol.error.noSuchClass", type);
				return type;
			}
			type.owner(info.name());
		}

		Signature sign = info.parsedAttr(info.cp(), Attribute.SIGNATURE);
		int count = sign == null ? 0 : sign.typeParams.size();

		if (type.genericType() == IType.GENERIC_TYPE) {
			Generic type1 = (Generic) type;
			List<IType> gp = type1.children;

			if (gp.size() != count) {
				if (count == 0) report(Diagnostic.Kind.ERROR, "symbol.error.generic.paramCount.0", type.rawType());
				else if (gp.size() != 1 || gp.get(0) != Asterisk.anyGeneric) report(Diagnostic.Kind.ERROR, "symbol.error.generic.paramCount", type.rawType(), gp.size(), count);
			}

			for (int i = 0; i < gp.size(); i++) resolveType(gp.get(i));

			// TODO check GenericSub
		} else if (count > 0) {
			report(Diagnostic.Kind.WARNING, "symbol.warn.generic.rawTypes", type);
		}
		return type;
	}

	private final CharList fieldResolveTmp = new CharList();
	private final SimpleList<Object> fieldResolveResult = new SimpleList<>();
	/**
	 * 将这种格式的字符串 net/minecraft/client/Minecraft/fontRender/FONT_HEIGHT
	 * 解析为类与字段的组合
	 * @return 错误码,null为成功
	 */
	public String resolveClassField(CharList desc, boolean allowClassExpr) {
		CharList sb = fieldResolveTmp;

		IClass directClz = null;
		String anySuccess = "";
		int slash = desc.indexOf("/");
		if (slash >= 0) {
			directClz = file.resolve(desc.toString(0, slash));
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
					fieldResolveResult.clear();
					fieldResolveResult.add(directClz);
					return "";
				}

				return "symbol.error.expression:".concat(desc.toString());
			}
			return "symbol.error.noSuchSymbol:".concat(desc.toString());
		}
		return anySuccess;
	}
	public String resolveField(IClass clz, CharList desc) { return resolveField(clz, null, desc, 0); }
	public String resolveField(IClass clz, IType generic, CharList desc) { return resolveField(clz, generic, desc, 0); }
	private String resolveField(IClass clz, IType fieldType, CharList desc, int prevI) {
		List<Object> result = fieldResolveResult;
		result.add(clz);

		int i = desc.indexOf("/", prevI);
		while (true) {
			String name = desc.toString(prevI, i < 0 ? desc.length() : i);

			// TODO 泛型的处理
			// then FieldNode (RawNode) => getfield opcode
			FieldNode field;
			block: {
				ComponentList fields = classes.fieldList(clz, name);
				if (fields != null) {
					FieldResult fr = fields.findField(this, 0);
					if (fr.error == null) {
						field = fr.field;
						break block;
					}
					return fr.error;
				}
				return "symbol.error.noSuchSymbol:".concat(name);
			}

			Signature cSign = clz.parsedAttr(clz.cp(), Attribute.SIGNATURE);
			if (cSign != null) {
				Signature fSign = field.parsedAttr(clz.cp(), Attribute.SIGNATURE);
				if (fSign != null) {
					MyHashMap<String, IType> knownTps = new MyHashMap<>(cSign.typeParams.size());
					if (fieldType.genericType() != IType.GENERIC_TYPE) {
						for (Map.Entry<String, List<IType>> entry : cSign.typeParams.entrySet()) {
							List<IType> value = entry.getValue();
							knownTps.put(entry.getKey(), value.get(0).genericType() == IType.PLACEHOLDER_TYPE ? OBJECT_TYPE : value.get(0));
						}
					} else {
						Generic gType = (Generic) fieldType;
						Iterator<String> itr = cSign.typeParams.keySet().iterator();
						assert gType.children.size() == cSign.typeParams.size();
						for (IType child : gType.children) knownTps.put(itr.next(), child);
					}

					fieldType = fSign.values.get(0).resolveTypeParam(knownTps, cSign.typeParams);
				}
			}
			result.add(field);

			if (i < 0) {
				if (fieldType != null) result.add(fieldType);
				return null;
			}
			prevI = i+1;
			i = desc.indexOf("/", prevI);

			Type type = field.fieldType();
			if (type.isPrimitive()) {
				if (i < 0) {
					result.add(fieldType);
					return null;
				}
				// 不能解引用基本类型
				return "symbol.error.derefPrimitiveField";
			}

			clz = type.array() > 0 ? ClassContext.anyArray() : classes.getClassInfo(type.owner);
			if (clz == null) return "symbol.error.noSuchClass:".concat(type.owner);
		}
	}
	public Object popLastResolveResult() { return fieldResolveResult.pop(); }
	public Object[] fieldResolveResult() {
		if (fieldResolveResult.isEmpty()) throw new IllegalStateException("no result");
		Object[] array = fieldResolveResult.toArray();
		fieldResolveResult.clear();
		return array;
	}
	// endregion


	public Annotation getAnnotation(IClass type, Attributed node, String annotation, boolean rt) {
		return Attributes.getAnnotation(Attributes.getAnnotations(type.cp(), node, rt), annotation);
	}

	public void addException(IType type) {
		TypeCast.Cast cast = TypeCast.checkCast(type, new Type("java/lang/RuntimeException"), classEnv, genericEnv);
		if (cast.type >= 0) return;
		// TODO 丢给别人
		report(Diagnostic.Kind.NOTE, "丢出这个异常", type);
	}

	public Map<String, Variable> variables = Collections.emptyMap();
	public Variable tryVariable(String name) {
		return variables.get(name);
	}
	public MethodNode tryImportMethod(String name) {
		if (name.equals("test")) {
			IClass classInfo = classes.getClassInfo("java/util/Objects");
			return (MethodNode) classInfo.methods().get(classInfo.getMethod("requireNonNull"));
		}
		return null;
	}
	public Object[] tryImportField(String name) {
		// 好消息是，至少alias可以在这里实现
		if (name.equals("testF")) {
			IClass classInfo = classes.getClassInfo("java/lang/System");
			return new Object[] {classInfo, classInfo.fields().get(classInfo.getField("out"))};
		}
		return null;
	}

	public boolean instanceOf(CharSequence testClass, CharSequence instClass, int isInterface) {
		IClass clz;
		do {
			if (isInterface <= 0 && testClass.equals(instClass)) return true;

			clz = classes.getClassInfo(instClass);
			if (clz == null) {
				report(Diagnostic.Kind.ERROR, "symbol.error.noSuchClass", instClass);
				return false;
			}

			if (isInterface >= 0 && clz.interfaces().contains(testClass)) return true;

			instClass = clz.parent();
		} while (instClass != null);
		return false;
	}

	public IType getCommonParent(IType a, IType b) throws UnableCastException {
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
			report(Diagnostic.Kind.ERROR, "symbol.error.noSuchClass", a);
			return a;
		}
		// noinspection all
		IClass infoB = classes.getClassInfo(b.owner());
		if (infoB == null) {
			report(Diagnostic.Kind.ERROR, "symbol.error.noSuchClass", b);
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
				report(Diagnostic.Kind.ERROR, "symbol.error.generic.paramCount", ga.children.size(), gb.children.size());
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

			String commonParent = null;
			int minIndex = listB.size();
			for (int i = 0; i < listA.size(); i++) {
				String klass = listA.get(i);

				int j = listB.getValueOrDefault(klass, minIndex);
				if (j < minIndex) {
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
			report(Diagnostic.Kind.ERROR, "symbol.error.noSuchClass", e.getMessage());
			return a;
		}
	}
	// region 应该代理给不知道哪个类的
	private static final IntMap<ExprNode> EMPTY = new IntMap<>(0);
	public IntMap<ExprNode> getDefaultValue(IClass owner, MethodNode mn) {
		if (mn.name().equals("emptyTest")) {
			IntMap<ExprNode> test = new IntMap<>();
			test.putInt(0, new roj.compiler.ast.expr.Constant(Type.std(Type.INT), AnnVal.valueOf(114514)));
			test.putInt(1, new roj.compiler.ast.expr.Constant(new Type("java/lang/String"), "STRING"));
			return test;
		}
		return EMPTY;
	}

	// 被cast和intanceof调用，若返回true则禁用编译警告和化简
	public boolean isDynamicType(IType type) {
		return false;
	}

	public MethodNode getBinaryOverride(IType left, IType right, short operator) {
		if (operator == JavaLexer.mul && left.array() == 0 && left.owner().equals("java/lang/String") && right.getActualType() == Type.INT) {
			return new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "repeat", "(I)Ljava/lang/String;");
		}
		return null;
	}
	public MethodNode getUnaryOverride(IType type, short operator, boolean post) {
		if (operator == JavaLexer.logic_not && type.array() == 0 && type.owner().equals("java/lang/String")) {
			return new MethodNode(Opcodes.ACC_PUBLIC, "java/lang/String", "isEmpty", "()Z");
		}
		return null;
	}

	public Object getConstantValue(IClass fch, FieldNode fn) {
		assert fch.fields().contains(fn);
		ConstantValue cv = fn.parsedAttr(fch.cp(), Attribute.ConstantValue);
		if (cv != null) {
			switch (cv.c.type()) {
				case Constant.INT: return AnnVal.valueOf(((CstInt) cv.c).value);
				case Constant.FLOAT: return AnnVal.valueOf(((CstFloat) cv.c).value);
				case Constant.LONG: return AnnVal.valueOf(((CstLong) cv.c).value);
				case Constant.DOUBLE: return AnnVal.valueOf(((CstDouble) cv.c).value);
				case Constant.CLASS: return cv.c;
				case Constant.STRING: return cv.c.getEasyCompareValue();
			}
		}

		if ((fch.modifier()&fn.modifier&Opcodes.ACC_ENUM) != 0 && annotationEnv) {
			return new AnnValEnum(fch.name(), fn.name());
		}

		return null;
	}
	// endregion
	// region CompileUnit用到的
	private static final ThreadLocal<List<Object>> FTL = new ThreadLocal<>();

	public static void set(CompileContext cache) {
		SimpleList<Object> list = new SimpleList<>(2);
		list.add(new MutableInt(1));
		list.add(cache);
		FTL.set(list);
	}

	public static CompileContext get() {
		List<Object> list = FTL.get();
		return (CompileContext) list.get(((Number) list.get(0)).intValue());
	}

	public static void depth(int ud) {
		List<Object> list = FTL.get();
		MutableInt mi = (MutableInt) list.get(0);
		int v = mi.addAndGet(ud);
		if (v >= list.size()) list.add(new CompileContext(((CompileContext) list.get(1)).classes));
	}

	public List<GenericPrimer> genericDeDup = new SimpleList<>();
	public MyHashSet<IType> toResolve_unc = new MyHashSet<>();

	public MyHashSet<String> names = new MyHashSet<>();
	public SimpleList<AnnotationPrimer> annotationTmp = new SimpleList<>();
	public CharList tmpList = new CharList();

	public MyHashMap<String, IClass> importCache = new MyHashMap<>();
	public MyHashSet<String> annotationMissed = new MyHashSet<>();

	public ExprParser ep = new ExprParser(0);
	public BlockParser bp = new BlockParser(0);
	// endregion
}