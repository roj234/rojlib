package roj.archive.ui;

import roj.io.IOUtil;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;

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
		ByteList b = IOUtil.getSharedByteBuf();
		b.ensureCapacity(4096);
		byte[] data = b.list;

		while (true) {
			if (Thread.interrupted()) throw new ClosedByInterruptException();

			int len = in.read(data);
			if (len < 0) break;
			out.write(data, 0, len);

			if (bar != null) bar.addCurrent(len);
		}
	}
}