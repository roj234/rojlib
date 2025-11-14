package roj.compiler.resolve;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.*;
import roj.asm.attr.Attribute;
import roj.asm.attr.InnerClasses;
import roj.asm.type.IType;
import roj.asm.type.ParameterizedType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.*;
import roj.compiler.CompileUnit;
import roj.compiler.api.Types;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.WildcardType;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.Kind;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.library.JarLibrary;
import roj.compiler.library.Library;
import roj.compiler.library.RuntimeLibrary;
import roj.io.IOUtil;
import roj.reflect.resolver.IResolver;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 提供类信息的上下文，可以用来进行类型推断，而不需要创建完整的Compiler
 * @author Roj234
 * @since 2025/07/30 06:34
 */
public class Resolver implements IResolver {
	private static File cacheDirectory;
	private static boolean cacheEnabled;

	static {
		String property = System.getProperty("roj.compiler.symbolCache", ".lavaCache");
		//noinspection AssignmentUsedAsCondition
		if ((cacheEnabled = !property.equals("DISABLE"))) {
			cacheDirectory = new File(property);
		}
	}

	public static final class Libs {
		public static final JarLibrary SELF;
		static {
			try {
				SELF = new JarLibrary(IOUtil.getJar(Resolver.class));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static void setCacheEnabled(boolean b) {cacheEnabled = b;}

	@Nullable
	public static File getCacheDirectory(String subPath) {return cacheEnabled ? new File(cacheDirectory, subPath) : null;}
	public static void setCacheDirectory(File o) {cacheDirectory = o;}

	private static final XashMap.Template<String, CompileUnit> COMPILE_UNIT_TEMPLATE = XashMap.forType(String.class, CompileUnit.class).key("name").build();
	private static final XashMap.Template<ClassNode, LinkedClass> CLASS_EXTRA_INFO_TEMPLATE = XashMap.forType(ClassNode.class, LinkedClass.class).key("owner").hasher(Hasher.identity()).newValue(LinkedClass::new).build();

	protected final XashMap<String, CompileUnit> compileUnits = COMPILE_UNIT_TEMPLATE.create();

	// Libraries
	protected final HashMap<String, Object> libraryByName = new HashMap<>();
	// 布局：
	// [0, unernumerableEnd): 不可枚举的库
	// [unernumerableEnd, libraries.size()]: 可枚举的库
	protected final List<Library> libraries = new ArrayList<>();
	protected int unernumerableEnd;

	protected final XashMap<ClassNode, LinkedClass> extraInfos = CLASS_EXTRA_INFO_TEMPLATE.create();
	protected Map<String, List<String>> shortNameIndex = Collections.emptyMap();

	public Resolver() {this(true);}
	public Resolver(boolean addRuntimeLibrary) {
		if (addRuntimeLibrary) {
			libraries.add(RuntimeLibrary.ALL_MODULES);
			unernumerableEnd = 1;
		}
	}

	/**
	 * 先添加的优先级更高
	 */
	public void addLibrary(Library library) {
		var content = library.indexedContent();
		if (content.isEmpty()) {
			libraries.add(unernumerableEnd++, library);
		} else {
			libraries.add(library);
			for (String className : content)
				libraryByName.putIfAbsent(className, library);
		}
		shortNameIndex = Collections.emptyMap();
	}

	public final ClassNode resolve(CharSequence name) {
		ClassNode clz = compileUnits.get(name);
		if (clz == null) {
			var entry = libraryByName.getEntry(name);
			if (entry != null) {
				var value = entry.getValue();
				if (value instanceof ClassNode c) return c;

				synchronized (entry) {
					value = entry.getValue();
					if (value instanceof ClassNode c) return c;

					clz = ((Library) value).get(name);
					clz = onClassLoaded(entry.getKey(), clz);
					entry.setValue(clz);
				}
				return clz;
			}

			for (int i = 0; i < unernumerableEnd; i++) {
				var library = libraries.get(i);
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
		String moduleName = null;

		Map<String, List<String>> javacSB = fastPath;
		Consumer<String> addExported = name -> {
			if (!name.contains("$")) {
				addFastPath(name, javacSB);
			}
		};

		for (Library library : libraries) {
			library.exportedContent(moduleName, addExported);
		}

		for (Map.Entry<String, List<String>> entry : fastPath.entrySet()) {
			var value = entry.getValue();
			if (value.size() == 1) entry.setValue(Collections.singletonList(value.get(0).intern()));
			else for (int i = 0; i < value.size(); i++) value.set(i, value.get(i).intern());
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
	@NotNull public LinkedClass link(@NotNull ClassNode info) {
		LinkedClass linkedClass = extraInfos.get(info);
		if (linkedClass == null) {
			synchronized (this) {
				linkedClass = extraInfos.computeIfAbsent(info);
			}
		}
		return linkedClass;
	}
	public synchronized void unlink(ClassNode file) {extraInfos.removeKey(file);}

	@NotNull public final ToIntMap<String> getHierarchyList(ClassNode info) {return link(info).getHierarchyList(this);}
	@NotNull public final ComponentList getMethodList(ClassNode info, String name) {return link(info).getMethods(this).getOrDefault(name, ComponentList.NOT_FOUND);}
	@NotNull public final ComponentList getFieldList(ClassNode info, String name) {return link(info).getFields(this).getOrDefault(name, ComponentList.NOT_FOUND);}
	@Nullable public final List<IType> getTypeArgumentsFor(ClassNode info, String superType) {return link(info).getTypeParamOwner(this).get(superType);}
	@NotNull public final Map<String, InnerClasses.Item> getInnerClassInfo(ClassNode info) {return link(info).getInnerClasses(this);}
	public final AnnotationType getAnnotationDescriptor(ClassNode info) {return link(info).annotationInfo();}
	public final void fillAnnotationDefault(AnnotationPrimer annotation) {
		var self = getAnnotationDescriptor(resolve(annotation.type()));
		for (var entry : self.elementDefault.entrySet()) annotation.raw().putIfAbsent(entry.getKey(), entry.getValue());
	}
	//endregion
	//region 无上下文依赖的类型转换&推断
	private static final AbstractList<IType> ANY_GENERIC_LIST = new AbstractList<>() {
		@Override public IType get(int index) {return WildcardType.anyType;}
		@Override public int size() {return 0;}
	};
	/**
	 * Infers the actual type arguments for a given target type within the generic context of an instance.
	 * <p>
	 * This method determines the concrete type parameters of {@code targetType} when it appears
	 * in the inheritance hierarchy (superclass or interface) of {@code typeInst}.
	 * <p>
	 * <b>Example Scenario</b>: For a type declaration {@code class A<K extends Number, V> extends B<String> implements C<K>}
	 * <ul>
	 *   <li>If the {@code targetType} is its superclass {@code B}, the concrete type argument {@code [String]} is returned.</li>
	 *   <li>If the {@code targetType} is interface {@code C} and {@code typeInst} refers to {@code A} as a raw type (e.g., just {@code A}),
	 *     the bounded type argument {@code [Number]} is returned.</li>
	 *   <li>If {@code typeInst} is {@code A<X,Y>} (where X and Y are type variables), and the target type is interface {@code C},
	 *     the variable reference {@code [X]} is returned.</li>
	 * </ul>
	 * The inference logic follows these priorities:
	 * <ol>
	 *   <li><b>Inheritance Chain Check</b> - If {@code targetType} is not found in the inheritance chain
	 *     of {@code typeInst}, or if {@code targetType} is not a generic type itself that can have parameters,
	 *     returns {@code null}.
	 *     <pre>{@code inferGeneric(A<x,y>, D) → null}</pre></li>
	 *   <li><b>Concrete Type Resolution</b> - If the target type's parameters are already concrete and resolvable,
	 *     a list of these concrete {@link IType} objects is returned.
	 *     <pre>{@code inferGeneric(A<x,y>, B) → [String]}</pre></li>
	 *   <li><b>Raw Type Handling</b> - If {@code typeInst} is a raw type or has no explicit generic parameters
	 *     (e.g., {@code A} or {@code A<>}), the upper bounds of the target type's parameters are generally returned.
	 *     <pre>{@code inferGeneric(A, C) → [Number]}</pre></li>
	 *   <li><b>Dynamic Variable Resolution</b> - If the target type's parameters depend on
	 *     {@code typeInst}'s generic variables, these variables are resolved against the generic context
	 *     of {@code typeInst}, and their corresponding {@link IType} references are returned.
	 *     <pre>{@code inferGeneric(A<x,y>, C) → [x]}</pre></li>
	 * </ol>
	 *
	 * @param typeInst The generic type instance to analyze (can be a concrete generic type, raw type, or wildcard generic type).
	 * @param targetType The fully qualified name of the target type (e.g., "java.util.List", using '/' as separator).
	 *
	 * @return A list of the resolved actual generic parameters for the target type. Possible return values:
	 * <ul>
	 *   <li>{@code null} - If the {@code targetType} is not found in the inheritance hierarchy of {@code typeInst},
	 *                       or if {@code typeInst} does not provide sufficient generic information to resolve {@code targetType}'s parameters.</li>
	 *   <li>An empty list - If {@code targetType} is a non-generic type, or if it is a generic type instantiated as raw.</li>
	 *   <li>A list of {@link IType} objects (e.g., {@code [String, Integer]}),
	 *       or {@link IType} objects representing resolved type variables or their bounds.</li>
	 * </ul>
	 * @throws IllegalStateException If the inheritance hierarchy data is inconsistent or invalid,
	 *         e.g., {@code targetType} is declared as a supertype/interface but cannot be definitively resolved within the hierarchy.
	 * @implNote If {@code targetType} cannot be found or resolved on the classpath, a compile-time error
	 *           will typically be reported using the internal {@link #report} method.
	 */
	@Nullable
	public final List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType) {return inferGeneric(typeInst, targetType, null);}
	@Nullable
	public final List<IType> inferGeneric(@NotNull IType typeInst, @NotNull String targetType, @Nullable Map<?, ?> temp) {
		var info = resolve(typeInst.owner());

		if (targetType.equals(typeInst.owner())) {
			// 20250708 这里返回null应该没有问题吧？
			if (!(typeInst instanceof ParameterizedType parameterizedType)) return null;

			// 将<>擦除为上界
			if (parameterizedType.typeParameters.size() == 1 && parameterizedType.typeParameters.get(0) == WildcardType.anyGeneric) {
				return info.getAttribute(info.cp, Attribute.SIGNATURE).getBounds();
			}

			return parameterizedType.typeParameters;
		}

		List<IType> bounds = getTypeArgumentsFor(info, targetType);

		if (bounds == null || bounds.getClass() == ArrayList.class) return bounds;

		// bounds是SimpleList$1，代表其中含有类型参数，需要动态解析
		List<IType> children;
		if (typeInst instanceof ParameterizedType g) {
			children = g.typeParameters;

			if (g.typeParameters.size() == 1 && g.typeParameters.get(0) == WildcardType.anyGeneric) {
				// 20250411 这里应该用目标类型……之前写错了，现在是擦除到上界，因为anyGeneric嘛
				info = resolve(targetType);
				var sign = info.getAttribute(info.cp, Attribute.SIGNATURE);

				HashMap<String, IType> typeParameters = temp==null?new HashMap<>():Helpers.cast(temp);
				Inferrer.substituteMissingTypeParametersToBound(sign.typeVariables, typeParameters);

				bounds = new ArrayList<>(bounds);
				for (int i = 0; i < bounds.size(); i++) {
					bounds.set(i, Inferrer.substituteTypeVariables(bounds.get(i), typeParameters, sign.typeVariables));
				}

				typeParameters.clear();
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
		Map<String, IType> substitution = new HashMap<>();

		int depthInfo = getHierarchyList(typeInst).getOrDefault(target, -1);
		if (depthInfo == -1) throw new IllegalStateException("无法从"+typeInst.name()+"<"+params+">推断"+target);

		loop:
		for(;;) {
			var sign = typeInst.getAttribute(typeInst.cp, Attribute.SIGNATURE);

			int i = 0;
			final List<IType> values = sign.values;
			Map<String, List<IType>> typeVariables = sign.typeVariables;

			substitution.clear();
			for (String name : typeVariables.keySet())
				substitution.put(name, params.get(i++));

			// 250915 第一个和non static generic兼容有关的更新
			var nonStaticGenericParent = typeInst;
			while (true) {
				var item = link(nonStaticGenericParent).getInnerClasses(this).get(nonStaticGenericParent.name());
				if (item == null || (item.modifier & Opcodes.ACC_STATIC) != 0) break;
				nonStaticGenericParent = resolve(item.parent);
				if (nonStaticGenericParent == null) break;

				if (typeVariables == sign.typeVariables) typeVariables = new HashMap<>(typeVariables);

				sign = nonStaticGenericParent.getAttribute(typeInst.cp, Attribute.SIGNATURE);
				for (var entry : sign.typeVariables.entrySet()) {
					String name = entry.getKey();

					typeVariables.putIfAbsent(name, entry.getValue());
					substitution.putIfAbsent(name, params.get(i++));
				}

			}

			// < 0 => flag[0x80000000] => 从接口的接口继承，而不是父类的接口
			i = depthInfo < 0 ? 1 : 0;
			for(;;) {
				IType type = values.get(i);
				if (target.equals(type.owner())) {
					// rawtypes
					if (type.kind() == IType.SIMPLE_TYPE) return Collections.emptyList();

					var rubber = (ParameterizedType) Inferrer.substituteTypeVariables(type, substitution, typeVariables);
					return rubber.typeParameters;
				}

				typeInst = resolve(type.owner());
				depthInfo = getHierarchyList(typeInst).getOrDefault(target, -1);
				if (depthInfo != -1) {
					var rubber = (ParameterizedType) Inferrer.substituteTypeVariables(type, substitution, typeVariables);
					params = rubber.typeParameters;
					continue loop;
				}

				i++;
				assert i < values.size();
			}
		}
	}
	/**
	 * Checks if a given source type can be widened or is equivalent to a target type.
	 * <p>
	 * This method determines whether {@code testClass} is assignable to {@code instClass},
	 * mimicking the behavior of the Java language's `instanceof` operator or a widening
	 * reference conversion. It essentially checks if {@code testClass} is a subtype of,
	 * or the same type as, {@code instClass}.
	 * <p>
	 * <b>Note</b>: Both parameters must be provided as fully qualified names using
	 * '/' as a separator (e.g., "java/lang/Object"). This method currently
	 * does not support array types.
	 *
	 * @param testClass The fully qualified name of the source type to be checked.
	 * @param instClass The fully qualified name of the target type.
	 * @return {@code true} if {@code testClass} can be widened or is equivalent to {@code instClass};
	 *         {@code false} if either type does not exist or if the conversion is not possible according to Java's rules.
	 * @see Class#isInstance(Object)
	 * @see <a href="https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.5">JLS 5.1.5. Widening Reference Conversions</a>
	 */
	public final boolean instanceOf(String testClass, String instClass) {
		ClassNode info = resolve(testClass);
		if (info == null) return false;
		return getHierarchyList(info).containsKey(instClass);
	}

	/**
	 * Computes the least common supertype (LCS) for two given types, {@code a} and {@code b}.
	 * <p>
	 * This algorithm supports a variety of type categories, including primitive types, arrays,
	 * and complex generic types, determining the most specific type that is a supertype
	 * of both input types.
	 * <ol>
	 *   <li><b>Primitive Types</b> - For numeric primitives, the common supertype will be
	 *     their corresponding wrapper class or {@code java.lang.Number} (e.g., LCS of {@code int} and {@code double} is {@code Number}).
	 *     Non-numeric primitives (like {@code boolean} if combined with other object types) will generally result in {@code java.lang.Object}.</li>
	 *   <li><b>Array Types</b> - For arrays of different dimensions or component types,
	 *     the LCS resolution might result in {@code java.lang.Object} or {@code java.lang.Cloneable} & {@code java.lang.Serializable},
	 *     depending on the array commonality and dimensions.</li>
	 *   <li><b>Generic Types</b> - The generic parameters of the LCS are determined by
	 *     recursively computing the LCS for corresponding type arguments.
	 *     For instance, the LCS of {@code List<String>} and {@code List<Integer>} is typically {@code List<?>}.
	 *     Specific handling is applied for wildcards, aiming for the most precise common bounding.</li>
	 * </ol>
	 *
	 * @param a The first type.
	 * @param b The second type.
	 * @return The least common supertype of {@code a} and {@code b}.
	 *         <ul>
	 *           <li>If one or both types are {@link IType#BOUNDED_WILDCARD} (representing {@code Materialized Type Param}), their bounds are considered for LCS.</li>
	 *           <li>If a raw type is involved (i.e., not a generic instantiation), the erased raw type will be used.</li>
	 *           <li>If generic parameters mismatch (e.g., different numbers of type arguments or unresolvable bounds
	 *               after recursive inference), it might throw an {@link AssertionError} if the structure is invalid,
	 *               or resolve to a wildcard type (e.g., {@code ?}) for that specific parameter.</li>
	 *         </ul>
	 * @throws AssertionError If internal assumptions about generic type parameter counts or structure are violated
	 *                          during the inference process, indicating an unexpected state.
	 */
	public final IType getCommonAncestor(IType a, IType b) {
		if (a.equals(b)) return a;

		if (a.kind() >= IType.BOUNDED_WILDCARD) {
			a = ((WildcardType) a).getBound();
			if (a == null) return b.isPrimitive() ? Type.getWrapper(b) : b;
		}
		if (b.kind() >= IType.BOUNDED_WILDCARD) {
			b = ((WildcardType) b).getBound();
			if (b == null) return a.isPrimitive() ? Type.getWrapper(a) : a;
		}

		int capa = Type.getSort(a.getActualType())-1;
		int capb = Type.getSort(b.getActualType())-1;
		// 双方都是数字
		if ((capa&7) != 0 && (capb&7) != 0) return Type.primitive(Type.getBySort(Math.max(capa, capb)+1));
		// 没有任何一方是对象 (boolean=0 | void=-1)
		if ((capa|capb) < 8) return Types.OBJECT_TYPE;

		if (a.isPrimitive()) a = Type.getWrapper(a);
		if (b.isPrimitive()) b = Type.getWrapper(b);

		// noinspection all
		if (a.array() != b.array()) {
			// common parent of Object[][] | Object[]
			return Math.min(a.array(), b.array()) == 0
					? Types.OBJECT_TYPE
					: new WildcardType(Arrays.asList(Type.klass("java/lang/Cloneable"), Type.klass("java/lang/Serializable")));
		}

		ClassNode infoA = resolve(a.owner());
		if (infoA == null) return a; // should be checked by CompileContext#resolveType
		ClassNode infoB = resolve(b.owner());
		if (infoB == null) return a; //

		String commonParent = getCommonAncestor(infoA, infoB);
		assert commonParent != null;

		var info = resolve(commonParent);

		int extendType = a instanceof ParameterizedType ga ? ga.wildcard : ParameterizedType.NO_WILDCARD;
		int extendType2 = b instanceof ParameterizedType gb ? gb.wildcard : ParameterizedType.NO_WILDCARD;
		if (extendType != extendType2) {
			boolean hasSuper = extendType == ParameterizedType.SUPER_WILDCARD || extendType2 == ParameterizedType.SUPER_WILDCARD;
			boolean hasExtends = extendType == ParameterizedType.EXTENDS_WILDCARD || extendType2 == ParameterizedType.EXTENDS_WILDCARD;

			// LCA(T, ? extends T) = ? extends T
			// LCA(T, ? super T) = ?
			// LCA(? extends, ? super T) = ?
			if (hasSuper) return Signature.unboundedWildcard(); // 通配符类型
			if (hasExtends) extendType = ParameterizedType.EXTENDS_WILDCARD;
		}

		List<IType> aGeneric = inferGeneric(a, commonParent);
		List<IType> bGeneric = inferGeneric(b, commonParent);

		// 如果不是泛型类，那么直接返回
		if (extendType == ParameterizedType.NO_WILDCARD && (aGeneric == null || bGeneric == null)) {
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
			IType c = aa.equals(a) && bb.equals(b) ? Signature.unboundedWildcard() : getCommonAncestor(aa, bb);
			typeParams.set(i, c);
		}

		// TODO primitive generic
		// 	CP(ArrayList<int>, List<Integer>) => List<Integer>
		//  CP(List<int>, List<Integer>) => Collection<Integer>

		ParameterizedType parameterizedType = new ParameterizedType(commonParent, a.array(), extendType);
		parameterizedType.typeParameters = typeParams;
		return parameterizedType;
	}
	/**
	 * 返回ab两个类(Class)的共同祖先
	 */
	public final String getCommonAncestor(ClassNode infoA, ClassNode infoB) {
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
	public boolean report(Diagnostic diagnostic) {
		boolean isError = reporter.apply(diagnostic);
		if (isError) hasError = true;
		return isError;
	}

	public void report(ClassDefinition source, Kind kind, int pos, String code) { report(source, kind, pos, code, (Object[]) null);}
	public void report(ClassDefinition source, Kind kind, int pos, String code, Object... args) {report(new Diagnostic(source, kind, pos, pos, code, args));}
	//endregion

	public List<CompileUnit> getDirectInheritorFor(String name) {
		var inheritors = new ArrayList<CompileUnit>();
		for (CompileUnit file : compileUnits) {
			if (file.parent().equals(name) || file.interfaces().contains(name)) {
				inheritors.add(file);
			}
		}
		return inheritors;
	}

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