package roj.http.curl;

import roj.http.HttpRequest;
import roj.io.source.Source;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Roj234
 * @since 2020/9/13 12:28
 */
final class Chunked extends Downloader {
	private final RandomAccessFile info;

	private final long beginLen;
	long off, len;
	private int delta;

	public Chunked(int seq, Source file, File Info, long off, long len) throws IOException {
		super(file);
		this.beginLen = len;

		if (Info != null) {
			this.info = new RandomAccessFile(Info, "rw");
			info.seek(8 * seq);
			long dt = info.readLong();
			off += dt;
			len -= dt;
			if (len <= 0 || dt < 0) {
				if (dt > 0) writePos(-1);
				state = SUCCESS;
				info.close();
				file.close();
				return;
			}
		} else {
			this.info = null;
		}

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
	protected void onBeforeSend(HttpRequest q) throws IOException {
		file.seek(off);
		q.header("range", "bytes="+off+'-'+(off+len-1));
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