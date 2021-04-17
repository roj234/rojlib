package roj.io.misc;

import java.io.FileDescriptor;
import java.io.IOException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 22:35
 */
public interface FileNIODispatcher {
    int read(FileDescriptor fd, long addr, int len) throws IOException;

    long readv(FileDescriptor fd, long addr, int len) throws IOException;

    int write(FileDescriptor fd, long addr, int len, boolean append) throws IOException;

    long writev(FileDescriptor fd, long addr, int len, boolean append) throws IOException;

    void close(FileDescriptor fd) throws IOException;

    /**
     * FileDispatcherImpl
     */
    long duplicateHandle(long handle) throws IOException;
}
