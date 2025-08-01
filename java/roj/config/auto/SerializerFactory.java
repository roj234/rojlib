package roj.config.auto;

import org.intellij.lang.annotations.MagicConstant;
import roj.ReferenceByGeneratedClass;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.annotation.Annotation;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.collect.HashMap;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.text.CharList;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import static roj.asm.Opcodes.*;

/**
 * 这样抽象是为了能先加载Adapter
 * @author Roj234
 * @since 2024/3/24 18:53
 */
public abstract class SerializerFactory {
	static final VirtualReference<GenSerRepo> IMPLEMENTATION_CACHE = new VirtualReference<>();
	static final Function<ClassLoader, GenSerRepo> Fn = GenSerRepo::new;
	static final class GenSerRepo {
		GenSerRepo(ClassLoader loader) {}
		final HashMap<String, IntFunction<?>> dataContainer = new HashMap<>();
		final HashMap<String, Adapter> generated = new HashMap<>();
	}

	static final boolean UNSAFE_ADAPTER;
	static {
		boolean unsafe = false;
		try {
			ClassNode c = ClassNode.parseSkeleton(IOUtil.getResourceIL("roj/config/auto/Adapter.class"));
			c.parent(Reflection.MAGIC_ACCESSOR_CLASS);
			if (Reflection.JAVA_VERSION > 21)
				c.modifier |= ACC_PUBLIC;
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
		NO_SCHEMA = 1024;

	int flag;
	public ToIntFunction<Class<?>> perClassFlag;

	protected SerializerFactory(int flag) {
		this.flag = flag&(ALLOW_DYNAMIC|PREFER_DYNAMIC_INTERNAL|OBJECT_POOL);
		this.perClassFlag = x -> flag;
	}

	public static SerializerFactory getInstance() {return getInstance0(GENERATE|CHECK_INTERFACE|CHECK_PARENT);}
	public static SerializerFactory getInstance(@MagicConstant(flags = {GENERATE, CHECK_INTERFACE, CHECK_PARENT, NO_CONSTRUCTOR, ALLOW_DYNAMIC, PREFER_DYNAMIC, OBJECT_POOL, CHECK_PUBLIC, SERIALIZE_PARENT, NO_SCHEMA }) int flag) {return getInstance0(flag);}
	private static SerializerFactory getInstance0(int flag) {return new SerializerFactoryImpl(flag, Reflection.getCallerClass(3).getClassLoader());}
	public static SerializerFactory getInstance0(int flag, ClassLoader classLoader) {return new SerializerFactoryImpl(flag, classLoader);}

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
	 * 添加一个允许（反）序列化的类，带上它的配置
	 * @implNote 警告：这个序列化器不会保存在全局缓存中
	 */
	public abstract SerializerFactory add(Class<?> type, SerializeSetting setting);
	public interface SerializeSetting {
		SerializeSetting IDENTITY = (a, b, c) -> c;
		List<Annotation> processField(ClassNode owner, FieldNode field, List<Annotation> annotations);
	}

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
	public static String FromChars(char[] t) {return t == null ? null : new String(t);}
	public static char[] ToChars(String t) {return t == null ? null : t.toCharArray();}
	public final SerializerFactory serializeCharListToString() {return add(CharList.class, SerializerFactory.class,"FromCharList","ToCharList");}
	public static String FromCharList(CharList t) {return t == null ? null : t.toString();}
	public static CharList ToCharList(String t) {return t == null ? null : new CharList(t);}
	public final SerializerFactory serializeFileToString() {return add(File.class, SerializerFactory.class,"FromFile","ToFile");}
	public static String FromFile(File t) {return t == null ? null : t.getAbsolutePath();}
	public static File ToFile(String t) {return t == null ? null : new File(t);}
	public final SerializerFactory serializeCharsetToString() {return add(Charset.class, SerializerFactory.class,"FromCharset","ToCharset");}
	public static String FromCharset(Charset t) {return t == null ? null : t.name();}
	public static Charset ToCharset(String t) {return t == null ? null : Charset.forName(t);}
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
	public static String ToHex(byte[] b) {return IOUtil.encodeHex(b);}
	public static byte[] FromHex(String str) {return IOUtil.decodeHex(str);}

	public final SerializerFactory asBase64() {return as("base64",byte[].class,SerializerFactory.class,"ToBase64","FromBase64");}
	public static String ToBase64(byte[] b) {return IOUtil.encodeBase64(b);}
	public static byte[] FromBase64(String str) {return IOUtil.decodeBase64(str).toByteArray();}

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
	@SuppressWarnings("unchecked")
	public final <T> Serializer<List<T>> listOf(Class<T> content) { return (Serializer<List<T>>) serializer(Signature.parseGeneric("Ljava/util/List<" + TypeHelper.class2asm(content) + ">;")); }
	@SuppressWarnings("unchecked")
	public final <T> Serializer<Set<T>> setOf(Class<T> content) { return (Serializer<Set<T>>) serializer(Signature.parseGeneric("Ljava/util/Set<" + TypeHelper.class2asm(content) + ">;")); }
	@SuppressWarnings("unchecked")
	public final <T> Serializer<Collection<T>> collectionOf(Class<T> content) { return (Serializer<Collection<T>>) serializer(Signature.parseGeneric("Ljava/util/Collection<" + TypeHelper.class2asm(content) + ">;")); }
	@SuppressWarnings("unchecked")
	public final <T> Serializer<Map<String, T>> mapOf(Class<T> content) { return (Serializer<Map<String, T>>) serializer(Signature.parseGeneric("Ljava/util/Map<Ljava/lang/String;" + TypeHelper.class2asm(content) + ">;")); }

	public static <T> IntFunction<T> dataContainer(Class<?> type) {
		var dc = IMPLEMENTATION_CACHE.computeIfAbsent(type.getClassLoader(), Fn).dataContainer;

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

			var c = new ClassNode();
			c.name(type.getName().replace('.', '/')+"$DC");
			c.addInterface("java/util/function/IntFunction");

			CodeWriter cw = c.newMethod(ACC_PUBLIC|ACC_FINAL, "apply", "(I)Ljava/lang/Object;");

			String asmName = type.getName().replace('.', '/');
			if (hasNP) {
				if (hasSized) {
					cw.visitSize(3, 2);
					Label label = new Label();
					cw.insn(ILOAD_1);
					cw.jump(IFLT, label);
					cw.clazz(NEW, asmName);
					cw.insn(DUP);
					cw.insn(ILOAD_1);
					cw.invokeD(asmName, "<init>", "(I)V");
					cw.insn(ARETURN);
					cw.label(label);
				}
				cw.visitSizeMax(2, 2);
				cw.newObject(asmName);
			} else {
				cw.visitSize(3, 2);
				Label label = new Label();

				cw.insn(ILOAD_1);
				cw.jump(IFGE, label);

				cw.ldc(16);
				cw.insn(ISTORE_1);
				cw.label(label);

				cw.clazz(NEW, asmName);
				cw.insn(DUP);
				cw.insn(ILOAD_1);
				cw.invokeD(asmName, "<init>", "(I)V");
			}

			cw.insn(ARETURN);

			IntFunction<T> fn = Helpers.cast(ClassDefiner.newInstance(c, type.getClassLoader()));
			dc.put(type.getName(), fn);
			return fn;
		}
	}

	@ReferenceByGeneratedClass
	public static String valueOf(Object o) {return o == null ? null : o.toString();}
}