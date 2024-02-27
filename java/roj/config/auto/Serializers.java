package roj.config.auto;

import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.util.ByteList;

import static roj.config.auto.SerializerFactory.*;

/**
 * 一些常用的自定义序列化
 * @author Roj234
 * @since 2023/3/24 0024 12:45
 */
public final class Serializers {
	// 默认的flag仅支持在类中实际写出的具体类，不涉及任意对象的反序列化
	// 实际上，如果在序列化的类中有字段是Object或接口或抽象类（除去CharSequence Map Collection等一些基本的类），它会报错
	// 如果是这种情况，请使用Unsafe或Pooled或自己getInstance
	public static final SerializerFactory SAFE = getInstance();
	public static final SerializerFactory UNSAFE = getInstance(GENERATE|ALLOW_DYNAMIC|CHECK_INTERFACE|SERIALIZE_PARENT);
	public static final SerializerFactory POOLED = getInstance(GENERATE|ALLOW_DYNAMIC|OBJECT_POOL|CHECK_INTERFACE|SERIALIZE_PARENT|NO_CONSTRUCTOR);

	private static final Serializers INSTANCE = new Serializers();
	private Serializers() {}

	public static void registerAsHex(SerializerFactory factory) { factory.as("hex",byte[].class,INSTANCE,"writeHex","readHex"); }
	public static void registerAsBase64(SerializerFactory factory) { factory.as("base64",byte[].class,INSTANCE,"writeBase64","readBase64"); }
	public static void registerAsRGB(SerializerFactory factory) { factory.as("rgb",int.class,INSTANCE,"writeRGB","readRGB"); }
	public static void registerAsRGBA(SerializerFactory factory) { factory.as("rgba",int.class,INSTANCE,"writeRGBA","readRGB"); }
	public static void registerAsIsoTime(SerializerFactory factory) { factory.as("isotime",long.class,INSTANCE,"writeISO","readISO"); }

	public static void serializeCharArrayToString(SerializerFactory factory) { factory.add(char[].class,INSTANCE,"writeChars","readChars"); }

	public final String writeHex(byte[] arr) {return IOUtil.SharedCoder.get().encodeHex(arr);}
	public final byte[] readHex(String s) {
		ByteList b = new ByteList();
		byte[] bb = TextUtil.hex2bytes(s, b).toByteArray();
		b._free();
		return bb;
	}

	public final String writeBase64(byte[] arr) { return IOUtil.SharedCoder.get().encodeBase64(arr); }
	public final byte[] readBase64(String s) { return IOUtil.SharedCoder.get().decodeBase64(s).toByteArray(); }

	public final String writeRGB(int color) { return "#".concat(Integer.toHexString(color&0xFFFFFF)); }
	public final String writeRGBA(int color) { return "#".concat(Integer.toHexString(color)); }
	public final int readRGB(String s) { return Integer.parseInt(s.substring(1), 16); }

	public final void writeISO(long t, CVisitor out) { out.valueTimestamp(t); }
	public final long readISO(long t) { return t; }

	public final String writeChars(char[] t) { return new String(t); }
	public final char[] readChars(String t) { return t.toCharArray(); }
}