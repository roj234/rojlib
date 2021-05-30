package roj.io;

import roj.collect.SingleBitSet;
import roj.io.misc.FileNIODispatcher;
import roj.io.misc.SocketNIODispatcher;
import roj.net.tcp.util.SharedConfig;
import roj.reflect.DirectFieldAccess;
import roj.reflect.DirectFieldAccessor;
import roj.reflect.DirectMethodAccess;
import roj.util.ByteList;
import sun.nio.ch.DirectBuffer;
import sun.nio.ch.FileChannelImpl;

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
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/6 14:15
 */
public final class NonblockingUtil {
    static DirectFieldAccessor implGet, socketFD, socketChFD, fileFD;
    static SocketNIODispatcher snd;
    static FileNIODispatcher fnf;
    static AI1 blockConf, fciDelegate, fcDelegate;

    static {
        try {
            implGet = DirectFieldAccess.get(Socket.class, "impl");
            socketFD = DirectFieldAccess.get(SocketImpl.class, "fd");
            socketChFD = DirectFieldAccess.get(Class.forName("sun.nio.ch.SocketChannelImpl"), "fd");
            fileFD = DirectFieldAccess.get(FileChannelImpl.class, "fd");

            blockConf = DirectMethodAccess.getStatic(AI1.class, "configureBlocking", IOUtil.class, "configureBlocking");

            String[] ss1 = new String[]{
                    "read", "readv", "write", "writev", "preClose", "close"
            };
            String[] ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "preClose0", "close0"
            };
            snd = DirectMethodAccess.getStatic(SocketNIODispatcher.class, ss1, Class.forName("sun.nio.ch.SocketDispatcher"), ss2);

            ss1 = new String[]{
                    "read", "readv", "write", "writev", "duplicateHandle", "close"
            };
            ss2 = new String[]{
                    "read0", "readv0", "write0", "writev0", "duplicateHandle", "close0"
            };
            fnf = DirectMethodAccess.getStatic(FileNIODispatcher.class, ss1, Class.forName("sun.nio.ch.FileDispatcherImpl"), ss2);

            ss1 = new String[]{
                    "map0", "unmap0", "transferTo0", "position0"
            };
            fciDelegate = DirectMethodAccess.getNCI(AI1.class, ss1, new SingleBitSet(), FileChannelImpl.class, ss1);

            ss1 = new String[]{
                    "begin", "end"
            };
            fcDelegate = DirectMethodAccess.getNCI(AI1.class, ss1, new SingleBitSet(), FileChannel.class, ss1);
        } catch (Throwable e) {
            e.printStackTrace();
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
        ((DirectBuffer) shared).cleaner().clean();
    }

    private static int writeFromNativeBuffer(FileDescriptor fd, ByteBuffer buf, int socket) throws IOException {
        int pos = buf.position();
        int lim = buf.limit();

        int len = pos < lim ? lim - pos : 0;
        if (len == 0) {
            return 0;
        } else {
            int wrote = socket == 1 ? snd.write(fd, addr(buf) + pos, len) : fnf.write(fd, addr(buf) + pos, len, (socket & 2) == 2);

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

    private static int readToNativeBuffer(FileDescriptor fd, ByteBuffer buf, int socket) throws IOException {
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

    private static long addr(ByteBuffer buf) {
        return ((DirectBuffer) buf).address();
    }

    public static FileDescriptor fd(Socket socket) throws IOException {
        implGet.setInstance(socket);
        SocketImpl impl = (SocketImpl) implGet.getObject();
        implGet.clearInstance();

        socketFD.setInstance(impl);
        FileDescriptor fd = (FileDescriptor) socketFD.getObject();
        socketFD.clearInstance();

        blockConf.configureBlocking(fd, false);

        return fd;
    }

    public static FileDescriptor fd(SocketChannel socket) {
        socketChFD.setInstance(socket);
        FileDescriptor fd = (FileDescriptor) socketFD.getObject();
        socketChFD.clearInstance();

        return fd;
    }

    public static FileDescriptor fd(FileChannel fc) {
        fileFD.setInstance(fc);
        FileDescriptor fd = (FileDescriptor) fileFD.getObject();
        fileFD.clearInstance();
        return fd;
    }

    public static void preClose(FileDescriptor fd) throws IOException {
        snd.preClose(fd);
    }

    public static void close(FileDescriptor fd) throws IOException {
        snd.close(fd);
    }

    public static long transferInto(FileChannel fc, long pos, long len, FileDescriptor target, boolean socket) throws IOException {
        long got = NonblockingUtil.transferInto_sendfile(fc, pos, len, target);

        if (got == UNSUPPORTED_CASE) {
            // todo cache
            return NonblockingUtil.transferInto_mmap(fc, pos, len, target, 4 | (socket ? 1 : 0));
        }
        return got;
    }

    public static long transferInto_mmap(FileChannel fc, long pos, long len, FileDescriptor target, int flag) throws IOException {
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
        final AI1 fciDelegate = NonblockingUtil.fciDelegate;

        long resl = -1L;

        FileDescriptor fd = fd(fc);

        try {
            fcDelegate.begin(fc);

            if (!fc.isOpen()) {
                return EOF;
            }

            do {
                resl = fciDelegate.transferTo0(fc, fd, pos, len, target);
            } while (resl == INTERRUPTED && fc.isOpen());

            if (resl == UNSUPPORTED_CASE) {
                return UNSUPPORTED_CASE;
            }

            if (resl == UNSUPPORTED) {
                return UNSUPPORTED;
            }

            return normalize(resl);
        } finally {
            fcDelegate.end(fc, resl > -1L);
        }
    }

    private interface AI1 {
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

        long position0(Object self, FileDescriptor fd, long pos);

        /**
         * FileChannel
         */
        void begin(Object self);

        void end(Object self, boolean completed);
    }
}
