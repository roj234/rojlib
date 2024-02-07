package roj.io.down;

import roj.io.source.Source;
import roj.net.http.HttpRequest;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/2/28 21:38
 */
final class Streaming extends Downloader {
	private long downloaded;
	private int delta;

	Streaming(Source file) { super(file); }

	long getDownloaded() {
		return downloaded;
	}
	long getRemain() {
		return 1;
	}
	long getTotal() {
		return -1;
	}
	// Unit: byte per second
	public long getAverageSpeed() {
		return (long) ((double) downloaded / (System.currentTimeMillis() - begin) * 1000);
	}
	int getDelta() {
		int d = delta;
		delta = 0;
		return d;
	}

	@Override
	void onUpdate(int r) {
		downloaded += r;
		delta += r;
	}

	@Override
	void onDone() throws IOException {
		file.setLength(file.position());
	}

	@Override
	void onBeforeSend(HttpRequest q) throws IOException {
		downloaded = 0;
		file.seek(0);
	}
}
