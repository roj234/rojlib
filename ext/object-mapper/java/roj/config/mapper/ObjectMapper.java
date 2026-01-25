package roj.config.mapper;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.ci.annotation.IndirectReference;
import roj.ci.annotation.StaticHook;
import roj.collect.HashMap;
import roj.config.*;
import roj.config.node.ConfigValue;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.text.CharList;
import roj.text.ParseException;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/3/24 18:53
 */
public abstract class ObjectMapper {
	@StaticHook("roj/debug/DebugTool.inspect(Ljava/lang/Object;)Ljava/lang/String;")
	public static String __inspect(Object o) {
		try {
			CharList sb = new CharList();
			TextEmitter textEmitter = new JsonSerializer("    ").to(sb);
			SAFE.writer(o.getClass()).write(textEmitter, Helpers.cast(o));
			return sb.toStringAndFree();
		} catch (Throwable e) {
			e.printStackTrace();
			return "failed to inspect "+o.getClass();
		}
	}

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
			Reflection.defineClass(ObjectMapper.class.getClassLoader(), c);
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

	protected ObjectMapper(int flag) {
		this.flag = flag&(PARSE_DYNAMIC|PREFER_DYNAMIC_0|OBJECT_POOL);
		this.perClassFlag = x -> flag;
	}

	public static ObjectMapper getInstance() {return create(GENERATE|CHECK_INTERFACE|CHECK_PARENT);}
	public static ObjectMapper getInstance(@MagicConstant(flags = {GENERATE, CHECK_INTERFACE, CHECK_PARENT, PARSE_DYNAMIC, PREFER_DYNAMIC, OBJECT_POOL, SERIALIZE_PUBLIC_ONLY, SERIALIZE_PARENT, NO_SCHEMA}) int flag) {return create(flag);}
	public static ObjectMapper getInstance(int flag, ClassLoader classLoader) {return new ObjectMapperImpl(flag, classLoader);}

	private static ObjectMapper create(int flag) {return new ObjectMapperImpl(flag, Reflection.getCallerClass(3, ObjectMapper.class).getClassLoader());}

	// SAFE默认的flag仅支持在类中实际写出的具体类，不涉及任意对象的反序列化
	// 实际上，如果在序列化的类中有字段是Object或接口或抽象类（除去CharSequence Map Collection等一些基本的类），它会报错
	// 如果是这种情况，请自己getInstance
	public static final ObjectMapper SAFE = getInstance();

	private static final ObjectMapper POOLED = getInstance(GENERATE|PARSE_DYNAMIC|OBJECT_POOL|CHECK_INTERFACE|SERIALIZE_PARENT);
	public static ObjectMapper pooled() {return POOLED;}

	/**
	 * 添加一个允许（反）序列化的类
	 * 仅对不开启{@link #GENERATE}的实例才有意义
	 */
	public abstract ObjectMapper registerType(Class<?> type);
	/**
	 * 添加一些允许（反）序列化的类
	 * 仅对不开启{@link #GENERATE}的实例才有意义
	 */
	public final ObjectMapper registerType(Class<?>... types) {
		for (Class<?> type : types) registerType(type);
		return this;
	}
	/**
	 * 添加一个允许（反）序列化的类，带上它的配置
	 * @implNote 警告：这个序列化器不会保存在全局缓存中
	 */
	public abstract ObjectMapper registerType(Class<?> type, SerializeSetting setting);
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
	public abstract ObjectMapper registerAdapter(Class<?> type, Object adapter, String serializeMethod, String deserializeMethod);
	/**
	 * 同上，但会根据type自动选择方法，在找不到或找到多个时会报错
	 * @see #registerAdapter(Class, Object, String, String)
	 */
	public abstract ObjectMapper registerAdapter(Class<?> type, Object adapter);

	//region 一些常用的序列化转换器
	public final ObjectMapper serializeCharArrayToString() {return registerAdapter(char[].class, ObjectMapper.class,"FromChars","ToChars");}
	public static String FromChars(char[] t) {return t == null ? null : new String(t);}
	public static char[] ToChars(String t) {return t == null ? null : t.toCharArray();}
	public final ObjectMapper serializeCharListToString() {return registerAdapter(CharList.class, ObjectMapper.class,"FromCharList","ToCharList");}
	public static String FromCharList(CharList t) {return t == null ? null : t.toString();}
	public static CharList ToCharList(String t) {return t == null ? null : new CharList(t);}
	public final ObjectMapper serializeFileToString() {return registerAdapter(File.class, ObjectMapper.class,"FromFile","ToFile");}
	public static String FromFile(File t) {return t == null ? null : t.getAbsolutePath();}
	public static File ToFile(String t) {return t == null ? null : new File(t);}
	public final ObjectMapper serializeCharsetToString() {return registerAdapter(Charset.class, ObjectMapper.class,"FromCharset","ToCharset");}
	public static String FromCharset(Charset t) {return t == null ? null : t.name();}
	public static Charset ToCharset(String t) {return t == null ? null : Charset.forName(t);}
	public final ObjectMapper serializePatternToString() {return registerAdapter(Pattern.class, ObjectMapper.class,"FromPattern","ToPattern");}
	public static String FromPattern(Pattern t) {return t == null ? null : t.pattern();}
	public static Pattern ToPattern(String t) {return t == null ? null : Pattern.compile(t);}
	//endregion

	/**
	 * 注册{@link As @As}的序列化器 <BR>
	 * 与实例绑定 <pre>
	 * 转换方法两种方式参考:
	 *   {@link #ToHex(byte[])}
	 *   {@link #ToTime(long, ValueEmitter)}
	 * <BR>
	 * 注册的类和方法必须都是public的，或至少能在type的上下文访问
	 * @param name 注解的value
	 * @param type 转换到的目标的类
	 * @param adapter 转换器实例或Class
	 * @param serializeMethod 转换方法名称(原类型 -> type)
	 * @param deserializeMethod 反转换方法名称(type -> 原类型)
	 */
	public abstract ObjectMapper registerAdapter(String name, Class<?> type, Object adapter, String serializeMethod, String deserializeMethod);
	/**
	 * 同下，但自动寻找符合第一种示例的方法
	 * @see #registerAdapter(String, Class, Object, String, String)
	 */
	public abstract ObjectMapper registerAdapter(String name, Class<?> type, Object adapter);

	//region 一些常用的As及其实现
	public ObjectMapper enableAsJson() {return registerAdapter("json", String.class, ObjectMapper.class,"ToJson","FromJson");}
	public static String ToJson(ConfigValue obj) {
		var textEmitter = new JsonSerializer().to(new CharList());
		obj.accept(textEmitter);
		return textEmitter.getValue().toStringAndFree();
	}
	public static ConfigValue FromJson(String str, String actualType) {
		try {
			return new JsonParser().parse(str);
		} catch (ParseException e) {
			throw new IllegalArgumentException("类型"+actualType+"解析失败", e);
		}
	}

	public final ObjectMapper enableAsHex() {return registerAdapter("hex",byte[].class, ObjectMapper.class,"ToHex","FromHex");}
	public static String ToHex(byte[] b) {return IOUtil.encodeHex(b);}
	public static byte[] FromHex(String str) {return IOUtil.decodeHex(str);}

	public final ObjectMapper enableAsBase64() {return registerAdapter("base64",byte[].class, ObjectMapper.class,"ToBase64","FromBase64");}
	public static String ToBase64(byte[] b) {return IOUtil.encodeBase64(b);}
	public static byte[] FromBase64(String str) {return IOUtil.decodeBase64(str).toByteArray();}

	public final ObjectMapper enableAsRGB() {return registerAdapter("rgb",int.class, ObjectMapper.class,"ToRGB","FromRGB");}
	public final ObjectMapper enableAsRGBA() {return registerAdapter("rgba",int.class, ObjectMapper.class,"ToRGBA","FromRGB");}
	public static String ToRGB(int color) {return "#".concat(Integer.toHexString(color&0xFFFFFF));}
	public static String ToRGBA(int color) {return "#".concat(Integer.toHexString(color));}
	public static int FromRGB(String str) {return Integer.parseInt(str.substring(1), 16);}

	public final ObjectMapper enableAsTimestamp() {return registerAdapter("time",long.class, ObjectMapper.class,"ToTime","FromTime");}
	public static void ToTime(long t, ValueEmitter out) {out.emitTimestamp(t);}
	public static long FromTime(long t) {return t;}
	//endregion

	public abstract ObjectReader<?> reader(Class<?> type, IType generic, boolean pooled);
	public abstract ObjectWriter<?> writer(Class<?> type, IType generic, boolean pooled);

	@SuppressWarnings("unchecked")
	public final <T> ObjectReader<T> reader(Class<T> type) {return (ObjectReader<T>) reader(type, null, false);}
	public final ObjectReader<?> reader(IType type) {return reader(null, type, false);}

	@SuppressWarnings("unchecked")
	public final <T> ObjectWriter<T> writer(Class<T> type) {return (ObjectWriter<T>) writer(type, null, false);}
	public final ObjectWriter<?> writer(IType type) {return writer(null, type, false);}
	@SuppressWarnings("unchecked")
	public final <T> ObjectReader<List<T>> listOf(Class<T> content) { return (ObjectReader<List<T>>) reader(Signature.parseGeneric("Ljava/util/List<"+TypeHelper.class2asm(content)+">;")); }
	@SuppressWarnings("unchecked")
	public final <T> ObjectReader<Map<String, T>> mapOf(Class<T> content) { return (ObjectReader<Map<String, T>>) reader(Signature.parseGeneric("Ljava/util/Map<Ljava/lang/String;"+TypeHelper.class2asm(content)+">;")); }

	@SuppressWarnings("unchecked")
	public final <T> ObjectWriter<Collection<T>> listWriter(Class<T> content) { return (ObjectWriter<Collection<T>>) writer(Signature.parseGeneric("Ljava/util/Collection<"+TypeHelper.class2asm(content)+">;")); }
	@SuppressWarnings("unchecked")
	public final <T> ObjectWriter<Map<String, T>> mapWriter(Class<T> content) { return (ObjectWriter<Map<String, T>>) writer(Signature.parseGeneric("Ljava/util/Map<Ljava/lang/String;"+TypeHelper.class2asm(content)+">;")); }

	public final <T> T read(ConfigValue in, Class<T> type) {return reader(type).read(in);}
	public final <T> T read(File in, Class<T> type, ConfigMaster configType) throws IOException, ParseException {return reader(type).read(in, configType, StandardCharsets.UTF_8);}
	public final <T> T read(DynByteBuf in, Class<T> type, ConfigMaster configType) throws IOException, ParseException {return reader(type).read(in, configType, StandardCharsets.UTF_8);}
	public final <T> T read(InputStream in, Class<T> type, ConfigMaster configType) throws IOException, ParseException {return reader(type).read(in, configType, StandardCharsets.UTF_8);}
	public final <T> T read(CharSequence in, Class<T> type, ConfigMaster configType) throws ParseException {return reader(type).read(in, configType);}

	public final void write(ValueEmitter emitter, Object object) {
		if (object == null) emitter.emitNull();
		else writer(object.getClass()).write(emitter, Helpers.cast(object));
	}
	public final <T> void write(ConfigMaster configType, Object o, File file) throws IOException {writer(o.getClass()).write(configType, Helpers.cast(o), file);}
	public final void write(ConfigMaster configType, Object o, OutputStream out) throws IOException {writer(o.getClass()).write(configType, Helpers.cast(o), out);}
	public final CharList write(ConfigMaster configType, Object o, CharList buf) {return writer(o.getClass()).write(configType, Helpers.cast(o), buf);}
	public final DynByteBuf write(ConfigMaster configType, Object o, DynByteBuf buf) {return writer(o.getClass()).write(configType, Helpers.cast(o), buf);}
	public final boolean write(ConfigMaster configType, Object o, File file, String indent) throws IOException {return writer(o.getClass()).write(configType, Helpers.cast(o), file, indent);}

	@SuppressWarnings("unchecked")
	public static <T> IntFunction<T> containerFactory(Class<?> type) {
		var factories = CACHE.computeIfAbsent(type.getClassLoader(), CACHE_NEW).containerFactories;

		var entry = factories.getEntry(type.getName());
		if (entry != null) return (IntFunction<T>) entry.getValue();

		synchronized (factories) {
			entry = factories.getEntry(type.getName());
			if (entry != null) return (IntFunction<T>) entry.getValue();

			IntFunction<?> fn = makeContainerFactory(type);
			factories.put(type.getName(), fn);
			return (IntFunction<T>) fn;
		}
	}
	private static <T> IntFunction<T> makeContainerFactory(Class<T> type) {
		boolean hasNP = false, hasSized = false;
		var node = ClassNode.fromType(type);
		if (node == null) {
			var publicLookup = MethodHandles.publicLookup();
			try {
				publicLookup.findConstructor(type, MethodType.methodType(void.class, int.class));
				hasSized = true;
			} catch (Exception ignored) {}
			try {
				publicLookup.findConstructor(type, MethodType.methodType(void.class));
				hasNP = true;
			} catch (Exception ignored) {}
		} else {
			MethodNode mn = node.getMethodObj("<init>", "(I)V");
			if (mn != null && (mn.modifier()&ACC_PUBLIC) != 0) hasSized = true;

			mn = node.getMethodObj("<init>", "()V");
			if (mn != null && (mn.modifier()&ACC_PUBLIC) != 0) hasNP = true;
		}

		if (!(hasNP|hasSized)) return null;

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
		return Helpers.cast(Reflection.createInstance(type, c));
	}

	@IndirectReference
	public static String valueOf(Object o) {return o == null ? null : o.toString();}
}