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

import roj.io.misc.FCNative;
import roj.io.misc.SCNative;
import roj.reflect.DirectAccessor;
import roj.text.TextUtil;
import roj.util.FastThreadLocal;
import roj.util.Helpers;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.*;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since  2020/12/6 14:15
 */
public final class NIOUtil {
    private static SCNative SCN, DCN;
    private static FCNative FCN;
    private static H        UTIL;
    private static Consumer<Object> CLEAN;

    private static Throwable e;

    static {
        try {
            __init();
        } catch (IOException e1) {
            if (e == null) e = e1;
            else e.addSuppressed(e1);
        }
        if (e != null) e.printStackTrace();
    }

    private static void __init() throws IOException {
        ByteBuffer b = ByteBuffer.allocateDirect(1);

        SocketChannel sc = SocketChannel.open();
        sc.close();

        File tmp = new File(System.getProperty("java.io.tmpdir", ".") + "/nio.1");
        FileChannel fc = FileChannel.open(tmp.toPath(),
                                          StandardOpenOption.WRITE,
                                          StandardOpenOption.CREATE,
                                          StandardOpenOption.DELETE_ON_CLOSE);
        fc.close();

        DatagramChannel dc = DatagramChannel.open();
        dc.close();

        try {
            UTIL = DirectAccessor.builder(H.class)
                                 .access(Socket.class, "impl", "socketImpl", null)
                                 .access(SocketImpl.class, "fd", "socketFd", null)
                                 .access(DatagramSocket.class, "impl", "socketImpl1", null)
                                 .access(DatagramSocketImpl.class, "fd", "socketFd1", null)
                                 .access(ServerSocket.class, "impl", "socketImpl2", null)
                                 .access(FileDescriptor.class, "fd", "fdVal", "fdFd")
                                 .access(sc.getClass(), new String[] {"fd", "nd"}, new String[] {"sChFd", "sChNd"}, null)
                                 .access(fc.getClass(), new String[] {"fd", "nd"}, new String[] {"fChFd", "fChNd"}, null)
                                 .access(dc.getClass(), new String[] {"fd", "nd"}, new String[] {"dChFd", "dChNd"}, null)
                                 .delegate(Class.forName("sun.nio.ch.IOUtil"), "configureBlocking")
                                 .delegate_o(b.getClass(), new String[] {"attachment", "cleaner"})
                                 .access(b.getClass(), "address", "address", null)
                                 .build();
        } catch (Throwable e1) {
            if (e == null) e = e1;
            else e.addSuppressed(e1);
        }

        try {
            String[] ss1 = new String[]{
                    "read", "readv", "write", "writev", "preClose", "close"
            };
            String[] ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "preClose0", "close0"
            };
            SCN = DirectAccessor.builder(SCNative.class)
                                .delegate(UTIL.sChNd().getClass(), ss2, ss1).build();

            ss1 = new String[]{
                    "read", "readv", "write", "writev", "duplicateHandle", "close"
            };
            ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "duplicateHandle", "close0"
            };
            FCN = DirectAccessor.builder(FCNative.class)
                                .delegate(UTIL.fChNd(fc).getClass(), ss2, ss1).build();

            ss1 = new String[]{
                    "read", "readv", "write", "writev"
            };
            ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0"
            };
            DCN = DirectAccessor.builder(SCNative.class)
                                .delegate(UTIL.dChNd().getClass(), ss2, ss1).build();
        } catch (Throwable e1) {
            if (e == null) e = e1;
            else e.addSuppressed(e1);
        }

        try {
            CLEAN = Helpers.cast(
                    DirectAccessor.builder(Consumer.class)
                                  .delegate(UTIL.cleaner(b).getClass(), "clean", "accept")
                                  .build());
            clean(b);
        } catch (Throwable e1) {
            if (e == null) e = e1;
            else e.addSuppressed(e1);
        }
    }

    public static final int EOF = -1;
    public static final int UNAVAILABLE = -2;
    public static final int INTERRUPTED = -3;
    public static final int UNSUPPORTED = -4;
    public static final int THROWN = -5;
    public static final int UNSUPPORTED_CASE = -6;

    public static final int SOCKET_FD = 1;
    public static final int DATAGRAM_FD = 2;
    public static final int APPEND_FD = 3;

    public static final int DIRECT_CACHE_MAX = 9999;
    static final FastThreadLocal<ByteBuffer> DIRECT_CACHE = new FastThreadLocal<ByteBuffer>() {
        @Override
        protected ByteBuffer initialValue() {
            return ByteBuffer.allocateDirect(512);
        }
    };
    public static ByteBuffer getSharedDirectBuffer() {
        return DIRECT_CACHE.get();
    }

    public static int swrite(FileDescriptor fd, byte[] array, int pos, int lim, int socket) throws IOException {
        assert lim > pos;
        lim = Math.min(DIRECT_CACHE_MAX, lim - pos);

        ByteBuffer shared = DIRECT_CACHE.get();
        if (shared == null || shared.capacity() < lim) {
            if (shared != null) {
                clean(shared);
            }
            DIRECT_CACHE.set(shared = ByteBuffer.allocateDirect(lim));
        }
        shared.clear();
        shared.put(array, pos, lim).flip();
        return writeFromNativeBuffer(fd, shared, socket);
    }

    public static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer buf, int flag) throws IOException {
        int pos = buf.position();
        int lim = buf.limit();

        int len = lim - pos;
        if (len <= 0) {
            return 0;
        } else {
            if(!fd.valid())
                throw new IOException();
            int wrote;
            long addr = UTIL.address(buf) + pos;
            switch (flag) {
                case SOCKET_FD:
                    wrote = SCN.write(fd, addr, len);
                    break;
                case DATAGRAM_FD:
                    wrote = DCN.write(fd, addr, len);
                    break;
                default:
                    wrote = FCN.write(fd, addr, len, (flag & APPEND_FD) != 0);
                    break;
            }

            if (wrote > 0) {
                buf.position(pos + wrote);
            }

            return wrote == UNAVAILABLE ? 0 : wrote;
        }
    }

    public static int readToNativeBuffer(FileDescriptor fd, ByteBuffer buf, int flag) throws IOException {
        int pos = buf.position();
        int lim = buf.limit();

        int len = lim - pos;
        if (len <= 0) {
            return 0;
        } else {
            if(!fd.valid())
                throw new IOException();
            int read;
            long addr = UTIL.address(buf) + pos;
            switch (flag) {
                case SOCKET_FD:
                    read = SCN.read(fd, addr, len);
                    break;
                case DATAGRAM_FD:
                    read = DCN.read(fd, addr, len);
                    break;
                default:
                    read = FCN.read(fd, addr, len);
                    break;
            }

            if (read > 0) {
                buf.position(pos + read);
            }

            return read == UNAVAILABLE ? 0 : read;
        }
    }

    public static void configureBlocking(FileDescriptor fd, boolean tag) throws IOException {
        if(fd.valid())
            UTIL.configureBlocking(fd, tag);
        else
            throw new IOException("Invalid FileDescriptor");
    }

    public static FileDescriptor fd(Socket socket) throws IOException {
        FileDescriptor fd = UTIL.socketFd(UTIL.socketImpl(socket));
        if(fd.valid())
            UTIL.configureBlocking(fd, false);
        else
            throw new IOException("Invalid FileDescriptor");

        return fd;
    }

    public static FileDescriptor fd(ServerSocket socket) throws IOException {
        FileDescriptor fd = UTIL.socketFd(UTIL.socketImpl2(socket));
        if(fd.valid())
            UTIL.configureBlocking(fd, false);
        else
            throw new IOException("Invalid FileDescriptor");

        return fd;
    }

    public static FileDescriptor fd(DatagramSocket socket) throws IOException {
        FileDescriptor fd = UTIL.socketFd1(UTIL.socketImpl1(socket));
        if(fd.valid())
            UTIL.configureBlocking(fd, false);
        else
            throw new IOException("Invalid FileDescriptor");

        return fd;
    }

    public static FileDescriptor fd(SocketChannel socket) {
        return UTIL.sChFd(socket);
    }

    public static FileDescriptor fd(DatagramChannel socket) {
        return UTIL.dChFd(socket);
    }

    public static FileDescriptor fd(FileChannel fc) {
        return UTIL.fChFd(fc);
    }

    public static void close(FileDescriptor fd) throws IOException {
        if(!fd.valid()) return;
        SCN.close(fd);
        UTIL.fdFd(fd, -1);
    }

    public static void closeF(FileDescriptor fd) throws IOException {
        if(!fd.valid()) return;
        FCN.close(fd);
        UTIL.fdFd(fd, -1);
    }

    public static boolean available() {
        return e == null;
    }

    public static Throwable getWhy() {
        return e;
    }

    private interface H {
        SocketImpl socketImpl(Socket socket);
        DatagramSocketImpl socketImpl1(DatagramSocket socket);
        SocketImpl socketImpl2(ServerSocket socket);

        FileDescriptor socketFd(SocketImpl impl);
        FileDescriptor socketFd1(DatagramSocketImpl impl);
        FileDescriptor sChFd(SocketChannel ch);
        FileDescriptor dChFd(DatagramChannel ch);
        FileDescriptor fChFd(FileChannel ch);
        void fdFd(FileDescriptor fd, int fd2);
        int fdVal(FileDescriptor fd);

        Object fChNd(FileChannel ch);
        Object sChNd();
        Object dChNd();

        long address(Object buf);
        Object attachment(Object buf);
        Object cleaner(Object buf);

        void configureBlocking(FileDescriptor fd, boolean var1) throws IOException;
    }

    private static Object topMost(Object o) {
        while (UTIL.attachment(o) != null)
            o = UTIL.attachment(o);
        return o;
    }

    public static void clean(Buffer shared) {
        if (!shared.isDirect()) return;
        // Java做了多次运行的处理，无须担心
        Object cl = UTIL.cleaner(topMost(shared));
        if (cl != null) CLEAN.accept(cl);
    }

    public static boolean directBufferEquals(Buffer a, Buffer b) {
        if (!a.isDirect() || !b.isDirect()) { return a == b || (a.hasArray() & b.hasArray() && a.array() == b.array()); }
        return UTIL.address(topMost(a)) == UTIL.address(topMost(b));
    }

    /**
     * 0 -> lim
     */
    public static String dumpBuffer(ByteBuffer rb) {
        int p = rb.position();
        rb.position(0);
        byte[] tmp = new byte[rb.limit()];
        rb.get(tmp).position(p);
        return TextUtil.dumpBytes(tmp);
    }

    /**
     * 0 -> pos
     */
    public static String dumpDirty(ByteBuffer rb) {
        int p = rb.position();
        rb.position(0);
        byte[] tmp = new byte[p];
        rb.get(tmp).position(p);
        return TextUtil.dumpBytes(tmp);
    }

    /**
     * pos -> lim
     */
    public static String dumpClean(ByteBuffer rb) {
        int p = rb.position();
        byte[] tmp = new byte[rb.remaining()];
        rb.get(tmp).position(p);
        return TextUtil.dumpBytes(tmp);
    }
}
