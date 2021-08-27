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
import roj.net.tcp.util.SharedConfig;
import roj.reflect.DirectAccessor;
import roj.util.ByteList;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketImpl;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedByInterruptException;
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
    static SocketNIODispatcher snd;
    static FileNIODispatcher fnf;
    static AI1 Util;

    static {
        try {
            Util = DirectAccessor.builder(AI1.class)
                                 .delegate(Class.forName("sun.nio.ch.IOUtil"), "configureBlocking")
                                 .delegate(FileChannel.class, new String[] {"begin", "end"})
                                 .delegate(Class.forName("sun.nio.ch.FileChannelImpl"), "map0", "unmap0", "transferTo0"/*, "position0"*/)
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
            snd = DirectAccessor.builder(SocketNIODispatcher.class).delegate(Class.forName("sun.nio.ch.SocketDispatcher"), ss2, ss1).build();

            ss1 = new String[]{
                    "read", "readv", "write", "writev", "duplicateHandle", "close"
            };
            ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "duplicateHandle", "close0"
            };
            fnf = DirectAccessor.builder(FileNIODispatcher.class).delegate(Class.forName("sun.nio.ch.FileDispatcherImpl"), ss2, ss1).build();
        } catch (Throwable e) {
            e.printStackTrace();
            System.err.println("Failed to initialize NonblockingUtil: Unsupported java version");
        }
    }

    public static final int EOF = -1;
    public static final int UNAVAILABLE = -2;
    public static final int INTERRUPTED = -3;
    public static final int UNSUPPORTED = -4;
    public static final int THROWN = -5;
    public static final int UNSUPPORTED_CASE = -6;

    static final int MMAP_LIMIT = 8388608;

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

        Object[] data = SharedConfig.SYNC_BUFFER.get();
        ByteBuffer shared = (ByteBuffer) data[3];
        if (shared == null || shared.capacity() < len) {
            if (shared != null) {
                clean(shared);
            }
            if (len > SharedConfig.DIRECT_CACHE_MAX) {
                shared = ByteBuffer.allocateDirect(len);
            } else {
                data[3] = shared = ByteBuffer.allocateDirect(len);
            }
        }

        int wrote;
        try {
            buf.putInto(shared, len);
            buf.writePos(pos);
            shared.flip();
            wrote = writeFromNativeBuffer(fd, shared, socket);
            if (wrote > 0) {
                buf.writePos(pos + wrote);
            }
        } finally {
            if (data[3] != shared) {
                clean(shared);
            } else {
                shared.position(0).limit(shared.capacity());
            }
        }

        return wrote;
    }

    public static void clean(ByteBuffer shared) {
        ((sun.nio.ch.DirectBuffer) shared).cleaner().clean();
    }

    public static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer buf, int flag) throws IOException {
        int pos = buf.position();
        int lim = buf.limit();

        int len = pos < lim ? lim - pos : 0;
        if (len == 0) {
            return 0;
        } else {
            int wrote = flag == 1 ? snd.write(fd, addr(buf) + pos, len) : fnf.write(fd, addr(buf) + pos, len, (flag & 2) == 2);

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
            buf.limit(max);
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
        int len = Math.min(buf.capacity() - buf.pos(), max);

        Object[] data = SharedConfig.SYNC_BUFFER.get();
        ByteBuffer shared = (ByteBuffer) data[3];
        if (shared == null || shared.capacity() < len) {
            if (shared != null) {
                clean(shared);
            }
            if (len > SharedConfig.DIRECT_CACHE_MAX) {
                shared = ByteBuffer.allocateDirect(len);
            } else {
                data[3] = shared = ByteBuffer.allocateDirect(len);
            }
        }

        int read;
        try {
            read = readToNativeBuffer(fd, shared, socket);
            if (read > 0) {
                buf.readFrom(shared);
            }
        } finally {
            if (data[3] != shared) {
                clean(shared);
            } else {
                shared.position(0).limit(shared.capacity());
            }
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
            int read = socket == 1 ? snd.read(fd, addr(buf) + pos, len) : fnf.read(fd, addr(buf) + pos, len);

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
        snd.preClose(fd);
    }

    public static void close(FileDescriptor fd) throws IOException {
        if(!fd.valid())
            throw new IOException("Invalid FileDescriptor");
        snd.close(fd);
    }

    static boolean supportSF = true;
    public static long transferInto(FileChannel fc, long pos, long len, FileDescriptor target, boolean socket) throws IOException {
        long got;
        if(supportSF) {
            got = NonblockingUtil.transferInto_sendfile(fc, pos, len, target);
            if (got == UNSUPPORTED_CASE) {
                supportSF = false;
            } else {
                return got;
            }
        }
        return NonblockingUtil.transferInto_mmap(fc, pos, len, target, 4 | (socket ? 1 : 0));
    }

    public static long transferInto_mmap(FileChannel fc, long pos, long len, FileDescriptor target, int flag) throws IOException {
        if(!target.valid())
            throw new IOException("Invalid trg FileDescriptor");
        long remain = len;

        while (remain > 0L) {
            long mapSize = Math.min(remain, MMAP_LIMIT);

            try {
                MappedByteBuffer mb = fc.map(FileChannel.MapMode.READ_ONLY, pos, mapSize);

                try {
                    int wrote = writeFromNativeBuffer(target, mb, flag & 3);

                    remain -= wrote;
                    if ((flag & 4) == 4) {
                        break;
                    }

                    pos += wrote;
                } finally {
                    clean(mb);
                }
            } catch (ClosedByInterruptException e) {
                assert !fc.isOpen();

                try {
                    fc.close();
                } catch (Throwable e1) {
                    e.addSuppressed(e1);
                }

                throw e;
            } catch (IOException e) {
                if (remain != len) {
                    break;
                }

                throw e;
            }
        }

        return len - remain;
    }

    public static long transferInto_sendfile(FileChannel fc, long pos, long len, FileDescriptor target) {
        if(target == null || !target.valid())
            throw new RuntimeException("Invalid trg FileDescriptor");
        long resl = -1L;

        FileDescriptor fd = fd(fc);
        if(fd == null || !fd.valid())
            throw new RuntimeException("Invalid src FileDescriptor");

        try {
            Util.begin(fc);

            if (!fc.isOpen()) {
                return EOF;
            }

            do {
                resl = Util.transferTo0(fc, fd, pos, len, target);
            } while (resl == INTERRUPTED && fc.isOpen());

            if (resl == UNSUPPORTED_CASE) {
                return UNSUPPORTED_CASE;
            }

            if (resl == UNSUPPORTED) {
                return UNSUPPORTED;
            }

            return normalize(resl);
        } finally {
            Util.end(fc, resl > -1L);
        }
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

    private interface AI1 {
        SocketImpl getSocketImpl(Socket socket);
        FileDescriptor getSocketImplFd(SocketImpl impl);
        FileDescriptor getSocketChannelFd(SocketChannel socketChannelImpl);
        FileDescriptor getFileChannelFd(FileChannel channel);

        void configureBlocking(FileDescriptor fd, boolean var1) throws IOException;

        /**
         * FileChannelImpl
         */

        /**
         * 注意对齐!
         *
         * @param ch permission
         *           READ_ONLY 0 <br>
         *           READ_WRITE 1 <br>
         *           PRIVATE 2 <br>
         */
        long map0(Object self, int ch, long pos, long len) throws IOException;

        /* static */
        int unmap0(long ptr, long len);

        long transferTo0(Object self, FileDescriptor fd, long pos, long len, FileDescriptor target);

        //long position0(Object self, FileDescriptor fd, long pos);

        /**
         * FileChannel
         */
        void begin(Object self);

        void end(Object self, boolean completed);
    }
}
