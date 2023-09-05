package roj.text;

import roj.io.IOUtil;

import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2023/9/7 0007 9:36
 */
public interface LinedReader extends Closeable {
	default String readLine() throws IOException {
		CharList s = IOUtil.getSharedCharBuf();
		boolean ok = readLine(s);
		return ok ? s.toString() : null;
	}
	boolean readLine(CharList buf) throws IOException;
}
