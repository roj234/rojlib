package roj.compiler.resolve;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.*;
import roj.asm.attr.Attribute;
import roj.asm.attr.InnerClasses;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.*;
import roj.compiler.CompileUnit;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Types;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Asterisk;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.library.Library;
import roj.compiler.library.SymbolCacheLibrary;
import roj.text.Interner;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 提供类信息的上下文，可以用来进行类型推断，而不需要创建完整的Compiler
 * @author Roj234
 * @since 2025/07/30 06:34
 */
public class Resolver {
	private static final XashMap.Builder<String, CompileUnit> COMPILE_UNIT_BUILDER = XashMap.noCreation(CompileUnit.class, "name");
	private static final XashMap.Builder<ClassDefinition, LinkedClass> CLASS_EXTRA_INFO_BUILDER = XashMap.builder(ClassDefinition.class, LinkedClass.class, "owner", "_next", Hasher.identity());

	protected final XashMap<String, CompileUnit> compileUnits = COMPILE_UNIT_BUILDER.create();

	// Libraries
	protected final HashMap<String, Object> libraryByName = new HashMap<>();
	protected final List<Library> libraries = new ArrayList<>(), unenumerableLibraries = new ArrayList<>();

	protected final XashMap<ClassDefinition, LinkedClass> extraInfos = CLASS_EXTRA_INFO_BUILDER.create();
	protected Map<String, List<String>> shortNameIndex = Collections.emptyMap();

	public Resolver() {unenumerableLibraries.addAll(SymbolCacheLibrary.SYSTEM_MODULES);}
	public Resolver(boolean addRuntimeLibrary) {}

	public void addLibrary(Library library) {
		var content = library.indexedContent();
		if (content.isEmpty()) {
			unenumerableLibraries.add(library);
		} else {
			for (String className : content)
				libraryByName.put(className, library);
		}
		libraries.add(library);
		shortNameIndex = Collections.emptyMap();
	}

	public final ClassNode resolve(CharSequence name) {
		ClassNode clz = compileUnits.get(name);
		if (clz == null) {
			var entry = libraryByName.getEntry(name);
			if (entry != null) {
				synchronized (entry) {
					var value = entry.getValue();
					if (value instanceof ClassNode c) return c;

					clz = ((Library) value).get(name);
					clz = onClassLoaded(entry.getKey(), clz);
					entry.setValue(clz);
				}
				return clz;
			}

			for (int i = unenumerableLibraries.size()-1; i >= 0; i--) {
				var library = unenumerableLibraries.get(i);
				if ((clz = library.get(name)) != null) {
					var key = name.toString();
					clz = onClassLoaded(key, clz);
					synchronized (libraryByName) {libraryByName.put(key, clz);}
					return clz;
				}
			}
		}
		return clz;
	}
	protected ClassNode onClassLoaded(String name, ClassNode clazz) {return clazz;}

	//region 短名称索引 (importAny)
	/**
	 * 通过短名称获取全限定名（若存在）
	 * @return 包含候选包名的列表 例如 [java/lang]
	 */
	public final List<String> getPackageNameByShortName(String shortName) {
		var index = shortNameIndex;
		if (index.isEmpty()) index = buildPackageCache();
		return index.getOrDefault(shortName, Collections.emptyList());
	}
	private synchronized Map<String, List<String>> buildPackageCache() {
		var fastPath = shortNameIndex;
		if (!fastPath.isEmpty()) return fastPath;

		fastPath = new HashMap<>();

		for (Library library : libraries) {
			for (String name : library.content()) {
				if (!name.contains("$") && !name.startsWith("[")) addFastPath(name, fastPath);
			}
		}
		// 大部分是空的，但SymbolCache不是，因为它的类太多了
		for (Library module : unenumerableLibraries) {
			for (String name : module.content()) {
				addFastPath(name, fastPath);
			}
		}

		for (Map.Entry<String, List<String>> entry : fastPath.entrySet()) {
			var value = entry.getValue();
			if (value.size() == 1) entry.setValue(Collections.singletonList(Interner.intern(value.get(0))));
			else for (int i = 0; i < value.size(); i++) value.set(i, Interner.intern(value.get(i)));
		}

		//Unaligned.U.storeFence();
		return shortNameIndex = fastPath;
	}
	private void addFastPath(String n, Map<String, List<String>> fastPath) {
		int i = n.lastIndexOf('/');
		String packag = i < 0 ? "" : n.substring(0, i);
		String name = n.substring(i+1);

		var allPackages = fastPath.computeIfAbsent(name, Helpers.fnArrayList());
		if (!allPackages.contains(packag)) allPackages.add(packag);
	}
	//endregion
	//region ResolveHelper
	@NotNull public synchronized LinkedClass link(@NotNull ClassDefinition info) { return extraInfos.computeIfAbsent(info); }
	public synchronized void unlink(ClassDefinition file) {extraInfos.removeKey(file);}

	@NotNull public final ToIntMap<String> getHierarchyList(ClassDefinition info) {return link(info).getHierarchyList(this);}
	@NotNull public final ComponentList getMethodList(ClassDefinition info, String name) {return link(info).getMethods(this).getOrDefault(name, ComponentList.NOT_FOUND);}
	@NotNull public final ComponentList getFieldList(ClassDefinition info, String name) {return link(info).getFields(this).getOrDefault(name, ComponentList.NOT_FOUND);}
	@Nullable public final List<IType> getTypeArgumentsFor(ClassDefinition info, String superType) {return link(info).getTypeParamOwner(this).get(superType);}
	@NotNull public final Map<String, InnerClasses.Item> getInnerClassInfo(ClassDefinition info) {return link(info).getInnerClasses(this);}
	public final AnnotationType getAnnotationDescriptor(ClassDefinition info) {return link(info).annotationInfo();}
	public final void fillAnnotationDefault(AnnotationPrimer annotation) {
		var self = getAnnotationDescriptor(resolve(annotation.type()));
		for (var entry : self.elementDefault.entrySet()) annotation.raw().putIfAbsent(entry.getKey(), entry.getValue());
	}
	//endregion
	//region 无上下文依赖的类型转换&推断
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
	 * @implNote 若{@code targetType}在类路径中不存在，将通过{@link LavaCompiler#report}方法抛出编译错误
	 */
	@Nullable
	public final List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType) {return inferGeneric(typeInst, targetType, null);}
	@Nullable
	public final List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType, @Nullable Map<?, ?> temp) {
		var info = resolve(typeInst.owner());

		if (targetType.equals(typeInst.owner())) {
			// 20250708 这里返回null应该没有问题吧？
			if (!(typeInst instanceof Generic generic)) return null;

			// 将<>擦除为上界
			if (generic.children.size() == 1 && generic.children.get(0) == Asterisk.anyGeneric) {
				return info.getAttribute(info.cp, Attribute.SIGNATURE).getBounds();
			}

			return generic.children;
		}

		List<IType> bounds = getTypeArgumentsFor(info, targetType);

		if (bounds == null || bounds.getClass() == ArrayList.class) return bounds;

		// bounds是SimpleList$1，代表其中含有类型参数，需要动态解析
		List<IType> children;
		if (typeInst instanceof Generic g) {
			children = g.children;

			if (g.children.size() == 1 && g.children.get(0) == Asterisk.anyGeneric) {
				// 20250411 这里应该用目标类型……之前写错了，现在是擦除到上界，因为anyGeneric嘛
				info = resolve(targetType);
				var sign = info.getAttribute(info.cp, Attribute.SIGNATURE);

				HashMap<String, IType> realType = temp==null?new HashMap<>():Helpers.cast(temp);
				Inferrer.fillDefaultTypeParam(sign.typeParams, realType);

				bounds = new ArrayList<>(bounds);
				for (int i = 0; i < bounds.size(); i++) {
					bounds.set(i, Inferrer.clearTypeParam(bounds.get(i), realType, sign.typeParams));
				}

				realType.clear();
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
		Map<String, IType> visType = new HashMap<>();

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

				typeInst = resolve(type.owner());
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
		ClassDefinition info = resolve(testClass);
		if (info == null) return false;
		return getHierarchyList(info).containsKey(instClass);
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
	public final IType getCommonAncestor(IType a, IType b) {
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

		ClassDefinition infoA = resolve(a.owner());
		if (infoA == null) return a; // should be checked by CompileContext#resolveType
		ClassDefinition infoB = resolve(b.owner());
		if (infoB == null) return a; //

		String commonParent = getCommonAncestor(infoA, infoB);
		assert commonParent != null;

		var info = resolve(commonParent);

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

		List<IType> aGeneric = inferGeneric(a, commonParent);
		List<IType> bGeneric = inferGeneric(b, commonParent);

		// 如果不是泛型类，那么直接返回
		if (extendType == Generic.EX_NONE && aGeneric == null && bGeneric == null) {
			// 少创建对象
			if (info == infoA) return a;
			if (info == infoB) return b;
			return Type.klass(commonParent, a.array());
		}

		// 否则进行泛型推断
		// CP(List<String>, SimpleList<String>) => List<String>
		// CP(List<?>, SimpleList<String>) => List<?>
		// CP(List<String>, SimpleList<?>) => List<?>

		if (aGeneric == null || bGeneric == null || aGeneric.size() != bGeneric.size()) throw new AssertionError();

		//Arrays.asList也许也可以？
		List<IType> typeParams = ArrayList.asModifiableList(new IType[aGeneric.size()]);
		for (int i = 0; i < aGeneric.size(); i++) {
			IType aa = aGeneric.get(i);
			IType bb = bGeneric.get(i);
			// 如果再次递归那么用Pair把所有调用链上的<a,b>替换为any
			IType c = aa.equals(a) && bb.equals(b) ? Signature.any() : getCommonAncestor(aa, bb);
			typeParams.set(i, c);
		}

		// TODO primitive generic
		// 	CP(ArrayList<int>, List<Integer>) => List<Integer>
		//  CP(List<int>, List<Integer>) => Collection<Integer>

		Generic generic = new Generic(commonParent, a.array(), extendType);
		generic.children = typeParams;
		return generic;
	}
	/**
	 * 返回ab两个类(Class)的共同祖先
	 */
	public final String getCommonAncestor(ClassDefinition infoA, ClassDefinition infoB) {
		ToIntMap<String> tmp,
				listA = getHierarchyList(infoA),
				listB = getHierarchyList(infoB);

		if (listA.size() > listB.size()) {
			tmp = listA;
			listA = listB;
			listB = tmp;
		}

		String commonAncestor = infoA.name();
		int minIndex = listB.size();
		for (var entry : listA.selfEntrySet()) {
			String klass = entry.getKey();

			int val = listB.getOrDefault(klass, minIndex)&0x7FFF_FFFF;
			int j = val&0xFFFF;
			if (j < minIndex || (j == minIndex && val < minIndex)) {
				commonAncestor = klass;
				minIndex = j;
			}
		}
		return commonAncestor;
	}
	//endregion
	//region 诊断API
	public Function<Diagnostic, Boolean> reporter = new TextDiagnosticReporter(0, 0, 0);
	public boolean hasError;

	public boolean hasError() {return hasError;}
	public void report(ClassDefinition source, Kind kind, int pos, String code) { report(source, kind, pos, code, (Object[]) null);}
	public void report(ClassDefinition source, Kind kind, int pos, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, pos, pos, code, args);
		if (reporter.apply(diagnostic)) hasError = true;
	}
	public void report(ClassDefinition source, Kind kind, int start, int end, String code, Object... args) {
		Diagnostic diagnostic = new Diagnostic(source, kind, start, end, code, args);
		if (reporter.apply(diagnostic)) hasError = true;
	}
	//endregion

	protected static final ClassNode AnyArray;
	static {
		var array = Object[].class;
		var ref = new ClassNode();
		ref.name("[Ljava/lang/Object;");
		ref.parent(array.getSuperclass().getName().replace('.', '/'));
		for (Class<?> itf : array.getInterfaces()) ref.addInterface(itf.getName().replace('.', '/'));
		ref.fields.add(new FieldNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "length", "I"));
		ref.methods.add(new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL, "java/lang/Object", "clone", "()Ljava/lang/Object;"));
		AnyArray = ref;
	}
	public static ClassNode anyArray() {return AnyArray;}
	public static List<String> arrayInterfaces() {return AnyArray.interfaces();}
	public static FieldNode arrayLength() {return AnyArray.fields.get(0);}
	public static MethodNode arrayClone() {return AnyArray.methods.get(0);}

	private static final Logger logger = Logger.getLogger("Lavac/Debug");
	public static Logger debugLogger() {return logger;}
}