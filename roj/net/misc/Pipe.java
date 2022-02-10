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

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * @author Roj233
 * @since 2021/12/24 23:27
 */
public class Pipe implements Selectable {
    public Object att;
    protected SelectionKey upKey, downKey;

    protected FileDescriptor upstream, downstream;
    protected ByteBuffer toh, toc;
    public int idleTime;

    // state / flag
    public long uploaded, downloaded;

    public final boolean isUpstreamEof() {
        return upstream == null;
    }

    public final boolean isDownstreamEof() {
        return downstream == null;
    }

    public final void setUpstream(FileDescriptor upstream) {
        if (toh == null) throw new IllegalStateException();
        this.upstream = upstream;
    }

    static final int S_BUFFER = -1, S_NET = 0, S_NOTHING = 1, S_CLOSED = 2;
    public static final int BUFFER_CAPACITY = 4096;

    public Pipe(FileDescriptor upstream, FileDescriptor downstream) {
        this.upstream = upstream;
        this.downstream = downstream;

        this.toh = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        this.toh.limit(0);
        this.toc = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
        this.toc.limit(0);
    }

    public void selected(int readyOps) throws IOException {
        if (upstream == null || downstream == null) return;
        transfer(false);
    }

    protected final int transfer(boolean bufferOnly) throws IOException {
        int c;
        if (toh.hasRemaining()) {
            try {
                c = NIOUtil.writeFromNativeBuffer(upstream, toh, NIOUtil.SOCKET_FD);
            } catch (IOException e) {
                c = -1;
            }
            if (c < 0) {
                close();
                return S_CLOSED;
            }
            if (c > 0) {
                idleTime = 0;
                uploaded += c;
            } else {
                SelectionKey upKey = this.upKey;
                if (upKey != null && upKey.interestOps() != SelectionKey.OP_WRITE)
                    upKey.interestOps(SelectionKey.OP_WRITE);
            }
        }
        if (toh.hasRemaining()) return S_BUFFER;
        if (toc.hasRemaining()) {
            try {
                c = NIOUtil.writeFromNativeBuffer(downstream, toc, NIOUtil.SOCKET_FD);
            } catch (IOException e) {
                c = -1;
            }
            if (c < 0) {
                closeDown();
                return S_CLOSED;
            }
            if (c > 0) {
                idleTime = 0;
                downloaded += c;
            } else {
                SelectionKey downKey = this.downKey;
                if (downKey != null && downKey.interestOps() != SelectionKey.OP_WRITE)
                    downKey.interestOps(SelectionKey.OP_WRITE);
            }
        }
        if (toc.hasRemaining()) return S_BUFFER;
        if (bufferOnly) return S_NOTHING;

        toc.clear();
        try {
            c = NIOUtil.readToNativeBuffer(upstream, toc, NIOUtil.SOCKET_FD);
        } catch (IOException e) {
            c = -1;
        }
        toc.flip();

        boolean flag = false;
        if (c > 0) {
            flag = true;
        } else if (c < 0) {
            close();
            return S_CLOSED;
        } else {
            SelectionKey upKey = this.upKey;
            if (upKey != null && upKey.interestOps() != SelectionKey.OP_READ)
                upKey.interestOps(SelectionKey.OP_READ);
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
            closeDown();
            return S_CLOSED;
        } else {
            SelectionKey downKey = this.downKey;
            if (downKey != null && downKey.interestOps() != SelectionKey.OP_READ)
                downKey.interestOps(SelectionKey.OP_READ);
        }

        if (flag) {
            idleTime = 0;
            try {
                doCipher();
            } catch (GeneralSecurityException e) {
                closeDown();
                throw new IOException(e);
            }
            transfer(true);
            return S_NET;
        }
        return S_NOTHING;
    }

    @Override
    public void tick() {
        idleTime++;
    }

    @Override
    public boolean isClosedOn(SelectionKey key) {
        return (key == upKey && upstream == null) || (key == downKey && downstream == null);
    }

    private void closeDown() {
        try {
            NIOUtil.close(downstream);
        } catch (IOException ignored) {}
        downstream = null;
    }

    public void setClient(FileDescriptor client) {
        this.toc.position(0).limit(0);
        this.toh.position(0).limit(0);
        this.downloaded = this.uploaded = 0;
        idleTime = 0;
        this.downstream = client;
    }

    protected void doCipher() throws GeneralSecurityException {}

    public final void close() throws IOException {
        if (upstream == null) return;
        NIOUtil.clean(toh);
        NIOUtil.clean(toc);
        try {
            NIOUtil.close(upstream);
            if (downstream != null)
                NIOUtil.close(downstream);
        } finally {
            upstream = downstream = null;
            toc = toh = null;
        }
    }

    @Override
    public String toString() {
        return "Pipe{" + "att=" + att + ", idle=" + idleTime + ", U=" + uploaded + ", D=" + downloaded + '}';
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
        public void setClient(FileDescriptor client) {
            super.setClient(client);
            if (client != null) {
                sm4_h.restore(bak0);
                sm4_c.restore(bak1);
            }
        }

        @Override
        protected final void doCipher() throws GeneralSecurityException {
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
                tmp.clear();
                sm4_c.crypt(target, tmp);
                tmp.flip();
                target.clear();
                target.put(tmp).flip();
            }
        }
    }
}
