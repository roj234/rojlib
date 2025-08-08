package roj.filesystem;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2025/08/20 19:24
 */
public class Disk {
	static {
		System.loadLibrary("ILHDAccess");
	}

	private long handle;

	public static final int DEVICE_DISCONNECTED = -1, DEVICE_HDD = 0, DEVICE_SSD = 1;

	public static native Disk[] list() throws IOException;

	public native int deviceType(); // NOT_EXIST=-1 HDD=0 or SSD=1 or other
	public native long totalSize() throws IOException;
	public native long blockSize() throws IOException;
	public native String serialNumber() throws IOException;
	public native Partition[] partitions() throws IOException;
	public native BlockDevice open(boolean writable) throws IOException;
}
