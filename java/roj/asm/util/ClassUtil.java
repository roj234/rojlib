package roj.asm.util;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.IClass;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Mapper Util
 *
 * @author Roj234
 * @since 2020/8/19 21:32
 */
public final class ClassUtil {
	private static final ThreadLocal<ClassUtil> ThreadBasedCache = ThreadLocal.withInitial(ClassUtil::new);

	private ClassUtil() { this.localClassInfo = s -> null; }
	public ClassUtil(Function<CharSequence, IClass> classInfo) { this.localClassInfo = classInfo; }

	public final Desc sharedDC = new Desc();
	public boolean checkSubClass;

	public static final ReflectClass FAILED = new ReflectClass(ClassUtil.class);
	public static final ConcurrentHashMap<String, ReflectClass> classInfo = new ConcurrentHashMap<>();

	private final Function<CharSequence, IClass> localClassInfo;

	private final CharList sharedCL = new CharList(128), sharedCL2 = new CharList(12);

	public static ClassUtil getInstance() {
		ClassUtil u = ThreadBasedCache.get();
		u.checkSubClass = false;
		return u;
	}

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
		} else if (checkSubClass) {
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
	// region 各种可继承性的判断
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

	public static ReflectClass reflectClassInfo(CharSequence _name) {
		ReflectClass me = classInfo.get(_name);
		if (me != null) return me == FAILED ? null : me;

		synchronized (classInfo) {
			me = classInfo.get(_name);
			if (me != null) return me == FAILED ? null : me;

			String name = _name.toString();
			try {
				Class<?> inst = Class.forName(name.replace('/', '.'), false, ClassUtil.class.getClassLoader());
				classInfo.put(name, me = ReflectClass.from(inst));
				return me;
			} catch (ClassNotFoundException | NoClassDefFoundError e) {
				classInfo.put(name, FAILED);
				return null;
			} catch (Throwable e) {
				Logger.getLogger("ASM").error("Exception Loading {}", e, name);
				classInfo.put(name, FAILED);
				return null;
			}
		}
	}
	public IClass getClassInfo(CharSequence name) {
		IClass ref = localClassInfo.apply(name);
		if (ref != null) return ref;
		return reflectClassInfo(name);
	}

	/**
	 * method所表示的方法是否从parents(若非空)或method.owner的父类继承/实现
	 */
	public Boolean isInherited(Desc method, List<String> parents, Boolean defVal) {
		// 检查Object的继承
		if (method.param.startsWith("()")) {
			switch (method.name) {
				case "clone":
				case "toString":
				case "hashCode":
				case "finalize":
					return true;
			}
		} else if (method.param.equals("(Ljava/lang/Object;)Z") && method.name.equals("equals")) {
			return true;
		}

		if (parents != null && parents.isEmpty()) return defVal;

		boolean failedSome = false;

		if (parents == null) {
			ReflectClass ref = reflectClassInfo(method.owner);
			if (ref == null) return defVal;

			List<Class<?>> sup = ref.allParentsWithSelf();
			// from 1, not check itself
			for (int i = 1; i < sup.size(); i++) {
				Class<?> clz = sup.get(i);
				String name = clz.getName().replace('.', '/');

				ref = classInfo.get(name);
				if (ref == null) classInfo.putIfAbsent(name, ref = new ReflectClass(clz));

				if (ref.getMethod(method.name, method.rawDesc()) >= 0) return true;
			}
		} else {
			for (int i = 0; i < parents.size(); i++) {
				IClass ref = getClassInfo(parents.get(i));
				if (ref == null) {
					failedSome = true;
					continue;
				}

				if (ref.getMethod(method.name, method.rawDesc()) >= 0) return true;
			}
		}
		return failedSome ? defVal : false;
	}

	// endregion
}