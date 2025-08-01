package roj.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.collect.CollectionX;
import roj.collect.HashMap;
import roj.collect.ToIntMap;
import roj.compiler.CompileContext;
import roj.compiler.library.ClassLoaderLibrary;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.Resolver;
import roj.compiler.resolve.TypeCast;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Mapper Util
 *
 * @author Roj234
 * @since 2020/8/19 21:32
 */
public final class ClassUtil {
	private static final ThreadLocal<ClassUtil> ThreadBasedCache = ThreadLocal.withInitial(ClassUtil::new);
	public static ClassUtil getInstance() {return ThreadBasedCache.get();}

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
			while ((dollar = TextUtil.gLastIndexOf(name, "$", dollar-1, s)) >= 0) {
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

	private final TypeCast caster = new TypeCast();
	private final Map<Object, Object> temp1 = new HashMap<>();

	public boolean instanceOf(String testClass, String instClass) {return resolver().instanceOf(testClass, instClass);}
	public List<IType> inferGeneric(IType typeInst, String targetType) {return resolver().inferGeneric(typeInst, targetType, temp1);}
	public String getCommonAncestor(String type1, String type2) {return resolver().getCommonAncestor(Type.klass(type1), Type.klass(type2)).owner();}
	public String getCommonChild(String type1, String type2) {
		caster.context = resolver();

		var left = type1.startsWith("[") ? Type.fieldDesc(type1) : Type.klass(type1);
		var right = type2.startsWith("[") ? Type.fieldDesc(type2) : Type.klass(type2);
		// copied from Inferrer
		var result = caster.checkCast(left, right);
		if (result.type >= 0) return type1; // a更不具体
		result = caster.checkCast(right, left);
		if (result.type >= 0) return type2; // b更不具体

		//throw new UnableCastException(a, b, result);
		throw new UnsupportedOperationException("这两个类型在继承链上没有交集:"+type1+","+type2);
	}
}