package roj.archive.ui;

import roj.archive.qz.QZCoder;
import roj.archive.qz.QZFileWriter;
import roj.archive.qz.QZWriter;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;

import java.io.File;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2023/6/4 0004 3:54
 */
@Deprecated
public class QZWriterAsync {
	private final QZFileWriter writer;
	private final TaskPool pool;
	private final QZCoder[] codec;
	private final BiConsumer<File, QZWriter> c;
	private int blocksize, length;
	private final List<File> files = new SimpleList<>();

	public QZWriterAsync(
		QZFileWriter writer, TaskPool pool,
		BiConsumer<File, QZWriter> consumer, QZCoder... codec) {
		this.writer = writer;
		this.pool = pool;
		this.c = consumer;
		this.codec = codec;
	}

	public QZWriterAsync blockSize(int size) { blocksize = size; return this; }

	public void add(File file) {
		length += file.length();
		files.add(file);
		if (length >= blocksize) {
			flush();
		}
	}

	public void flush() {
		if (files.isEmpty()) return;

		File[] f = files.toArray(new File[files.size()]);
		length = 0;
		files.clear();

		pool.pushTask(() -> {
			try (QZWriter w = writer.parallel()) {
				w.setCodec(codec);
				for (File ent : f) c.accept(ent, w);
			}
		});
	}
}
