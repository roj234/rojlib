package roj.archive.qz;

import roj.concurrent.TaskPool;
import roj.io.FastFailException;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Roj234
 * @since 2023/5/26 0026 19:17
 */
public class QZUtils {
	public static void verify7z(File file, TaskPool pool) throws Exception {
		EasyProgressBar bar = new EasyProgressBar("验证压缩文件");
		bar.setUnit("B");

		AtomicReference<Throwable> failed = new AtomicReference<>();
		try (QZArchive archive = new QZArchive(file)) {
			for (QZEntry entry : archive.getEntriesByPresentOrder()) {
				bar.addMax(entry.getSize());
			}

			archive.parallelDecompress(pool, (entry, in) -> {
				byte[] arr = ArrayCache.getByteArray(40960, false);
				try {
					while (true) {
						int r = in.read(arr);
						if (r < 0) break;

						if (failed.get() != null) throw new FastFailException("-other thread failed-");
						bar.addCurrent(r);
					}
				} catch (FastFailException e) {
					throw e;
				} catch (Throwable e) {
					failed.set(e);
					throw new FastFailException("-验证失败-");
				} finally {
					ArrayCache.putArray(arr);
				}
			}, null);

			pool.awaitFinish();
		} catch (Exception e) {
			failed.set(e);
		}

		Throwable exception = failed.getAndSet(null);
		if (exception != null) {
			bar.end("验证失败");
			Helpers.athrow(exception);
		}

		bar.end("验证成功");
	}
}
