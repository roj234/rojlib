package roj.net.tcp.serv.response;

import roj.net.NetworkUtil;
import roj.net.tcp.serv.util.ReusableGZOutput;
import roj.net.tcp.util.SharedConfig;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.zip.Deflater;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/5 18:31
 */
public class GZipFileResponse extends FileResponse {
    ReusableGZOutput gz;
    final ByteList zipped = new ByteList();
    final byte[] hex = new byte[10];

    public GZipFileResponse(File absolute) {
        super(absolute);
    }

    public GZipFileResponse(URI relative) {
        super(relative);
    }

    @Override
    public void prepare() throws IOException {
        super.prepare();

        if (this.gz == null) {
            this.gz = new ReusableGZOutput(zipped.asOutputStream(), SharedConfig.WRITE_MAX, Deflater.DEFAULT_COMPRESSION);

            zipped.ensureCapacity(Math.min(SharedConfig.WRITE_MAX, stream.available()));

            hex[8] = '\r';
            hex[9] = '\n';
        }
    }

    @Override
    public boolean send(WrappedSocket channel) throws IOException {
        if (stream == null)
            throw new IllegalStateException();
        long pos = position;
        if (pos < 0)
            throw new IllegalStateException();

        if (pos >= length) {
            return false;
        }

        ByteList buf = channel.buffer();
        buf.clear();

        long delta = buf.readStreamArray(stream, SharedConfig.WRITE_MAX);
        buf.writeToStream(gz);
        buf.clear();

        pos += delta;

        boolean undone = (position = pos) < length;

        if (!undone) {
            gz.finish();
        }

        if ((buf = this.zipped).pos() > 0) {
            byte[] hex = this.hex;
            int off = NetworkUtil.number2hex(buf.pos(), hex);

            channel.write(new ByteList.ReadOnlySubList(hex, off, 10 - off));

            buf.addAll(hex, 8, 2);

            if (!undone) {
                buf.addAll(SharedConfig.END_OF_CHUNK);
            }

            channel.write(buf);
            buf.clear();
        }

        return undone;
    }

    @Override
    public void release() throws IOException {
        super.release();
        gz.reset(null);
    }

    @Override
    public void writeHeader(CharList list) {
        super.writeHeader(list);
        list.append("Content-Disposition: attachment; filename=\"" + file.getName() + '"').append(CRLF)
                .append("Transfer-Encoding: chunked").append(CRLF)
                .append("Content-Encoding: gzip").append(CRLF);
    }
}
