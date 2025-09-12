package roj.config.mapper;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.annotation.Annotation;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.HashMap;
import roj.config.ConfigMaster;
import roj.config.ValueEmitter;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.text.CharList;
import roj.text.ParseException;
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
public abstract class ObjectMapperFactory {
	static final VirtualReference<MapperCache> CACHE = new VirtualReference<>();
	static final Function<ClassLoader, MapperCache> CACHE_NEW = MapperCache::new;
	/**
	 * 保存ASM生成的类的同时，将它的生命周期和类加载器绑定
	 */
	static final class MapperCache {
		MapperCache(ClassLoader loader) {}
		final HashMap<String, IntFunction<?>> containerFactories = new HashMap<>();
		final HashMap<String, TypeAdapter> adapters = new HashMap<>();
	}

	static final boolean MAGIC_ADAPTER;
	static {
		boolean ok = false;
		try {
			ClassNode c = ClassNode.parseSkeleton(IOUtil.getResourceIL("roj/config/mapper/TypeAdapter.class"));
			c.parent(Reflection.MAGIC_ACCESSOR_CLASS);
			if (Reflection.JAVA_VERSION > 21)
				c.modifier |= ACC_PUBLIC;
			ClassDefiner.defineGlobalClass(c);
			ok = true;
		} catch (Throwable ignored) {}

		MAGIC_ADAPTER = ok;
	}

	static final int PREFER_DYNAMIC_0 = 32;
	/**
	 * 允许根据字段类型自动递归生成序列化器类
	 */
	public static final int
	GENERATE = 1,
	/**
	 * 检查并使用接口的预定义序列化器，例如List
	 */
	CHECK_INTERFACE = 2,
	/**
	 * 检查父类并使用其预定义序列化器
	 */
	CHECK_PARENT = 4,
	/**
	 * 仅允许序列化操作，禁止反序列化
	 * （注：ASM生成的类仍支持序列化，但1. 不对外暴露反序列化能力；2. 遇到无法反序列化的类时静默忽略，如缺少无参构造器的情况）
	 */
	SERIALIZE_ONLY = 8,
	/**
	 * 允许动态类型解析
	 * 注意，这不是泛型，这是对抽象基类、抽象接口等使用的机制，例如AbstractXX，IXX，Object等
	 */
	PARSE_DYNAMIC = 16,
	/**
	 * 优先使用动态类型解析(实际类型)，忽略字段类型(形式类型)
	 */
	PREFER_DYNAMIC = PARSE_DYNAMIC | PREFER_DYNAMIC_0,
	/**
	 * 使用对象序号引用避免循环引用问题
	 */
	OBJECT_POOL = 64,
	/**
	 * 只允许序列化公共类
	 */
	SERIALIZE_PUBLIC_ONLY = 128,
	/**
	 * 序列化父类字段
	 */
	SERIALIZE_PARENT = 256,
	/**
	 * 使用整数而不是字符串键以压缩，适合Msgpack等原生支持整数键的目标
	 */
	NO_SCHEMA = 1024;

	int flag;
	public ToIntFunction<Class<?>> perClassFlag;

	protected ObjectMapperFactory(int flag) {
		this.flag = flag&(PARSE_DYNAMIC|PREFER_DYNAMIC_0|OBJECT_POOL);
		this.perClassFlag = x -> flag;
	}

	public static ObjectMapperFactory getInstance() {return create(GENERATE|CHECK_INTERFACE|CHECK_PARENT);}
	public static ObjectMapperFactory getInstance(@MagicConstant(flags = {GENERATE, CHECK_INTERFACE, CHECK_PARENT, SERIALIZE_ONLY, PARSE_DYNAMIC, PREFER_DYNAMIC, OBJECT_POOL, SERIALIZE_PUBLIC_ONLY, SERIALIZE_PARENT, NO_SCHEMA}) int flag) {return create(flag);}
	public static ObjectMapperFactory getInstance(int flag, ClassLoader classLoader) {return new Factory(flag, classLoader);}

	private static ObjectMapperFactory create(int flag) {return new Factory(flag, Reflection.getCallerClass(3, ObjectMapperFactory.class).getClassLoader());}

	// SAFE默认的flag仅支持在类中实际写出的具体类，不涉及任意对象的反序列化
	// 实际上，如果在序列化的类中有字段是Object或接口或抽象类（除去CharSequence Map Collection等一些基本的类），它会报错
	// 如果是这种情况，请自己getInstance
	public static final ObjectMapperFactory
			SAFE = getInstance(),
			SAFE_SERIALIZE_ONLY = getInstance(GENERATE|PARSE_DYNAMIC|CHECK_INTERFACE|CHECK_PARENT|SERIALIZE_ONLY);

	private static final ObjectMapperFactory POOLED = getInstance(GENERATE|PARSE_DYNAMIC|OBJECT_POOL|CHECK_INTERFACE|SERIALIZE_PARENT);
	public static ObjectMapperFactory pooled() {return POOLED;}

	/**
	 * 添加一个允许（反）序列化的类
	 * 仅对不开启{@link #GENERATE}的实例才有意义
	 */
	public abstract ObjectMapperFactory registerType(Class<?> type);
	/**
	 * 添加一些允许（反）序列化的类
	 * 仅对不开启{@link #GENERATE}的实例才有意义
	 */
	public final ObjectMapperFactory registerType(Class<?>... types) {
		for (Class<?> type : types) registerType(type);
		return this;
	}
	/**
	 * 添加一个允许（反）序列化的类，带上它的配置
	 * @implNote 警告：这个序列化器不会保存在全局缓存中
	 */
	public abstract ObjectMapperFactory registerType(Class<?> type, SerializeSetting setting);
	public interface SerializeSetting {
		SerializeSetting IDENTITY = (a, b, c) -> c;
		List<Annotation> customizeFieldAnnotations(ClassNode owner, FieldNode field, List<Annotation> annotations);
	}

	/**
	 * 注册{@code type}的自定义序列化器 <BR>
	 * 与实例绑定
	 * @param type 类型
	 * @param adapter 转换器实例或Class
	 * @param serializeMethod 转换方法名称(原类型 -> type)
	 * @param deserializeMethod 反转换方法名称(type -> 原类型)
	 */
	public abstract ObjectMapperFactory registerAdapter(Class<?> type, Object adapter, String serializeMethod, String deserializeMethod);
	/**
	 * 同上，但会根据type自动选择方法，在找不到或找到多个时会报错
	 * @see #registerAdapter(Class, Object, String, String)
	 */
	public abstract ObjectMapperFactory registerAdapter(Class<?> type, Object adapter);

	//region 一些常用的序列化转换器
	public final ObjectMapperFactory serializeCharArrayToString() {return registerAdapter(char[].class, ObjectMapperFactory.class,"FromChars","ToChars");}
	public static String FromChars(char[] t) {return t == null ? null : new String(t);}
	public static char[] ToChars(String t) {return t == null ? null : t.toCharArray();}
	public final ObjectMapperFactory serializeCharListToString() {return registerAdapter(CharList.class, ObjectMapperFactory.class,"FromCharList","ToCharList");}
	public static String FromCharList(CharList t) {return t == null ? null : t.toString();}
	public static CharList ToCharList(String t) {return t == null ? null : new CharList(t);}
	public final ObjectMapperFactory serializeFileToString() {return registerAdapter(File.class, ObjectMapperFactory.class,"FromFile","ToFile");}
	public static String FromFile(File t) {return t == null ? null : t.getAbsolutePath();}
	public static File ToFile(String t) {return t == null ? null : new File(t);}
	public final ObjectMapperFactory serializeCharsetToString() {return registerAdapter(Charset.class, ObjectMapperFactory.class,"FromCharset","ToCharset");}
	public static String FromCharset(Charset t) {return t == null ? null : t.name();}
	public static Charset ToCharset(String t) {return t == null ? null : Charset.forName(t);}
	//endregion

	/**
	 * 注册{@link As @As}的序列化器 <BR>
	 * 与实例绑定 <pre>
	 * 转换方法两种方式参考:
	 *   {@link #ToHex(byte[])}
	 *   {@link #ToTime(long, ValueEmitter)}
	 * @param name 注解的value
	 * @param type 转换到的目标的类
	 * @param adapter 转换器实例或Class
	 * @param serializeMethod 转换方法名称(原类型 -> type)
	 * @param deserializeMethod 反转换方法名称(type -> 原类型)
	 */
	public abstract ObjectMapperFactory registerAdapter(String name, Class<?> type, Object adapter, String serializeMethod, String deserializeMethod);
	/**
	 * 同下，但自动寻找符合第一种示例的方法
	 * @see #registerAdapter(String, Class, Object, String, String)
	 */
	public abstract ObjectMapperFactory registerAdapter(String name, Class<?> type, Object adapter);

	//region 一些常用的As及其实现
	public ObjectMapperFactory enableAsJson() {return registerAdapter("json",String.class, ObjectMapperFactory.class,"ToJson","FromJson");}
	public static String ToJson(Object obj) {return ConfigMaster.JSON.writeObject(obj, new CharList()).toStringAndFree();}
	public static Object FromJson(String str, String type) {
		try {
			return ConfigMaster.JSON.readObject(SAFE.serializer(Signature.parseGeneric(type)), str);
		} catch (ParseException e) {
			throw new IllegalArgumentException("类型"+type+"解析失败", e);
		}
	}

	public final ObjectMapperFactory enableAsHex() {return registerAdapter("hex",byte[].class, ObjectMapperFactory.class,"ToHex","FromHex");}
	public static String ToHex(byte[] b) {return IOUtil.encodeHex(b);}
	public static byte[] FromHex(String str) {return IOUtil.decodeHex(str);}

	public final ObjectMapperFactory enableAsBase64() {return registerAdapter("base64",byte[].class, ObjectMapperFactory.class,"ToBase64","FromBase64");}
	public static String ToBase64(byte[] b) {return IOUtil.encodeBase64(b);}
	public static byte[] FromBase64(String str) {return IOUtil.decodeBase64(str).toByteArray();}

	public final ObjectMapperFactory enableAsRGB() {return registerAdapter("rgb",int.class, ObjectMapperFactory.class,"ToRGB","FromRGB");}
	public final ObjectMapperFactory enableAsRGBA() {return registerAdapter("rgba",int.class, ObjectMapperFactory.class,"ToRGBA","FromRGB");}
	public static String ToRGB(int color) {return "#".concat(Integer.toHexString(color&0xFFFFFF));}
	public static String ToRGBA(int color) {return "#".concat(Integer.toHexString(color));}
	public static int FromRGB(String str) {return Integer.parseInt(str.substring(1), 16);}

	public final ObjectMapperFactory enableAsTimestamp() {return registerAdapter("time",long.class, ObjectMapperFactory.class,"ToTime","FromTime");}
	public static void ToTime(long t, ValueEmitter out) {out.emitTimestamp(t);}
	public static long FromTime(long t) {return t;}
	//endregion

	public abstract <T> ObjectMapper<T> serializer(Class<T> type);
	public abstract ObjectMapper<?> serializer(IType generic);
	// for Lavac
	//public abstract <T> Serializer<T> serializer(IType<T> generic);
	@SuppressWarnings("unchecked")
	public final <T> ObjectMapper<List<T>> listOf(Class<T> content) { return (ObjectMapper<List<T>>) serializer(Signature.parseGeneric("Ljava/util/List<"+TypeHelper.class2asm(content)+">;")); }
	@SuppressWarnings("unchecked")
	public final <T> ObjectMapper<Set<T>> setOf(Class<T> content) { return (ObjectMapper<Set<T>>) serializer(Signature.parseGeneric("Ljava/util/Set<"+TypeHelper.class2asm(content)+">;")); }
	@SuppressWarnings("unchecked")
	public final <T> ObjectMapper<Collection<T>> collectionOf(Class<T> content) { return (ObjectMapper<Collection<T>>) serializer(Signature.parseGeneric("Ljava/util/Collection<"+TypeHelper.class2asm(content)+">;")); }
	@SuppressWarnings("unchecked")
	public final <T> ObjectMapper<Map<String, T>> mapOf(Class<T> content) { return (ObjectMapper<Map<String, T>>) serializer(Signature.parseGeneric("Ljava/util/Map<Ljava/lang/String;"+TypeHelper.class2asm(content)+">;")); }

	public static <T> IntFunction<T> containerFactory(Class<?> type) {
		var dc = CACHE.computeIfAbsent(type.getClassLoader(), CACHE_NEW).containerFactories;

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