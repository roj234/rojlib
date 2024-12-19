package roj.archive.ui;

import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ClosedByInterruptException;

/**
 * @author Roj234
 * @since 2023/5/26 0026 19:17
 */
public class QZUtils {
	public static void copyStreamWithProgress(InputStream in, OutputStream out, EasyProgressBar bar) throws IOException {
		byte[] tmp = ArrayCache.getByteArray(65536, false);
		try {
			while (true) {
				if (Thread.interrupted()) throw new ClosedByInterruptException();

				int len = in.read(tmp);
				if (len < 0) break;
				out.write(tmp, 0, len);

				if (bar != null) bar.increment(len);
			}
		} finally {
			ArrayCache.putArray(tmp);
		}
	}
}