package roj.config.serial;

import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;

import static roj.config.serial.SerializerFactory.*;

/**
 * @author Roj234
 * @since 2023/3/24 0024 12:45
 */
public final class SerializerUtils {
	static boolean injected;
	static {
		try {
			ConstantData c = Parser.parseConstants(IOUtil.readRes("roj/config/serial/Adapter.class"));
			c.parent(DirectAccessor.MAGIC_ACCESSOR_CLASS);
			ClassDefiner.INSTANCE.defineClassC(c);
			injected = true;
		} catch (Throwable ignored) {}
	}

	private static final SerializerUtils INSTANCE = new SerializerUtils();

	public static void registerAsHex(SerializerFactory factory) { factory.registerAsAdapter("hex",byte[].class,INSTANCE,"writeHex","readHex"); }
	public static void registerAsBase64(SerializerFactory factory) { factory.registerAsAdapter("base64",byte[].class,INSTANCE,"writeBase64","readBase64"); }
	public static void registerAsRGB(SerializerFactory factory) { factory.registerAsAdapter("rgb",int.class,INSTANCE,"writeRGB","readRGB"); }
	public static void registerAsRGBA(SerializerFactory factory) { factory.registerAsAdapter("rgba",int.class,INSTANCE,"writeRGBA","readRGB"); }
	public static void registerAsIsoTime(SerializerFactory factory) { factory.registerAsAdapter("isotime",long.class,INSTANCE,"writeISO","readISO"); }

	public static void serializeCharArrayToString(SerializerFactory factory) { factory.register(char[].class,INSTANCE,"writeChars","readChars"); }

	private SerializerUtils() {}

	public final String writeHex(byte[] arr) {
		return TextUtil.bytes2hex(arr,0,arr.length, new CharList()).toStringAndFree();
	}
	public final byte[] readHex(String s) {
		ByteList b = new ByteList();
		byte[] bb = TextUtil.hex2bytes(s, b).toByteArray();
		b._free();
		return bb;
	}

	public final String writeBase64(byte[] arr) { return Base64.btoa(arr); }
	public final byte[] readBase64(String s) { return Base64.atob(s); }

	public final String writeRGB(int color) { return "#".concat(Integer.toHexString(color&0xFFFFFF)); }
	public final String writeRGBA(int color) { return "#".concat(Integer.toHexString(color)); }
	public final int readRGB(String s) { return Integer.parseInt(s.substring(1), 16); }

	public final void writeISO(long t, CVisitor out) { out.valueTimestamp(t); }
	public final long readISO(long t) { return t; }

	public final String writeChars(char[] t) { return new String(t); }
	public final char[] readChars(String t) { return t.toCharArray(); }

	public static SerializerFactory newSerializerFactory() { return newSerializerFactory(GENERATE|CHECK_INTERFACE|CHECK_PARENT|ALLOW_DYNAMIC); }
	public static SerializerFactory newSerializerFactory(int flag) { return new SerializerFactory(flag); }
}
