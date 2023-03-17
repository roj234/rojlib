package roj.io.misc;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2020/12/19 22:35
 */
public interface FDRW {
	int read(FileDescriptor fd, long addr, int len) throws IOException;
	long readv(FileDescriptor fd, long addr, int len) throws IOException;

	int write(FileDescriptor fd, long addr, int len) throws IOException;
	long writev(FileDescriptor fd, long addr, int len) throws IOException;
}
