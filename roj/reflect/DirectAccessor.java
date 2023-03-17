package roj.reflect;

import roj.asm.cst.CstClass;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnHelper;
import roj.asm.util.InsnList;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.EmptyArrays;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.CLASS;
import static roj.asm.util.AccessFlag.PUBLIC;

/**
 * 替代反射，目前不能修改final字段，然而这是JVM的锅 <br>
 * <br>
 * PackagePrivateProxy已被Nixim替代，能用到它的都是【在应用启动前加载的class】那还不如boot class替换
 *
 * @author Roj233
 * @since 2021/8/13 20:16
 */
public final class DirectAccessor<T> {
	public static final String MAGIC_ACCESSOR_CLASS = ReflectionUtils.JAVA_VERSION <= 8 ?
		"sun/reflect/MagicAccessorImpl" : Java9Compat.HackMagicAccessor();

	public static final boolean DEBUG = false;
	public static final MyBitSet EMPTY_BITS = new MyBitSet(0);

	static final AtomicInteger NEXT_ID = new AtomicInteger();

	//
	private final MyHashMap<String, Method> methodByName;
	private final Class<T> owner;
	private ConstantData var;

	// Cached object
	private final MyHashMap<String, Cache> caches;
	private Cache cache;

	// Cast check
	private boolean check;

	// Debug
	private CharList sb;

	// State machine mode
	private Class<?> target;
	private String[] from, to;
	private List<Class<?>[]> fuzzy;

	static final class Cache {
		Class<?> clazz;
		FieldInsnNode node;
	}

	private DirectAccessor(Class<T> deClass, String pkg, boolean checkDuplicate) {
		this.owner = deClass;
		this.check = true;
		if (!deClass.isInterface()) throw new IllegalArgumentException(deClass.getName() + " must be a interface");
		Method[] methods = deClass.getMethods();
		this.methodByName = new MyHashMap<>(methods.length);
		for (Method method : methods) {
			if ((method.getModifiers() & AccessFlag.STATIC) != 0) continue;

			// skip 'internal' methods
			if (("toString".equals(method.getName()) || "clone".equals(method.getName())) && method.getParameterCount() == 0) continue;

			if (methodByName.put(method.getName(), method) != null && checkDuplicate) {
				throw new IllegalArgumentException("方法名重复: '" + method.getName() + "' in " + deClass.getName());
			}
		}
		var = new ConstantData();
		caches = new MyHashMap<>();
		String clsName = pkg + "DAB$" + NEXT_ID.getAndIncrement();
		makeHeader(clsName, deClass.getName().replace('.', '/'), var);
		FastInit.prepare(var);
		if (DEBUG) this.sb = new CharList().append("实现类: ").append(deClass.getName()).append("\n自身: ").append(var.name);
	}

	/**
	 * 构建DirectAccessor
	 *
	 * @return T
	 */
	@SuppressWarnings("unchecked")
	public final T build() {
		if (var == null) throw new IllegalStateException("Already built");

		writeDebugInfo();
		methodByName.clear();

		try {
			return (T) FastInit.make(var);
		} finally {
			var = null;
		}
	}

	public final DirectAccessor<T> cloneable() {
		var.cloneable();
		return this;
	}

	private void writeDebugInfo() {
		if (sb != null) {
			CodeWriter cw = var.newMethod(PUBLIC, "toString", "()Ljava/lang/String;");

			cw.visitSize(1,1);
			cw.ldc(LDC, new CstString(sb.toString()));
			cw.one(ARETURN);
			cw.finish();

			sb = null;
		}
	}

	/**
	 * @see #makeCache(Class, String, int)
	 */
	public final DirectAccessor<T> makeCache(Class<?> targetClass) {
		return makeCache(targetClass, "instance", 7);
	}

	/**
	 * get,set,clear Instance via Instanced or other... <br>
	 *
	 * @param methodFlag 1: get 2:set 4:clear 8:check existence, plus them
	 */
	public final DirectAccessor<T> makeCache(Class<?> targetClass, String name, int methodFlag) {
		if (caches.getEntry(name) != null) throw new IllegalStateException("Cache already set!");

		char c = Character.toUpperCase(name.charAt(0));
		String name1 = c == name.charAt(0) ? name : c + name.substring(1);

		String type = targetClass.getName().replace('.', '/');
		Type type1 = new Type(type);
		var.newField(PUBLIC, name, type1);

		CodeWriter cw;
		InsnList insn;

		if ((methodFlag & 2) != 0) {
			if ((methodFlag & 8) != 0) {
				checkExistence("set" + name1);
			}
			cw = var.newMethod(PUBLIC, "set" + name1, "(Ljava/lang/Object;)V");

			cw.visitSize(2, 2);
			cw.one(ALOAD_0);
			cw.one(ALOAD_1);
			if (check) cw.clazz(CHECKCAST, type);
			cw.field(PUTFIELD, var.name, name, type1);
			cw.one(RETURN);
		}

		if ((methodFlag & 4) != 0) {
			if ((methodFlag & 8) != 0) {
				checkExistence("clear" + name1);
			}
			cw = var.newMethod(PUBLIC, "clear" + name1, "()V");

			cw.visitSize(2,1);

			cw.one(ALOAD_0);
			cw.one(ACONST_NULL);
			cw.field(PUTFIELD, var.name, name, type1);
			cw.one(RETURN);
		}

		if ((methodFlag & 1) != 0) {
			if ((methodFlag & 8) != 0) {
				checkExistence("get" + name1);
			}
			cw = var.newMethod(PUBLIC, "get" + name1, "()Ljava/lang/Object;");

			cw.visitSize(1,1);

			cw.one(ALOAD_0);
			cw.field(GETFIELD, var.name, name, type1);
			cw.one(ARETURN);
		}

		Cache cache = new Cache();
		cache.clazz = targetClass;
		cache.node = new FieldInsnNode(GETFIELD, var.name, name, type1);
		caches.put(name, cache);

		return this;
	}

	private void checkExistence(String name) {
		Method method = methodByName.remove(name);
		if (method == null) {
			throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
		}
	}

	public final DirectAccessor<T> useCache() {
		return useCache("instance");
	}

	public final DirectAccessor<T> useCache(String name) {
		cache = caches.get(name);
		if (cache == null && name != null) {
			throw new IllegalArgumentException("Cache '" + name + "' not exist");
		}
		return this;
	}

	/**
	 * @see #construct(Class, String[], List)
	 */
	public final DirectAccessor<T> construct(Class<?> target, String name) {
		return construct(target, new String[] {name}, null);
	}

	/**
	 * @see #construct(Class, String[], List)
	 */
	public final DirectAccessor<T> construct(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return construct(target, names, null);
	}

	/**
	 * @see #construct(Class, String[], List)
	 */
	public final DirectAccessor<T> construct(Class<?> target, String name, Class<?>... param) {
		if (param.length == 0) throw new IllegalArgumentException("Wrong call");
		return construct(target, new String[] {name}, Collections.singletonList(param));
	}

	/**
	 * @see #construct(Class, String[], List)
	 */
	public final DirectAccessor<T> constructFuzzy(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return construct(target, names, Collections.emptyList());
	}

	/**
	 * 把 names 中的方法标记为 target 的实例化器 <br>
	 * <br>
	 * all-object 模式: 输入和输出均为 Object, 当你无法在代码中访问目标类时有奇效<br>
	 * #双重动态<br>
	 * <br>
	 *
	 * @param fuzzy <br>
	 * 当这个值为null: 不使用 all-object 模式 <br>
	 * 当这个值为空列表: 使用 模糊的 all-object 模式 <br>
	 * 当这个值为非空列表 (长度必须等于 names.length): <br>
	 * 对其中值为null的项使用模糊的 all-object 模式 <br>
	 * 否则使用精确的 all-object 模式 <br>
	 * <br>
	 *
	 * @return this
	 *
	 * @throws IllegalArgumentException 当提供的参数有误,不支持或者不存在时
	 */
	public DirectAccessor<T> construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy) throws IllegalArgumentException {
		if (names.length == 0) return this;

		Method[] sMethods = new Method[names.length];
		Constructor<?>[] tMethods = new Constructor<?>[names.length];

		Constructor<?>[] constructors = fuzzy == null ? null : target.getDeclaredConstructors();
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			Method m = methodByName.get(name);
			if (m == null) {
				throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
			}
			if (!m.getReturnType().isAssignableFrom(target)) {
				throw new IllegalArgumentException(owner.getName() + '.' + name + " 的返回值 (" + m.getReturnType().getName() + ") 不兼容 " + target.getName());
			}
			sMethods[i] = m;
			Class<?> r = m.getReturnType();
			Class<?>[] types = m.getParameterTypes();

			try {
				if (fuzzy == null || (!fuzzy.isEmpty() && fuzzy.get(i) != null)) {
					// for exception
					tMethods[i] = target.getDeclaredConstructor(fuzzy == null ? types : (types = fuzzy.get(i)));
				} else {
					for (int j = 0; j < types.length; j++) {
						Class<?> type = types[j];
						if (!type.isPrimitive() && type != Object.class) {
							throw new IllegalArgumentException("无法为 " + owner.getName() + '.' + name + " 使用all-object: 第[" + (j + 1) + "]个参数既不是基本类型又不是Object");
						}
					}
					if (!r.isPrimitive() && r != Object.class) throw new IllegalArgumentException("无法为 " + owner.getName() + '.' + name + " 使用all-object: 返回值既不是基本类型又不是Object");

					objectToObject(types);
					int found = 0;
					outer:
					for (Constructor<?> cr : constructors) {
						if (cr.getParameterCount() == types.length) {
							Class<?>[] constParams = objectToObject(cr.getParameterTypes());
							for (int j = 0; j < constParams.length; j++) {
								if (constParams[j] != types[j]) {
									continue outer;
								}
							}
							if (found++ > 0) {
								throw new IllegalArgumentException(
									"无法为 " + owner.getName() + '.' + name + " 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n" +
										"其一: " + TypeHelper.class2asm(tMethods[i].getParameterTypes(), void.class) +
										"\n" + "其二: " + TypeHelper.class2asm(cr.getParameterTypes(), void.class));
							}
							tMethods[i] = cr;
						}
					}
					if (found == 0) throw new NoSuchMethodException();
				}
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到 " + target.getName() + " 的构造器, 参数: " + TypeHelper.class2asm(types, void.class));
			}
			methodByName.remove(name);
		}
		if (sb != null) {
			sb.append("\n  构造器代理: ").append(target.getName()).append("\n  方法:\n    ");
			for (int i = 0; i < tMethods.length; i++) {
				Constructor<?> tm = tMethods[i];
				sb.append(names[i]).append(" (").append(TypeHelper.class2asm(tm.getParameterTypes(), void.class)).append(")").append("\n    ");
			}
			sb.setLength(sb.length() - 5);
		}

		String tName = target.getName().replace('.', '/');

		for (int i = 0; i < tMethods.length; i++) {
			Constructor<?> m = tMethods[i];
			Class<?>[] params = m.getParameterTypes();
			String tDesc = TypeHelper.class2asm(params, void.class);

			Method sm = sMethods[i];
			Class<?>[] params2 = sm.getParameterTypes();
			String sDesc = objectDescriptors(params, sm.getReturnType(), fuzzy == null);

			CodeWriter cw = var.newMethod(PUBLIC, sm.getName(), sDesc);

			cw.clazz(NEW, tName);
			cw.one(DUP);

			int size = 1;
			for (int j = 0; j < params.length; j++) {
				Class<?> param = params[j];
				String tag = InsnHelper.XPrefix(param);
				InsnHelper.compress(cw, InsnHelper.X_LOAD(tag.charAt(0)), size++);
				if (check && !param.isAssignableFrom(params2[j])) cw.clazz(CHECKCAST, param.getName().replace('.', '/'));
				switch (tag) {
					case "D":
					case "L": size++;
				}
			}

			// special design for lazy write size
			cw.visitSize(size+1, size+1);

			cw.invoke(INVOKESPECIAL, tName, "<init>", tDesc);
			cw.one(ARETURN);
			cw.finish();
		}

		return this;
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate(Class<?> target, String name) {
		String[] arr = new String[] {name};
		return delegate(target, arr, EMPTY_BITS, arr, null);
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate(Class<?> target, String name, String selfName) {
		return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, null);
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, names, EMPTY_BITS, names, null);
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate(Class<?> target, String[] names, String[] selfNames) {
		return delegate(target, names, EMPTY_BITS, selfNames, null);
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String name) {
		String[] arr = new String[] {name};
		return delegate(target, arr, EMPTY_BITS, arr, Collections.emptyList());
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames) {
		return delegate(target, methodNames, EMPTY_BITS, methodNames, Collections.emptyList());
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String method, String self) {
		return delegate(target, new String[] {method}, EMPTY_BITS, new String[] {self}, Collections.emptyList());
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String method, String self, Class<?>... param) {
		if (param.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, new String[] {method}, EMPTY_BITS, new String[] {self}, Collections.singletonList(param));
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String[] methodNames, String[] selfNames) {
		return delegate(target, methodNames, EMPTY_BITS, selfNames, Collections.emptyList());
	}

	/**
	 * 把 selfMethodNames 中的方法标记为 target 的 methodNames 方法的调用者 <br>
	 * <br>
	 *
	 * @param flags 当set中对应index项为true时代表直接调用此方法(忽略继承)
	 * @param fuzzyMode : {@link #construct(Class, String[], List)}
	 *
	 * @return this
	 *
	 * @throws IllegalArgumentException 当提供的参数有误,不支持或者不存在时
	 */
	public DirectAccessor<T> delegate(Class<?> target, String[] methodNames, @Nullable MyBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode) throws IllegalArgumentException {
		if (selfNames.length == 0) return this;
		boolean useCache = cache != null;
		if (useCache) {
			if (!target.isAssignableFrom(cache.clazz)) {
				throw new IllegalArgumentException("使用了缓存的对象 '" + cache.clazz.getName() + "', 但是 '" + target.getName() + "' 不能转换为缓存的类 '" + cache.clazz.getName() + "'.");
			}
		}

		Method[] tMethods = new Method[selfNames.length];
		Method[] sMethods = new Method[selfNames.length];

		List<Method> methods = ReflectionUtils.getMethods(target);
		for (int i = 0; i < selfNames.length; i++) {
			String name = selfNames[i];
			Method method = methodByName.get(name);
			if (method == null) {
				throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
			}
			sMethods[i] = method;

			Class<?>[] types = method.getParameterTypes();
			if (fuzzyMode != null) {
				for (int j = 0; j < types.length; j++) {
					Class<?> type = types[j];
					if (!type.isPrimitive() && type != Object.class) {
						throw new IllegalArgumentException("无法为 " + owner.getName() + '.' + name + " 使用all-object: 第[" + (j + 1) + "]个参数既不是基本类型又不是Object");
					}
				}
				if (!method.getReturnType().isPrimitive() && method.getReturnType() != Object.class) {
					throw new IllegalArgumentException("无法为 " + owner.getName() + '.' + name + " 使用all-object: 返回值既不是基本类型又不是Object");
				}
			}

			int off = useCache ? 0 : 1;
			String targetMethodName = methodNames[i];
			try {
				boolean fuzzy = false;
				if (fuzzyMode != null) {
					if (!fuzzyMode.isEmpty() && fuzzyMode.get(i) != null) {
						types = fuzzyMode.get(i);
						off = 0;
					} else {
						objectToObject(types);
						fuzzy = true;
					}
				}

				int found = -1;
				outer:
				for (int j = 0; j < methods.size(); j++) {
					Method m = methods.get(j);
					// NCI 无法用在静态方法上
					int off1 = (m.getModifiers() & AccessFlag.STATIC) != 0 ? 0 : off;
					if (m.getName().equals(targetMethodName) && m.getParameterCount() == types.length - off1) {
						Class<?>[] types2 = m.getParameterTypes();
						if (fuzzy) {
							objectToObject(types2);
						}
						for (int k = 0; k < types2.length; k++) {
							if (types2[k] != types[k + off1]) {
								continue outer;
							}
						}

						types = method.getParameterTypes();
						if (off1 == 1 && !types[0].isAssignableFrom(target)) {
							throw new IllegalArgumentException("非缓存方法 " + owner.getName() + '.' + name + " 的第一个参数 (" + types[0].getName() + ") 不能转换为 " + target.getName());
						}
						if (found != -1) {
							if (!Arrays.equals(m.getParameterTypes(), tMethods[i].getParameterTypes())) {
								throw new IllegalArgumentException(
									"无法为 " + owner.getName() + '.' + name + " 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n" + "其一: " + TypeHelper.class2asm(tMethods[i].getParameterTypes(),
																																				  tMethods[i].getReturnType()) + "\n" + "其二: " + TypeHelper.class2asm(
										m.getParameterTypes(), m.getReturnType()));
							} else {
								// 继承，却改变了返回值的类型
								// 同参同反不考虑
								m = findInheritLower(m, tMethods[i]);
							}
						}
						found = j;
						tMethods[i] = m;
					}
				}
				if (found == -1) throw new NoSuchMethodException();
				methods.remove(found);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到指定的方法: " + target.getName() + '.' + targetMethodName + " 参数 " + TypeHelper.class2asm(types, method.getReturnType()));
			}

			if (!method.getReturnType().isAssignableFrom(tMethods[i].getReturnType())) {
				throw new IllegalArgumentException(owner.getName() + '.' + name + " 的返回值 (" + method.getReturnType().getName() + ") 不兼容 " + tMethods[i].getReturnType().getName());
			}
			methodByName.remove(name);
		}

		if (sb != null) {
			sb.append("\n  方法代理: ").append(target.getName()).append("\n  方法:\n    ");
			for (int i = 0; i < tMethods.length; i++) {
				Method tm = tMethods[i];
				sb.append(tm).append(" => ").append(selfNames[i]).append(" (").append(TypeHelper.class2asm(tm.getParameterTypes(), tm.getReturnType())).append(")").append("\n    ");
			}
			sb.setLength(sb.length() - 5);
		}

		String tName = target.getName().replace('.', '/');

		for (int i = 0, len = tMethods.length; i < len; i++) {
			Method tm = tMethods[i];
			Class<?>[] params = tm.getParameterTypes();
			String tDesc = TypeHelper.class2asm(params, tm.getReturnType());

			Method sm = sMethods[i];
			Class<?>[] params2 = sm.getParameterTypes();
			String sDesc = objectDescriptors(params2, sm.getReturnType(), fuzzyMode == null);
			CodeWriter cw = var.newMethod(PUBLIC, selfNames[i], sDesc);

			int isStatic = (tm.getModifiers() & AccessFlag.STATIC) != 0 ? 1 : 0;
			if (isStatic == 0) {
				if (useCache) {
					cw.one(ALOAD_0);
					cw.field(GETFIELD, cache.node.owner, cache.node.name, cache.node.rawType);
				} else {
					cw.one(ALOAD_1);
					if (check && !target.isAssignableFrom(params2[0])) cw.clazz(CHECKCAST, tName);
				}
			}

			int size = useCache || isStatic != 0 ? 0 : 1;
			int j = size;
			for (Class<?> param : params) {
				String tag = InsnHelper.XPrefix(param);
				InsnHelper.compress(cw, InsnHelper.X_LOAD(tag.charAt(0)), ++size);
				if (check && !param.isPrimitive() && !param.isAssignableFrom(params2[j])) // 强制转换再做检查...
					cw.clazz(CHECKCAST, param.getName().replace('.', '/'));
				j++;
				switch (tag) {
					case "D":
					case "L": size++;
				}
			}

			cw.visitSize(Math.max(size+isStatic, 1), size+1);

			if (isStatic != 0) {
				cw.invoke(INVOKESTATIC, tName, tm.getName(), tDesc);
			} else if (target.isInterface()) {
				cw.invoke_interface(tName, tm.getName(), tDesc);
			} else {
				cw.invoke((flags != null && flags.contains(i) ? INVOKESPECIAL : INVOKEVIRTUAL), tName, tm.getName(), tDesc);
			}

			cw.one(InsnHelper.X_RETURN222(InsnHelper.XPrefix(tm.getReturnType())));
			cw.finish();
		}
		return this;
	}

	private static Method findInheritLower(Method a, Method b) {
		Class<?> aClass = a.getDeclaringClass();
		Class<?> bClass = b.getDeclaringClass();
		// b instanceof a
		return aClass.isAssignableFrom(bClass) ? b : a;
	}

	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final DirectAccessor<T> access(Class<?> target, String fieldName) {
		return access(target, new String[] {fieldName});
	}

	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final DirectAccessor<T> access(Class<?> target, String[] fields) {
		return access(target, fields, capitalize(fields, "get"), capitalize(fields, "set"));
	}

	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final DirectAccessor<T> access(Class<?> target, String field, String getter, String setter) {
		return access(target, new String[] {field}, new String[] {getter}, new String[] {setter});
	}

	/**
	 * 把 setter/getters 中的方法标记为 target 的 fields 的 setter / getter <br>
	 * <br>
	 *
	 * @return this
	 *
	 * @throws IllegalArgumentException 当提供的参数有误,不支持或者不存在时
	 */
	public DirectAccessor<T> access(Class<?> target, String[] fields, String[] getters, String[] setters) throws IllegalArgumentException {
		if (fields.length == 0) return this;
		boolean useCache = cache != null;
		if (useCache) {
			if (!target.isAssignableFrom(cache.clazz)) {
				throw new IllegalArgumentException("使用了缓存的对象 '" + cache.clazz.getName() + "', 但是 '" + target.getName() + "' 不能转换为缓存的类 '" + cache.clazz.getName() + "'.");
			}
		}

		Field[] fieldFs = new Field[fields.length];
		Method[] setterMs = new Method[fields.length];
		Method[] getterMs = new Method[fields.length];

		List<Field> allFields = ReflectionUtils.getFields(target);
		for (int i = 0; i < fields.length; i++) {
			String name = fields[i];

			int found = -1;
			for (int j = 0; j < allFields.size(); j++) {
				Field f = allFields.get(j);
				if (f.getName().equals(name)) {
					if (found != -1) {
						throw new IllegalArgumentException("卧槽！居然有同名字段！这大概是被混淆了, 你这时候应该使用那几个'弃用'的内部方法了");
					}
					found = j;
				}
			}

			if (found == -1) throw new IllegalArgumentException("无法找到字段 " + target.getName() + '.' + fields[i]);
			fieldFs[i] = allFields.remove(found);
			int off = useCache || (fieldFs[i].getModifiers() & AccessFlag.STATIC) != 0 ? 0 : 1;

			name = getters == null ? null : getters[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) {
					throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
				}
				if (method.getParameterCount() != off) throw new IllegalArgumentException(owner.getName() + '.' + name + " 是个 getter, " + "不应该有参数, got " + (method.getParameterCount() - off) + '!');
				if (!method.getReturnType().isAssignableFrom(fieldFs[i].getType())) {
					throw new IllegalArgumentException(owner.getName() + '.' + name + " 是个 getter, " + "但是返回值不兼容 " + fieldFs[i].getType().getName() + " (" + method.getReturnType().getName() + ')');
				}
				getterMs[i] = method;
				methodByName.remove(name);
			}

			name = setters == null ? null : setters[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) {
					throw new IllegalArgumentException(owner.getName() + '.' + name + " 不存在或已被占用!");
				}
				if (method.getParameterCount() != off + 1) throw new IllegalArgumentException(owner.getName() + '.' + name + " 是个 setter, " + "只因有1个参数, got " + method.getParameterCount() + '!');
				if (!method.getParameterTypes()[off].isAssignableFrom(fieldFs[i].getType())) {
					throw new IllegalArgumentException(
						owner.getName() + '.' + name + " 是个 setter, " + "但是参数[" + (off + 1) + "]不兼容 " + fieldFs[i].getType().getName() + " (" + method.getReturnType().getName() + ')');
				}
				if (method.getReturnType() != void.class) throw new IllegalArgumentException(owner.getName() + '.' + name + " 是个 setter, " + "但是它的返回值不是void: " + method.getReturnType().getName());
				setterMs[i] = method;
				methodByName.remove(name);
			}
		}

		if (sb != null) {
			sb.append("\n  字段代理: ").append(target.getName()).append("\n  方法:\n    ");
			for (int i = 0; i < fieldFs.length; i++) {
				Field tf = fieldFs[i];
				sb.append(tf.getName()).append(' ').append(tf.getType().getName()).append(" => [").append(getterMs[i]).append(", ").append(setterMs[i]).append("\n    ");
			}
			sb.setLength(sb.length() - 5);
		}

		String tName = target.getName().replace('.', '/');

		for (int i = 0, len = fieldFs.length; i < len; i++) {
			Field field = fieldFs[i];
			Type fType = TypeHelper.parseField(TypeHelper.class2asm(field.getType()));
			boolean isStatic = (field.getModifiers() & AccessFlag.STATIC) != 0;

			Method getter = getterMs[i];
			if (getter != null) {
				Class<?>[] params2 = isStatic || useCache ? EmptyArrays.CLASSES : getter.getParameterTypes();
				CodeWriter cw = var.newMethod(PUBLIC, getter.getName(), TypeHelper.class2asm(params2, getter.getReturnType()));

				byte type = fType.type;
				int localSize;

				if (!isStatic) {
					localSize = (char) (useCache ? 1 : 2);
					if (useCache) {
						cw.one(ALOAD_0);
						cw.field(GETFIELD, cache.node.owner, cache.node.name, cache.node.rawType);
					} else {
						cw.one(ALOAD_1);
						if (check && !target.isAssignableFrom(params2[0])) cw.clazz(CHECKCAST, tName);
					}
					cw.field(GETFIELD, tName, field.getName(), fType);
				} else {
					localSize = 1;
					cw.field(GETSTATIC, tName, field.getName(), fType);
				}
				cw.one(InsnHelper.X_RETURN222(fType));
				cw.visitSize(type == Type.DOUBLE || type == Type.LONG ? 2 : 1, localSize);
				cw.finish();
			}

			Method setter = setterMs[i];
			if (setter != null) {
				Class<?>[] params2 = setter.getParameterTypes();
				CodeWriter cw = var.newMethod(PUBLIC, setter.getName(), TypeHelper.class2asm(params2, void.class));

				byte type = fType.type;
				int localSize;
				int stackSize = (char) (type == Type.DOUBLE || type == Type.LONG ? 3 : 2);

				if (!isStatic) {
					localSize = (char) (stackSize + (useCache ? 0 : 1));
					if (useCache) {
						cw.one(ALOAD_0);
						cw.field(GETFIELD, cache.node.owner, cache.node.name, cache.node.rawType);
					} else {
						cw.one(ALOAD_1);
						if (check && !target.isAssignableFrom(params2[0])) cw.clazz(CHECKCAST, tName);
					}
				} else {
					localSize = stackSize--;
				}
				cw.one(InsnHelper.X_LOAD_I222(fType.nativeName().charAt(0), isStatic || useCache ? 1 : 2));
				if (check && type == CLASS && !field.getType().isAssignableFrom(params2[isStatic || useCache ? 0 : 1]))
					cw.clazz(CHECKCAST, fType.owner);
				cw.field(isStatic ? PUTSTATIC : PUTFIELD, tName, field.getName(), fType);
				cw.one(RETURN);
				cw.visitSize(stackSize, localSize);
				cw.finish();
			}
		}
		return this;
	}

	public final DirectAccessor<T> i_construct(String target, String desc, String self) {
		Method mm = methodByName.remove(self);
		if (mm == null) {
			throw new IllegalArgumentException(owner.getName() + '.' + self + " 不存在或已被占用!");
		}

		return i_construct(target, desc, mm);
	}

	public final DirectAccessor<T> i_construct(String target, String desc, Method self) {
		target = target.replace('.', '/');

		CodeWriter cw = var.newMethod(PUBLIC, self.getName(), TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType()));

		cw.clazz(NEW, target);
		cw.one(DUP);

		List<Type> params = TypeHelper.parseMethod(desc);
		params.remove(params.size() - 1);
		int size = 1;
		for (int i = 0; i < params.size(); i++) {
			Type param = params.get(i);
			char x = param.nativeName().charAt(0);
			InsnHelper.compress(cw, InsnHelper.X_LOAD(x), ++size);
			switch (x) {
				case 'D':
				case 'L':
					size++;
			}
		}

		cw.visitSize(size+1,size+1);

		cw.invoke(INVOKESPECIAL, target, "<init>", desc);
		cw.one(ARETURN);
		cw.finish();

		if (sb != null) {
			sb.append("\n  构造器代理[不安全]: ").append(target).append("\n  方法:\n    ").append(self.getName()).append(' ').append(desc);
		}

		return this;
	}

	public final DirectAccessor<T> i_delegate(String target, String name, String desc, String self, byte opcode) {
		Method m = methodByName.remove(self);
		if (m == null) {
			throw new IllegalArgumentException(owner.getName() + '.' + self + " 不存在或已被占用!");
		}
		return i_delegate(target, name, desc, m, opcode);
	}

	public final DirectAccessor<T> i_delegate(String target, String name, String desc, Method self, byte opcode) {
		target = target.replace('.', '/');

		String sDesc = TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType());

		CodeWriter cw = var.newMethod(PUBLIC, self.getName(), sDesc);

		boolean isStatic = opcode == INVOKESTATIC;
		if (!isStatic) cw.one(ALOAD_1);

		List<Type> params = TypeHelper.parseMethod(desc);
		params.remove(params.size() - 1);
		int size = isStatic ? 0 : 1;
		for (int i = 0; i < params.size(); i++) {
			Type param = params.get(i);
			char x = param.nativeName().charAt(0);
			InsnHelper.compress(cw, InsnHelper.X_LOAD(x), ++size);
			switch (x) {
				case 'D':
				case 'L': size++;
			}
		}

		cw.visitSize(Math.max(size + (isStatic ? 1 : 0), 1), size+1);

		if (isStatic) {
			cw.invoke(INVOKESTATIC, target, self.getName(), desc);
		} else if (opcode == INVOKEINTERFACE) {
			cw.invoke_interface(target, self.getName(), desc);
		} else {
			cw.invoke(opcode, target, self.getName(), desc);
		}
		cw.one(InsnHelper.X_RETURN222(InsnHelper.XPrefix(self.getReturnType())));
		cw.finish();

		if (sb != null) {
			sb.append("\n  方法代理[不安全]: ").append(target)
			  .append("\n  方法:\n    ").append(name).append(' ').append(desc).append(" => ").append(self.getName()).append(' ').append(sDesc);
		}

		return this;
	}

	public final DirectAccessor<T> i_access(String target, String name, Type type, String getter, String setter, boolean isStatic) {
		Method g = methodByName.remove(getter);
		if (g == null && getter != null) {
			throw new IllegalArgumentException(owner.getName() + '.' + getter + " 不存在或已被占用!");
		}
		Method s = methodByName.remove(setter);
		if (s == null && setter != null) {
			throw new IllegalArgumentException(owner.getName() + '.' + setter + " 不存在或已被占用!");
		}
		return i_access(target, name, type, g, s, isStatic);
	}

	public final DirectAccessor<T> i_access(String target, String name, Type type, Method getter, Method setter, boolean isStatic) {
		target = target.replace('.', '/');

		if (getter != null) {
			Class<?>[] params2 = isStatic ? EmptyArrays.CLASSES : getter.getParameterTypes();
			CodeWriter cw = var.newMethod(PUBLIC, getter.getName(), TypeHelper.class2asm(params2, getter.getReturnType()));

			byte typeId = type.type;
			int stackSize = (char) (typeId == Type.DOUBLE || typeId == Type.LONG ? 2 : 1);

			int localSize;
			if (!isStatic) {
				localSize = 2;
				cw.one(ALOAD_1);
				cw.field(GETFIELD, target, name, type);
			} else {
				localSize = 1;
				cw.field(GETSTATIC, target, name, type);
			}
			cw.visitSize(stackSize, localSize);
			cw.one(InsnHelper.X_RETURN222(type));
			cw.finish();
		}

		if (setter != null) {
			Class<?>[] params2 = setter.getParameterTypes();
			CodeWriter cw = var.newMethod(PUBLIC, setter.getName(), TypeHelper.class2asm(params2, void.class));

			byte typeId = type.type;
			int stackSize = (char) (typeId == Type.DOUBLE || typeId == Type.LONG ? 3 : 2);
			int localSize;

			if (!isStatic) {
				localSize = (char) (stackSize + 1);
				cw.one(ALOAD_1);
			} else {
				localSize = stackSize--;
			}
			cw.visitSize(stackSize, localSize);
			cw.one(InsnHelper.X_LOAD_I222(type.nativeName().charAt(0), isStatic ? 1 : 2));
			cw.field(isStatic ? PUTSTATIC : PUTFIELD, target, name, type);
			cw.one(RETURN);
			cw.finish();
		}

		if (sb != null) {
			sb.append("\n  字段代理[不安全]: ").append(target).append("\n  方法:\n    ")
			  .append(target).append(' ').append(type).append(" => [").append(getter == null ? "null" : getter.getName()).append(", ")
			  .append(setter == null ? "null" : setter.getName()).append(']');
		}

		return this;
	}

	public static <V> DirectAccessor<V> builder(Class<V> impl) {
		return new DirectAccessor<>(impl, "roj/reflect/", true);
	}
	public static <V> DirectAccessor<V> builderInternal(Class<V> impl) {
		return new DirectAccessor<>(impl, "roj/reflect/", false);
	}

	public final DirectAccessor<T> unchecked() {
		check = false;
		return this;
	}

	public final DirectAccessor<T> from(String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong parameter count");
		this.from = names;
		return this;
	}

	public final DirectAccessor<T> in(Class<?> target) {
		this.target = target;
		this.to = null;
		this.fuzzy = null;
		return this;
	}

	public final DirectAccessor<T> to(String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong parameter count");
		this.to = names;
		this.fuzzy = null;
		return this;
	}

	public final DirectAccessor<T> withFuzzy(boolean fuzzy) {
		this.fuzzy = fuzzy ? Collections.emptyList() : null;
		return this;
	}

	public final DirectAccessor<T> withFuzzy(Class<?>... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong parameter count");
		this.fuzzy = Collections.singletonList(names);
		return this;
	}

	public final DirectAccessor<T> withFuzzy(List<Class<?>[]> names) {
		this.fuzzy = names;
		return this;
	}

	public final DirectAccessor<T> op(String op) {
		if (target == null || from == null) throw new IllegalStateException("Missing arguments");
		switch (op) {
			case "access":
				return access(target, to, capitalize(from, "get"), capitalize(from, "set"));
			case "delegate":
				return delegate(target, to, EMPTY_BITS, from, fuzzy);
			case "construct":
				return construct(target, from, fuzzy);
			default:
				throw new IllegalArgumentException("Invalid operation " + op);
		}
	}

	/**
	 * 首字母大写: xx,set => setXxx
	 */
	public static String[] capitalize(String[] orig, String prefix) {
		CharList cl = new CharList();
		String[] dest = new String[orig.length];
		for (int i = 0; i < orig.length; i++) {
			cl.append(prefix).append(orig[i]);
			cl.set(prefix.length(), Character.toUpperCase(cl.charAt(prefix.length())));
			dest[i] = cl.toString();
			cl.clear();
		}
		return dest;
	}

	/**
	 * Header
	 */
	public static void makeHeader(String selfName, String invokerName, ConstantData clz) {
		clz.version = 52 << 16;
		clz.name(selfName.replace('.', '/'));

		clz.parent(MAGIC_ACCESSOR_CLASS);
		clz.interfaces.add(new CstClass(invokerName.replace('.', '/')));
		clz.access = AccessFlag.SUPER_OR_SYNC | PUBLIC;
	}

	/**
	 * cast non-primitive to Object
	 */
	static Class<?>[] objectToObject(Class<?>[] params) {
		for (int i = 0; i < params.length; i++) {
			if (!params[i].isPrimitive()) params[i] = Object.class;
		}
		return params;
	}

	static String objectDescriptors(Class<?>[] classes, Class<?> returns, boolean no_obj) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (Class<?> clazz : classes) {
			TypeHelper.class2asm(sb, no_obj | clazz.isPrimitive() ? clazz : Object.class);
		}
		sb.append(')');
		return TypeHelper.class2asm(sb, no_obj | returns.isPrimitive() ? returns : Object.class).toString();
	}
}
