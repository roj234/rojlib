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

import roj.io.NIOUtil;
import sun.nio.ch.Net;
import sun.nio.ch.SelChImpl;
import sun.nio.ch.SelectionKeyImpl;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 * What is PD? Platform dependent
 * @author Roj233
 * @version 0.1
 * @since 2021/12/25 0:10
 */
public class FileDescriptorChannel extends AbstractSelectableChannel implements SelChImpl {
    private final FileDescriptor socket;

    public FileDescriptorChannel(FileDescriptor socket) {
        super(SelectorProvider.provider());
        this.socket = socket;
        try {
            configureBlocking(false);
        } catch (IOException ignored) {}
    }

    @Override
    public FileDescriptor getFD() {
        return socket;
    }

    @Override
    public int getFDVal() {
        return sun.misc.SharedSecrets.getJavaIOFileDescriptorAccess().get(socket);
    }

    private boolean translateReadyOps(int ops, int initialOps,
                                     SelectionKeyImpl sk) {
        int intOps = sk.nioInterestOps(); // Do this just once, it synchronizes
        int oldOps = sk.nioReadyOps();
        int newOps = initialOps;

        if ((ops & Net.POLLNVAL) != 0) {
            // This should only happen if this channel is pre-closed while a
            // selection operation is in progress
            // ## Throw an error if this channel has not been pre-closed
            return false;
        }

        if ((ops & (Net.POLLERR | Net.POLLHUP)) != 0) {
            newOps = intOps;
            sk.nioReadyOps(newOps);
            // No need to poll again in checkConnect,
            // the error will be detected there
            // readyToConnect = true;
            return (newOps & ~oldOps) != 0;
        }

        if ((ops & Net.POLLIN) != 0 && (intOps & SelectionKey.OP_READ) != 0)
            newOps |= SelectionKey.OP_READ;

        if ((ops & Net.POLLOUT) != 0 && (intOps & SelectionKey.OP_WRITE) != 0)
            newOps |= SelectionKey.OP_WRITE;

        sk.nioReadyOps(newOps);
        return (newOps & ~oldOps) != 0;
    }

    public boolean translateAndUpdateReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, sk.nioReadyOps(), sk);
    }

    public boolean translateAndSetReadyOps(int ops, SelectionKeyImpl sk) {
        return translateReadyOps(ops, 0, sk);
    }

    public void translateAndSetInterestOps(int ops, SelectionKeyImpl sk) {
        int newOps = 0;
        if ((ops & SelectionKey.OP_READ) != 0)
            newOps |= Net.POLLIN;
        if ((ops & SelectionKey.OP_WRITE) != 0)
            newOps |= Net.POLLOUT;
        sk.selector.putEventOps(sk, newOps);
    }

    @Override
    public int validOps() {
        return SelectionKey.OP_READ | SelectionKey.OP_WRITE;
    }

    @Override
    public void kill() throws IOException {
        NIOUtil.close(socket);
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        NIOUtil.close(socket);
    }

    @Override
    protected void implConfigureBlocking(boolean block) throws IOException {
        NIOUtil.configureBlocking(socket, block);
    }
}
