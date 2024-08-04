package roj.reflect;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nullable;
import roj.RojLib;
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
import roj.io.fs.Filesystem;
import roj.io.fs.WritableFilesystem;
import roj.text.CharList;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * 用接口替代反射，虽然看起来和{@link java.lang.invoke.MethodHandle}很相似，其实却是同一个原理 <br>
 * * 但是理论上比MethodHandle快<br>
 * * 而且MethodHandle也不能随便用(不限制权限)啊<br>
 *
 * @author Roj233
 * @since 2021/8/13 20:16
 */
public sealed class Bypass<T> {
	public static final String MAGIC_ACCESSOR_CLASS = VMInternals.HackMagicAccessor();
	public static final MyBitSet EMPTY_BITS = new MyBitSet(0);

	private final MyHashMap<String, Method> methodByName;
	private final Class<T> itf;
	ConstantData var;

	// Cast check
	byte flags;
	private static final byte UNCHECKED_CAST = 1, WEAK_REF = 2, INLINE = 4;

	private Bypass(Class<T> itf, boolean checkDuplicate) {
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
		String clsName = "roj/gen/DAc$"+ReflectionUtils.uniqueId();
		makeHeader(clsName, itfClass, var);
		ClassDefiner.premake(var);
	}
	Bypass() {
		assert getClass() != Bypass.class;
		this.methodByName = null;
		this.itf = null;
	}

	public final T build() { return build(ClassDefiner.APP_LOADER); }
	@SuppressWarnings("unchecked")
	public T build(ClassLoader def) {
		if (var == null) throw new IllegalStateException("Already built");
		methodByName.clear();

		if ((flags&INLINE) != 0) {
			// jdk.internal.vm.annotation.ForceInline
			Annotation annotation = new Annotation("Ljdk/internal/vm/annotation/ForceInline;", Collections.emptyMap());
			for (MethodNode mn : var.methods) {
				if (mn.name().startsWith("<")) continue;
				mn.putAttr(new Annotations(true, annotation));
			}
		}

		var data = Parser.toByteArrayShared(var);
		try {
			return (T) define(def, data);
		} finally {
			var = null;
		}
	}
	Object define(ClassLoader def, ByteList b) {
		if ((flags&(INLINE|WEAK_REF)) != 0) def = null;

		var klass = def == null
			? ReflectionUtils.defineWeakClass(b)
			: ClassDefiner.defineClass(def, null, b);
		return ClassDefiner.postMake(klass);
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
	public final Bypass<T> construct(Class<?> target, String name) {return construct(target, new String[] {name}, null);}
	/**
	 * @see #construct(Class, String[], List)
	 */
	public final Bypass<T> construct(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return construct(target, names, null);
	}
	/**
	 * @see #construct(Class, String[], List)
	 */
	public final Bypass<T> construct(Class<?> target, String name, Class<?>... param) {
		if (param.length == 0) throw new IllegalArgumentException("Wrong call");
		return construct(target, new String[] {name}, Collections.singletonList(param));
	}
	/**
	 * @see #construct(Class, String[], List)
	 */
	public final Bypass<T> constructFuzzy(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return construct(target, names, Collections.emptyList());
	}

	/**
	 * 将target的构造器代理到names方法，按照它们的参数，或fuzzy指定的参数 <br>
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
	 */
	public Bypass<T> construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy) throws IllegalArgumentException {
		if (names.length == 0) return this;

		String tName = target.getName().replace('.', '/');

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		Constructor<?>[] constructors = fuzzy == null ? null : target.getDeclaredConstructors();
		for (int i = 0; i < names.length; i++) {
			String name = names[i];
			Method m = methodByName.get(name);
			Constructor<?> c = null;
			if (m == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
			if (!m.getReturnType().isAssignableFrom(target)) throw new IllegalArgumentException(itf.getName()+'.'+name+" 的返回值 ("+m.getReturnType().getName()+") 不兼容 "+target.getName());

			Class<?>[] types = m.getParameterTypes();

			try {
				if (fuzzy == null || (!fuzzy.isEmpty() && fuzzy.get(i) != null)) {
					// for exception
					c = target.getDeclaredConstructor(fuzzy == null ? types : (types = fuzzy.get(i)));
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
										"其一: "+TypeHelper.class2asm(c.getParameterTypes(), void.class)+"\n"+
										"其二: "+TypeHelper.class2asm(cr.getParameterTypes(), void.class));
							}
							c = cr;
							found = true;
						}
					}
					if (!found) throw new NoSuchMethodException();
				}
				if (sm1 != null && !sm1.checkConstruct(c)) throw new NoSuchMethodException();
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到 "+target.getName()+" 的构造器, 参数: "+TypeHelper.class2asm(types, void.class)+", 在报错前已成功"+i+"个");
			}
			methodByName.remove(name);

			Class<?>[] tTypes = c.getParameterTypes();
			types = m.getParameterTypes();
			String sDesc = TypeHelper.class2asm(types, m.getReturnType());

			CodeWriter cw = var.newMethod(ACC_PUBLIC, m.getName(), sDesc);

			cw.clazz(NEW, tName);
			cw.one(DUP);

			int varId = 1;
			for (int j = 0; j < tTypes.length; j++) {
				Class<?> param = tTypes[j];
				Type type = TypeHelper.class2type(param);
				cw.varLoad(type, varId);
				if ((flags&UNCHECKED_CAST) == 0 && !param.isAssignableFrom(types[j]))
					cw.clazz(CHECKCAST, type.getActualClass());
				varId += type.length();
			}
			cw.visitSize(varId+1, varId);

			String tDesc = TypeHelper.class2asm(tTypes, void.class);
			cw.invoke(INVOKESPECIAL, tName, "<init>", tDesc);
			cw.one(ARETURN);
			cw.finish();
		}

		return this;
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String name) { String[] arr = new String[] {name}; return delegate(target, arr, EMPTY_BITS, arr, null); }
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, names, EMPTY_BITS, names, null);
	}

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String name, String selfName) { return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, null); }
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String[] names, String[] selfNames) { return delegate(target, names, EMPTY_BITS, selfNames, null); }

	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String name) { String[] arr = new String[] {name}; return delegate(target, arr, EMPTY_BITS, arr, Collections.emptyList()); }
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, names, EMPTY_BITS, names, Collections.emptyList());
	}
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String name, String selfName) { return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, Collections.emptyList()); }
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String name, String selfName, Class<?>... param) {
		if (param.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, new String[] {name}, EMPTY_BITS, new String[] {selfName}, Collections.singletonList(param));
	}
	/**
	 * @see #delegate(Class, String[], MyBitSet, String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String[] names, String[] selfNames) { return delegate(target, names, EMPTY_BITS, selfNames, Collections.emptyList()); }

	/**
	 * 将target.methodNames方法代理到selfNames，参数从方法获取，或通过fuzzyMode <br>
	 * <br>
	 *
	 * @param flags 当set中对应index项为true时代表直接调用此方法(忽略继承)
	 * @param fuzzyMode : {@link #construct(Class, String[], List)}
	 */
	public Bypass<T> delegate(Class<?> target, String[] methodNames, @Nullable MyBitSet flags, String[] selfNames, List<Class<?>[]> fuzzyMode) throws IllegalArgumentException {
		if (selfNames.length == 0) return this;

		String tName = target.getName().replace('.', '/');

		ILSecurityManager sm1 = ILSecurityManager.getSecurityManager();
		List<Method> methods = ReflectionUtils.getMethods(target, methodNames);
		for (int i = 0; i < selfNames.length; i++) {
			String name = selfNames[i];
			Method sm = methodByName.get(name), tm = null;
			if (sm == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");

			Class<?>[] types = sm.getParameterTypes();

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
						if (off1 == 1 && !types[0].isAssignableFrom(target)) continue;

						if (found != -1) {
							if (!Arrays.equals(m.getParameterTypes(), tm.getParameterTypes())) {
								throw new IllegalArgumentException(
									"无法为 "+itf.getName()+'.'+name+" 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n"+
										"其一: "+TypeHelper.class2asm(tm.getParameterTypes(), tm.getReturnType())+"\n"+
										"其二: "+TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType()));
							} else {
								// 继承，却改变了返回值的类型
								// 同参同反不考虑
								m = findSuccessor(m, tm);
							}
						}
						found = j;
						tm = m;
					}
				}
				if (found == -1 || sm1 != null && !sm1.checkInvoke(tm)) throw new NoSuchMethodException();
				methods.remove(found);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到指定的方法: "+target.getName()+'.'+targetMethodName+" 参数 "+TypeHelper.class2asm(types, sm.getReturnType())+", 报错前已成功"+i+"个");
			}

			if (!sm.getReturnType().isAssignableFrom(tm.getReturnType()))
				throw new IllegalArgumentException(itf.getName()+'.'+name+" 的返回值 ("+sm.getReturnType().getName()+") 不兼容 "+tm.getReturnType().getName());

			methodByName.remove(name);

			Class<?>[] params = tm.getParameterTypes();
			String tDesc = TypeHelper.class2asm(params, tm.getReturnType());

			types = sm.getParameterTypes();
			String sDesc = TypeHelper.class2asm(types, sm.getReturnType());
			CodeWriter cw = var.newMethod(ACC_PUBLIC, selfNames[i], sDesc);

			int isStatic = (tm.getModifiers() & ACC_STATIC) != 0 ? 1 : 0;
			int varId;
			if (isStatic != 0) varId = 1;
			else {
				cw.one(ALOAD_1);
				if ((this.flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(types[0])) cw.clazz(CHECKCAST, tName);
				varId = 2;
			}

			int j = varId-1;
			for (Class<?> param : params) {
				Type type = TypeHelper.class2type(param);
				cw.varLoad(type, varId);
				if ((this.flags&UNCHECKED_CAST) == 0 && !param.isAssignableFrom(types[j++])) // 强制转换再做检查...
					cw.clazz(CHECKCAST, type.getActualClass());
				varId += type.length();
			}

			cw.visitSize(varId == 1 && !sDesc.endsWith("V") ? 1 : varId, varId);

			if (isStatic != 0) {
				cw.invoke(INVOKESTATIC, tName, tm.getName(), tDesc, target.isInterface());
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

	private static Method findSuccessor(Method a, Method b) {
		Class<?> aClass = a.getDeclaringClass();
		Class<?> bClass = b.getDeclaringClass();
		// b instanceof a
		return aClass.isAssignableFrom(bClass) ? b : a;
	}

	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final Bypass<T> access(Class<?> target, String fieldName) { return access(target, new String[] {fieldName}); }
	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final Bypass<T> access(Class<?> target, String[] fields) { return access(target, fields, capitalize(fields, "get"), capitalize(fields, "set")); }
	/**
	 * @see #access(Class, String[], String[], String[])
	 */
	public final Bypass<T> access(Class<?> target, String field, String getter, String setter) { return access(target, new String[] {field}, new String[] {getter}, new String[] {setter}); }

	/**
	 * 把 setter/getters 中的方法标记为 target 的 fields 的 setter / getter
	 */
	public Bypass<T> access(Class<?> target, String[] fields, String[] getters, String[] setters) throws IllegalArgumentException {
		if (fields.length == 0) return this;

		String tName = target.getName().replace('.', '/');
		Field[] fieldFs = ReflectionUtils.getFieldsByName(target, fields);

		for (int i = 0; i < fields.length; i++) {
			Field field = fieldFs[i];
			Type fType = TypeHelper.class2type(field.getType());

			String name;
			int off = (field.getModifiers() & ACC_STATIC) != 0 ? 0 : 1;
			boolean isStatic = (field.getModifiers() & ACC_STATIC) != 0;

			name = getters == null ? null : getters[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
				if (method.getParameterCount() != off) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 getter, 不应该有参数, got "+(method.getParameterCount() - off)+'!');
				if (!method.getReturnType().isAssignableFrom(fieldFs[i].getType())) {
					throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 getter, 但是返回值不兼容 "+fieldFs[i].getType().getName()+" ("+method.getReturnType().getName()+')');
				}

				methodByName.remove(name);

				Class<?>[] params2 = isStatic ? ArrayCache.CLASSES : method.getParameterTypes();
				CodeWriter cw = var.newMethod(ACC_PUBLIC, method.getName(), TypeHelper.class2asm(params2, method.getReturnType()));

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
				cw.visitSize(fType.length(), localSize);
				cw.return_(fType);
				cw.finish();
			}

			name = setters == null ? null : setters[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
				if (method.getParameterCount() != off+1) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 只应有1个参数, got "+method.getParameterCount()+'!');
				if (!method.getParameterTypes()[off].isAssignableFrom(fieldFs[i].getType())) {
					throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 但是参数["+(off+1)+"]不兼容 "+fieldFs[i].getType().getName()+" ("+method.getReturnType().getName()+')');
				}
				if (method.getReturnType() != void.class) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 但是它的返回值不是void: "+method.getReturnType().getName());

				methodByName.remove(name);

				Class<?>[] params2 = method.getParameterTypes();
				CodeWriter cw = var.newMethod(ACC_PUBLIC, method.getName(), TypeHelper.class2asm(params2, void.class));

				// MAI only ignore visibility, not final
				if ((field.getModifiers()&ACC_FINAL) != 0) {
					cw.visitSize(4, isStatic ? 2 : 3);
					// stack = 4
					// local = 2
					cw.field(GETSTATIC, "roj/reflect/ReflectionUtils", "u", "Lsun/misc/Unsafe;");
					if (isStatic) cw.ldc(new CstClass(tName));
					else {
						if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0]))
							cw.clazz(CHECKCAST, tName);
						cw.one(ALOAD_1);
					}
					cw.ldc(isStatic ? u.staticFieldOffset(field) : u.objectFieldOffset(field));
					cw.varLoad(fType, isStatic ? 1 : 2);
					if ((flags&UNCHECKED_CAST) == 0 && !fType.isPrimitive() && !field.getType().isAssignableFrom(params2[off]))
						cw.clazz(CHECKCAST, fType.getActualClass());
					cw.invoke(INVOKESPECIAL, "sun/misc/Unsafe", "put"+(fType.isPrimitive()?upper(fType.toString()):"Object"), "(Ljava/lang/Object;J"+(fType.isPrimitive()?fType.toDesc():"Ljava/lang/Object;")+")V");
				} else {
					int localSize, stackSize = fType.length()+1;

					if (!isStatic) {
						localSize = stackSize+1;
						cw.one(ALOAD_1);
						if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0]))
							cw.clazz(CHECKCAST, tName);
					} else {
						localSize = stackSize--;
					}
					cw.visitSize(stackSize, localSize);
					cw.varLoad(fType, isStatic ? 1 : 2);
					if ((flags&UNCHECKED_CAST) == 0 && !fType.isPrimitive() && !field.getType().isAssignableFrom(params2[off]))
						cw.clazz(CHECKCAST, fType.getActualClass());
					cw.field(isStatic ? PUTSTATIC : PUTFIELD, tName, field.getName(), fType);
				}

				cw.one(RETURN);
				cw.finish();
			}
		}
		return this;
	}
	private static String upper(String s) {
		char[] tmp = new char[s.length()];
		s.getChars(0, s.length(), tmp, 0);
		tmp[0] = Character.toUpperCase(tmp[0]);
		return new String(tmp);
	}

	public final Bypass<T> i_construct(String target, String desc, String self) { return i_construct(target, desc, checkExistence(self)); }
	public Bypass<T> i_construct(String target, String desc, Method self) {
		target = target.replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkAccess(target, "<init>", desc);

		CodeWriter cw = var.newMethod(ACC_PUBLIC, self.getName(), TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType()));

		cw.clazz(NEW, target);
		cw.one(DUP);

		List<Type> argTypes = TypeHelper.parseMethod(desc);
		argTypes.remove(argTypes.size()-1);

		int varId = 1;
		for (int i = 0; i < argTypes.size(); i++) {
			Type type = argTypes.get(i);
			cw.varLoad(type, varId);
			varId += type.length();
		}

		cw.visitSize(varId+1, varId);

		cw.invoke(INVOKESPECIAL, target, "<init>", desc);
		cw.one(ARETURN);
		cw.finish();

		return this;
	}

	public static final byte INVOKE_STATIC = 1, INVOKE_SPECIAL = 2, INVOKE_INTERFACE = 4;
	public final Bypass<T> i_delegate(String target, String name, String desc, String self, byte flags) { return i_delegate(target, name, desc, checkExistence(self), flags); }
	public Bypass<T> i_delegate(String target, String name, String desc, Method self, @MagicConstant(flags = {INVOKE_STATIC, INVOKE_SPECIAL, INVOKE_INTERFACE}) byte flags) {
		target = target.replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkAccess(target, name, desc);

		String sDesc = TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType());

		CodeWriter cw = var.newMethod(ACC_PUBLIC, self.getName(), sDesc);

		List<Type> argTypes = TypeHelper.parseMethod(desc);
		Type retype = argTypes.remove(argTypes.size()-1);

		boolean isStatic = (flags&INVOKE_STATIC) != 0;
		int varId;
		if (isStatic) varId = 1;
		else {
			cw.one(ALOAD_1);
			varId = 2;
		}
		for (int i = 0; i < argTypes.size(); i++) {
			Type type = argTypes.get(i);
			cw.varLoad(type, varId);
			varId += type.length();
		}

		cw.visitSize(varId == 1 && !sDesc.endsWith("V") ? 1 : varId-1, varId);

		if (isStatic) {
			cw.invoke(INVOKESTATIC, target, self.getName(), desc, (flags&INVOKE_INTERFACE)!=0);
		} else if ((flags&INVOKE_INTERFACE) != 0) {
			cw.invokeItf(target, self.getName(), desc);
		} else {
			cw.invoke((flags&INVOKE_SPECIAL) == 0 ? INVOKESPECIAL : INVOKEVIRTUAL, target, self.getName(), desc);
		}
		cw.return_(retype);
		cw.finish();

		return this;
	}

	public final Bypass<T> i_access(String target, String name, Type type, String getter, String setter, boolean isStatic) {
		return i_access(target, name, type, checkExistence(getter), checkExistence(setter), isStatic);
	}
	public Bypass<T> i_access(String target, String name, Type type, Method getter, Method setter, boolean isStatic) {
		target = target.replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkAccess(target, name, type.toDesc());

		if (getter != null) {
			Class<?>[] params2 = isStatic ? ArrayCache.CLASSES : getter.getParameterTypes();
			CodeWriter cw = var.newMethod(ACC_PUBLIC, getter.getName(), TypeHelper.class2asm(params2, getter.getReturnType()));

			int stackSize = type.length();

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

			int stackSize = type.length()+1;
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

	public static <V> Bypass<V> builder(Class<V> impl) {return new Bypass<>(impl, true);}
	public static <V> Bypass<V> custom(Class<V> impl) {return new Bypass<>(impl, false);}

	public final Bypass<T> unchecked() { flags |= UNCHECKED_CAST; return this; }
	public final Bypass<T> weak() { flags |= WEAK_REF; return this; }
	public final Bypass<T> inline() { flags |= INLINE; return this; }

	public static <V> Bypass<V> cached(String cache, Class<V> impl) {
		var fs = (Filesystem)RojLib.inject("roj.reflect.Bypass.cache");
		try {
			InputStream in;
			if (fs != null && (in=fs.getStream(cache)) != null)
				return Helpers.cast(new Cached(IOUtil.read(in)));
		} catch (IOException ignored) {}
		if (fs instanceof WritableFilesystem wfs)
			return Helpers.cast(new ToCache(wfs, cache, impl));

		return builder(impl);
	}
	private static final class ToCache extends Bypass<Object> {
		private final WritableFilesystem wfs;
		private final String name;
		public ToCache(WritableFilesystem wfs, String name, Class<?> impl) {
			super(Helpers.cast(impl), true);
			this.wfs = wfs;
			this.name = name;
		}

		@Override
		Object define(ClassLoader def, ByteList b) {
			try {
				wfs.write(name, b);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return super.define(def, b);
		}
	}
	private static final class Cached extends Bypass<Object> {
		private final byte[] data;
		private Cached(byte[] data) {this.data = data;}
		@Override public Bypass<Object> access(Class<?> a, String[] b, String[] c, String[] d) throws IllegalArgumentException {return this;}
		@Override public Bypass<Object> construct(Class<?> a, String[] b, List<Class<?>[]> c) throws IllegalArgumentException {return this;}
		@Override public Bypass<Object> delegate(Class<?> a, String[] b, MyBitSet c, String[] d, List<Class<?>[]> e) throws IllegalArgumentException {return this;}
		@Override public Bypass<Object> i_access(String a, String b, Type c, Method d, Method e, boolean f) {return this;}
		@Override public Bypass<Object> i_construct(String a, String b, Method c) {return this;}
		@Override public Bypass<Object> i_delegate(String a, String b, String c, Method d, byte e) {return this;}
		@Override public Object build(ClassLoader def) {return define(def, DynByteBuf.wrap(data));}
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

	private static void makeHeader(String selfName, String invokerName, ConstantData clz) {
		clz.version = 52 << 16;
		clz.name(selfName.replace('.', '/'));

		clz.parent(MAGIC_ACCESSOR_CLASS);
		clz.addInterface(invokerName.replace('.', '/'));
		clz.modifier = ACC_SUPER | ACC_PUBLIC;
	}

	private static Class<?>[] toFuzzyMode(Class<?>[] params) {
		for (int i = 0; i < params.length; i++) {
			if (!params[i].isPrimitive()) params[i] = Object.class;
		}
		return params;
	}
}