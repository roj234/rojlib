package roj.config.auto;

import org.intellij.lang.annotations.MagicConstant;
import roj.ReferenceByGeneratedClass;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.type.IType;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.MyHashMap;
import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;
import roj.reflect.VirtualReference;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static roj.asm.Opcodes.*;

/**
 * 这样抽象是为了能先加载Adapter
 * @author Roj234
 * @since 2024/3/24 0024 18:53
 */
public abstract class SerializerFactory {
	static final VirtualReference<GenSerRepo> Isolation = new VirtualReference<>();
	static final Function<ClassLoader, GenSerRepo> Fn = GenSerRepo::new;
	static final class GenSerRepo {
		GenSerRepo(ClassLoader loader) {}
		final MyHashMap<String, IntFunction<?>> dataContainer = new MyHashMap<>();
		final MyHashMap<String, Adapter> generated = new MyHashMap<>();
	}

	static final boolean UNSAFE_ADAPTER;
	static {
		boolean unsafe = false;
		try {
			ConstantData c = Parser.parseConstants(IOUtil.getResource("roj/config/auto/Adapter.class"));
			c.parent(Bypass.MAGIC_ACCESSOR_CLASS);
			ClassDefiner.defineGlobalClass(c);
			unsafe = true;
		} catch (Throwable ignored) {}

		UNSAFE_ADAPTER = unsafe;
	}

	public static SerializerFactory getInstance() {return getInstance0(GENERATE|CHECK_INTERFACE|CHECK_PARENT);}
	public static SerializerFactory getInstance(@MagicConstant(flags = {GENERATE, CHECK_INTERFACE, CHECK_PARENT, NO_CONSTRUCTOR, ALLOW_DYNAMIC, PREFER_DYNAMIC, OBJECT_POOL, SAFE, SERIALIZE_PARENT }) int flag) {return getInstance0(flag);}
	private static SerializerFactory getInstance0(int flag) {return new SerializerFactoryImpl(flag, ReflectionUtils.getCallerClass(3).getClassLoader());}

	static final int PREFER_DYNAMIC_INTERNAL = 32;
	public static final int
		GENERATE = 1,
		CHECK_INTERFACE = 2,
		CHECK_PARENT = 4,
		NO_CONSTRUCTOR = 8,
		ALLOW_DYNAMIC = 16,
		PREFER_DYNAMIC = ALLOW_DYNAMIC|PREFER_DYNAMIC_INTERNAL,
		OBJECT_POOL = 64,
		SAFE = 128,
		SERIALIZE_PARENT = 256,
		OPTIONAL_BY_DEFAULT = 512;

	int flag;
	public ToIntFunction<Class<?>> perClassFlag;

	protected SerializerFactory(int flag) {
		this.flag = flag&(ALLOW_DYNAMIC|PREFER_DYNAMIC_INTERNAL|OBJECT_POOL);
		this.perClassFlag = x -> flag;
	}

	/**
	 * 添加一个允许（反）序列化的类
	 */
	public abstract SerializerFactory add(Class<?> type);
	/**
	 * 添加一些允许（反）序列化的类
	 */
	public abstract SerializerFactory add(Class<?>... type);

	/**
	 * 注册type类型的序列化适配器，在序列化时会转换成writeMethod的返回值 <BR>
	 * 和SerializerFactory绑定
	 * @param type 类型
	 * @param adapter 转换器实例，不支持静态的Class
	 */
	public abstract SerializerFactory add(Class<?> type, Object adapter, String writeMethod, String readMethod);
	/**
	 * 同上，但会根据type自动选择方法，在找不到或找到多个时会报错
	 * @see SerializerFactory#add(Class, Object, String, String)
	 */
	public abstract SerializerFactory add(Class<?> type, Object adapter);

	/**
	 * 注册@{@link As}的序列化器 <BR>
	 * 和SerializerFactory绑定 <pre>
	 * 转换方法两种方式参考:
	 *   {@link Serializers#writeHex(byte[])}
	 *   {@link Serializers#writeISO(long, CVisitor)}
	 * @param name 注解的value
	 * @param type 转换到的目标的类
	 * @param adapter 转换器实例，不支持静态的Class
	 * @param writeMethod 转换方法名称(原类型 -> type)
	 * @param readMethod 反转换方法名称(type -> 原类型)
	 */
	public abstract SerializerFactory as(String name, Class<?> type, Object adapter, String writeMethod, String readMethod);
	/**
	 * 同上，除了自动寻找下面doc中第一种例子的方法
	 * @see SerializerFactory#as(String, Class, Object, String, String)
	 */
	public abstract SerializerFactory as(String name, Class<?> type, Object adapter);

	public abstract <T> Serializer<T> serializer(Class<T> type);
	public abstract Serializer<?> serializer(IType generic);
	// for Lavac
	//public abstract <T> Serializer<T> serializer(IType<T> generic);
	public abstract <T> Serializer<List<T>> listOf(Class<T> content);
	public abstract <T> Serializer<Map<String, T>> mapOf(Class<T> content);

	public static <T> IntFunction<T> dataContainer(Class<?> type) {
		var dc = Isolation.computeIfAbsent(type.getClassLoader(), Fn).dataContainer;

		var entry = dc.getEntry(type.getName());
		if (entry != null) return Helpers.cast(entry.getValue());

		synchronized (dc) {
			entry = dc.getEntry(type.getName());
			if (entry != null) return Helpers.cast(entry.getValue());

			boolean hasNP = false, hasSized = false;
			try {
				Constructor<?> c = type.getDeclaredConstructor(int.class);
				if ((c.getModifiers()&ACC_PUBLIC) != 0) hasSized = true;
			} catch (NoSuchMethodException ignored) {}
			try {
				Constructor<?> c = type.getDeclaredConstructor(ArrayCache.CLASSES);
				if ((c.getModifiers()&ACC_PUBLIC) != 0) hasNP = true;
			} catch (NoSuchMethodException ignored) {}

			if (!(hasNP|hasSized)) {
				dc.put(type.getName(), null);
				return null;
			}

			ConstantData c = new ConstantData();
			c.name("roj/gen/DC$"+ ReflectionUtils.uniqueId());
			c.addInterface("java/util/function/IntFunction");
			ClassDefiner.premake(c);

			CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "apply", "(I)Ljava/lang/Object;");

			String asmName = type.getName().replace('.', '/');
			if (hasNP) {
				if (hasSized) {
					cw.visitSize(3, 2);
					Label label = new Label();
					cw.one(ILOAD_1);
					cw.jump(IFLT, label);
					cw.clazz(NEW, asmName);
					cw.one(DUP);
					cw.one(ILOAD_1);
					cw.invokeD(asmName, "<init>", "(I)V");
					cw.one(ARETURN);
					cw.label(label);
				}
				cw.visitSizeMax(2, 2);
				cw.newObject(asmName);
			} else {
				cw.visitSize(3, 2);
				Label label = new Label();

				cw.one(ILOAD_1);
				cw.jump(IFGE, label);

				cw.ldc(16);
				cw.one(ISTORE_1);
				cw.label(label);

				cw.clazz(NEW, asmName);
				cw.one(DUP);
				cw.one(ILOAD_1);
				cw.invokeD(asmName, "<init>", "(I)V");
			}

			cw.one(ARETURN);

			IntFunction<T> fn = Helpers.cast(ClassDefiner.make(c, type.getClassLoader()));
			dc.put(type.getName(), fn);
			return fn;
		}
	}

	@ReferenceByGeneratedClass
	public static String valueOf(Object o) {return o == null ? null : o.toString();}
}