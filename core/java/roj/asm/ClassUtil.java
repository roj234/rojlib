package roj.asm;

import org.jetbrains.annotations.Nullable;
import roj.annotation.MayMutate;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.*;
import roj.compiler.CompileContext;
import roj.compiler.library.ClassLoaderLibrary;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.Resolver;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;
import roj.util.Pair;

import java.util.*;
import java.util.function.Function;

/**
 * Mapper Util
 *
 * @author Roj234
 * @since 2020/8/19 21:32
 */
public final class ClassUtil {
	private static final ThreadLocal<ClassUtil> STORAGE = ThreadLocal.withInitial(ClassUtil::new);
	public static ClassUtil getInstance() {return STORAGE.get();}
	public static void setInstance(ClassUtil instance) {STORAGE.set(instance);}

	public final MemberDescriptor sharedDesc = new MemberDescriptor();
	private final CharList sharedCL = new CharList(128), sharedCL2 = new CharList(12);

	// region 映射各种名字

	public String mapClassName(Map<String, String> classMap, CharSequence name) { return mapClassName(classMap, name, 0, name.length()); }
	@Nullable
	private String mapClassName(Map<? extends CharSequence, String> map, CharSequence name, int s, int e) {
		CharList sb = sharedCL; sb.clear();

		String nn;
		if ((nn = map.get(sb.append(name, s, e))) != null) return nn;

		// This is for array class
		if (name.charAt(s) == '[') {
			int arrLv = s;
			while (name.charAt(s) == '[') s++;

			boolean endSemi = name.charAt(e-1) == ';';
			if (endSemi != (name.charAt(s) == 'L'))
				throw new IllegalArgumentException("Unknown array state: " + name.subSequence(s,e));

			if (endSemi) {
				String result = mapClassName(map, name, s+1, e-1);
				if (result == null) return null;

				sb.clear();
				return sb.append(name, arrLv, s+1).append(result).append(';').toString();
			}
		} else {
			int dollar = e;
			while ((dollar = TextUtil.lastIndexOf(name, "$", dollar-1, s)) >= 0) {
				sb.clear();
				if ((nn = map.get(sb.append(name, s, dollar))) != null) {
					sb.clear();
					return sb.append(nn).append(name, dollar, e).toString();
				}
			}
		}

		return null;
	}

	public String mapMethodParam(Map<String, String> classMap, String md) {
		if (md.length() <= 4) // min = ()La;
			return md;

		boolean changed = false;

		CharList out = sharedCL2; out.clear();

		int prevI = 0, i = 0;
		while ((i = md.indexOf('L', i)+1) > 0) {
			int j = md.indexOf(';', i);
			if (j < 0) throw new IllegalStateException("Illegal desc " + md);

			String s = mapClassName(classMap, md, i, j);
			if (s == null) out.append(md, prevI, j);
			else {
				changed = true;
				out.append(md, prevI, i).append(s);
			}

			out.append(';');
			prevI = i = j+1;
		}
		return changed ? out.append(md, prevI, md.length()).toString() : md;
	}

	public String mapFieldType(Map<String, String> classMap, String fd) {
		// min = La;
		if (fd.length() < 3) return null;

		int off = 0;
		char type = fd.charAt(0);
		if (type == Type.ARRAY) type = fd.charAt(off = (fd.lastIndexOf(Type.ARRAY) + 1));
		if (type != Type.CLASS) return null;

		String nn = mapClassName(classMap, fd, off+1, fd.length()-1);
		if (nn == null) return null;

		sharedCL.clear();
		return sharedCL.append(fd, 0, off+1).append(nn).append(';').toString();
	}
	// endregion
	public static boolean arePackagesSame(String packageA, String packageB) {
		int ia = packageA.lastIndexOf('/');

		if (packageB.lastIndexOf('/') != ia) return false;
		if (ia == -1) return true;

		return packageA.regionMatches(0, packageB, 0, ia);
	}
	public static boolean canAccessPrivate(String fieldOwner, String accessor) {
		int ia = accessor.lastIndexOf('/');
		ia = accessor.indexOf('$', ia);
		if (ia < 0) ia = accessor.length();

		return fieldOwner.regionMatches(0, accessor, 0, ia);
	}
	public static boolean isOverridable(String owner, char ownerModifier, String referent) {
		if ((ownerModifier&(Opcodes.ACC_PUBLIC|Opcodes.ACC_PROTECTED)) != 0) return true;
		if ((ownerModifier&Opcodes.ACC_PRIVATE) != 0) return false;
		return arePackagesSame(owner, referent);
	}

	private static volatile Resolver global;
	private Resolver resolver() {
		var lc = CompileContext.get();
		if (lc != null) return lc.compiler;

		if (local == null) {
			if (global == null) {
				synchronized (ClassUtil.class) {
					if (global == null) {
						global = new Resolver(false);
						global.addLibrary(new ClassLoaderLibrary(ClassUtil.class.getClassLoader()));
					}
				}
			}
			local = global;
		}
		return local;
	}

	private Resolver local;

	public ClassUtil() {}
	public ClassUtil(ClassLoader... classLoaders) {
		local = new Resolver(false);
		for (ClassLoader loader : classLoaders) {
			local.addLibrary(new ClassLoaderLibrary(loader));
		}
	}

	public Resolver getResolver() {return resolver();}

	public ClassDefinition resolve(CharSequence name) {return resolver().resolve(name);}

	private final Function<ClassNode, List<String>> getSuperClassListCached = CollectionX.lazyLru(info -> {
		ToIntMap<String> classList = resolver().getHierarchyList(info);

		List<String> parents = new ArrayList<>(classList.keySet());
		parents.sort((o1, o2) -> Integer.compare(classList.getInt(o1) >>> 16, classList.getInt(o2) >>> 16));
		return parents;
	}, 1000);
	public List<String> getHierarchyList(String type) {
		ClassNode info = resolver().resolve(type);
		if (info == null) return Collections.emptyList();
		return getSuperClassListCached.apply(info);
	}

	/**
	 * method所表示的方法是否从parents(若非空)或method.owner的父类继承/实现
	 */
	public Boolean isInherited(MemberDescriptor method, @Nullable List<String> parents, Boolean defVal) {
		ClassNode info = resolver().resolve(method.owner);
		if (info == null) return defVal;
		ComponentList ml = local.getMethodList(info, method.name);
		if (ml == ComponentList.NOT_FOUND) return defVal;

		for (MethodNode mn : ml.getMethods()) {
			if (mn.rawDesc().equals(method.rawDesc())) {
				return parents == null || parents.contains(mn.owner());
			}
		}

		return false;
	}

	private final Map<Object, Object> temp1 = new HashMap<>();

	public boolean instanceOf(String testClass, String instClass) {return resolver().instanceOf(testClass, instClass);}
	@Nullable public List<IType> inferGeneric(IType typeInst, String targetType) {return resolver().inferGeneric(typeInst, targetType, temp1);}

	private final HashSet<?> sharedSet = new HashSet<>();
	public <T> Set<T> getSharedSet() {
		sharedSet.clear();
		return Helpers.cast(sharedSet);
	}

	private final LRUCache<Object, List<String>> cache = new LRUCache<>(1000);
	private final Function<Pair<Collection<String>, Collection<String>>, List<String>> ancestorMapper = pair -> {
		Resolver resolver = resolver();

		Collection<String> a = pair.getKey();
		Collection<String> b = pair.getValue();

		Set<String> commonAncestors1 = findCommonAncestors(a, resolver);
		Set<String> commonAncestors2 = findCommonAncestors(b, resolver);

		if (commonAncestors1.size() == 0) throw new IllegalStateException("找不到"+a+"的类");
		if (commonAncestors2.size() == 0) throw new IllegalStateException("找不到"+b+"的类");

		Set<String> finalCommonAncestors = new HashSet<>(commonAncestors1);
		finalCommonAncestors.retainAll(commonAncestors2);

		return getCommonChild(finalCommonAncestors);
	};
	private final Function<Set<String>, List<String>> childMapper = classes -> {
		Resolver resolver = resolver();

		// 1. 初始化结果集，它是输入集合的一个可变拷贝
		List<String> result = new ArrayList<>(classes);

		for (String klass : classes) {
			for (String type : getHierarchyFor(klass, resolver)) {
				if (type.equals(klass)) continue;
				result.remove(type);
			}
		}

		return result;
	};

	/**
	 * 计算两个类型集合的最具体共同祖先。
	 *
	 * @param a 第一个类型集合。
	 * @param b 第二个类型集合。
	 * @return 包含最具体共同祖先的列表。
	 */
	public List<String> getCommonAncestors(Collection<String> a, Collection<String> b) {return cache.computeIfAbsent(new Pair<>(a, b), Helpers.cast(ancestorMapper));}


	public List<String> getCommonChild(@MayMutate Set<String> types) {return cache.computeIfAbsent(types, Helpers.cast(childMapper));}

	/**
	 * 辅助方法：为一个类型集合找到它们共同的所有祖先。
	 *
	 * @param types 要分析的类型集合。
	 * @return 一个包含所有共同祖先的集合（包括接口和父类）。
	 */
	private Set<String> findCommonAncestors(Collection<String> types, Resolver resolver) {
		Set<String> commonAncestors = new HashSet<>();

		for (String type : types) {
			Set<String> hierarchy = getHierarchyFor(type, resolver);
			commonAncestors.addAll(hierarchy);
		}
		return commonAncestors;
	}

	/**
	 * 辅助方法，用于获取指定类的完整继承链（包括自身）。
	 * 这段逻辑是从原方法中提取出来的，用于处理普通类和数组类型。
	 * @param klass    类名 (e.g., "java/lang/String" or "[Ljava/lang/Object;")
	 * @param resolver 解析器实例
	 * @return 包含所有父类型和接口的集合，如果无法解析则可能为空集
	 */
	private Set<String> getHierarchyFor(String klass, Resolver resolver) {
		if (klass.startsWith("[")) {
			Set<String> hierarchy = new HashSet<>();
			hierarchy.add(klass);
			hierarchy.add("java/lang/Cloneable");
			hierarchy.add("java/lang/Serializable");
			hierarchy.add("java/lang/Object"); // 数组的父类是 Object

			Type type = typeOf(klass);
			if (type.owner != null) {
				ClassNode ownerNode = resolver.resolve(type.owner);
				if (ownerNode != null) {
					// 如果 String 继承自 CharSequence，那么 String[] 也“继承”自 CharSequence[]
					ToIntMap<String> ownerHierarchy = resolver.getHierarchyList(ownerNode);
					String arrayPrefix = "[".repeat(type.array());
					for (String parent : ownerHierarchy.keySet()) {
						hierarchy.add(arrayPrefix+"L"+parent+";");
					}
				}
			}
			for (int i = 1; i < type.array(); i++) {
				hierarchy.add("[".repeat(i)+"Ljava/lang/Object;");
			}
			return hierarchy;
		} else {
			ClassNode classNode = resolver.resolve(klass);
			if (classNode != null) {
				// getHierarchyList 返回包含自身在内的所有父类和接口
				return resolver.getHierarchyList(classNode).keySet();
			}
		}
		return Collections.emptySet();
	}

	private static Type typeOf(String type1) {return type1.startsWith("[") ? Type.fieldDesc(type1) : Type.klass(type1);}
}