package roj.io;

import org.jetbrains.annotations.Range;
import roj.text.FastCharset;
import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author Roj233
 * @since 2025/9/19 11:45
 */
public interface ByteOutput extends DataOutput, Closeable {
	long totalWritten();
	void flush() throws IOException;

	ByteOutput putZero(int count) throws IOException;

	ByteOutput put(byte[] b) throws IOException;
	ByteOutput put(byte[] b, int off, int len) throws IOException;

	ByteOutput put(DynByteBuf b) throws IOException;
	ByteOutput put(DynByteBuf b, int len) throws IOException;
	ByteOutput put(DynByteBuf b, int off, int len) throws IOException;

	ByteOutput putBool(boolean n) throws IOException;
	ByteOutput putByte(byte n) throws IOException;
	ByteOutput put(@Range(from = -128, to = 0xFF) int n) throws IOException;
	ByteOutput putShort(@Range(from = -32768, to = 0xFFFF) int n) throws IOException;
	ByteOutput putShortLE(@Range(from = -32768, to = 0xFFFF) int n) throws IOException;
	ByteOutput putMedium(@Range(from = 0, to = 0xFFFFFF) int n) throws IOException;
	ByteOutput putMediumLE(@Range(from = 0, to = 0xFFFFFF) int n) throws IOException;
	ByteOutput putInt(int n) throws IOException;
	ByteOutput putIntLE(int n) throws IOException;
	ByteOutput putLong(long n) throws IOException;
	ByteOutput putLongLE(long n) throws IOException;
	ByteOutput putFloat(float n) throws IOException;
	ByteOutput putDouble(double n) throws IOException;

	static int zig(int i) {return (i << 1) ^ (i >> 31);}
	static long zig(long i) {return (i << 1) ^ (i >> 63);}

	ByteOutput putVarInt(int x) throws IOException;
	ByteOutput putVarLong(long x) throws IOException;

	ByteOutput putVUInt(int x) throws IOException;
	ByteOutput putVULong(long x) throws IOException;

	// UTF-16BE
	ByteOutput putChars(CharSequence s) throws IOException;
	ByteOutput putAscii(CharSequence s) throws IOException;

	ByteOutput putUTF(CharSequence s) throws IOException;
	ByteOutput putVarIntUTF(CharSequence s) throws IOException;
	ByteOutput putVUIUTF(CharSequence s) throws IOException;
	ByteOutput putUTFData(CharSequence s) throws IOException;

	ByteOutput putVUIGB(CharSequence s) throws IOException;
	ByteOutput putGBData(CharSequence s) throws IOException;

	ByteOutput putVUIStr(CharSequence str, FastCharset charset) throws IOException;
	ByteOutput putStrData(CharSequence str, FastCharset charset) throws IOException;
	ByteOutput putStrData(CharSequence str, int len, FastCharset charset) throws IOException;
}