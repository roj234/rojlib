package roj.net.tcp.util;

import roj.concurrent.OperationDone;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.util.Notify;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/2/4 15:43
 */
public class StreamLikeSequence implements CharSequence {
    CharList cl;
    WrappedSocket socket;
    long timeout;
    int bufOff, maxRecv;
    boolean async;

    public StreamLikeSequence(int initialCapacity, boolean async) {
        this.cl = new CharList(initialCapacity);
        this.async = async;
    }

    public StreamLikeSequence init(WrappedSocket socket, Router router) {
        return init(socket, router.readTimeout(), router.maxLength());
    }

    public StreamLikeSequence init(WrappedSocket socket, long timeout, int maxRecv) {
        this.timeout = System.currentTimeMillis() + timeout;
        this.socket = socket;
        this.bufOff = 0;
        this.maxRecv = maxRecv;
        return this;
    }

    public void release() {
        if (cl.length() > SharedConfig.MAX_CHAR_BUFFER_CAPACITY) {
            cl = new CharList();
        } else {
            cl.clear();
        }
        this.socket = null;
    }

    @Override
    public int length() {
        return bufOff == -1 ? cl.length() : Integer.MAX_VALUE;
    }

    @Override
    public char charAt(int index) {
        ensureLength(index + 1);
        return cl.charAt(index);
    }

    private void ensureLength(int required) {
        int start = bufOff;
        if(start == -1)
            return;
        ByteList buf = socket.buffer();
        try {
            int read;
            while (cl.length() < required) {
                read = socket.read();
                if (read >= 0) {
                    if (read > 0) {
                        start += ByteReader.decodeUTFPartialExternal(start, -1, cl, buf);
                    }

                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ignored) {}
                } else {
                    if(read == -1) {
                        bufOff = -1;
                        return;
                    }
                    throw new Notify(new IOException("socket.read() got " + read));
                }

                if(buf.pos() > maxRecv) {
                    throw new Notify(-127);
                }

                if (System.currentTimeMillis() > timeout) {
                    throw new Notify(-128);
                }

                if(cl.length() < required && async) {
                    throw OperationDone.INSTANCE;
                }
            }
        } catch (IOException e) {
            throw new Notify(e);
        } finally {
            bufOff = start;
        }
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        ensureLength(end + 1);
        return cl.subSequence(start, end);
    }

    @Override
    public IntStream chars() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IntStream codePoints() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public String toString() {
        return cl.toString();
    }
}
