package roj.archive.qz;

import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.io.IOUtil;
import roj.math.MutableLong;
import roj.ui.ProgressBar;
import roj.util.Helpers;

import java.io.*;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2023/5/26 0026 19:17
 */
public class QZUtils {
	public static void parallelCompressWithProgress(QZFileWriter fw, TaskPool pool, File baseDir, List<File> files, int blockSize, ProgressBar bar) throws IOException {
		MutableLong total = new MutableLong();
		LongAdder delta = new LongAdder();

		BiConsumer<InputStream, OutputStream> copyStream = bar == null ? null : (in, out) -> {
			byte[] data = IOUtil.getSharedByteBuf().list;
			try {
				while (true) {
					int len = in.read(data);
					if (len < 0) break;
					out.write(data, 0, len);

					bar.update(delta.sum() / (double)total.value, len);
					delta.add(len);
				}
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		};

		int baseLen = baseDir == null ? -1 : baseDir.getAbsolutePath().length()+1;
		long sum = 0;
		List<File> list = new SimpleList<>();

		for (int i = 0; i < files.size(); i++) {
			if (sum >= blockSize) {
				File[] arr = list.toArray(new File[0]);
				pool.pushTask(() -> {
					try (QZWriter w = fw.parallel()) {
						w.setSolidSize(0);
						for (File file : arr) {
							w.beginEntry(new QZEntry(baseLen < 0 ? file.getName() : file.getAbsolutePath().substring(baseLen)));
							try (FileInputStream in = new FileInputStream(file)) {
								if (bar == null) IOUtil.copyStream(in, w);
								else copyStream.accept(in, w);
							}
							w.closeEntry();
						}
					}
				});
				sum = 0;
				list.clear();
			}

			File f = files.get(i);
			sum += f.length();
			list.add(f);
		}

		pool.awaitFinish();

		try (QZWriter w = fw) {
			for (File file : list) {
				w.beginEntry(new QZEntry(file.getName()));
				try (FileInputStream in = new FileInputStream(file)) {
					if (bar == null) IOUtil.copyStream(in, w);
					else copyStream.accept(in, w);
				}
				w.closeEntry();
			}
		}
	}
}
