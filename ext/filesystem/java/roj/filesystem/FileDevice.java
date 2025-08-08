package roj.filesystem;

import roj.reflect.Unaligned;
import roj.util.DynByteBuf;
import roj.util.NativeMemory;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * @author Roj234
 * @since 2025/07/12 23:27
 */
final class FileDevice implements BlockDevice {
	private final FileChannel channel;
	private final long capacity;
	private final int blockSize = 4096;

	public FileDevice(File file, long capacity) throws IOException {
		this.capacity = capacity;
		channel = FileChannel.open(file.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SPARSE).truncate(capacity);
	}

	@Override
	public void read(long position, long count, DynByteBuf data) throws IOException {
		channel.read(data.nioBuffer(), position);
	}

	@Override
	public void write(long position, long count, DynByteBuf data) throws IOException {
		data.putZero((int)count - data.readableBytes());
		channel.write(data.nioBuffer(), position);
	}

	@Override
	public void erase(long position, long count) throws IOException {
		MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, position, count);
		Unaligned.U.setMemory(NativeMemory.getAddress(map), map.capacity(), (byte) 0);
		NativeMemory.freeDirectBuffer(map);
	}

	@Override public void sync() throws IOException {channel.force(false);}
	@Override public int getBlockSize() {return blockSize;}
	@Override public long getCapacity() {return capacity;}
	@Override public boolean supportFastErase() {return false;}
	@Override public void close() throws IOException {channel.close();}
}
