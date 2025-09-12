package roj.http.curl;

import roj.http.HttpHead;
import roj.http.HttpRequest;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.net.*;
import roj.util.DynByteBuf;

import java.io.*;

/**
 * @author Roj233
 * @since 2022/2/28 21:49
 */
public abstract sealed class Downloader implements Runnable, Closeable, ChannelHandler {
	Downloader(Source file) { this.file = file; }

	final Source file;
	HttpRequest client;
	MyChannel ch;
	DownloadTask owner;
	DownloadListener listener;

	volatile byte state;
	static final byte DISCONNECTED = 0, CONNECTING = 1, DOWNLOADING = 2, SUCCESS = 3, FAILED = 4;

	abstract long getDownloaded();
	abstract long getTotal();

	@Override
	public final void channelOpened(ChannelCtx ctx) throws IOException {
		state = DOWNLOADING;

		HttpHead head = client.response();
		int code = head.getCode();
		if (code < 200 || code > 299) throw new FileNotFoundException("远程返回: "+head);

		DownloadTask.LOGGER.trace("子任务{}: 已连接服务器 {}", this, head);
	}

	@Override
	public final void channelTick(ChannelCtx ctx) throws IOException {
		if (listener != null && listener.isCancelled()) close();
		if (++idleTime > DownloadTask.timeout) retry();
	}

	@Override
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;
		int len = buf.readableBytes();
		file.write(buf);
		idleTime = Math.max(idleTime - len, 0);
		if (listener != null) listener.onProgress(this, len);
		onProgress(len);
	}

	@Override
	public final void channelClosed(ChannelCtx ctx) throws IOException {
		if (state >= SUCCESS) return;
		if (state != DISCONNECTED) retry();
	}

	@Override
	public final void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		DownloadTask.LOGGER.trace("子任务{}: 发生异常, 当前重试次数: {}", ex, this, retry);
		if (retry == 0) owner.exceptionCaught(ctx, ex);
		ctx.close();
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		String id = event.id;
		if (id.equals(HttpRequest.DOWNLOAD_EOF)) {
			if (event.getData() == Boolean.TRUE)
				success();
		}
	}

	abstract void onBeforeSend(HttpRequest client) throws Exception;
	abstract void onProgress(int count) throws IOException;
	abstract void onSuccess() throws IOException;

	void onClose() {}

	final void success() throws IOException {
		synchronized (client) {
			if (state >= SUCCESS) return;
			state = SUCCESS;
		}
		DownloadTask.LOGGER.trace("子任务{}: 成功", this);
		onSuccess();
		if (listener != null) listener.onSuccess(this);
		owner.onChunkSuccess();
		close();
	}

	int idleTime, retry;

	private void retry() throws IOException {
		if ((listener == null || !listener.isCancelled()) && retry-- > 0) {
			DownloadTask.LOGGER.trace("子任务{}: 正在重试, 还有{}次尝试", this, retry);
			idleTime = 0;

			synchronized (client) {
				if (state >= SUCCESS) { close(); return; }
				state = DISCONNECTED;
			}

			ch.close();
			DownloadTask.POOL.execute(this);
		} else {
			DownloadTask.LOGGER.trace("子任务{}: 无法重试, 失败", this);
			close();
		}
	}

	@Override
	public final void close() {
		boolean fail = false;
		synchronized (client) {
			if (state < SUCCESS) {
				state = FAILED;
				fail = true;
			}
		}
		if (fail) {
			DownloadTask.LOGGER.trace("子任务{}: 失败", this);
			owner.cancel();
		}

		IOUtil.closeSilently(ch);
		IOUtil.closeSilently(file);

		onClose();

		synchronized (client) { client.notifyAll(); }
	}

	public final boolean isDone() { return state >= SUCCESS; }

	@Override
	public final void run() {
		if (listener != null && listener.isCancelled()) { close(); return; }

		try {
			onBeforeSend(client);
			DownloadTask.LOGGER.trace("子任务{}: 开始运行", this);
			synchronized (client) {
				if (state >= SUCCESS) { close(); return; }
				state = CONNECTING;
			}

			MyChannel channel = ch;
			if (channel != null) channel.close();

			ch = channel = MyChannel.openTCP();
			channel.addLast("Downloader", this);

			client.attach(channel, DownloadTask.timeout);
			ServerLaunch.DEFAULT_LOOPER.register(channel, null);
		} catch (Exception e) {
			e.printStackTrace();
			close();
		}
	}

	@Override
	public String toString() {return "Downloader{State="+state+"/Progress="+getDownloaded()+"/"+getTotal()+"}";}

	static final class Chunked extends Downloader {
		private final RandomAccessFile progressFile;
		private long lastProgressWriteTime;

		private final long length;
		private long offset;
		long remaining;

		public Chunked(Source file, long offset, long length, int chunkId, File progress) throws IOException {
			super(file);
			this.length = length;

			if (progress != null) {
				progressFile = new RandomAccessFile(progress, "rw");
				progressFile.seek((long) chunkId << 3);

				long downloadedBytes = progressFile.readLong();
				offset += downloadedBytes;
				length -= downloadedBytes;

				DownloadTask.LOGGER.debug("子任务{}: 共{}/{}字节", chunkId, offset, length);
				if (length <= 0 || downloadedBytes < 0) {
					if (downloadedBytes > 0) writeProgress(-1);
					state = SUCCESS;
					progressFile.close();
					file.close();
					return;
				}
			} else {
				progressFile = null;
			}

			this.offset = offset;
			this.remaining = length;
		}

		long getDownloaded() {return length - remaining;}
		long getTotal() {return length;}

		@Override
		protected void onBeforeSend(HttpRequest q) throws IOException {
			file.seek(offset);
			q.header("range", "bytes="+offset+'-'+(offset + remaining - 1));
		}

		@Override
		void onProgress(int count) throws IOException {
			offset += count;
			remaining -= count;

			if (remaining <= 0) success();
			else writeProgress(length - remaining);
		}

		@Override
		void onSuccess() throws IOException {
			lastProgressWriteTime = 0;
			writeProgress(-1);
		}

		@Override
		void onClose() {
			if (progressFile != null) {
				try {
					if (state == FAILED) {
						lastProgressWriteTime = 0;
						writeProgress(length - remaining);
					}
				} catch (IOException ignored) {}
				IOUtil.closeSilently(progressFile);
			}
		}

		private void writeProgress(long progress) throws IOException {
			if (progressFile != null) {
				long t = System.currentTimeMillis();
				if (t - lastProgressWriteTime > 1000) {
					progressFile.seek(progressFile.getFilePointer() - 8);
					progressFile.writeLong(progress);
					lastProgressWriteTime = t;
				}
			}
		}
	}

	static final class Streaming extends Downloader {
		private long downloaded;

		Streaming(Source file) { super(file); }

		long getDownloaded() {return downloaded;}
		long getTotal() {return -1;}

		@Override void onBeforeSend(HttpRequest q) throws IOException {
			downloaded = 0;
			file.seek(0);
		}
		@Override void onProgress(int count) {downloaded += count;}
		@Override void onSuccess() throws IOException {file.setLength(file.position());}
	}
}