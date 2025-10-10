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
public interface XDataOutput extends DataOutput, Closeable {
	long totalWritten();
	void flush() throws IOException;

	XDataOutput putZero(int count) throws IOException;

	XDataOutput put(byte[] b) throws IOException;
	XDataOutput put(byte[] b, int off, int len) throws IOException;

	XDataOutput put(DynByteBuf b) throws IOException;
	XDataOutput put(DynByteBuf b, int len) throws IOException;
	XDataOutput put(DynByteBuf b, int off, int len) throws IOException;

	XDataOutput putBool(boolean n) throws IOException;
	XDataOutput putByte(byte n) throws IOException;
	XDataOutput put(@Range(from = -128, to = 0xFF) int n) throws IOException;
	XDataOutput putShort(@Range(from = -32768, to = 0xFFFF) int n) throws IOException;
	XDataOutput putShortLE(@Range(from = -32768, to = 0xFFFF) int n) throws IOException;
	XDataOutput putMedium(@Range(from = 0, to = 0xFFFFFF) int n) throws IOException;
	XDataOutput putMediumLE(@Range(from = 0, to = 0xFFFFFF) int n) throws IOException;
	XDataOutput putInt(int n) throws IOException;
	XDataOutput putIntLE(int n) throws IOException;
	XDataOutput putLong(long n) throws IOException;
	XDataOutput putLongLE(long n) throws IOException;
	XDataOutput putFloat(float n) throws IOException;
	XDataOutput putDouble(double n) throws IOException;

	static int zig(int i) {return (i << 1) ^ (i >> 31);}
	static long zig(long i) {return (i << 1) ^ (i >> 63);}

	XDataOutput putVarInt(int x) throws IOException;
	XDataOutput putVarLong(long x) throws IOException;

	XDataOutput putVUInt(int x) throws IOException;
	XDataOutput putVULong(long x) throws IOException;

	// UTF-16BE
	XDataOutput putChars(CharSequence s) throws IOException;
	XDataOutput putAscii(CharSequence s) throws IOException;

	XDataOutput putUTF(CharSequence s) throws IOException;
	XDataOutput putVarIntUTF(CharSequence s) throws IOException;
	XDataOutput putVUIUTF(CharSequence s) throws IOException;
	XDataOutput putUTFData(CharSequence s) throws IOException;

	XDataOutput putVUIGB(CharSequence s) throws IOException;
	XDataOutput putGBData(CharSequence s) throws IOException;

	XDataOutput putVUIStr(CharSequence str, FastCharset charset) throws IOException;
	XDataOutput putStrData(CharSequence str, FastCharset charset) throws IOException;
	XDataOutput putStrData(CharSequence str, int len, FastCharset charset) throws IOException;
}