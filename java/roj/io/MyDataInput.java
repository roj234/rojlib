package roj.io;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/3/10 0010 3:53
 */
public interface MyDataInput extends DataInput, Closeable {
	int DEFAULT_MAX_STRING_LEN = 65536;

	static int zag(int i) {return (i >> 1) & ~(1 << 31) ^ -(i & 1);}
	static long zag(long i) {return (i >> 1) & ~(1L << 63) ^ -(i & 1);}

	void readFully(byte[] b) throws IOException;
	void readFully(byte[] b, int off, int len) throws IOException;

	int skipBytes(int n) throws IOException;
	default void skipForce(long n) throws IOException {
		err: {
			while (n > Integer.MAX_VALUE) {
				if (skipBytes(Integer.MAX_VALUE) != Integer.MAX_VALUE) break err;
				n -= Integer.MAX_VALUE;
			}
			if (skipBytes((int) n) == n) return;
		}

		throw new EOFException("Failed to skip "+n+" bytes");
	}

	boolean readBoolean() throws IOException;

	byte readByte() throws IOException;
	int readUnsignedByte() throws IOException;

	short readShort() throws IOException;
	char readChar() throws IOException;

	int readUnsignedShort() throws IOException;
	int readUShortLE() throws IOException;

	int readMedium() throws IOException;
	int readMediumLE() throws IOException;

	int readInt() throws IOException;
	int readIntLE() throws IOException;

	long readUInt() throws IOException;
	long readUIntLE() throws IOException;

	long readLong() throws IOException;
	long readLongLE() throws IOException;

	float readFloat() throws IOException;
	double readDouble() throws IOException;

	int readVarInt() throws IOException;

	long readVarLong() throws IOException;

	int readVUInt() throws IOException;
	long readVULong() throws IOException;

	String readAscii(int len) throws IOException;

	@NotNull String readUTF() throws IOException;

	String readVUIUTF() throws IOException;
	String readVUIUTF(int max) throws IOException;
	String readUTF(int len) throws IOException;
	<T extends Appendable> T readUTF(int len, T target) throws IOException;

	String readVUIGB() throws IOException;
	String readVUIGB(int max) throws IOException;
	String readGB(int len) throws IOException;
	<T extends Appendable> T readGB(int len, T target) throws IOException;

	String readLine() throws IOException;

	long position() throws IOException;

	boolean isReadable();
}