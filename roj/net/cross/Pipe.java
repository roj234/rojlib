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
package roj.net.cross;

import roj.crypt.SM4;
import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.util.Helpers;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * 这里不管加密，加密有Host和Client使用SM4 Cipher
 */
public class Pipe implements Runnable {
    public Object att;

    FileDescriptor upstream, downstream;
    final ByteBuffer toh, toc;

    // state / flag
    long nBytesToH, nBytesToC;
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
        this.toc = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
    }

    public final int transfer(boolean bufferOnly) throws IOException {
        if (eof != 0) return S_CLOSED;

        boolean flag = false;
        if (toh.hasRemaining()) {
            int w = NonblockingUtil.writeFromNativeBuffer(upstream, toh, NonblockingUtil.SOCKET_FD);
            if (w < 0) {
                release();
                return S_CLOSED;
            }
            nBytesToH += w;

            if (!toh.hasRemaining()) { toh.clear(); } else flag = true;
        }
        if (toc.hasRemaining()) {
            int w = NonblockingUtil.writeFromNativeBuffer(downstream, toc, NonblockingUtil.SOCKET_FD);
            if (w < 0) {
                this.eof = 2;
                return S_CLOSED;
            }
            nBytesToC += w;

            if (!toc.hasRemaining()) { toc.clear(); } else flag = true;
        }
        if (flag | bufferOnly) return flag ? S_BUFFER : S_NOTHING;

        int r = NonblockingUtil.readToNativeBuffer(upstream, toc, NonblockingUtil.SOCKET_FD);
        if (r > 0) {
            toc.flip();
            flag = true;
        } else if (r < 0) {
            release();
            return S_CLOSED;
        }

        r = NonblockingUtil.readToNativeBuffer(downstream, toh, NonblockingUtil.SOCKET_FD);
        if (r > 0) {
            toh.flip();
            flag = true;
        } else if (r < 0) {
            this.eof = 2;
            return S_CLOSED;
        }

        if (flag) {
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
        this.downstream = client;
    }

    public final void setActive() {
        if (this.upstream == null)
            throw new IllegalStateException("Buffers have been released.");
        this.eof = 0;
        this.toc.clear();
        this.toh.clear();
        this.nBytesToC = this.nBytesToH = 0;
    }

    void doCipher() throws GeneralSecurityException {}

    public final void setInactive() {
        if (upstream == null)
            throw new IllegalStateException("Buffers have been released.");
        this.eof = 4;
        this.nBytesToC = this.nBytesToH = 0;
    }

    public final void release() throws IOException {
        if (upstream == null) return;
        eof = 3;
        IOUtil.clean(toh);
        IOUtil.clean(toc);
        try {
            NonblockingUtil.close(upstream);
            NonblockingUtil.close(downstream);
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
        final SM4 sm4_h, sm4_c;
        final ByteBuffer tmp_h, tmp_c;

        public CipherPipe(FileDescriptor up, FileDescriptor down, byte[] pass) {
            super(up, down);
            byte[] iv = Arrays.copyOf(pass, 16);

            sm4_h = new SM4();
            sm4_h.reset(SM4.ENCRYPT | SM4.SM4_PADDING | SM4.SM4_STREAMED);
            sm4_h.setOption(SM4.SM4_IV, iv);
            sm4_h.setKey(pass);
            tmp_h = toh.duplicate();

            sm4_c = new SM4();
            sm4_c.reset(SM4.DECRYPT | SM4.SM4_PADDING | SM4.SM4_STREAMED);
            sm4_c.setOption(SM4.SM4_IV, iv);
            sm4_c.setKey(pass);
            tmp_c = toc.duplicate();
        }

        @Override
        final void doCipher() throws GeneralSecurityException {
            ByteBuffer target = toh;
            ByteBuffer tmp;
            if (target.hasRemaining()) {
                tmp = tmp_h;
                tmp.clear();
                sm4_h.crypt(target, tmp);
                target.position(0).limit(tmp.position());
            }

            target = toc;
            if (target.hasRemaining()) {
                tmp = tmp_c;
                tmp.clear();
                sm4_c.crypt(target, tmp);
                target.position(0).limit(tmp.position());
            }
        }
    }
}
