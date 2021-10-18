/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.io;

import roj.io.misc.FileNIODispatcher;
import roj.io.misc.SocketNIODispatcher;
import roj.reflect.DirectAccessor;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketImpl;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/12/6 14:15
 */
public final class NonblockingUtil {
    private static final SocketNIODispatcher sdp;
    private static final FileNIODispatcher   fdp;
    private static final Ux                  Util;

    static {
        try {
            Util = DirectAccessor.builder(Ux.class)
                                 .delegate(Class.forName("sun.nio.ch.IOUtil"), "configureBlocking")
                                 .access(Socket.class, "impl", "getSocketImpl", null)
                                 .access(SocketImpl.class, "fd", "getSocketImplFd", null)
                                 .access(Class.forName("sun.nio.ch.SocketChannelImpl"), "fd", "getSocketChannelFd", null)
                                 .access(Class.forName("sun.nio.ch.FileChannelImpl"), "fd", "getFileChannelFd", null)
                                 .build();

            String[] ss1 = new String[]{
                    "read", "readv", "write", "writev", "preClose", "close"
            };
            String[] ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "preClose0", "close0"
            };
            sdp = DirectAccessor.builder(SocketNIODispatcher.class).delegate(Class.forName("sun.nio.ch.SocketDispatcher"), ss2, ss1).build();

            ss1 = new String[]{
                    "read", "readv", "write", "writev", "duplicateHandle", "close"
            };
            ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "duplicateHandle", "close0"
            };
            fdp = DirectAccessor.builder(FileNIODispatcher.class).delegate(Class.forName("sun.nio.ch.FileDispatcherImpl"), ss2, ss1).build();
        } catch (Throwable e) {
            throw new Error("Failed to initialize NonblockingUtil: Unsupported java version",e );
        }
    }

    public static final int EOF = -1;
    public static final int UNAVAILABLE = -2;
    public static final int INTERRUPTED = -3;
    public static final int UNSUPPORTED = -4;
    public static final int THROWN = -5;
    public static final int UNSUPPORTED_CASE = -6;

    public static final int SOCKET_FD = 1;
    public static final int APPEND_FD = 2;

    public static final int DIRECT_CACHE_MAX = 1048576;
    static final FastThreadLocal<ByteBuffer> DIRECT_CACHE = new FastThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocateDirect(512);
        }
    };
    public static ByteBuffer getNativeDirectBuffer() {
        return DIRECT_CACHE.get();
    }

    public static int normalize(int length) {
        return length == UNAVAILABLE ? 0 : length;
    }

    public static long normalize(long length) {
        return length == UNAVAILABLE ? 0 : length;
    }

    public static int writeSocket(FileDescriptor fd, ByteList buf, int max) throws IOException {
        return write(fd, buf, max, 1);
    }

    public static int writeFile(FileDescriptor fd, ByteList buf, int max) throws IOException {
        return write(fd, buf, max, 0);
    }

    private static int write(FileDescriptor fd, ByteList buf, int max, int socket) throws IOException {
        int pos = buf.writePos();
        int lim = buf.pos();

        int len = pos < lim ? Math.min(lim - pos, max) : 0;
        len = Math.min(DIRECT_CACHE_MAX, len);

        ByteBuffer shared = DIRECT_CACHE.get();
        if (shared == null || shared.capacity() < len) {
            if (shared != null) {
                IOUtil.clean(shared);
            }
            DIRECT_CACHE.set(shared = ByteBuffer.allocateDirect(len));
        }
        shared.position(0).limit(shared.capacity());

        buf.putInto(shared, len);
        buf.writePos(pos);
        shared.flip();
        int wrote = writeFromNativeBuffer(fd, shared, socket);
        if (wrote > 0) {
            buf.writePos(pos + wrote);
        }

        return wrote;
    }

    public static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer buf, int flag) throws IOException {
        int pos = buf.position();
        int lim = buf.limit();

        int len = pos < lim ? lim - pos : 0;
        if (len == 0) {
            return 0;
        } else {
            if(!fd.valid())
                throw new IOException();
            int wrote = flag == 1 ? sdp.write(fd, addr(buf) + pos, len) : fdp.write(fd, addr(buf) + pos, len, (flag & 2) == 2);

            if (wrote > 0) {
                buf.position(pos + wrote);
            }

            return wrote;
        }
    }

    public static int readSocket(FileDescriptor fd, ByteBuffer buf, int max) throws IOException {
        return read(fd, buf, max, 1);
    }

    public static int readFile(FileDescriptor fd, ByteBuffer buf, int max) throws IOException {
        return read(fd, buf, max, 0);
    }

    private static int read(FileDescriptor fd, ByteBuffer buf, int max, int socket) throws IOException {
        if (buf.isDirect()) {
            int lim = buf.limit();
            buf.limit(Math.min(max, buf.capacity()));
            int read = readToNativeBuffer(fd, buf, socket);
            buf.limit(lim);
            return read;
        }
        throw new RuntimeException("Not direct buffer");
    }

    public static int readSocket(FileDescriptor fd, ByteList buf, int max) throws IOException {
        return read(fd, buf, max, 1);
    }

    public static int readFile(FileDescriptor fd, ByteList buf, int max) throws IOException {
        return read(fd, buf, max, 0);
    }

    private static int read(FileDescriptor fd, ByteList buf, int max, int socket) throws IOException {
        int len = Math.min(buf.list.length - buf.pos(), max);
        len = Math.min(DIRECT_CACHE_MAX, len);

        ByteBuffer shared = DIRECT_CACHE.get();
        if (shared == null || shared.capacity() < len) {
            if (shared != null) {
                IOUtil.clean(shared);
            }
            DIRECT_CACHE.set(shared = ByteBuffer.allocateDirect(len));
        }
        shared.position(0).limit(len);

        int read = readToNativeBuffer(fd, shared, socket);
        if (read > 0) {
            shared.flip();
            buf.readFrom(shared);
        }

        return read;
    }

    public static int readToNativeBuffer(FileDescriptor fd, ByteBuffer buf, int socket) throws IOException {
        int pos = buf.position();
        int lim = buf.limit();

        int len = pos < lim ? lim - pos : 0;
        if (len == 0) {
            return 0;
        } else {
            if(!fd.valid())
                throw new IOException();
            int read = socket == 1 ? sdp.read(fd, addr(buf) + pos, len) : fdp.read(fd, addr(buf) + pos, len);

            if (read > 0) {
                buf.position(pos + read);
            }

            return read;
        }
    }

    public static long addr(ByteBuffer buf) {
        return ((sun.nio.ch.DirectBuffer) buf).address();
    }

    public static FileDescriptor fd(Socket socket) throws IOException {
        FileDescriptor fd = Util.getSocketImplFd(Util.getSocketImpl(socket));
        if(fd != null && fd.valid())
            Util.configureBlocking(fd, false);
        else
            throw new IOException("Invalid FileDescriptor");

        return fd;
    }

    public static FileDescriptor fd(SocketChannel socket) {
        return Util.getSocketChannelFd(socket);
    }

    public static FileDescriptor fd(FileChannel fc) {
        return Util.getFileChannelFd(fc);
    }

    public static void preClose(FileDescriptor fd) throws IOException {
        if(!fd.valid())
            throw new IOException("Invalid FileDescriptor");
        sdp.preClose(fd);
    }

    public static void close(FileDescriptor fd) throws IOException {
        if(!fd.valid())
            throw new IOException("Invalid FileDescriptor");
        sdp.close(fd);
    }

    public static boolean available() {
        return Util != null;
    }

    public static void setBlockState(Socket socket, boolean block) throws IOException {
        FileDescriptor fd = Util.getSocketImplFd(Util.getSocketImpl(socket));
        if(fd == null || !fd.valid())
            throw new IOException("Invalid FileDescriptor");
        Util.configureBlocking(fd, block);
    }

    private interface Ux {
        SocketImpl getSocketImpl(Socket socket);
        FileDescriptor getSocketImplFd(SocketImpl impl);
        FileDescriptor getSocketChannelFd(SocketChannel socketChannelImpl);
        FileDescriptor getFileChannelFd(FileChannel channel);

        void configureBlocking(FileDescriptor fd, boolean var1) throws IOException;
    }
}
