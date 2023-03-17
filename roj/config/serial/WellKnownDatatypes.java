package roj.config.serial;

import roj.crypt.Base64;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2023/6/2 0002 13:34
 */
public class WellKnownDatatypes {
	public static final WellKnownDatatypes INSTANCE = new WellKnownDatatypes();

	public String writeHex(byte[] arr) {
		return TextUtil.bytes2hex(arr,0,arr.length, new CharList()).toStringAndFree();
	}
	public byte[] readHex(String s) {
		ByteList b = new ByteList();
		byte[] bb = TextUtil.hex2bytes(s, b).toByteArray();
		b._free();
		return bb;
	}

	public String writeBase64(byte[] arr) { return Base64.btoa(arr); }
	public byte[] readBase64(String s) { return Base64.atob(s); }

	public String writeRGB(int color) { return "#".concat(Integer.toHexString(color&0xFFFFFF)); }
	public String writeRGBA(int color) { return "#".concat(Integer.toHexString(color)); }
	public int readRGB(String s) { return Integer.parseInt(s.substring(1), 16); }

	public void writeISO(long t, CVisitor out) { out.valueTimestamp(t); }
	public long readISO(long t) { return t; }

	public static void registerHexAdapter(SerializerFactory factory) {
		factory.registerAsType("hex", byte[].class, INSTANCE, "writeHex", "readHex");
	}
	public static void registerBase64Adapter(SerializerFactory factory) {
		factory.registerAsType("base64", byte[].class, INSTANCE, "writeBase64", "readBase64");
	}
	public static void registerRGBAdapter(SerializerFactory factory) {
		factory.registerAsType("rgb", int.class, INSTANCE, "writeRGB", "readRGB");
	}
	public static void registerRGBAAdapter(SerializerFactory factory) {
		factory.registerAsType("rgba", int.class, INSTANCE, "writeRGBA", "readRGB");
	}
	public static void registerISOTimeAdapter(SerializerFactory factory) {
		factory.registerAsType("isotime", long.class, INSTANCE, "writeISO", "readISO");
	}
}
