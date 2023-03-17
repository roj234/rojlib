package roj.io.down;

import roj.io.source.Source;
import roj.net.http.IHttpClient;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;

/**
 * @author Roj234
 * @since 2020/9/13 12:28
 */
final class Chunked extends Downloader {
	private final RandomAccessFile info;

	private final long beginLen;
	long off, len;
	private int delta;

	public Chunked(int seq, Source file, File Info, URL url, long off, long len, IProgress progress) throws IOException {
		super(file, url);
		this.beginLen = len;

		if (Info != null) {
			this.info = new RandomAccessFile(Info, "rw");
			if (this.info.length() < 8 * seq + 8) this.info.setLength(8 * seq + 8);
			info.seek(8 * seq);
			long dt = info.readLong();
			off += dt;
			len -= dt;
			if (len <= 0 || dt < 0) {
				if (dt > 0) writePos(-1);
				state = SUCCESS;
				close();
				return;
			}
		} else {
			this.info = null;
		}

		this.progress = progress;
		if (progress != null) progress.onJoin(this);

		this.off = off;
		this.len = len;
	}

	long getDownloaded() {
		return beginLen - len;
	}
	long getRemain() {
		return len;
	}
	long getTotal() {
		return beginLen;
	}
	// Unit: byte per second
	long getAverageSpeed() {
		long spd = (long) ((double) delta / (System.currentTimeMillis() - begin) * 1000);
		delta = 0;
		begin = System.currentTimeMillis();
		return spd;
	}
	int getDelta() {
		int d = delta;
		delta = 0;
		return d;
	}

	@Override
	protected void onBeforeSend(IHttpClient client) throws IOException {
		file.seek(off);
		client.header("RANGE", "bytes=" + off + '-' + (off+len-1));
	}

	@Override
	void onUpdate(int r) throws IOException {
		off += r;
		len -= r;
		delta += r;

		if (len <= 0) {
			// writePos会限制写入速度, 100ms
			// 所以这里赋值, 一定让它写入
			last = 0;
			done();
		} else {
			writePos(beginLen - len);
		}
	}

	@Override
	void onDone() throws IOException {
		writePos(-1);
	}

	@Override
	void onClose() {
		if (info != null) {
			try {
				if (state == FAILED)
					writePos(beginLen - len);
			} catch (IOException ignored) {}
			try {
				info.close();
			} catch (IOException ignored) {}
		}
	}

	private long last;

	private void writePos(long i) throws IOException {
		if (info != null) {
			long t = System.currentTimeMillis();
			if (t - last > 50) {
				info.seek(info.getFilePointer() - 8);
				info.writeLong(i);
				last = t;
			}
		}
	}
}