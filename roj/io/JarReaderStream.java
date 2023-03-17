package roj.io;

import roj.util.ByteList;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * @author Roj234
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
		byteCache.put((byte) i);
	}

	@Override
	public void write(byte[] bytes, int i, int i1) {
		byteCache.put(bytes, i, i1);
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
