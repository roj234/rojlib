package roj.net.tcp.serv.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/5 23:34
 */
public final class ReusableGZOutput extends DeflaterOutputStream {
    private final CRC32 crc = new CRC32();

    private final static int GZIP_MAGIC = 0x8b1f;
    private final static int TRAILER_SIZE = 8;

    public ReusableGZOutput(OutputStream out, int size, int compressionLevel) throws IOException {
        super(out, new Deflater(compressionLevel, true), size, false);
        reset(out);
    }

    public void write(byte[] buf, int off, int len) throws IOException {
        super.write(buf, off, len);
        crc.update(buf, off, len);
    }

    public void finish() throws IOException {
        if (!def.finished()) {
            def.finish();
            while (!def.finished()) {
                int len = def.deflate(buf, 0, buf.length);
                if (def.finished() && len <= buf.length - TRAILER_SIZE) {
                    // last deflater buffer. Fit trailer at the end
                    writeTrailer(buf, len);
                    len += TRAILER_SIZE;
                    out.write(buf, 0, len);
                    return;
                }
                if (len > 0)
                    out.write(buf, 0, len);
            }
            // if we can't fit the trailer at the end of the last
            // deflater buffer, we write it separately
            byte[] trailer = new byte[TRAILER_SIZE];
            writeTrailer(trailer, 0);
            out.write(trailer);
        }
    }

    private void writeHeader() throws IOException {
        out.write(new byte[]{
                (byte) GZIP_MAGIC,        // Magic number (short)
                (byte) (GZIP_MAGIC >> 8),  // Magic number (short)
                Deflater.DEFLATED,        // Compression method (CM)
                0,                        // Flags (FLG)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Modification time MTIME (int)
                0,                        // Extra flags (XFLG)
                0                         // Operating system (OS)
        });
    }

    private void writeTrailer(byte[] buf, int offset) {
        int i = (int) crc.getValue();
        buf[offset++] = (byte) i;
        buf[offset++] = (byte) (i >>> 8);
        buf[offset++] = (byte) (i >>> 16);
        buf[offset++] = (byte) (i >>> 24);
        i = def.getTotalIn();
        buf[offset++] = (byte) i;
        buf[offset++] = (byte) (i >>> 8);
        buf[offset++] = (byte) (i >>> 16);
        buf[offset] = (byte) (i >>> 24);
    }

    public void reset(OutputStream out) throws IOException {
        this.out = out;
        if (out != null) {
            writeHeader();
            crc.reset();
        }
        def.reset();
    }

    @Override
    public void close() throws IOException {
        super.close();
        def.end();
    }
}
