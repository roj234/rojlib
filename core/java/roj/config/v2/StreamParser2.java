package roj.config.v2;

import org.jetbrains.annotations.Nullable;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.IOException;

/**
 * 现在的访问者模式在反序列化的时候做的不够好，我需要把Parser改成Tokenizer那种基于预期的格式：
 * 我希望接下来是一个Map，我希望接下来是一个String……我希望接下来是一个整数……转换由Parser或者一个适配器完成，而生成的类不需要做各种奇怪的转换
 * 然而，这种范式的转变很麻烦，又要重构了，再说吧
 * @author Roj234
 * @since 2025/4/26 18:28
 */
public interface StreamParser2 {
	int TOKEN_MAP = 0, TOKEN_ARRAY = 1, TOKEN_END = 2, TOKEN_STRING = 3, TOKEN_NULL = 4, TOKEN_BOOL = 5, TOKEN_INT = 6, TOKEN_INT64 = 7, TOKEN_FLOAT = 8, TOKEN_FLOAT64 = 9, TOKEN_EXTENDED = 10;

	ParseException error(String message);

	default String peekExtendedToken() {return null;}
	int peekToken();
	int nextToken() throws ParseException, IOException;
	boolean getBoolean() throws ParseException;
	default byte getByte() throws ParseException, IOException {
		int val = getInt();
		if ((byte) val != val) throw error(val+"超过范围");
		return (byte) val;
	}
	default short getShort() throws ParseException, IOException {
		int val = getInt();
		if ((short) val != val) throw error(val+"超过范围");
		return (short) val;
	}
	default char getChar() throws ParseException, IOException {
		int val = getInt();
		if ((char) val != val) throw error(val+"超过范围");
		return (char) val;
	}
	int getInt() throws ParseException;
	long getLong() throws ParseException;
	default float getFloat() throws ParseException {return (float)getDouble();}
	double getDouble() throws ParseException;
	@Nullable String getString() throws ParseException;
	/**
	 * -2null, -1长度未知
	 */
	int getMap() throws ParseException, IOException;
	default void nextMapKey() throws ParseException, IOException {}
	boolean nextIsMapEnd() throws ParseException, IOException;
	/**
	 * -2null, -1长度未知
	 */
	int getArray() throws ParseException, IOException;
	boolean nextIsArrayEnd() throws ParseException, IOException;

	default byte[] getByteArray() throws ParseException, IOException {
		int size = getArray();
		if (size == -2) return null;
		if (size == -1 || size > 0xFFFF) {
			ByteList buf = IOUtil.getSharedByteBuf();
			while (!nextIsArrayEnd()) {
				buf.put(getByte());
			}
			return buf.toByteArray();
		}

		byte[] buf = new byte[size];
		for (int i = 0; i < size; i++) {
			nextIsArrayEnd();
			buf[i] = getByte();
		}
		nextIsArrayEnd();
		return buf;
	}
	default int[] getIntArray() throws ParseException, IOException {
		int size = getArray();
		if (size == -2) return null;
		if (size == -1 || size > 0xFFFF) {
			ByteList buf = IOUtil.getSharedByteBuf();
			while (!nextIsArrayEnd()) {
				buf.putInt(getInt());
			}
			//return buf.toByteArray();
		}

		int[] buf = new int[size];
		for (int i = 0; i < size; i++) {
			nextIsArrayEnd();
			buf[i] = getInt();
		}
		nextIsArrayEnd();
		return buf;
	}
	default long[] getLongArray() throws ParseException, IOException {
		int size = getArray();
		if (size == -2) return null;
		if (size == -1 || size > 0xFFFF) {
			ByteList buf = IOUtil.getSharedByteBuf();
			while (!nextIsArrayEnd()) {
				buf.put(getByte());
			}
			//return buf.toByteArray();
		}

		long[] buf = new long[size];
		for (int i = 0; i < size; i++) {
			nextIsArrayEnd();
			buf[i] = getLong();
		}
		nextIsArrayEnd();
		return buf;
	}
}