package roj.filesystem;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/08/20 19:27
 */
final class NDevice implements BlockDevice {
	private long handle, capacity;
	private int blockSize;
	private boolean trim;

	public NDevice() {}

	@Override
	public void read(long position, long count, DynByteBuf data) throws IOException {read0(position, data.array(), data._unsafeAddr(), count);}
	@Override
	public void write(long position, long count, DynByteBuf data) throws IOException {write0(position, data.array(), data._unsafeAddr(), count);}

	@Override public synchronized native void erase(long position, long count) throws IOException;
	@Override public synchronized native void sync() throws IOException;
	@Override public int getBlockSize() {return blockSize;}
	@Override public long getCapacity() {return capacity;}
	@Override public boolean supportFastErase() {return trim;}

	@Override
	public synchronized void close() throws IOException {
		if (handle != 0) close0();
	}

	private synchronized native void read0(long position, byte[] address, long offset, long count) throws IOException;
	private synchronized native void write0(long position, byte[] address, long offset, long count) throws IOException;
	private native void close0() throws IOException;
}
