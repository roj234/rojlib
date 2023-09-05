package roj.mapper;

import roj.archive.zip.ZipFileWriter;
import roj.asm.cst.CstClass;
import roj.asm.misc.ReflectClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.mapper.util.*;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
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
	public boolean checkSubClass = true;

	static final ReflectClass FAILED = new ReflectClass(MapUtil.class);
	final MyHashMap<String, ReflectClass> classInfo = new MyHashMap<>();

	private final CharList sharedCL = new CharList(128);
	private final CharList sharedCL2 = new CharList(12);
	public final SimpleList<?> sharedAL = new SimpleList<>();

	public static MapUtil getInstance() {
		MapUtil util = ThreadBasedCache.get();
		util.checkSubClass = true;
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

	/**
	 * 父类的方法被子类实现的接口使用
	 */
	public Set<SubImpl> findSubImplements(List<Context> ctx, ConstMapper mapper, Map<String, IClass> classInfo) {
		if (classInfo.isEmpty()) {
			for (int i = 0; i < ctx.size(); i++) {
				ConstantData data = ctx.get(i).getData();
				classInfo.put(data.name, data);
			}
		}

		Map<String, List<Desc>> mapperMethods = null;

		MyHashSet<SubImpl> dest = new MyHashSet<>();

		SubImpl sTest = new SubImpl();
		NameAndType nTest = new NameAndType();

		SimpleList<NameAndType> interfaceMethods = new SimpleList<>();

		for (int k = 0; k < ctx.size(); k++) {
			ConstantData data = ctx.get(k).getData();
			if ((data.modifier() & (AccessFlag.INTERFACE | AccessFlag.ANNOTATION | AccessFlag.MODULE)) != 0) continue;
			List<CstClass> itfs = data.interfaces;
			if (itfs.isEmpty()) continue;

			interfaceMethods.clear();
			List<String> parents = superClasses(data.parent, mapper.selfSupers);
			for (int i = 0; i < itfs.size(); i++) {
				String name = itfs.get(i).name().str();
				if (!parents.contains(name)) {
					// 接口方法
					IClass clz = classInfo.get(name);
					if (clz == null) {
						clz = reflectClassInfo(name);
						if (clz == null) {
							if (mapperMethods == null) {
								mapperMethods = new MyHashMap<>();
								for (Desc key : mapper.methodMap.keySet()) {
									mapperMethods.computeIfAbsent(key.owner, Helpers.fnArrayList()).add(key);
								}
							}
							classInfo.put(name, clz = new ReflectClass(name, Helpers.cast(mapperMethods.getOrDefault(name, Collections.emptyList()))));
						}
					}

					for (MoFNode node : clz.methods()) {
						NameAndType key = new NameAndType();
						key.owner = clz.name();
						key.name = node.name();
						key.param = node.rawDesc();
						interfaceMethods.add(key);
					}
				}
			}
			if (interfaceMethods.isEmpty()) continue;

			for (int i = 0; i < parents.size(); i++) {
				String parent = parents.get(i);

				IClass clz = classInfo.get(parent);
				if (clz == null) {
					clz = reflectClassInfo(parent);
					if (clz == null) {
						if (mapperMethods == null) {
							mapperMethods = new MyHashMap<>();
							for (Desc key : mapper.methodMap.keySet()) {
								mapperMethods.computeIfAbsent(key.owner, Helpers.fnArrayList()).add(key);
							}
						}
						classInfo.put(parent, clz = new ReflectClass(parent, Helpers.cast(mapperMethods.getOrDefault(parent, Collections.emptyList()))));
					}
				}

				List<? extends MoFNode> nodes = clz.methods();
				for (int j = 0; j < nodes.size(); j++) {
					MoFNode method = nodes.get(j);
					// 感谢接口函数必须public，省的抄一遍ConstMapper的代码
					// todo 这里的static不确定()
					if ((method.modifier() & (AccessFlag.PUBLIC|AccessFlag.STATIC)) != AccessFlag.PUBLIC) continue;
					if ((nTest.name = method.name()).startsWith("<")) continue;
					nTest.param = method.rawDesc();

					int id = -1;
					while ((id = interfaceMethods.indexOf(nTest, id+1)) >= 0) {
						Desc issuer = interfaceMethods.get(id);
						sharedDC.owner = issuer.owner;
						sharedDC.name = issuer.name;
						sharedDC.param = issuer.param;
						if (!mapper.methodMap.containsKey(sharedDC)) {
							sharedDC.owner = parent;
							if (!mapper.methodMap.containsKey(sharedDC)) continue;
						}

						sTest.type = issuer;
						SubImpl s_get = dest.intern(sTest);
						if (s_get == sTest) sTest = new SubImpl();

						Set<String> set = null;
						for (Set<String> set1 : s_get.owners) {
							if (set1.contains(parent) || set1.contains(issuer.owner)) {
								set = set1;
								break;
							}
						}
						if (set == null) {
							set = new MyHashSet<>(2);
							s_get.owners.add(set);
						}
						set.add(parent);
						set.add(issuer.owner);

						// native不能
						if ((method.modifier() & AccessFlag.NATIVE) != 0) set.add(null);
						// 至少有一个类不是要处理的类: 不能混淆
						IClass ref = classInfo.get(parent);
						if (ref == null || ref instanceof ReflectClass) set.add(null);
					}
				}
			}
		}

		// 加上一步工序, 删除找得到的“找不到的”class
		for (String key : classInfo.keySet()) {
			this.classInfo.remove(key, FAILED);
		}

		return dest;
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

	public static final List<String> OBJECT_INHERIT = Collections.singletonList("java/lang/Object");

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

	public static Thread writeResourceAsync(@Nonnull ZipFileWriter zfw, @Nonnull Map<String, ?> resources) {
		Thread writer = new Thread(new ResWriter(zfw, resources), "Resource Writer");
		writer.setDaemon(true);
		writer.start();
		return writer;
	}

	public static TaskPool POOL = TaskPool.CpuMassive();

	static void async(Consumer<Context> action, List<List<Context>> ctxs) {
		ArrayList<Worker> wait = new ArrayList<>(ctxs.size());
		for (int i = 0; i < ctxs.size(); i++) {
			Worker w = new Worker(ctxs.get(i), action);
			POOL.pushTask(w);
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

	public String mapClassName(Map<String, String> classMap, String name) {
		// should indexOf(';') ?
		String nn = mapClassName(classMap, name, false, 0, name.length());
		return nn == null ? name : nn;
	}

	@Nullable
	public String mapOwner(Map<? extends CharSequence, String> map, CharSequence name, boolean file) {
		return mapClassName(map, name, file, 0, name.length());
	}

	@Nullable
	private String mapClassName(Map<? extends CharSequence, String> map, CharSequence name, boolean file, int s, int e) {
		if (e == 0) return "";

		CharList cl = sharedCL;
		cl.clear();

		String b;
		if ((b = map.get(cl.append(name, s, e - (file ? 6 : 0)))) != null) {
			return file ? (b + ".class") : b;
		}

		boolean endSemi = name.charAt(e - 1) == ';';
		switch (name.charAt(s)) {
			// This is for [Field type]
			case 'L':
				if (endSemi) {
					if (file) throw new IllegalArgumentException("Unk cls " + name.subSequence(s + 1, e - 1));
					String result = mapClassName(map, name, false, s + 1, e - 1);
					cl.clear();
					return result == null ? null : cl.append('L').append(result).append(';').toString();
				} // class name starts with L
				break;
			// This is for [Field type, Class type]
			case '[':
				if (file) throw new IllegalArgumentException("Unk arr " + name.subSequence(s, e));

				int arrLv = s;
				while (name.charAt(s) == '[') {
					s++;
				}
				arrLv = s - arrLv;

				if (name.charAt(s) == 'L') {
					if (endSemi) {
						String result = mapClassName(map, name, false, s + 1, e - 1);
						if (result == null) return null;

						cl.clear();
						for (int i = 0; i < arrLv; i++) {
							cl.append('[');
						}
						return cl.append('L').append(result).append(';').toString();
					} else {
						throw new IllegalArgumentException("Unk arr 1 " + name);
					}
				} else if (endSemi) // primitive array ?
					throw new IllegalArgumentException("Unk arr 2 " + name);
				break;
		}

		if (checkSubClass) {
			int dollar = TextUtil.gIndexOf(name, "$", s, e);

			cl.clear();
			if (dollar != -1 && (b = map.get(cl.append(name, s, dollar))) != null) {
				cl.clear();
				return cl.append(b).append(name, dollar, e).toString();
			}
		}

		return file ? name.subSequence(s, e).toString() : null;
	}

	public String mapMethodParam(Map<String, String> classMap, String md) {
		if (md.length() <= 4) // min = ()La;
			return md;

		boolean changed = false;

		CharList out = sharedCL2;
		out.clear();

		for (int i = 0; i < md.length(); ) {
			char c = md.charAt(i++);
			out.append(c);
			if (c == 'L') {
				int j = md.indexOf(';', i);
				if (j == -1) throw new IllegalStateException("Illegal descriptor");

				String s = mapClassName(classMap, md, false, i, j);
				if (s != null) {
					changed = true;
					out.append(s);
				} else {
					out.append(md, i, j);
				}
				i = j;
			}
		}
		return changed ? out.toString() : md;
	}

	public String mapFieldType(Map<String, String> classMap, String fd) {
		// min = La;
		if (fd.length() < 3) return null;

		char first = fd.charAt(0);
		// 数组
		if (first == Type.ARRAY) {
			first = fd.charAt(fd.lastIndexOf(Type.ARRAY) + 1);
		}
		// 不是object类型
		if (first != Type.CLASS) return null;

		return mapClassName(classMap, fd, false, 0, fd.length());
	}

	// endregion

	public static long libHash(List<?> list) {
		long hash = 0;
		for (int i = 0; i < list.size(); i++) {
			if (!(list.get(i) instanceof File)) continue;
			File f = (File) list.get(i);
			if (f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
				hash = 31 * hash + f.getName().hashCode();
				hash = 31 * hash + (f.length() & 262143);
				hash ^= f.lastModified();
			}
		}

		return hash;
	}
}
