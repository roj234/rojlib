package roj.io.misc;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2020/12/19 22:35
 */
public interface File_LLIO {
	int read(FileDescriptor fd, long _Address, int len) throws IOException;
	int write(FileDescriptor fd, long _Address, int len, boolean append) throws IOException;

	/**
	 * struct iovec {
	 * 		void  *iov_base;
	 * 		size_t iov_len;
	 * };
	 */
	long readVector(FileDescriptor fd, long _IoVec, int _IoVec_Length) throws IOException;
	long writeVector(FileDescriptor fd, long _IoVec, int _IoVec_Length, boolean append) throws IOException;

	int readPositional(FileDescriptor fd, long _Address, int len, long _Position) throws IOException;
	int writePositional(FileDescriptor fd, long _Address, int len, long _Position) throws IOException;

	int truncate(FileDescriptor fd, long size) throws IOException;
	long size(FileDescriptor fd) throws IOException;

	int sync(FileDescriptor fd, boolean doSync) throws IOException;

	int lock(FileDescriptor fd, boolean notTryLock, long offset, long length, boolean shared) throws IOException;
	void unlock(FileDescriptor fd, long offset, long length) throws IOException;

	void close(FileDescriptor fd) throws IOException;
}
