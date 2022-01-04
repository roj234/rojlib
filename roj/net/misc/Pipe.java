/*
 * This file is a part of MoreItems
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
package roj.net.misc;

import roj.crypt.MyCipher;
import roj.crypt.SM4;
import roj.io.NIOUtil;
import roj.util.Helpers;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2021/12/24 23:27
 */
public class Pipe implements Runnable {
    public Object att;

    protected FileDescriptor upstream, downstream;
    protected final ByteBuffer toh, toc;
    public int idleTime;

    // state / flag
    public long nBytesToH, nBytesToC;
    private byte eof;

    public final boolean isEof() {
        return eof != 0;
    }

    public boolean isUpstreamEof() {
        return (eof & 1) != 0;
    }

    public boolean isDownstreamEof() {
        return (eof & 2) != 0;
    }

    public FileDescriptor getUpstreamFD() {
        return upstream;
    }

    public FileDescriptor getDownstreamFD() {
        return downstream;
    }

    public long getnBytesToC() {
        return nBytesToC;
    }

    public long getnBytesToH() {
        return nBytesToH;
    }

    static final int S_BUFFER = -1, S_NET = 0, S_NOTHING = 1, S_CLOSED = 2;
    static final int BUFFER_CAPACITY = 4096;

    public Pipe(FileDescriptor upstream, FileDescriptor downstream) {
        this.upstream = upstream;
        this.downstream = downstream;

        this.toh = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        this.toh.limit(0);
        this.toc = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        this.toc.limit(0);
    }

    public final int transfer(boolean bufferOnly) throws IOException {
        if (eof != 0) return S_CLOSED;

        int c;
        if (toh.hasRemaining()) {
            try {
                c = NIOUtil.writeFromNativeBuffer(upstream, toh, NIOUtil.SOCKET_FD);
            } catch (IOException e) {
                c = -1;
            }
            if (c < 0) {
                release();
                return S_CLOSED;
            }
            idleTime = 0;
            nBytesToH += c;
        }
        if (toh.hasRemaining()) return S_BUFFER;
        if (toc.hasRemaining()) {
            try {
                c = NIOUtil.writeFromNativeBuffer(downstream, toc, NIOUtil.SOCKET_FD);
            } catch (IOException e) {
                c = -1;
            }
            if (c < 0) {
                this.eof = 2;
                return S_CLOSED;
            }
            idleTime = 0;
            nBytesToC += c;
        }
        if (toc.hasRemaining()) return S_BUFFER;
        if (bufferOnly) return S_NOTHING;

        boolean flag = false;

        toc.clear();
        try {
            c = NIOUtil.readToNativeBuffer(upstream, toc, NIOUtil.SOCKET_FD);
        } catch (IOException e) {
            c = -1;
        }
        toc.flip();
        if (c > 0) {
            flag = true;
        } else if (c < 0) {
            release();
            return S_CLOSED;
        }

        toh.clear();
        try {
            c = NIOUtil.readToNativeBuffer(downstream, toh, NIOUtil.SOCKET_FD);
        } catch (IOException e) {
            c = -1;
        }
        toh.flip();
        if (c > 0) {
            flag = true;
        } else if (c < 0) {
            this.eof = 2;
            return S_CLOSED;
        }

        if (flag) {
            idleTime = 0;
            try {
                doCipher();
            } catch (GeneralSecurityException e) {
                this.eof = 2;
                throw new IOException(e);
            }
            transfer(true);
            return S_NET;
        }
        return S_NOTHING;
    }

    public void setClient(FileDescriptor client) {
        if (client == null)
            setInactive();
        else
            setActive();
        idleTime = 0;
        this.downstream = client;
    }

    public void setActive() {
        if (this.upstream == null)
            throw new IllegalStateException("Pipe Closed.");
        this.eof = 0;
        this.toc.position(0).limit(0);
        this.toh.position(0).limit(0);
        this.nBytesToC = this.nBytesToH = 0;
    }

    void doCipher() throws GeneralSecurityException {}

    public final void setInactive() {
        if (upstream == null)
            throw new IllegalStateException("Pipe Closed.");
        this.eof = 4;
        this.nBytesToC = this.nBytesToH = 0;
    }

    public final void release() throws IOException {
        if (upstream == null) return;
        eof = 3;
        NIOUtil.clean(toh);
        NIOUtil.clean(toc);
        try {
            NIOUtil.close(upstream);
            if (downstream != null)
                NIOUtil.close(downstream);
        } finally {
            upstream = null;
            downstream = null;
        }
    }

    public final boolean isReleased() {
        return upstream == null;
    }

    @Override
    public final void run() {
        try {
            transfer(false);
        } catch (Throwable e) {
            Helpers.athrow(e);
        }
    }

    public static class CipherPipe extends Pipe {
        final MyCipher sm4_h, sm4_c;
        final ByteBuffer tmp;
        final Object bak0, bak1;

        public CipherPipe(FileDescriptor up, FileDescriptor down, byte[] pass) {
            super(up, down);
            byte[] iv = Arrays.copyOf(pass, 16);

            tmp = ByteBuffer.allocate(BUFFER_CAPACITY);

            sm4_h = new MyCipher(new SM4(), MyCipher.MODE_CFB);
            sm4_h.setKey(pass, MyCipher.ENCRYPT);
            sm4_h.setOption(MyCipher.IV, iv);
            bak0 = sm4_h.backup();

            sm4_c = new MyCipher(new SM4(), MyCipher.MODE_CFB);
            sm4_c.setKey(pass, MyCipher.DECRYPT);
            sm4_c.setOption(MyCipher.IV, iv);
            bak1 = sm4_c.backup();
        }

        @Override
        public void setActive() {
            super.setActive();
            sm4_h.restore(bak0);
            sm4_c.restore(bak1);
        }

        @Override
        final void doCipher() throws GeneralSecurityException {
            ByteBuffer target = toh;
            ByteBuffer tmp = this.tmp;
            if (target.hasRemaining()) {
                tmp.clear();
                sm4_h.crypt(target, tmp);
                tmp.flip();
                target.clear();
                target.put(tmp).flip();
            }

            target = toc;
            if (target.hasRemaining()) {
                System.out.println("TOC1 " + target);
                tmp.clear();
                sm4_c.crypt(target, tmp);
                tmp.flip();
                target.clear();
                target.put(tmp).flip();
                System.out.println("TOC2 " + target);
            }
        }
    }
}
