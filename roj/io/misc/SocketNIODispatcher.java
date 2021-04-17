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
public interface SocketNIODispatcher {
    int read(FileDescriptor fd, long addr, int len) throws IOException;

    long readv(FileDescriptor fd, long addr, int len) throws IOException;

    int write(FileDescriptor fd, long addr, int len) throws IOException;

    long writev(FileDescriptor fd, long addr, int len) throws IOException;

    void preClose(FileDescriptor fd) throws IOException;

    void close(FileDescriptor fd) throws IOException;
}
