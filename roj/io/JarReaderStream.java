/*
 * This file is a part of MI
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

import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class JarReaderStream extends JarOutputStream {
    private BiConsumer<ZipEntry, ByteList> consumer;
    private final ByteList byteCache = new ByteList(256);
    private ZipEntry entryCache;

    public JarReaderStream(BiConsumer<ZipEntry, ByteList> zipEntryConsumer) throws IOException {
        super(DummyOutputStream.INSTANCE);
        this.consumer = zipEntryConsumer;
    }

    public void putNextEntry(ZipEntry var1) {
        entryCache = var1;
    }

    @Override
    public void write(int i) {
        byteCache.add((byte) i);
    }

    @Override
    public void write(byte[] bytes, int i, int i1) {
        byteCache.addAll(bytes, i, i1);
    }

    @Override
    public void write(@Nonnull byte[] bytes) {
        byteCache.addAll(bytes);
    }

    @Override
    public void closeEntry() {
        consumer.accept(entryCache, byteCache);
        entryCache = null;
        byteCache.clear();
    }

    public void setConsumer(BiConsumer<ZipEntry, ByteList> consumer) {
        this.consumer = consumer;
    }
}
