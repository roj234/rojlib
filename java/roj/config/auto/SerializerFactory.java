package roj.config.auto;

import org.intellij.lang.annotations.MagicConstant;
import roj.ReferenceByGeneratedClass;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.MyHashMap;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;
import roj.reflect.VirtualReference;
import roj.text.CharList;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.File;
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

	static final int PREFER_DYNAMIC_INTERNAL = 32;
	public static final int
		GENERATE = 1,
		CHECK_INTERFACE = 2,
		CHECK_PARENT = 4,
		NO_CONSTRUCTOR = 8,
		ALLOW_DYNAMIC = 16,
		PREFER_DYNAMIC = ALLOW_DYNAMIC|PREFER_DYNAMIC_INTERNAL,
		OBJECT_POOL = 64,
		CHECK_PUBLIC = 128,
		SERIALIZE_PARENT = 256,
		OPTIONAL_BY_DEFAULT = 512;

	int flag;
	public ToIntFunction<Class<?>> perClassFlag;

	protected SerializerFactory(int flag) {
		this.flag = flag&(ALLOW_DYNAMIC|PREFER_DYNAMIC_INTERNAL|OBJECT_POOL);
		this.perClassFlag = x -> flag;
	}

	public static SerializerFactory getInstance() {return getInstance0(GENERATE|CHECK_INTERFACE|CHECK_PARENT);}
	public static SerializerFactory getInstance(@MagicConstant(flags = {GENERATE, CHECK_INTERFACE, CHECK_PARENT, NO_CONSTRUCTOR, ALLOW_DYNAMIC, PREFER_DYNAMIC, OBJECT_POOL, CHECK_PUBLIC, SERIALIZE_PARENT }) int flag) {return getInstance0(flag);}
	private static SerializerFactory getInstance0(int flag) {return new SerializerFactoryImpl(flag, ReflectionUtils.getCallerClass(3).getClassLoader());}

	// 默认的flag仅支持在类中实际写出的具体类，不涉及任意对象的反序列化
	// 实际上，如果在序列化的类中有字段是Object或接口或抽象类（除去CharSequence Map Collection等一些基本的类），它会报错
	// 如果是这种情况，请使用Unsafe或Pooled或自己getInstance
	public static final SerializerFactory
		SAFE = getInstance(),
		POOLED = getInstance(GENERATE|ALLOW_DYNAMIC|OBJECT_POOL|CHECK_INTERFACE|SERIALIZE_PARENT|NO_CONSTRUCTOR),
		UNSAFE = getInstance(GENERATE|ALLOW_DYNAMIC|CHECK_INTERFACE|SERIALIZE_PARENT);

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

	//region 一些常用的序列化转换器
	public final SerializerFactory serializeCharArrayToString() {return add(char[].class, SerializerFactory.class,"FromChars","ToChars");}
	public static String FromChars(char[] t) {return new String(t);}
	public static char[] ToChars(String t) {return t.toCharArray();}
	public final SerializerFactory serializeCharListToString() {return add(CharList.class, SerializerFactory.class,"FromCharList","ToCharList");}
	public static String FromCharList(CharList t) {return t.toString();}
	public static CharList ToCharList(String t) {return new CharList(t);}
	public final SerializerFactory serializeFileToString() {return add(File.class, SerializerFactory.class,"FromFile","ToFile");}
	public static String FromFile(File t) {return t.getAbsolutePath();}
	public static File ToFile(String t) {return new File(t);}
	//endregion

	/**
	 * 注册@{@link As}的序列化器 <BR>
	 * 和SerializerFactory绑定 <pre>
	 * 转换方法两种方式参考:
	 *   {@link #ToHex(byte[])}
	 *   {@link #ToTime(long, CVisitor)}
	 * @param name 注解的value
	 * @param type 转换到的目标的类
	 * @param adapter 转换器实例或Class
	 * @param writeMethod 转换方法名称(原类型 -> type)
	 * @param readMethod 反转换方法名称(type -> 原类型)
	 */
	public abstract SerializerFactory as(String name, Class<?> type, Object adapter, String writeMethod, String readMethod);
	/**
	 * 同上，除了自动寻找下面doc中第一种例子的方法
	 * @see SerializerFactory#as(String, Class, Object, String, String)
	 */
	public abstract SerializerFactory as(String name, Class<?> type, Object adapter);

	//region 一些常用的As及其实现
	public SerializerFactory asJson() {return as("json",String.class,SerializerFactory.class,"ToJson","FromJson");}
	public static String ToJson(Object obj) {return ConfigMaster.JSON.writeObject(obj, new CharList()).toStringAndFree();}
	public static Object FromJson(String str, String type) {
		try {
			return ConfigMaster.JSON.readObject(SAFE.serializer(Signature.parseGeneric(type)), str);
		} catch (ParseException e) {
			throw new IllegalArgumentException("类型"+type+"解析失败", e);
		}
	}

	public final SerializerFactory asHex() {return as("hex",byte[].class,SerializerFactory.class,"ToHex","FromHex");}
	public static String ToHex(byte[] b) {return IOUtil.SharedCoder.get().encodeHex(b);}
	public static byte[] FromHex(String str) {return IOUtil.SharedCoder.get().decodeHex(str);}

	public final SerializerFactory asBase64() {return as("base64",byte[].class,SerializerFactory.class,"ToBase64","FromBase64");}
	public static String ToBase64(byte[] b) {return IOUtil.SharedCoder.get().encodeBase64(b);}
	public static byte[] FromBase64(String str) {return IOUtil.SharedCoder.get().decodeBase64(str).toByteArray();}

	public final SerializerFactory asRGB() {return as("rgb",int.class,SerializerFactory.class,"ToRGB","FromRGB");}
	public final SerializerFactory asRGBA() {return as("rgba",int.class,SerializerFactory.class,"ToRGBA","FromRGB");}
	public static String ToRGB(int color) {return "#".concat(Integer.toHexString(color&0xFFFFFF));}
	public static String ToRGBA(int color) {return "#".concat(Integer.toHexString(color));}
	public static int FromRGB(String str) {return Integer.parseInt(str.substring(1), 16);}

	public final SerializerFactory asTimestamp() {return as("time",long.class,SerializerFactory.class,"ToTime","FromTime");}
	public static void ToTime(long t, CVisitor out) {out.valueTimestamp(t);}
	public static long FromTime(long t) {return t;}
	//endregion

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
				var c = type.getDeclaredConstructor(int.class);
				if ((c.getModifiers()&ACC_PUBLIC) != 0) hasSized = true;
			} catch (NoSuchMethodException ignored) {}
			try {
				var c = type.getDeclaredConstructor(ArrayCache.CLASSES);
				if ((c.getModifiers()&ACC_PUBLIC) != 0) hasNP = true;
			} catch (NoSuchMethodException ignored) {}

			if (!(hasNP|hasSized)) {
				dc.put(type.getName(), null);
				return null;
			}

			var c = new ConstantData();
			c.name(type.getName().replace('.', '/')+"$DC");
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