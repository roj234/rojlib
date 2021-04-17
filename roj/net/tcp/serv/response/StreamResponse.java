package roj.net.tcp.serv.response;

import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.io.InputStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/16 11:21
 */
public abstract class StreamResponse implements HTTPResponse {
    protected InputStream stream = null;
    protected long length, position = -1;

    public void prepare() throws IOException {
        if (stream == null) {//stream.close();
            stream = getStream();
            position = 0;
        }
    }

    protected abstract InputStream getStream() throws IOException;

    public boolean send(WrappedSocket channel) throws IOException {
        if (stream == null) throw new IllegalStateException();
        long pos = position;
        if (pos < 0) throw new IllegalStateException();

        if (pos >= length) {
            return false;
        }

        pos += channel.write(stream, length - pos);

        return (position = pos) < length;
    }

    public void release() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
            position = -1;
        }
    }
}
