package roj.io.misc;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2020/12/19 22:35
 */
public interface Net_LLIO {
	int read(FileDescriptor fd, long _Address, int len) throws IOException;
	int write(FileDescriptor fd, long _Address, int len) throws IOException;

	long readVector(FileDescriptor fd, long _IoVec_Length, int len) throws IOException;
	long writeVector(FileDescriptor fd, long _IoVec_Length, int len) throws IOException;

	void close(FileDescriptor fd) throws IOException;
}
