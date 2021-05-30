package roj.repackage.com_nothome_delta;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
 */
public interface SeekableSource extends Closeable {
    void seek(long var1) throws IOException;

    int read(ByteBuffer var1) throws IOException;
}