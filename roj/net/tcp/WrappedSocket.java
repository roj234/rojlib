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
package roj.net.tcp;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * @author solo6975
 * @since 2020/11/29 15:33
 */
public interface WrappedSocket extends AutoCloseable {
    Socket socket();

    /**
     * @return true if successful.
     */
    boolean handShake() throws IOException;

    int read() throws IOException;

    int read(int max) throws IOException;

    default void poll() {}

    ByteBuffer buffer();

    int write(ByteBuffer src) throws IOException;

    int write(InputStream src, int max) throws IOException;

    /**
     * Flush any pending data to the network if possible.
     *
     * @return true if successful.
     */
    boolean dataFlush() throws IOException;

    /**
     * Start any connection shutdown processing.
     *
     * @return true if successful, and the data has been flushed.
     */
    boolean shutdown() throws IOException;

    void close() throws IOException;

    void reuse() throws IOException;

    FileDescriptor fd();
}
