package roj.reflect;

import org.jetbrains.annotations.Nullable;
import roj.asm.Parser;
import roj.asm.cp.CstClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ArrayCache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.CLASS;

/**
 * 用接口替代反射，虽然看起来和{@link java.lang.invoke.MethodHandle}很相似，其实却是同一个原理 <br>
 * * 但是理论上比MethodHandle快<br>
 * * 而且MethodHandle也不能随便用(不限制权限)啊<br>
 * 限制: 不能写final字段，因为JVM太安全辣，请使用{@link ReflectionUtils#access(Field)} <br>
 *
 * @author Roj233
 * @since 2021/8/13 20:16
 */
public final class DirectAccessor<T> {
	public static final String MAGIC_ACCESSOR_CLASS = ReflectionUtils.JAVA_VERSION <= 8 ?
		"sun/reflect/MagicAccessorImpl" : VMInternals.HackMagicAccessor();

	public static final MyBitSet EMPTY_BITS = new MyBitSet(0);

	static final AtomicInteger NEXT_ID = new AtomicInteger();

	private final MyHashMap<String, Method> methodByName;
	private final Class<T> itf;
	private ConstantData var;

	// Cast check
	private byte flags;
	private static final int UNCHECKED_CAST = 1, WEAK_REF = 2, INLINE = 4;

	private DirectAccessor(Class<T> itf, boolean checkDuplicate) {
		if (!itf.isInterface()) throw new IllegalArgumentException(itf.getName()+" must be a interface");
		this.itf = itf;
		Method[] methods = itf.getMethods();
		this.methodByName = new MyHashMap<>(methods.length);
		for (Method method : methods) {
			if ((method.getModifiers() & ACC_STATIC) != 0) continue;

			// skip 'internal' methods
			if (("toString".equals(method.getName()) || "clone".equals(method.getName())) && method.getParameterCount() == 0) continue;

			if (methodByName.put(method.getName(), method) != null && checkDuplicate) {
				throw new IllegalArgumentException("方法名重复: '"+method.getName()+"' in "+itf.getName());
			}
		}
		var = new ConstantData();

		String itfClass = itf.getName().replace('.', '/');
		String clsName = itfClass+"$DAC$"+NEXT_ID.getAndIncrement();
		makeHeader(clsName, itfClass, var);
		FastInit.prepare(var);
	}

	public final T build() { return build(ClassDefiner.INSTANCE); }
	@SuppressWarnings("unchecked")
	public final T build(ClassDefiner def) {
		if (var == null) throw new IllegalStateException("Already built");
		methodByName.clear();

		if ((flags&INLINE) != 0) {
			// jdk.internal.vm.annotation.ForceInline
			Annotation annotation = new Annotation("Ljdk/internal/vm/annotation/ForceInline;", Collections.emptyMap());
			for (MethodNode mn : var.methods) {
				if (mn.name().startsWith("<")) continue;
				mn.putAttr(new Annotations(true, annotation));
			}

			try {
				byte[] array = Parser.toByteArray(var);
				Class<?> klass = VMInternals.DefineVMClass(null, array, 0, array.length);
				return (T) FastInit.manualGet(klass);
			} finally {
				var = null;
			}
		}

		try {
			return (T) FastInit.make(var, (flags&WEAK_REF) == 0 ? def : null);
		} finally {
			var = null;
		}
	}

	private Method checkExistence(String name) {
		if (name == null) return null;
		Method method = methodByName.remove(name);
		if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
		return method;
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

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		Constructor<?>[] constructors = fuzzy == null ? null : target.getDeclaredConstructors();
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			Method m = methodByName.get(name);
			if (m == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
			if (!m.getReturnType().isAssignableFrom(target)) throw new IllegalArgumentException(itf.getName()+'.'+name+" 的返回值 ("+m.getReturnType().getName()+") 不兼容 "+target.getName());
			sMethods[i] = m;
			Class<?>[] types = m.getParameterTypes();

			try {
				if (fuzzy == null || (!fuzzy.isEmpty() && fuzzy.get(i) != null)) {
					// for exception
					tMethods[i] = target.getDeclaredConstructor(fuzzy == null ? types : (types = fuzzy.get(i)));
				} else {
					// removed: 参数或返回值既不是基本类型又不是Object
					// reason: just cast-able
					toFuzzyMode(types);
					boolean found = false;
					outer:
					for (Constructor<?> cr : constructors) {
						if (cr.getParameterCount() == types.length) {
							Class<?>[] types2 = toFuzzyMode(cr.getParameterTypes());
							for (int j = 0; j < types2.length; j++) {
								if (types2[j] != types[j]) continue outer;
							}
							if (found) {
								throw new IllegalArgumentException(
									"无法为 "+itf.getName()+'.'+name+" 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n"+
										"其一: "+TypeHelper.class2asm(tMethods[i].getParameterTypes(), void.class)+"\n"+
										"其二: "+TypeHelper.class2asm(cr.getParameterTypes(), void.class));
							}
							tMethods[i] = cr;
							found = true;
						}
					}
					if (!found) throw new NoSuchMethodException();
				}
				if (sm1 != null && !sm1.checkConstruct(tMethods[i])) throw new NoSuchMethodException();
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到 "+target.getName()+" 的构造器, 参数: "+TypeHelper.class2asm(types, void.class));
			}
			methodByName.remove(name);
		}

		String tName = target.getName().replace('.', '/');

		for (int i = 0; i < tMethods.length; i++) {
			Constructor<?> m = tMethods[i];
			Class<?>[] params = m.getParameterTypes();
			String tDesc = TypeHelper.class2asm(params, void.class);

			Method sm = sMethods[i];
			Class<?>[] params2 = sm.getParameterTypes();
			String sDesc = toAsmDesc(params, sm.getReturnType(), fuzzy == null);

			CodeWriter cw = var.newMethod(ACC_PUBLIC, sm.getName(), sDesc);

			cw.clazz(NEW, tName);
			cw.one(DUP);

			int size = 1;
			for (int j = 0; j < params.length; j++) {
				Class<?> param = params[j];
				Type type = TypeHelper.class2type(param);
				cw.varLoad(type, size++);
				if ((flags&UNCHECKED_CAST) == 0 && !param.isAssignableFrom(params2[j])) cw.clazz(CHECKCAST, type.getActualClass());
				switch (type.getActualType()) {
					case 'D': case 'J': size++;
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
	public final DirectAccessor<T> delegate(Class<?> target, String name) { String[] arr = new String[] {name}; return delegate(target, arr, EMPTY_BITS, arr, null); }
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
	public final DirectAccessor<T> delegate(Class<?> target, String name, String selfName) { return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, null); }
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate(Class<?> target, String[] names, String[] selfNames) { return delegate(target, names, EMPTY_BITS, selfNames, null); }

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String name) { String[] arr = new String[] {name}; return delegate(target, arr, EMPTY_BITS, arr, Collections.emptyList()); }
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, names, EMPTY_BITS, names, Collections.emptyList());
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String name, String selfName) { return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, Collections.emptyList()); }

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String name, String selfName, Class<?>... param) {
		if (param.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, Collections.singletonList(param));
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final DirectAccessor<T> delegate_o(Class<?> target, String[] names, String[] selfNames) { return delegate(target, names, EMPTY_BITS, selfNames, Collections.emptyList()); }

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

		Method[] tMethods = new Method[selfNames.length];
		Method[] sMethods = new Method[selfNames.length];

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		List<Method> methods = ReflectionUtils.getMethods(target);
		for (int i = 0; i < selfNames.length; i++) {
			String name = selfNames[i];
			Method method = methodByName.get(name);
			if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");

			sMethods[i] = method;

			Class<?>[] types = method.getParameterTypes();

			int off = 1;
			String targetMethodName = methodNames[i];
			try {
				boolean fuzzy = false;
				if (fuzzyMode != null) {
					if (!fuzzyMode.isEmpty() && fuzzyMode.get(i) != null) {
						types = fuzzyMode.get(i);
						off = 0;
					} else {
						toFuzzyMode(types);
						fuzzy = true;
					}
				}

				int found = -1;
				outer:
				for (int j = 0; j < methods.size(); j++) {
					Method m = methods.get(j);
					// NCI 无法用在静态方法上
					int off1 = (m.getModifiers() & ACC_STATIC) != 0 ? 0 : off;
					if (m.getName().equals(targetMethodName) && m.getParameterCount() == types.length - off1) {
						Class<?>[] types2 = m.getParameterTypes();
						if (fuzzy) toFuzzyMode(types2);

						for (int k = 0; k < types2.length; k++) {
							if (types2[k] != types[k+off1]) {
								continue outer;
							}
						}

						types = method.getParameterTypes();
						if (off1 == 1 && !types[0].isAssignableFrom(target)) {
							throw new IllegalArgumentException(itf.getName()+'.'+name+" 的第一个参数 ("+types[0].getName()+") 不能转换为 "+target.getName());
						}
						if (found != -1) {
							if (!Arrays.equals(m.getParameterTypes(), tMethods[i].getParameterTypes())) {
								throw new IllegalArgumentException(
									"无法为 "+itf.getName()+'.'+name+" 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n"+
										"其一: "+TypeHelper.class2asm(tMethods[i].getParameterTypes(), tMethods[i].getReturnType())+"\n"+
										"其二: "+TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType()));
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
				if (found == -1 || sm1 != null && !sm1.checkInvoke(tMethods[i])) throw new NoSuchMethodException();
				methods.remove(found);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到指定的方法: "+target.getName()+'.'+targetMethodName+" 参数 "+TypeHelper.class2asm(types, method.getReturnType()));
			}

			if (!method.getReturnType().isAssignableFrom(tMethods[i].getReturnType())) {
				throw new IllegalArgumentException(itf.getName()+'.'+name+" 的返回值 ("+method.getReturnType().getName()+") 不兼容 "+tMethods[i].getReturnType().getName());
			}
			methodByName.remove(name);
		}

		String tName = target.getName().replace('.', '/');

		for (int i = 0, len = tMethods.length; i < len; i++) {
			Method tm = tMethods[i];
			Class<?>[] params = tm.getParameterTypes();
			String tDesc = TypeHelper.class2asm(params, tm.getReturnType());

			Method sm = sMethods[i];
			Class<?>[] params2 = sm.getParameterTypes();
			String sDesc = toAsmDesc(params2, sm.getReturnType(), fuzzyMode == null);
			CodeWriter cw = var.newMethod(ACC_PUBLIC, selfNames[i], sDesc);

			int isStatic = (tm.getModifiers() & ACC_STATIC) != 0 ? 1 : 0;
			if (isStatic == 0) {
				cw.one(ALOAD_1);
				if ((this.flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0])) cw.clazz(CHECKCAST, tName);
			}

			int size = isStatic != 0 ? 0 : 1;
			int j = size;
			for (Class<?> param : params) {
				Type type = TypeHelper.class2type(param);
				cw.varLoad(type, ++size);
				if ((this.flags&UNCHECKED_CAST) == 0 && !param.isAssignableFrom(params2[j])) // 强制转换再做检查...
					cw.clazz(CHECKCAST, type.getActualClass());
				j++;
				switch (type.getActualType()) {
					case 'D': case 'J': size++;
				}
			}

			cw.visitSize(Math.max(size+isStatic, 1), size+1);

			if (isStatic != 0) {
				cw.invoke(INVOKESTATIC, tName, tm.getName(), tDesc);
			} else if (target.isInterface()) {
				cw.invokeItf(tName, tm.getName(), tDesc);
			} else {
				cw.invoke((flags != null && flags.contains(i) ? INVOKESPECIAL : INVOKEVIRTUAL), tName, tm.getName(), tDesc);
			}

			cw.return_(TypeHelper.class2type(tm.getReturnType()));
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
	public final DirectAccessor<T> access(Class<?> target, String fieldName) { return access(target, new String[] {fieldName}); }

	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final DirectAccessor<T> access(Class<?> target, String[] fields) { return access(target, fields, capitalize(fields, "get"), capitalize(fields, "set")); }

	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final DirectAccessor<T> access(Class<?> target, String field, String getter, String setter) { return access(target, new String[] {field}, new String[] {getter}, new String[] {setter}); }

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

		Field[] fieldFs = new Field[fields.length];
		Method[] setterMs = new Method[fields.length];
		Method[] getterMs = new Method[fields.length];

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
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

			if (found == -1 || sm1 != null && !sm1.checkAccess(allFields.get(found))) throw new IllegalArgumentException("无法找到字段 "+target.getName()+'.'+fields[i]);
			fieldFs[i] = allFields.remove(found);
			int off = (fieldFs[i].getModifiers() & ACC_STATIC) != 0 ? 0 : 1;

			name = getters == null ? null : getters[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
				if (method.getParameterCount() != off) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 getter, 不应该有参数, got "+(method.getParameterCount() - off)+'!');
				if (!method.getReturnType().isAssignableFrom(fieldFs[i].getType())) {
					throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 getter, 但是返回值不兼容 "+fieldFs[i].getType().getName()+" ("+method.getReturnType().getName()+')');
				}
				getterMs[i] = method;
				methodByName.remove(name);
			}

			name = setters == null ? null : setters[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
				if (method.getParameterCount() != off+1) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 只因有1个参数, got "+method.getParameterCount()+'!');
				if (!method.getParameterTypes()[off].isAssignableFrom(fieldFs[i].getType())) {
					throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 但是参数["+(off+1)+"]不兼容 "+fieldFs[i].getType().getName()+" ("+method.getReturnType().getName()+')');
				}
				if (method.getReturnType() != void.class) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 但是它的返回值不是void: "+method.getReturnType().getName());
				setterMs[i] = method;
				methodByName.remove(name);
			}
		}

		String tName = target.getName().replace('.', '/');

		for (int i = 0, len = fieldFs.length; i < len; i++) {
			Field field = fieldFs[i];
			Type fType = TypeHelper.class2type(field.getType());
			boolean isStatic = (field.getModifiers() & ACC_STATIC) != 0;

			Method getter = getterMs[i];
			if (getter != null) {
				Class<?>[] params2 = isStatic ? ArrayCache.CLASSES : getter.getParameterTypes();
				CodeWriter cw = var.newMethod(ACC_PUBLIC, getter.getName(), TypeHelper.class2asm(params2, getter.getReturnType()));

				byte type = fType.type;
				int localSize;

				if (!isStatic) {
					localSize = 2;
					cw.one(ALOAD_1);
					if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0])) cw.clazz(CHECKCAST, tName);
					cw.field(GETFIELD, tName, field.getName(), fType);
				} else {
					localSize = 1;
					cw.field(GETSTATIC, tName, field.getName(), fType);
				}
				cw.return_(fType);
				cw.visitSize(type == Type.DOUBLE || type == Type.LONG ? 2 : 1, localSize);
				cw.finish();
			}

			Method setter = setterMs[i];
			if (setter != null) {
				Class<?>[] params2 = setter.getParameterTypes();
				CodeWriter cw = var.newMethod(ACC_PUBLIC, setter.getName(), TypeHelper.class2asm(params2, void.class));

				byte type = fType.type;
				int localSize;
				int stackSize = (char) (type == Type.DOUBLE || type == Type.LONG ? 3 : 2);

				if (!isStatic) {
					localSize = (char) (stackSize+1);
					cw.one(ALOAD_1);
					if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0])) cw.clazz(CHECKCAST, tName);
				} else {
					localSize = stackSize--;
				}
				cw.varLoad(fType, isStatic ? 1 : 2);
				if ((flags&UNCHECKED_CAST) == 0 && type == CLASS && !field.getType().isAssignableFrom(params2[isStatic ? 0 : 1]))
					cw.clazz(CHECKCAST, fType.owner);
				cw.field(isStatic ? PUTSTATIC : PUTFIELD, tName, field.getName(), fType);
				cw.one(RETURN);
				cw.visitSize(stackSize, localSize);
				cw.finish();
			}
		}
		return this;
	}

	public final DirectAccessor<T> i_construct(String target, String desc, String self) { return i_construct(target, desc, checkExistence(self)); }
	public final DirectAccessor<T> i_construct(String target, String desc, Method self) {
		target = target.replace('.', '/');

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		if (sm1 != null) sm1.checkAccess(target, "<init>", desc);

		CodeWriter cw = var.newMethod(ACC_PUBLIC, self.getName(), TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType()));

		cw.clazz(NEW, target);
		cw.one(DUP);

		List<Type> params = TypeHelper.parseMethod(desc);
		params.remove(params.size() - 1);
		int size = 1;
		for (int i = 0; i < params.size(); i++) {
			Type param = params.get(i);
			cw.varLoad(param, ++size);
			switch (param.getActualType()) {
				case 'D': case 'J': size++;
			}
		}

		cw.visitSize(size+1,size+1);

		cw.invoke(INVOKESPECIAL, target, "<init>", desc);
		cw.one(ARETURN);
		cw.finish();

		return this;
	}

	public final DirectAccessor<T> i_delegate(String target, String name, String desc, String self, byte opcode) { return i_delegate(target, name, desc, checkExistence(self), opcode); }
	public final DirectAccessor<T> i_delegate(String target, String name, String desc, Method self, byte opcode) {
		target = target.replace('.', '/');

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		if (sm1 != null) sm1.checkAccess(target, name, desc);

		String sDesc = TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType());

		CodeWriter cw = var.newMethod(ACC_PUBLIC, self.getName(), sDesc);

		boolean isStatic = opcode == INVOKESTATIC;
		if (!isStatic) cw.one(ALOAD_1);

		List<Type> params = TypeHelper.parseMethod(desc);
		params.remove(params.size() - 1);
		int size = isStatic ? 0 : 1;
		for (int i = 0; i < params.size(); i++) {
			Type param = params.get(i);
			cw.varLoad(param, ++size);
			switch (param.getActualType()) {
				case 'D': case 'J': size++;
			}
		}

		cw.visitSize(Math.max(size+(isStatic ? 1 : 0), 1), size+1);

		if (isStatic) {
			cw.invoke(INVOKESTATIC, target, self.getName(), desc);
		} else if (opcode == INVOKEINTERFACE) {
			cw.invokeItf(target, self.getName(), desc);
		} else {
			cw.invoke(opcode, target, self.getName(), desc);
		}
		cw.return_(TypeHelper.class2type(self.getReturnType()));
		cw.finish();

		return this;
	}

	public final DirectAccessor<T> i_access(String target, String name, Type type, String getter, String setter, boolean isStatic) {
		return i_access(target, name, type, checkExistence(getter), checkExistence(setter), isStatic);
	}
	public final DirectAccessor<T> i_access(String target, String name, Type type, Method getter, Method setter, boolean isStatic) {
		target = target.replace('.', '/');

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		if (sm1 != null) sm1.checkAccess(target, name, type.toDesc());

		if (getter != null) {
			Class<?>[] params2 = isStatic ? ArrayCache.CLASSES : getter.getParameterTypes();
			CodeWriter cw = var.newMethod(ACC_PUBLIC, getter.getName(), TypeHelper.class2asm(params2, getter.getReturnType()));

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
			cw.return_(type);
			cw.finish();
		}

		if (setter != null) {
			Class<?>[] params2 = setter.getParameterTypes();
			CodeWriter cw = var.newMethod(ACC_PUBLIC, setter.getName(), TypeHelper.class2asm(params2, void.class));

			byte typeId = type.type;
			int stackSize = (char) (typeId == Type.DOUBLE || typeId == Type.LONG ? 3 : 2);
			int localSize;

			if (!isStatic) {
				localSize = (char) (stackSize+1);
				cw.one(ALOAD_1);
			} else {
				localSize = stackSize--;
			}
			cw.visitSize(stackSize, localSize);
			cw.varLoad(type, isStatic ? 1 : 2);
			cw.field(isStatic ? PUTSTATIC : PUTFIELD, target, name, type);
			cw.one(RETURN);
			cw.finish();
		}

		return this;
	}

	public static <V> DirectAccessor<V> builder(Class<V> impl) { return new DirectAccessor<>(impl, true); }
	public static <V> DirectAccessor<V> builderInternal(Class<V> impl) { return new DirectAccessor<>(impl, false); }

	public final DirectAccessor<T> unchecked() { flags |= UNCHECKED_CAST; return this; }
	public final DirectAccessor<T> weak() { flags |= WEAK_REF; return this; }
	public final DirectAccessor<T> inline() { flags |= INLINE; return this; }

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

	private static void makeHeader(String selfName, String invokerName, ConstantData clz) {
		clz.version = 52 << 16;
		clz.name(selfName.replace('.', '/'));

		clz.parent(MAGIC_ACCESSOR_CLASS);
		clz.interfaces.add(new CstClass(invokerName.replace('.', '/')));
		clz.access = ACC_SUPER | ACC_PUBLIC;
	}

	private static Class<?>[] toFuzzyMode(Class<?>[] params) {
		for (int i = 0; i < params.length; i++) {
			if (!params[i].isPrimitive()) params[i] = Object.class;
		}
		return params;
	}

	private static String toAsmDesc(Class<?>[] classes, Class<?> returns, boolean no_obj) {
		CharList sb = IOUtil.getSharedCharBuf().append('(');

		for (Class<?> clazz : classes) {
			TypeHelper.class2asm(sb, no_obj | clazz.isPrimitive() ? clazz : Object.class);
		}
		sb.append(')');
		return TypeHelper.class2asm(sb, no_obj | returns.isPrimitive() ? returns : Object.class).toString();
	}
}