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

	private final ByteList buf = new ByteList(256);
	private ZipEntry entry;

	public JarReaderStream(BiConsumer<ZipEntry, ByteList> c) throws IOException {
		super(DummyOutputStream.INSTANCE);
		consumer = c;
	}

	public void putNextEntry(ZipEntry ze) { entry = ze; }

	public void write(int b) { buf.put((byte) b); }
	public void write(byte[] b, int off, int len) { buf.put(b, off, len); }

	public void closeEntry() {
		consumer.accept(entry, buf);
		entry = null;
		buf.clear();
	}

	public void setConsumer(BiConsumer<ZipEntry, ByteList> c) { consumer = c; }
}
