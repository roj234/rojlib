package roj.filesystem;

import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/07/12 23:24
 */
public interface BlockDevice extends Closeable {
	static BlockDevice file(File file, long capacity) throws IOException {return new FileDevice(file, capacity);}

	void read(long position, long count, DynByteBuf data) throws IOException;
	void write(long position, long count, DynByteBuf data) throws IOException;
	void erase(long position, long count) throws IOException;
	void sync() throws IOException;
	int getBlockSize();
	long getCapacity();
	boolean supportFastErase();
}
