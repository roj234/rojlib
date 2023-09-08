package roj.mapper;

import roj.asm.misc.ReflectClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.mapper.util.Desc;
import roj.mapper.util.Worker;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Mapper Util
 *
 * @author Roj234
 * @since 2020/8/19 21:32
 */
public final class MapUtil {
	private static final ThreadLocal<MapUtil> ThreadBasedCache = ThreadLocal.withInitial(MapUtil::new);

	public static final int CPU = Runtime.getRuntime().availableProcessors();

	private MapUtil() {}

	public final Desc sharedDC = new Desc("", "", "");
	public boolean checkSubClass = false;

	static final ReflectClass FAILED = new ReflectClass(MapUtil.class);
	final MyHashMap<String, ReflectClass> classInfo = new MyHashMap<>();

	private final CharList sharedCL = new CharList(128);
	private final CharList sharedCL2 = new CharList(12);
	public final SimpleList<?> sharedAL = new SimpleList<>();

	public static MapUtil getInstance() {
		MapUtil util = ThreadBasedCache.get();
		util.checkSubClass = false;
		return util;
	}

	// region 各种可继承性的判断

	public static boolean arePackagesSame(String packageA, String packageB) {
		int ia = packageA.lastIndexOf('/');

		if (packageB.lastIndexOf('/') != ia) return false;
		if (ia == -1) return true;

		return packageA.regionMatches(0, packageB, 0, ia);
	}

	public List<String> superClasses(String name, Map<String, List<String>> selfSupers) {
		List<String> superItf = selfSupers.get(name);
		if (superItf == null) {
			ReflectClass rc = reflectClassInfo(name);
			if (rc == null) return Collections.emptyList();
			superItf = rc.i_superClassAll();
		}
		return superItf;
	}

	public static MyHashMap<String, IClass> createNamedMap(List<Context> ctxs) {
		MyHashMap<String, IClass> classInfo = new MyHashMap<>(ctxs.size());
		for (int i = 0; i < ctxs.size(); i++) {
			ConstantData data = ctxs.get(i).getData();
			classInfo.put(data.name, data);
		}
		return classInfo;
	}

	public ReflectClass reflectClassInfo(CharSequence k) {
		ReflectClass me = classInfo.get(k);
		if (me != null) return me == FAILED ? null : me;

		String name = k.toString();
		try {
			Class<?> inst = Class.forName(name.replace('/', '.'), false, getClass().getClassLoader());
			classInfo.put(name, me = ReflectClass.from(inst));
			return me;
		} catch (ClassNotFoundException | NoClassDefFoundError e) {
			classInfo.put(name, FAILED);
			return null;
		} catch (Throwable e) {
			System.err.println("Exception Loading " + name);
			e.printStackTrace();
			classInfo.put(name, FAILED);
			return null;
		}
	}

	private List<Class<?>> mkRefs(CharSequence k) {
		ReflectClass me = reflectClassInfo(k);
		return me == null ? null : me.allParentsWithSelf();
	}

	// 使用反射查找实现类，避免RT太大不好解析
	public boolean isInherited(Desc k, List<String> toTest, boolean def) {
		if (classInfo.get(k.owner) == FAILED) return def;

		if (checkObjectInherit(k)) return true;

		SimpleList<Type> pars = Helpers.cast(sharedAL); pars.clear();
		TypeHelper.parseMethod(k.param, pars);
		pars.remove(pars.size() - 1);

		Class<?>[] par = new Class<?>[pars.size()];
		for (int i = 0; i < pars.size(); i++) {
			Type type = pars.get(i);

			ReflectClass p = classInfo.get(type.owner);
			if (p != null) {
				if (p == FAILED) return def;
				par[i] = p.owner;
			} else {
				try {
					par[i] = type.toJavaClass();
				} catch (ClassNotFoundException | NoClassDefFoundError e) {
					classInfo.put(type.owner, FAILED);
					return def;
				} catch (Throwable e) {
					String o = type.owner;
					System.err.println("Exception loading " + o);
					e.printStackTrace();
					classInfo.put(o, FAILED);
					return def;
				}
				if (type.owner != null) classInfo.put(type.owner, ReflectClass.from(par[i]));
			}
		}

		if (toTest == null || toTest.isEmpty()) {
			List<Class<?>> sup = mkRefs(k.owner);
			if (sup == null) return def;
			return test(k.name, par, sup);
		} else {
			for (int i = 0; i < toTest.size(); i++) {
				List<Class<?>> sup = mkRefs(toTest.get(i));
				if (sup == null) continue;
				if (test(k.name, par, sup)) return true;
			}
		}
		return false;
	}

	public static boolean checkObjectInherit(Desc k) {
		// 检查Object的继承
		// final不用管
		if (k.param.startsWith("()")) {
			switch (k.name) {
				case "clone":
				case "toString":
				case "hashCode":
				case "finalize":
					return true;
			}
		} else {return k.param.equals("(Ljava/lang/Object;)Z") && k.name.equals("equals");}
		return false;
	}

	private boolean test(String name, Class<?>[] par, List<Class<?>> sup) {
		for (int j = 0; j < sup.size(); j++) {
			Class<?> clz = sup.get(j);

			try {
				clz.getDeclaredMethod(name, par);
				return true;
			} catch (NoSuchMethodException ignored) {
			} catch (NoClassDefFoundError e) {
				classInfo.put(e.getMessage(), FAILED);
				return false;
			}
		}
		return false;
	}

	public interface NodeResolver {
		void getField(IClass owner, MoFNode node);

		void arrayLength();

		void partialErrorAt(CharSequence desc, int pos);
	}

	/**
	 * net/minecraft/client/Minecraft/fontRender/FONT_HEIGHT
	 * 从类名尽可能长开始
	 */
	public boolean resolveNode(Map<String, IClass> ctx, CharSequence desc, NodeResolver resolver) {
		CharList tmp = sharedCL;
		tmp.clear();
		tmp.append(desc);

		List<Object> list = Helpers.cast(sharedAL);
		list.clear();

		int i, len = tmp.length();
		do {
			i = tmp.lastIndexOf("/");
			if (i < 0) return false;

			tmp.setLength(i);

			IClass clz = ctx.get(tmp);
			if (clz == null) clz = reflectClassInfo(tmp);
			if (clz == null) continue;

			tmp.setLength(len);

			if (tryResolve(ctx, clz, tmp, i, list)) {
				i = list.size();
				while (i-- >= 1) {
					Object o = list.get(i--);
					if (o != null) {
						resolver.getField((IClass) list.get(i), (MoFNode) o);
					} else {
						resolver.arrayLength();
					}
				}
				return true;
			} else {
				list.clear();
				resolver.partialErrorAt(desc, tmp.length());
			}

			tmp.setLength(i);
		} while (true);
	}

	private boolean tryResolve(Map<String, IClass> ctx, IClass clz, CharList tmp, int i, List<Object> list) {
		i++;

		int pos = tmp.indexOf("/", i);
		if (pos < 0) pos = tmp.length();

		int idx = clz.getField(tmp.subSequence(i, pos));
		if (idx < 0) {
			tmp.setLength(i);
			return false;
		}

		FieldNode field = (FieldNode) clz.fields().get(idx);
		if (pos == tmp.length()) {
			list.add(clz);
			list.add(field);
			return true;
		}

		Type type = field.fieldType();
		if (type.owner == null || type.array() > 0) {
			i = pos + 1;

			pos = tmp.indexOf("/", i);
			if (pos >= 0) {
				tmp.setLength(i);
				return false;
			}

			if (tmp.regionMatches(i, "length")) {
				// 数组长度
				list.add(null);
				list.add(clz);
				list.add(field);
				return true;
			}
			return false;
		}

		String owner = type.owner;
		do {
			clz = ctx.get(owner);
			if (clz == null) clz = reflectClassInfo(owner);
			if (clz == null) {
				tmp.setLength(i);
				return false;
			}

			if (tryResolve(ctx, clz, tmp, pos, list)) {
				list.add(clz);
				list.add(field);
				return true;
			}

			owner = clz.parent();
		} while (true);
	}

	public boolean instanceOf(Map<String, IClass> ctx, CharSequence testClass, CharSequence instClass, int isInterface) {
		IClass clz;
		do {
			if (isInterface <= 0 && testClass.equals(instClass)) return true;

			clz = ctx.get(instClass);
			if (clz == null) clz = reflectClassInfo(instClass);
			if (clz == null) return false;

			if (isInterface >= 0 && clz.interfaces().contains(testClass)) return true;

			instClass = clz.parent();
		} while (instClass != null);
		return false;
	}

	// endregion

	static void async(Consumer<Context> action, List<List<Context>> ctxs) {
		ArrayList<Worker> wait = new ArrayList<>(ctxs.size());
		for (int i = 0; i < ctxs.size(); i++) {
			Worker w = new Worker(ctxs.get(i), action);
			TaskPool.CpuMassive().pushTask(w);
			wait.add(w);
		}

		for (int i = 0; i < wait.size(); i++) {
			try {
				wait.get(i).get();
			} catch (InterruptedException ignored) {
			} catch (ExecutionException e) {
				Helpers.athrow(e.getCause());
			}
		}
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
					System.out.println("sub class " + sb.append(nn).append(name, dollar, e));
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

		CharList out = sharedCL2;
		out.clear();

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

	public static long libHash(List<?> list) {
		long hash = 0;
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof File)) continue;
			File f = (File) list.get(i);
			if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
				hash = 31 * hash + f.getName().hashCode();
				hash = 31 * hash + (f.length() * 262143);
				hash ^= f.lastModified();
			}
		}

		return hash;
	}
}
