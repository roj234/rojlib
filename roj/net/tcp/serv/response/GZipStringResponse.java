package roj.net.tcp.serv.response;

import roj.net.tcp.serv.util.ReusableGZOutput;
import roj.net.tcp.util.SharedConfig;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.io.IOException;
import java.util.zip.Deflater;

public class GZipStringResponse extends StringResponse {
    public GZipStringResponse(CharSequence c, String mime) {
        super(c, mime);
    }

    public GZipStringResponse(CharSequence c) {
        super(c);
    }

    public void prepare() throws IOException {
        if (buf == null) {
            ByteList list = new ByteList(content.length());
            ByteWriter.writeUTF(list, content, (byte) -1);

            ByteList buf2 = new ByteList(list.pos());
            ReusableGZOutput gz = new ReusableGZOutput(buf2.asOutputStream(), SharedConfig.WRITE_MAX, Deflater.DEFAULT_COMPRESSION);

            list.writeToStream(gz);
            gz.finish();
            gz.close();

            buf = buf2;
        } else {
            buf.compress();
            buf.rewrite();
        }
    }
}
