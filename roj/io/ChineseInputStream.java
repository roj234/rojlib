package roj.io;

import roj.text.ChineseCharsetDetector;
import roj.util.ArrayCache;

import java.io.IOException;
import java.io.InputStream;

/**
 * 编码检测...
 * @author Roj234
 * @since 2023/3/21 8:58
 */
public class ChineseInputStream extends PushbackInputStream {
	private final String charset;
	private boolean sharedBuffer;

	public ChineseInputStream(InputStream in) throws IOException {
		super(in);
		try (ChineseCharsetDetector cd = new ChineseCharsetDetector(in)) {
			charset = cd.detect();
			setBuffer(cd.buffer(), 0, cd.limit());
			sharedBuffer = true;
		}
	}

	public final String getCharset() { return charset; }

	@Override
	protected void bufferEmpty() {
		if (sharedBuffer) {
			ArrayCache.getDefaultCache().putArray(buffer);
			buffer = null;
			sharedBuffer = false;
		}
	}
}