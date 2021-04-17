package roj.archive.qz;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/3/15 16:04
 */
final class Unknown extends QZCoder {
	private final byte[] id;
	Unknown(byte[] id) {
		this.id = id;
	}

	int in, out;

	QZCoder factory() { return this; }
	byte[] id() { return id; }
	public InputStream decode(InputStream in, byte[] password, long uncompressedSize, int maxMemoryLimitInKb) throws IOException {
		throw new IOException("不支持的解码器"+ Arrays.toString(id));
	}
}
