package roj.mapper.util;

import roj.archive.zip.ZipFileWriter;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author Roj234
 * @since 2021/5/29 16:43
 */
public class ResWriter implements Runnable, Callable<Void> {
	public ResWriter(ZipFileWriter zfw, Map<String, ?> resources) {
		this.zfw = zfw;
		this.resources = resources;
	}

	private final ZipFileWriter zfw;
	private final Map<String, ?> resources;

	/**
	 * Write resource into zip
	 */
	@Override
	public void run() {
		try {
			call();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Void call() throws IOException {
		ByteList bl = IOUtil.getSharedByteBuf();
		for (Map.Entry<String, ?> entry : resources.entrySet()) {
			Object v = entry.getValue();
			if (v instanceof InputStream) {
				bl.readStreamFully((InputStream) v);
				v = bl;
			} else if (v.getClass() == String.class) {
				bl.readStreamFully(new FileInputStream(v.toString()));
				v = bl;
			}
			zfw.writeNamed(entry.getKey(), v instanceof ByteList ? (ByteList) v : bl.setArray((byte[]) v));
			bl.clear();
		}
		return null;
	}
}
