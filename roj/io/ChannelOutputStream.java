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
package roj.io;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/2 15:33
 */
public final class ChannelOutputStream extends OutputStream {
    private final WritableByteChannel channel;
    private ByteBuffer one, last;
    private byte[] lastBuf;

    public ChannelOutputStream(WritableByteChannel channel1) {
        channel = channel1;
    }

    @Override
    public void write(int b) throws IOException {
        if(one == null)
            one = ByteBuffer.allocate(1);
        one.put(0, (byte) b).position(0).limit(1);
        channel.write(one);
    }

    @Override
    public void write(@Nonnull byte[] b, int off, int len) throws IOException {
        if(b == lastBuf) {
            last.position(off).limit(off + len);
            channel.write(last);
        } else
            channel.write(last = ByteBuffer.wrap(lastBuf = b, off, len));
    }

    @Override
    public void close() throws IOException {
        last = null;
        lastBuf = null;
        one = null;
        channel.close();
    }

    public WritableByteChannel getChannel() {
        return channel;
    }
}
