package roj.net.tcp.serv.response;

import roj.net.tcp.serv.util.ReusableGZOutput;
import roj.net.tcp.util.SharedConfig;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/5 18:31
 */
public class CachedFileResponse extends FileResponse {
    ByteList buf = new ByteList();

    public CachedFileResponse(File absolute) {
        super(absolute);
    }

    public CachedFileResponse(URI relative) {
        super(relative);
    }

    @Override
    public void prepare() throws IOException {
        if (file.length() == 0)
            throw new IllegalArgumentException("file is empty");
        if (buf.pos() == 0) {
            super.prepare();
            ReusableGZOutput gz = new ReusableGZOutput(buf.asOutputStream(), SharedConfig.WRITE_MAX, 5);

            ByteList buf = new ByteList(Math.min(SharedConfig.WRITE_MAX, stream.available()));
            int delta;
            do {
                delta = buf.readStreamArray(stream, SharedConfig.WRITE_MAX);
                buf.writeToStream(gz);
                buf.clear();
            } while (delta > 0);

            gz.finish();
            gz.close();

            this.buf.compress();
            super.release();
        }
        buf.rewrite();
    }

    int wrote;

    @Override
    public boolean send(WrappedSocket channel) throws IOException {
        if (buf == null)
            throw new IllegalStateException("Not prepared");
        wrote += channel.write(buf);

        return buf.remaining() > 0;
    }

    @Override
    public void release() {
    }

    @Override
    public void writeHeader(CharList list) {
        list.append("Content-Disposition: attachment; filename=\"" + file.getName() + '"').append(CRLF)
                .append("Content-Type: ").append("application/octet-stream").append(CRLF)
                .append("Content-Length: " + this.buf.pos()).append(CRLF)
                .append("Content-Encoding: gzip").append(CRLF);
    }
}
