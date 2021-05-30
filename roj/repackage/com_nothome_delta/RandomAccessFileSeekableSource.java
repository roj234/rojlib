package roj.repackage.com_nothome_delta;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
 */

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class RandomAccessFileSeekableSource implements SeekableSource {
    private RandomAccessFile raf;

    public RandomAccessFileSeekableSource(RandomAccessFile raf) {
        if (raf == null) {
            throw new NullPointerException("raf");
        } else {
            this.raf = raf;
        }
    }

    public void seek(long pos) throws IOException {
        this.raf.seek(pos);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return this.raf.read(b, off, len);
    }

    public long length() throws IOException {
        return this.raf.length();
    }

    public void close() throws IOException {
        this.raf.close();
    }

    public int read(ByteBuffer bb) throws IOException {
        int c = this.raf.read(bb.array(), bb.position(), bb.remaining());
        if (c == -1) {
            return -1;
        } else {
            bb.position(bb.position() + c);
            return c;
        }
    }
}