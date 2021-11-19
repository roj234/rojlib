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
package roj.net.tcp.util;


import roj.io.IOUtil;
import roj.io.NonblockingUtil;
import roj.net.ssl.EngineAllocator;
import roj.util.ByteList;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * A helper class which performs I/O using the SSLEngine API.
 *
 * <PRE>
 *             Application Data
 *             src        buffer
 *              |           ^
 *              |     |     |
 *              v     |     |
 *         +----+-----|-----+----+
 *         |          |          |
 *         |       SSL|Engine    |
 * wrap()  |          |          |  unwrap()
 *         | OUTBOUND | INBOUND  |
 *         |          |          |
 *         +----+-----|-----+----+
 *              |     |     ^
 *              |     |     |
 *              v           |
 *          networkOut  networkIn
 *                Net data
 * </PRE>
 */
public class SecureSocket extends InsecureSocket {
    private final EngineAllocator alloc;
    private SSLEngine engine;

    private int appBufSize, netBufSize;

    /*
     * All I/O goes through these buffers.
     * <P>
     * It might be nice to use a cache of ByteBuffers so we're
     * not alloc/dealloc'ing ByteBuffer's for each new SSLEngine.
     * <P>
     * We use our superclass' requestBB for our application input buffer.
     * Outbound application data is supplied to us by our callers.
     */
    private ByteBuffer networkIn, networkOut;

    /*
     * An empty ByteBuffer for use when one isn't available, say
     * as a source buffer during initial handshake wraps or for close
     * operations.
     */
    static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    /*
     * During our initial handshake, keep track of the next
     * SSLEngine operation that needs to occur:
     *
     *     NEED_WRAP/NEED_UNWRAP
     *
     * Once the initial handshake has completed, we can short circuit
     * handshake checks with initialHSComplete.
     */
    private HandshakeStatus status;
    private boolean hsDone;

    private boolean shutdown = false;

    protected SecureSocket(Socket sc, FileDescriptor fd, EngineAllocator sslc, boolean isClient) throws IOException {
        super(sc, fd);

        alloc = sslc;
        engine = sslc.allocate();
        engine.setUseClientMode(isClient);
        engine.beginHandshake();
        status = engine.getHandshakeStatus();
        hsDone = false;

        // Create a buffer using the normal expected packet size we'll
        // be getting.  This may change, depending on the peer's
        // SSL implementation.
        netBufSize = engine.getSession().getPacketBufferSize();
        networkIn = ByteBuffer.allocateDirect(netBufSize);
        networkOut = ByteBuffer.allocateDirect(netBufSize);
        networkOut.limit(0);
    }

    public static SecureSocket get(Socket sc, FileDescriptor fd, EngineAllocator ssl, boolean isClient) throws IOException {
        SecureSocket cio = new SecureSocket(sc, fd, ssl, isClient);

        // Create a buffer using the normal expected application size we'll
        // be getting.  This may change, depending on the peer's
        // SSL implementation.
        cio.appBufSize = cio.engine.getSession().getApplicationBufferSize();
        cio.expandBufferCapacity(cio.appBufSize);

        return cio;
    }

    private void expandNetBuf() {
        ByteBuffer bb = ByteBuffer.allocateDirect(netBufSize);
        networkIn.flip();
        bb.put(networkIn);
        IOUtil.clean(networkIn);
        networkIn = bb;
    }

    private boolean tryFlush(ByteBuffer bb) throws IOException {
        if (bb.hasRemaining()) {
            int wrote;
            do {
                wrote = NonblockingUtil.normalize(
                        NonblockingUtil.writeFromNativeBuffer(fd, bb,
                                                              NonblockingUtil.SOCKET_FD));
            } while (wrote == -3 && !socket.isClosed());
        }
        return !bb.hasRemaining();
    }

    /*
     * Perform any handshaking processing.
     * <P>
     * return:
     *          true when handshake is done.
     *          false while handshake is in progress
     */
    @SuppressWarnings("fallthrough")
    public boolean handShake() throws IOException {
        if (hsDone) {
            return true;
        }

        /*
         * Flush out the outgoing buffer, if there's anything left in
         * it.
         */
        if (networkOut.hasRemaining()) {
            if (!tryFlush(networkOut)) {
                return false;
            }

            // See if we need to switch from write to read mode.

            switch (this.status) {
                /*
                 * Is this the last buffer?
                 */
                case FINISHED:
                    hsDone = true;
                    // Fall-through to reregister need for a Read.
                case NEED_UNWRAP:
                    break;
            }

            return hsDone;
        }

        SSLEngineResult result;
        switch (this.status) {
            case NEED_UNWRAP:
                if (_read(networkIn) < 0) {
                    try {
                        engine.closeInbound();
                    } catch (SSLException ignored) {}
                    return hsDone;
                }

                needIO:
                while (this.status == HandshakeStatus.NEED_UNWRAP) {
                    result = _readNetworkIn(0);

                    this.status = result.getHandshakeStatus();

                    switch (result.getStatus()) {
                        case OK:
                            switch (this.status) {
                                case NOT_HANDSHAKING:
                                    throw new SSLException("Not handshaking during initial handshake");

                                case NEED_TASK:
                                    this.status = doTasks();
                                    break;

                                case FINISHED:
                                    hsDone = true;
                                    break needIO;
                            }

                            break;
                        case BUFFER_UNDERFLOW:
                            netBufSize = engine.getSession().getPacketBufferSize();
                            if (netBufSize > networkIn.capacity()) {
                                expandNetBuf();
                            }

                            /*
                             * Need to go reread the Channel for more data.
                             */
                            break needIO;
                        case BUFFER_OVERFLOW:
                            appBufSize = engine.getSession().getApplicationBufferSize();
                            if (appBufSize > buffer.capacity()) {
                                expandBufferCapacity(appBufSize);
                            }
                            break;

                        default: //CLOSED:
                            throw new SSLException("Received" + result.getStatus() +
                                    "during initial handshaking");
                    }
                }  // "needIO" block.

                /*
                 * Just transitioned from read to write.
                 */
                if (this.status != HandshakeStatus.NEED_WRAP) {
                    break;
                }

                // Fall through and fill the write buffers.
            case NEED_WRAP:
                /*
                 * The flush above guarantees the out buffer to be empty
                 */
                networkOut.clear();
                result = engine.wrap(SecureSocket.EMPTY, networkOut);
                networkOut.flip();

                this.status = result.getHandshakeStatus();

                if (result.getStatus() == Status.OK) {
                    if (this.status == HandshakeStatus.NEED_TASK) {
                        this.status = doTasks();
                    }
                } else {
                    // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
                    throw new SSLException("Received " + result.getStatus() + " during initial handshaking");
                }
                break;

            default: // NOT_HANDSHAKING/NEED_TASK/FINISHED
                throw new SSLException("Invalid Handshaking State" + this.status);
        }

        return hsDone;
    }

    private int _read(ByteBuffer buffer) throws IOException {
        return NonblockingUtil.normalize(
                NonblockingUtil.readToNativeBuffer(fd, buffer,
                                                   NonblockingUtil.SOCKET_FD));
    }

    private SSLEngineResult.HandshakeStatus doTasks() {
        Runnable runnable;

        // MAYBE async...
        while ((runnable = engine.getDelegatedTask()) != null) {
            runnable.run();
        }
        return engine.getHandshakeStatus();
    }

    int pushback;
    /*
     * Read the channel for more information, then unwrap the
     * (hopefully application) data we get.
     * <P>
     * If we run out of data, we'll return to our caller (possibly using
     * a Selector) to get notification that more is available.
     * <P>
     * Each call to this method will perform at most one underlying read().
     */
    public int read(int max) throws IOException {
        SSLEngineResult result;

        if (!hsDone) {
            throw new SSLException("Not handshake");
        }

        int nread;
        if(pushback > 0) {
            nread = Math.min(pushback, max);
            buffer.position(buffer.position() + nread);
            pushback -= nread;
            if (nread == max) {
                return max;
            }
        } else {
            nread = 0;
        }

        int read = _read(networkIn);
        if (read < 0) {
            System.err.println("!! Read Closed " + read);
            engine.closeInbound();  // probably throws exception
            return -1;
        }

        read = 0;
        vw:
        do {
            result = _readNetworkIn(max - read);
            read += result.bytesProduced();

            /*
             * Could check here for a renegotiation, but we're only
             * doing a simple read/write, and won't have enough state
             * transitions to do a complete handshake, so ignore that
             * possibility.
             */
            switch (result.getStatus()) {
                case BUFFER_OVERFLOW:
                    // Reset the application buffer size.
                    appBufSize = engine.getSession().getApplicationBufferSize();
                    if (appBufSize > buffer.capacity()) {
                        expandBufferCapacity(appBufSize);
                        break;
                    }
                    break;
                case BUFFER_UNDERFLOW:
                    // Resize buffer if needed.
                    netBufSize = engine.getSession().getPacketBufferSize();
                    if (netBufSize > networkIn.capacity()) {
                        expandNetBuf();
                    }
                    break vw;
                case OK:
                    if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                        doTasks();
                    }
                    break;

                default: // closed
                    if (read == 0)
                        return -1;
                    break vw;
            }
        } while (networkIn.position() != 0);
        return read + nread;
    }

    private SSLEngineResult _readNetworkIn(int mx) throws SSLException {
        networkIn.flip();
        SSLEngineResult result = engine.unwrap(networkIn, buffer);
        if (mx > 0) {
            int more = pushback = result.bytesProduced() - mx;
            if (more > 0) {
                buffer.position(buffer.position() - more);
            }
        }
        networkIn.compact();
        return result;
    }

    public int write(ByteList src) throws IOException {
        if (!hsDone) {
            throw new SSLException("Not handshake");
        }

        if (networkOut.hasRemaining() && !tryFlush(networkOut)) {
            return 0;
        }

        networkOut.clear();
        SSLEngineResult result = engine.wrap(ByteBuffer.wrap(src.list, src.writePos(), Math.min(src.pos() - src.writePos(), Shared.WRITE_MAX)), networkOut);
        networkOut.flip();

        if (result.getStatus() == Status.OK) {
            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                doTasks();
            }
        } else {
            throw new IOException("SSLEngine error during data write: " + result.getStatus());
        }
        int retValue = result.bytesConsumed();
        src.writePos(src.writePos() + retValue);

        if (networkOut.hasRemaining()) {
            tryFlush(networkOut);
        }

        return retValue;
    }

    public int write(ByteBuffer src) throws IOException {
        if (!hsDone) {
            throw new SSLException("Not handshake");
        }

        if (networkOut.hasRemaining() && !tryFlush(networkOut)) {
            return 0;
        }

        networkOut.clear();
        SSLEngineResult result = engine.wrap(src, networkOut);
        networkOut.flip();

        if (result.getStatus() == Status.OK) {
            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                doTasks();
            }
        } else {
            throw new IOException("SSLEngine error during data write: " + result.getStatus());
        }

        if (networkOut.hasRemaining()) {
            tryFlush(networkOut);
        }

        return result.bytesConsumed();
    }

    public boolean dataFlush() throws IOException {
        if (networkOut.hasRemaining()) {
            tryFlush(networkOut);
        }

        return !networkOut.hasRemaining();
    }

    @Override
    public int write(InputStream src, int max) throws IOException {
        if (!hsDone) {
            throw new SSLException("Not handshake");
        }

        if (networkOut.hasRemaining() && !tryFlush(networkOut)) {
            return 0;
        }

        int cap = Math.min(Shared.WRITE_MAX, max);
        if (writeBuffer.length < cap) {
            writeBuffer = new byte[cap];
        }
        int len = src.read(writeBuffer);
        if (len <= 0)
            return len;

        networkOut.clear();
        SSLEngineResult result = engine.wrap(ByteBuffer.wrap(writeBuffer, 0, len), networkOut);
        networkOut.flip();

        return len;
    }

    public boolean shutdown() throws IOException {
        if (!shutdown) {
            engine.closeOutbound();
            shutdown = true;
        }

        if (networkOut.hasRemaining() && tryFlush(networkOut)) {
            return false;
        }

        /*
         * By RFC 2616, we can "fire and forget" our close_notify
         * message, so that's what we'll do here.
         */
        networkOut.clear();
        SSLEngineResult result = engine.wrap(EMPTY, networkOut);
        if (result.getStatus() != Status.CLOSED) {
            throw new SSLException("Improper close state");
        }
        networkOut.flip();

        if (networkOut.hasRemaining()) {
            tryFlush(networkOut);
        }

        return (!networkOut.hasRemaining() && (result.getHandshakeStatus() != HandshakeStatus.NEED_WRAP));
    }

    @Override
    public void reuse() throws IOException {
        if (shutdown)
            throw new IOException("Stream closed.");
        hsDone = false;
        buffer.clear();

        SSLEngine newEngine = alloc.allocate();
        newEngine.setUseClientMode(engine.getUseClientMode());
        (engine = newEngine).beginHandshake();
        status = newEngine.getHandshakeStatus();
    }
}
