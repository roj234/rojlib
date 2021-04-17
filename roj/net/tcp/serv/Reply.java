package roj.net.tcp.serv;

import roj.net.tcp.serv.response.HTTPResponse;
import roj.net.tcp.util.Action;
import roj.net.tcp.util.ResponseCode;
import roj.net.tcp.util.SharedConfig;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Objects;

public class Reply implements Response {
    @Nonnull
    private final ResponseCode code;
    @Nonnull
    private final HTTPResponse response;
    private final boolean headerOnly;

    public Reply(ResponseCode code, HTTPResponse response) {
        this(code, response, -1);
    }

    public Reply(ResponseCode code, HTTPResponse response, int action) {
        this.code = Objects.requireNonNull(code, "code");
        this.response = Objects.requireNonNull(response, "response");
        this.headerOnly = (action == Action.HEAD);
    }

    private ByteList buf = null;

    protected ByteList headers() {
        Object[] data = SharedConfig.SYNC_BUFFER.get();

        CharList header = (CharList) data[2];
        header.ensureCapacity(100);
        header.clear();

        try {
            response.writeHeader(header.append("HTTP/1.1 ").append(code.toString()).append(CRLF)
                    .append("Server: Async/0.1").append(CRLF));

            ByteList bl = new ByteList(header.append(CRLF).length());

            ByteWriter.writeUTF(bl, header, -1);

            return bl;
        } finally {
            if (header.arrayLength() > SharedConfig.MAX_CHAR_BUFFER_CAPACITY)
                data[0] = new CharList(SharedConfig.MAX_CHAR_BUFFER_CAPACITY);
            else
                header.clear();
        }
    }

    public void prepare() throws IOException {
        response.prepare();
        if (buf == null)
            buf = headers();
        else
            buf.rewrite();
    }

    public boolean send(WrappedSocket channel) throws IOException {
        if (buf == null)
            throw new IllegalStateException();

        if (buf.remaining() > 0) {
            if (channel.write(buf) <= 0)
                return true;
        }

        if (!headerOnly) {
            if (response.send(channel))
                return true;
        }

        return !channel.dataFlush();
    }

    public void release() throws IOException {
        response.release();
    }
}
