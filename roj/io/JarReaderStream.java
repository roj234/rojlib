package roj.io;

import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: JarReaderStream.java
 */
public class JarReaderStream extends JarOutputStream {
    private BiConsumer<ZipEntry, ByteList> consumer;
    private final ByteList byteCache = new ByteList(256);
    private ZipEntry entryCache;

    public JarReaderStream(BiConsumer<ZipEntry, ByteList> zipEntryConsumer) throws IOException {
        super(new DummyOutputStream());
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
    public synchronized void write(byte[] bytes, int i, int i1) {
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
