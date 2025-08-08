package roj.reflect;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.ToIntMap;
import roj.compiler.resolve.TypeCast;
import roj.text.CharList;
import roj.util.ArrayCache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.reflect.Unaligned.U;

/**
 * 用接口替代反射，虽然看起来和{@link java.lang.invoke.MethodHandle}很相似，其实却是同一个原理 <br>
 * * 但是理论上比MethodHandle快<br>
 * * 而且MethodHandle也不能随便用(不限制权限)啊<br>
 *
 * @author Roj233
 * @since 2021/8/13 20:16
 */
public final class Bypass<T> {
	private final Class<?> caller;

	private final Class<T> itf;
	private final HashMap<String, Method> methodByName;
	private ClassNode impl;

	private byte flags;
	private static final byte UNCHECKED_CAST = 1, WEAK_REF = 2, INLINE = 4;

	private Bypass(Class<T> itf, boolean checkDuplicate) {
		if (!itf.isInterface()) throw new IllegalArgumentException(itf.getName()+" must be a interface");
		this.itf = itf;
		Method[] methods = itf.getMethods();
		this.methodByName = new HashMap<>(methods.length);
		for (Method method : methods) {
			if ((method.getModifiers() & ACC_STATIC) != 0) continue;

			// skip 'internal' methods
			if (("toString".equals(method.getName()) || "clone".equals(method.getName())) && method.getParameterCount() == 0) continue;

			if (methodByName.put(method.getName(), method) != null && checkDuplicate) {
				throw new IllegalArgumentException("方法名重复: '"+method.getName()+"' in "+itf.getName());
			}
		}
		impl = new ClassNode();
		Class<?> caller = Reflection.getCallerClass(3);
		String clsName = caller.getName().replace('.', '/')+"$Bypass$"+Reflection.uniqueId();
		String itfClass = itf.getName().replace('.', '/');
		makeHeader(clsName, itfClass, impl);
		this.caller = caller;
	}

	public final T build() { return build(ClassDefiner.APP_LOADER); }
	@SuppressWarnings("unchecked")
	public T build(ClassLoader def) {
		if (impl == null) throw new IllegalStateException("Already built");
		DMHBuild();
		methodByName.clear();

		if ((flags&INLINE) != 0) {
			// jdk.internal.vm.annotation.ForceInline
			Annotation annotation = new Annotation("Ljdk/internal/vm/annotation/ForceInline;", Collections.emptyMap());
			for (MethodNode mn : impl.methods) {
				if (mn.name().startsWith("<")) continue;
				mn.addAttribute(new Annotations(true, annotation));
			}
		}

		if ((flags&(INLINE|WEAK_REF)) != 0) def = null;

		var node = impl;
		impl = null;
		return (T) ClassDefiner.newInstance(node, def);
	}

	private Method checkExistence(String name) {
		if (name == null) return null;
		Method method = methodByName.remove(name);
		if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
		return method;
	}

	private CodeWriter dmh_si;
	private ToIntMap<String> dmh_desc;

	private boolean DMHInit() {
		if (Reflection.JAVA_VERSION < 22) return false;

		if (dmh_si == null) {
			dmh_si = impl.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
			dmh_si.visitSize(4,0);
			dmh_desc = new ToIntMap<>();
		}
		return true;
	}
	private void DMHBuild() {
		if (dmh_si != null) {
			dmh_si.insn(RETURN);
			dmh_si.finish();
		}
	}
	private int DMHNewConstructorHandle(String className, String methodDesc) {
		String key = className+'<'+methodDesc;
		int id = dmh_desc.getOrDefault(key, -1);
		if (id >= 0) return id;

		dmh_si.field(GETSTATIC, "roj/reflect/Reflection", "IMPL_LOOKUP", "Ljava/lang/invoke/MethodHandles$Lookup;");
		ldcClass(className);
		var args = Type.methodDesc(methodDesc);
		ldcType(args.remove(args.size()-1));
		dmh_si.newArraySized(Type.klass("java/lang/Class"), args.size());
		for (int i = 0; i < args.size(); i++) {
			dmh_si.insn(DUP);
			dmh_si.ldc(i);
			ldcType(args.get(i));
			dmh_si.insn(AASTORE);
		}
		dmh_si.visitSizeMax(7, 0);
		dmh_si.invokeS("java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;");
		dmh_si.invokeV("java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");

		int fid = impl.newField(ACC_STATIC|ACC_FINAL, "vh$"+impl.fields.size(), "Ljava/lang/invoke/MethodHandle;");

		dmh_si.field(PUTSTATIC, impl, fid);
		dmh_desc.putInt(key, fid);
		return fid;
	}
	private int DMHNewMethodHandle(String className, String methodName, String methodDesc, String methodId) {
		String key = className+'.'+methodName+'<'+methodDesc;
		int id = dmh_desc.getOrDefault(key, -1);
		if (id >= 0) return id;

		dmh_si.field(GETSTATIC, "roj/reflect/Reflection", "IMPL_LOOKUP", "Ljava/lang/invoke/MethodHandles$Lookup;");
		ldcClass(className);
		dmh_si.ldc(methodName);
		var args = Type.methodDesc(methodDesc);
		ldcType(args.remove(args.size()-1));
		dmh_si.newArraySized(Type.klass("java/lang/Class"), args.size());
		for (int i = 0; i < args.size(); i++) {
			dmh_si.insn(DUP);
			dmh_si.ldc(i);
			ldcType(args.get(i));
			dmh_si.insn(AASTORE);
		}
		dmh_si.visitSizeMax(8, 0);
		dmh_si.invokeS("java/lang/invoke/MethodType", "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;");
		dmh_si.invokeV("java/lang/invoke/MethodHandles$Lookup", methodId, "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");

		int fid = impl.newField(ACC_STATIC|ACC_FINAL, "vh$"+impl.fields.size(), "Ljava/lang/invoke/MethodHandle;");

		dmh_si.field(PUTSTATIC, impl, fid);
		dmh_desc.putInt(key, fid);
		return fid;
	}
	private int DMHNewVarHandle(String className, String fieldName, Type fieldType, String fieldId) {
		String key = className+'.'+fieldName;
		int id = dmh_desc.getOrDefault(key, -1);
		if (id >= 0) return id;

		dmh_si.field(GETSTATIC, "roj/reflect/Reflection", "IMPL_LOOKUP", "Ljava/lang/invoke/MethodHandles$Lookup;");
		ldcClass(className);
		dmh_si.ldc(fieldName);
		ldcType(fieldType);
		dmh_si.invokeV("java/lang/invoke/MethodHandles$Lookup", fieldId, "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/VarHandle;");

		int fid = impl.newField(ACC_STATIC|ACC_FINAL, "vh$"+impl.fields.size(), "Ljava/lang/invoke/VarHandle;");

		dmh_si.field(PUTSTATIC, impl, fid);
		dmh_desc.putInt(key, fid);
		return fid;
	}
	private void ldcType(Type fieldType) {
		if (fieldType.getActualType() == Type.CLASS) ldcClass(fieldType.getActualClass());
		else {
			String wrapperType = TypeCast.getWrapper(fieldType).owner;
			dmh_si.field(GETSTATIC, wrapperType, "TYPE", "Ljava/lang/Class;");
		}
	}
	private void ldcClass(String className) {
		dmh_si.ldc(className.replace('/', '.'));
		dmh_si.invokeS("java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;");
	}
	private static Class<?>[] fuzzyIfNotAccessible(Class<?>[] params) {
		for (int i = 0; i < params.length; i++) {
			Class<?> param = params[i];
			if ((param.getModifiers() & ACC_PUBLIC) == 0) {
				params[i] = Object.class;
			}
		}
		return params;
	}
	private static Class<?> fuzzyIfNotAccessible(Class<?> type) {return (type.getModifiers()&ACC_PUBLIC) == 0 ? Object.class : type;}


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
	 * 将目标类的构造器代理到接口方法，支持参数类型精确匹配或模糊匹配(all-object模式)<br>
	 *
	 * <p>当启用模糊匹配模式时，所有非基本类型参数和返回值将使用Object类型进行匹配，
	 * 适用于无法直接访问目标类的情况。
	 *
	 * @param target  目标构造器所属的类
	 * @param names   接口中对应构造器代理方法的方法名数组
	 * @param fuzzy   模糊匹配控制列表，允许三种模式：
	 *                <ul>
	 *                  <li>null - 禁用all-object模式，使用精确参数类型匹配</li>
	 *                  <li>空列表 - 对所有参数启用模糊匹配</li>
	 *                  <li>非空列表 - 长度必须与names数组一致：
	 *                    <ul>
	 *                      <li>列表中null元素对应的方法参数启用模糊匹配</li>
	 *                      <li>非null元素（Class数组）指定对应方法的精确参数类型</li>
	 *                    </ul>
	 *                  </li>
	 *                </ul>
	 * @throws IllegalArgumentException 当参数长度不匹配或参数类型无效时抛出
	 */
	public Bypass<T> construct(Class<?> target, String[] names, List<Class<?>[]> fuzzy) throws IllegalArgumentException {
		if (names.length == 0) return this;

		String targetName = target.getName().replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
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
				if (sm != null && !sm.checkConstruct(c, caller)) throw new NoSuchMethodException();
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到 "+target.getName()+" 的构造器, 参数: "+TypeHelper.class2asm(types, void.class)+", 在报错前已成功"+i+"个");
			}
			methodByName.remove(name);

			types = m.getParameterTypes();
			Class<?>[] constructorArguments = c.getParameterTypes();
			String targetDesc = TypeHelper.class2asm(constructorArguments, void.class);
			String selfDesc = TypeHelper.class2asm(types, m.getReturnType());

			CodeWriter cw = impl.newMethod(ACC_PUBLIC, m.getName(), selfDesc);

			if (DMHInit()) {
				int DMH_ID = DMHNewConstructorHandle(targetName, targetDesc);
				cw.field(GETSTATIC, impl, DMH_ID);
			} else {
				cw.clazz(NEW, targetName);
				cw.insn(DUP);
			}

			int varId = 1;
			for (int j = 0; j < constructorArguments.length; j++) {
				Class<?> param = constructorArguments[j];
				Type type = Type.from(param);
				cw.varLoad(type, varId);
				if ((flags&UNCHECKED_CAST) == 0 && !param.isAssignableFrom(types[j]))
					cw.clazz(CHECKCAST, type.getActualClass());
				varId += type.length();
			}
			cw.visitSize(varId+1, varId);

			if (DMHInit()) {
				cw.invokeV("java/lang/invoke/MethodHandle", "invoke", selfDesc);
			} else {
				cw.invoke(INVOKESPECIAL, targetName, "<init>", targetDesc);
			}

			cw.insn(ARETURN);
			cw.finish();
		}

		return this;
	}

	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String name) { String[] arr = new String[] {name}; return delegate(target, arr, arr, null); }
	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, names, names, null);
	}

	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String name, String selfName) { return delegate(target, new String[] {name}, new String[] {selfName}, null); }
	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate(Class<?> target, String[] names, String[] selfNames) { return delegate(target, names, selfNames, null); }

	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String name) { String[] arr = new String[] {name}; return delegate(target, arr, arr, Collections.emptyList()); }
	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String... names) {
		if (names.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, names, names, Collections.emptyList());
	}
	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String name, String selfName) { return delegate(target, new String[] {name}, new String[] {selfName}, Collections.emptyList()); }
	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String name, String selfName, Class<?>... param) {
		if (param.length == 0) throw new IllegalArgumentException("Wrong call");
		return delegate(target, new String[] {name}, new String[] {selfName}, Collections.singletonList(param));
	}
	/**
	 * @see #delegate(Class, String[], String[], List)
	 */
	public final Bypass<T> delegate_o(Class<?> target, String[] names, String[] selfNames) { return delegate(target, names, selfNames, Collections.emptyList()); }

	/**
	 * 将目标类的方法代理到接口方法，支持静态绑定和参数匹配模式
	 *
	 * @param target      目标方法所属的类
	 * @param methodNames 需要代理的目标方法名数组
	 * @param selfNames   接口中对应代理方法的方法名数组
	 * @param fuzzyMode   参数匹配模式（参考{@link #construct(Class, String[], List)}）：
	 *                    <ul>
	 *                      <li>null - 禁用模糊匹配</li>
	 *                      <li>空列表 - 启用全参数模糊匹配</li>
	 *                      <li>非空列表 - 根据每项的值为方法指定精确/模糊类型</li>
	 *                    </ul>
	 * @throws IllegalArgumentException 当参数长度不匹配或参数类型无效时抛出
	 */
	public Bypass<T> delegate(Class<?> target, String[] methodNames, String[] selfNames, List<Class<?>[]> fuzzyMode) throws IllegalArgumentException {
		if (selfNames.length == 0) return this;

		String targetName = target.getName().replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		List<Method> targetMethods = getMethods(target, methodNames);
		for (int i = 0; i < selfNames.length; i++) {
			String name = selfNames[i];
			Method selfMethod = methodByName.get(name), targetMethod = null;
			if (selfMethod == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");

			Class<?>[] types = selfMethod.getParameterTypes();

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
				for (int j = 0; j < targetMethods.size(); j++) {
					Method m = targetMethods.get(j);
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
							if (!Arrays.equals(m.getParameterTypes(), targetMethod.getParameterTypes())) {
								throw new IllegalArgumentException(
									"无法为 "+itf.getName()+'.'+name+" 使用模糊模式: 对于指定非基本类型的数量和位置有多个符合的方法\n"+
										"其一: "+TypeHelper.class2asm(targetMethod.getParameterTypes(), targetMethod.getReturnType())+"\n"+
										"其二: "+TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType()));
							} else {
								// 继承，却改变了返回值的类型
								// 同参同反不考虑
								m = findSuccessor(m, targetMethod);
							}
						}
						found = j;
						targetMethod = m;
					}
				}
				if (found == -1 || sm != null && !sm.checkInvoke(targetMethod, caller)) throw new NoSuchMethodException();
				targetMethods.remove(found);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException("无法找到指定的方法: "+target.getName()+'.'+targetMethodName+" 参数 "+TypeHelper.class2asm(types, selfMethod.getReturnType())+", 报错前已成功"+i+"个");
			}

			if (!selfMethod.getReturnType().isAssignableFrom(targetMethod.getReturnType()))
				throw new IllegalArgumentException(itf.getName()+'.'+name+" 的返回值 ("+selfMethod.getReturnType().getName()+") 不兼容 "+targetMethod.getReturnType().getName());

			methodByName.remove(name);

			Class<?>[] params = targetMethod.getParameterTypes();
			String targetDesc = TypeHelper.class2asm(params, targetMethod.getReturnType());

			types = selfMethod.getParameterTypes();
			String selfDesc = TypeHelper.class2asm(types, selfMethod.getReturnType());
			CodeWriter cw = impl.newMethod(ACC_PUBLIC, selfNames[i], selfDesc);

			int isStatic = (targetMethod.getModifiers() & ACC_STATIC) != 0 ? 1 : 0;
			int varId = 2 - isStatic;

			if (DMHInit()) {
				int fid = DMHNewMethodHandle(targetName, targetMethodName, targetDesc, isStatic == 0 ? "findVirtual" : "findStatic");
				cw.field(GETSTATIC, impl, fid);
			}

			if (isStatic == 0) {
				cw.insn(ALOAD_1);
				if ((this.flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(types[0])) cw.clazz(CHECKCAST, targetName);
			}

			int j = varId-1;
			for (Class<?> param : params) {
				Type type = Type.from(param);
				cw.varLoad(type, varId);
				if ((this.flags&UNCHECKED_CAST) == 0 && !param.isAssignableFrom(types[j++])) // 强制转换再做检查...
					cw.clazz(CHECKCAST, type.getActualClass());
				varId += type.length();
			}

			cw.visitSize(varId == 1 && !selfDesc.endsWith("V") ? 1 : varId, varId);

			if (DMHInit()) {
				targetDesc = TypeHelper.class2asm(fuzzyIfNotAccessible(params), fuzzyIfNotAccessible(targetMethod.getReturnType()));
				if (isStatic == 0) targetDesc = "(L"+targetName+";"+targetDesc.substring(1);
				cw.invokeV("java/lang/invoke/MethodHandle", "invoke", targetDesc);
			} else {
				if (isStatic != 0) {
					cw.invoke(INVOKESTATIC, targetName, targetMethod.getName(), targetDesc, target.isInterface());
				} else if (target.isInterface()) {
					cw.invokeItf(targetName, targetMethod.getName(), targetDesc);
				} else {
					cw.invokeV(targetName, targetMethod.getName(), targetDesc);
				}
			}

			cw.return_(Type.from(targetMethod.getReturnType()));
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
	 * 获取current + 父类 所有Method
	 */
	private static List<Method> getMethods(Class<?> clazz, String... methodNames) {
		HashSet<Method> result = new HashSet<>();
		HashSet<String> filter = new HashSet<>(methodNames);
		while (clazz != null && clazz != Object.class) {
			for (Method method : clazz.getDeclaredMethods()) {
				if (filter.contains(method.getName())) {
					result.add(method);
				}
			}
			clazz = clazz.getSuperclass();
		}
		return new ArrayList<>(result);
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
	 * 将接口方法映射为目标类的字段访问器
	 *
	 * @param target           目标字段所属的类
	 * @param targetFieldNames 目标字段名数组
	 * @param selfGetterNames  接口中字段getter方法名数组（允许单项为null表示不生成对应getter）
	 * @param selfSetterNames  接口中字段setter方法名数组（允许单项为null表示不生成对应setter）
	 * @throws IllegalArgumentException 当字段不存在或访问权限不足时抛出
	 */
	public Bypass<T> access(Class<?> target, String[] targetFieldNames, String[] selfGetterNames, String[] selfSetterNames) throws IllegalArgumentException {
		if (targetFieldNames.length == 0) return this;

		String targetName = target.getName().replace('.', '/');
		Field[] targetFields = getFieldsByName(target, targetFieldNames);

		for (int i = 0; i < targetFieldNames.length; i++) {
			Field field = targetFields[i];
			Type fieldType = Type.from(field.getType());

			String name;
			int off = (field.getModifiers() & ACC_STATIC) != 0 ? 0 : 1;
			boolean isStatic = (field.getModifiers() & ACC_STATIC) != 0;

			name = selfGetterNames == null ? null : selfGetterNames[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
				if (method.getParameterCount() != off) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 getter, 不应该有参数, got "+(method.getParameterCount() - off)+'!');
				if (!method.getReturnType().isAssignableFrom(targetFields[i].getType())) {
					throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 getter, 但是返回值不兼容 "+targetFields[i].getType().getName()+" ("+method.getReturnType().getName()+')');
				}

				methodByName.remove(name);

				Class<?>[] params = isStatic ? ArrayCache.CLASSES : method.getParameterTypes();
				CodeWriter cw = impl.newMethod(ACC_PUBLIC, method.getName(), TypeHelper.class2asm(params, method.getReturnType()));

				if (DMHInit()) {
					int DMH_ID = DMHNewVarHandle(targetName, field.getName(), fieldType, isStatic ? "findStaticVarHandle" : "findVarHandle");
					var targetName1 = fuzzyIfNotAccessible(target).getName().replace('.', '/');
					fieldType = Type.from(fuzzyIfNotAccessible(field.getType()));
					cw.field(GETSTATIC, impl, DMH_ID);
					if (isStatic) {
						cw.invokeV("java/lang/invoke/VarHandle", "get", "()"+fieldType.toDesc());
					} else {
						cw.insn(ALOAD_1);
						cw.invokeV("java/lang/invoke/VarHandle", "get", "(L"+targetName1+";)"+fieldType.toDesc());
					}
					cw.visitSize(fieldType.length()+1, isStatic ? 1 : 2);
				} else {
					int localSize;

					if (isStatic) {
						localSize = 1;
						cw.field(GETSTATIC, targetName, field.getName(), fieldType);
					} else {
						localSize = 2;
						cw.insn(ALOAD_1);
						if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params[0])) cw.clazz(CHECKCAST, targetName);
						cw.field(GETFIELD, targetName, field.getName(), fieldType);
					}
					cw.visitSize(fieldType.length(), localSize);
				}

				cw.return_(fieldType);
				cw.finish();
			}

			name = selfSetterNames == null ? null : selfSetterNames[i];
			if (name != null) {
				Method method = methodByName.get(name);
				if (method == null) throw new IllegalArgumentException(itf.getName()+'.'+name+" 不存在或已使用!");
				if (method.getParameterCount() != off+1) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 只应有1个参数, got "+method.getParameterCount()+'!');
				if (!method.getParameterTypes()[off].isAssignableFrom(targetFields[i].getType())) {
					throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 但是参数["+(off+1)+"]不兼容 "+targetFields[i].getType().getName()+" ("+method.getReturnType().getName()+')');
				}
				if (method.getReturnType() != void.class) throw new IllegalArgumentException(itf.getName()+'.'+name+" 是个 setter, 但是它的返回值不是void: "+method.getReturnType().getName());

				methodByName.remove(name);

				Class<?>[] params2 = method.getParameterTypes();
				CodeWriter cw = impl.newMethod(ACC_PUBLIC, method.getName(), TypeHelper.class2asm(params2, void.class));

				if (DMHInit()) {
					int DMH_ID = DMHNewVarHandle(targetName, field.getName(), fieldType, isStatic ? "findStaticVarHandle" : "findVarHandle");
					cw.field(GETSTATIC, impl, DMH_ID);
					if (!isStatic) {
						cw.insn(ALOAD_1);
						if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0]))
							cw.clazz(CHECKCAST, targetName);
					}
					cw.varLoad(fieldType, isStatic ? 1 : 2);
					if ((flags&UNCHECKED_CAST) == 0 && !fieldType.isPrimitive() && !field.getType().isAssignableFrom(params2[off]))
						cw.clazz(CHECKCAST, fieldType.getActualClass());
					cw.invokeV("java/lang/invoke/VarHandle", "set", isStatic ? "("+fieldType.toDesc()+")V" : "(L"+targetName+";"+fieldType.toDesc()+")V");
					int size = fieldType.length() + 1 + (isStatic ? 0 : 1);
					cw.visitSize(size, size);
				} else {
					// MAI only ignore visibility, not final
					if ((field.getModifiers()&ACC_FINAL) != 0) {
						cw.visitSize(4, isStatic ? 2 : 3);
						// stack = 4
						// local = 2
						cw.field(GETSTATIC, "roj/reflect/Unaligned", "U", "Lroj/reflect/Unaligned;");
						if (isStatic) cw.ldc(new CstClass(targetName));
						else {
							if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0]))
								cw.clazz(CHECKCAST, targetName);
							cw.insn(ALOAD_1);
						}
						cw.ldc(isStatic ? U.staticFieldOffset(field) : U.objectFieldOffset(field));
						cw.varLoad(fieldType, isStatic ? 1 : 2);
						if ((flags&UNCHECKED_CAST) == 0 && !fieldType.isPrimitive() && !field.getType().isAssignableFrom(params2[off]))
							cw.clazz(CHECKCAST, fieldType.getActualClass());
						cw.invoke(INVOKESPECIAL, "roj/reflect/Unaligned", "put"+Reflection.capitalizedType(fieldType), "(Ljava/lang/Object;J"+(fieldType.isPrimitive()?fieldType.toDesc():"Ljava/lang/Object;")+")V");
					} else {
						int localSize, stackSize = fieldType.length()+1;

						if (!isStatic) {
							localSize = stackSize+1;
							cw.insn(ALOAD_1);
							if ((flags&UNCHECKED_CAST) == 0 && !target.isAssignableFrom(params2[0]))
								cw.clazz(CHECKCAST, targetName);
						} else {
							localSize = stackSize--;
						}
						cw.visitSize(stackSize, localSize);
						cw.varLoad(fieldType, isStatic ? 1 : 2);
						if ((flags&UNCHECKED_CAST) == 0 && !fieldType.isPrimitive() && !field.getType().isAssignableFrom(params2[off]))
							cw.clazz(CHECKCAST, fieldType.getActualClass());
						cw.field(isStatic ? PUTSTATIC : PUTFIELD, targetName, field.getName(), fieldType);
					}
				}

				cw.insn(RETURN);
				cw.finish();
			}
		}
		return this;
	}
	private Field[] getFieldsByName(Class<?> type, String[] names) {
		Field[] result = new Field[names.length];
		ToIntMap<String> filter = ToIntMap.fromArray(names);
		var sm = ILSecurityManager.getSecurityManager();
		var origClass = type;

		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				int i = filter.removeInt(field.getName());
				if (i >= 0) {
					if (sm != null && !sm.checkAccess(field, caller)) break;

					result[i] = field;
					if (filter.isEmpty()) return result;
				}
			}

			type = type.getSuperclass();
		}

		throw new IllegalStateException("未找到某些字段:"+origClass+"."+filter.keySet());
	}

	public final Bypass<T> i_construct(String target, String desc, String self) { return i_construct(target, desc, checkExistence(self)); }
	public Bypass<T> i_construct(String target, String desc, Method self) {
		target = target.replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkAccess(target, "<init>", desc, caller);

		CodeWriter cw = impl.newMethod(ACC_PUBLIC, self.getName(), TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType()));

		cw.clazz(NEW, target);
		cw.insn(DUP);

		List<Type> argTypes = Type.methodDesc(desc);
		argTypes.remove(argTypes.size()-1);

		int varId = 1;
		for (int i = 0; i < argTypes.size(); i++) {
			Type type = argTypes.get(i);
			cw.varLoad(type, varId);
			varId += type.length();
		}

		cw.visitSize(varId+1, varId);

		cw.invoke(INVOKESPECIAL, target, "<init>", desc);
		cw.insn(ARETURN);
		cw.finish();

		return this;
	}

	public static final byte INVOKE_STATIC = 1, INVOKE_SPECIAL = 2, INVOKE_INTERFACE = 4;
	public final Bypass<T> i_delegate(String target, String name, String desc, String self, byte flags) { return i_delegate(target, name, desc, checkExistence(self), flags); }
	public Bypass<T> i_delegate(String target, String name, String desc, Method self, @MagicConstant(flags = {INVOKE_STATIC, INVOKE_SPECIAL, INVOKE_INTERFACE}) byte flags) {
		target = target.replace('.', '/');

		ILSecurityManager sm = ILSecurityManager.getSecurityManager();
		if (sm != null) sm.checkAccess(target, name, desc, caller);

		String sDesc = TypeHelper.class2asm(self.getParameterTypes(), self.getReturnType());

		CodeWriter cw = impl.newMethod(ACC_PUBLIC, self.getName(), sDesc);

		List<Type> argTypes = Type.methodDesc(desc);
		Type retype = argTypes.remove(argTypes.size()-1);

		boolean isStatic = (flags&INVOKE_STATIC) != 0;
		int varId;
		if (isStatic) varId = 1;
		else {
			cw.insn(ALOAD_1);
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
		if (sm != null) sm.checkAccess(target, name, type.toDesc(), caller);

		if (getter != null) {
			Class<?>[] params2 = isStatic ? ArrayCache.CLASSES : getter.getParameterTypes();
			CodeWriter cw = impl.newMethod(ACC_PUBLIC, getter.getName(), TypeHelper.class2asm(params2, getter.getReturnType()));

			int stackSize = type.length();

			int localSize;
			if (!isStatic) {
				localSize = 2;
				cw.insn(ALOAD_1);
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
			CodeWriter cw = impl.newMethod(ACC_PUBLIC, setter.getName(), TypeHelper.class2asm(params2, void.class));

			int stackSize = type.length()+1;
			int localSize;

			if (!isStatic) {
				localSize = (char) (stackSize+1);
				cw.insn(ALOAD_1);
			} else {
				localSize = stackSize--;
			}
			cw.visitSize(stackSize, localSize);
			cw.varLoad(type, isStatic ? 1 : 2);
			cw.field(isStatic ? PUTSTATIC : PUTFIELD, target, name, type);
			cw.insn(RETURN);
			cw.finish();
		}

		return this;
	}

	public static <V> Bypass<V> builder(Class<V> impl) {return new Bypass<>(impl, true);}
	public static <V> Bypass<V> custom(Class<V> impl) {return new Bypass<>(impl, false);}

	/**
	 * 禁用运行时类型检查（提升性能但可能造成NoSuchMethodError）
	 * @return this
	 */
	public final Bypass<T> unchecked() { if (!DMHInit()) flags |= UNCHECKED_CAST; return this; }
	/**
	 * 启用弱类加载器引用模式（允许生成的类被单独卸载）
	 * @return this
	 */
	public final Bypass<T> weak() { flags |= WEAK_REF; return this; }
	/**
	 * 强制JVM内联代理函数（提升调用性能）
	 * @return this
	 */
	public final Bypass<T> inline() { flags |= INLINE; return this; }

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

	private static void makeHeader(String selfName, String invokerName, ClassNode clz) {
		clz.version = 52;
		clz.name(selfName.replace('.', '/'));

		clz.parent(Reflection.MAGIC_ACCESSOR_CLASS);
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