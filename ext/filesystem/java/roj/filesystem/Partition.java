package roj.filesystem;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/08/20 19:24
 */
public class Partition {
	private long handle;

	public native String serialNumber() throws IOException;

	public native void lock() throws IOException;
	public native void unlock() throws IOException;

	public native String fileSystem() throws IOException;
	public native String volumeLabel() throws IOException;
	public native long usedSize() throws IOException;
	public native long totalSize() throws IOException;

	public native BlockDevice open(boolean writable) throws IOException;
}
