package roj.repackage.com_nothome_delta;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
 */

import java.io.IOException;
import java.nio.ByteBuffer;

public class ByteBufferSeekableSource implements SeekableSource {
    private ByteBuffer bb;
    private ByteBuffer cur;

    public ByteBufferSeekableSource(byte[] source) {
        this(ByteBuffer.wrap(source));
    }

    public ByteBufferSeekableSource(ByteBuffer bb) {
        if (bb == null) {
            throw new NullPointerException("bb");
        } else {
            this.bb = bb;
            bb.rewind();

            try {
                this.seek(0L);
            } catch (IOException var3) {
                throw new RuntimeException(var3);
            }
        }
    }

    public void seek(long pos) throws IOException {
        this.cur = this.bb.slice();
        if (pos > (long)this.cur.limit()) {
            throw new IOException("pos " + pos + " cannot seek " + this.cur.limit());
        } else {
            this.cur.position((int)pos);
        }
    }

    public int read(ByteBuffer dest) {
        if (!this.cur.hasRemaining()) {
            return -1;
        } else {
            int c;
            for(c = 0; this.cur.hasRemaining() && dest.hasRemaining(); ++c) {
                dest.put(this.cur.get());
            }

            return c;
        }
    }

    public void close() {
        this.bb = null;
        this.cur = null;
    }

    public String toString() {
        return "BBSeekable bb=" + this.bb.position() + "-" + this.bb.limit() + " cur=" + this.cur.position() + "-" + this.cur.limit() + "";
    }
}
