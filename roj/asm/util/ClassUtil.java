package roj.asm.util;

import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Mapper Util
 *
 * @author Roj234
 * @since 2020/8/19 21:32
 */
public final class ClassUtil {
	private static final ThreadLocal<ClassUtil> ThreadBasedCache = ThreadLocal.withInitial(ClassUtil::new);

	private ClassUtil() {}

	public final Desc sharedDC = new Desc();
	public boolean checkSubClass;

	private static final ReflectClass FAILED = new ReflectClass(ClassUtil.class);
	private static final ConcurrentHashMap<String, ReflectClass> classInfo = new ConcurrentHashMap<>();

	private Map<String, IClass> localClassInfo = Collections.emptyMap();

	private final CharList sharedCL = new CharList(128), sharedCL2 = new CharList(12);
	private final SimpleList<?> sharedAL = new SimpleList<>();

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
		IClass ref = localClassInfo.get(name);
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

	/**
	 * 将这种格式的字符串 net/minecraft/client/Minecraft/fontRender/FONT_HEIGHT
	 * 解析为 class => getfield (repeatable) => invokespeicla (optional)
	 */
	public String resolveSymbol(String desc, Consumer<List<Object>> cb, boolean stopOnFirstMatch) {
		CharList sb = sharedCL;
		List<Object> tmp = Helpers.cast(sharedAL); tmp.clear();

		String anySuccess = "symbolResolver.error.noSuchClass";
		int slash = 0;
		while (true) {
			slash = desc.indexOf('/', slash);
			if (slash < 0) break;

			sb.clear();
			sb.append(desc, 0, slash);

			int dollar = slash++;
			while (true) {
				IClass clz = getClassInfo(sb);
				if (clz != null) {
					String error = resolveSymbol(clz, desc, slash, tmp);
					if (error == null) {
						cb.accept(tmp);
						anySuccess = null;
						if (stopOnFirstMatch) break;
					} else if (anySuccess != null) {
						anySuccess = error;
					}

					tmp.clear();
				}

				dollar = sb.lastIndexOf("/", dollar);
				if (dollar < 0) break;
				sb.set(dollar, '$');
			}

		}
		return anySuccess;
	}
	private String resolveSymbol(IClass clz, String desc, int prevI, List<Object> list) {
		// first String => class name
		list.add(clz.name());
		int i = desc.indexOf('/', prevI);
		if (i < 0) return null; // just class node

		while (true) {
			String name = desc.substring(prevI, i);
			int fid = clz.getField(name);
			if (fid < 0) {
				fid = clz.getMethod(name);
				if (fid < 0) return "symbolResolver.error.noSuchSymbol";
				// last String => invoke (1/2) opcode
				list.add(name);
				return null;
			}

			// then FieldNode (RawNode) => getfield opcode
			FieldNode field = (FieldNode) clz.fields().get(fid);
			list.add(field);

			prevI = i+1;
			i = desc.indexOf('/', prevI);

			Type type = field.fieldType();
			if (type.isPrimitive()) {
				if (i < 0) return null;
				// 不能解引用基本类型
				return "symbolResolver.error.derefPrimitiveField";
			} else if (type.array() > 0) {
				if (i < 0) return null;

				// array solid methods / field
				name = desc.substring(prevI, i);
				list.add(name);
				switch (name) {
					case "getClass":
					case "toString":
					case "hashCode":
					case "equals":
					case "wait":
					case "notify":
					case "notifyAll":
					case "length": return null;
					default: return "symbolResolver.error.noSuchSymbol";
				}
			}

			clz = reflectClassInfo(type.owner);
			if (clz == null) return "symbolResolver.error.noSuchClass";

			if (i < 0) i = desc.length();
		}
	}

	public boolean instanceOf(Map<String, IClass> ctx, CharSequence testClass, CharSequence instClass, int isInterface) {
		IClass clz;
		do {
			if (isInterface <= 0 && testClass.equals(instClass)) return true;

			clz = ctx.get(instClass);
			if (clz == null) clz = getClassInfo(instClass);
			if (clz == null) return false;

			if (isInterface >= 0 && clz.interfaces().contains(testClass)) return true;

			instClass = clz.parent();
		} while (instClass != null);
		return false;
	}

	// endregion
}