package roj.http.curl;

import roj.concurrent.ITask;
import roj.http.HttpHead;
import roj.http.HttpRequest;
import roj.io.source.Source;
import roj.net.*;
import roj.util.DynByteBuf;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/2/28 21:49
 */
abstract class Downloader implements ITask, Closeable, ChannelHandler {
	// todo 支持HTTP2.0后移走
	public static int timeout = 10000;

	Downloader(Source file) { this.file = file; }

	final Source file;
	HttpRequest client;
	MyChannel ch;
	DownloadTask owner;
	IProgress progress;

	volatile byte state;
	static final byte DISCONNECTED = 0, CONNECTING = 1, DOWNLOADING = 2, SUCCESS = 3, FAILED = 4;

	long begin = System.currentTimeMillis();

	abstract long getDownloaded();
	abstract long getRemain();
	abstract long getTotal();
	abstract long getAverageSpeed();
	abstract int getDelta();

	@Override
	public final void channelOpened(ChannelCtx ctx) throws IOException {
		HttpHead header = client.response();
		int code = header.getCode();
		if (code < 200 || code > 299) {
			throw new FileNotFoundException("远程返回: " + header);
		}
	}

	@Override
	public final void channelTick(ChannelCtx ctx) throws IOException {
		if (progress != null && progress.wasShutdown()) close();
		if (++idle > timeout) retry();
	}

	@Override
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		idle = 0;

		int len = buf.readableBytes();
		try {
			file.write(buf);
		} finally {
			if (progress != null) progress.onChange(this);

			onUpdate(len);
		}
	}

	@Override
	public final void channelClosed(ChannelCtx ctx) throws IOException {
		if (state >= SUCCESS) return;
		if (state != DISCONNECTED) retry();
	}

	@Override
	public final void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		if (retry == 0) {
			if (owner.ex == null)
				owner.ex = ex;
			ex.printStackTrace();
		}
		ctx.close();
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		String id = event.id;
		if (id.equals(HttpRequest.DOWNLOAD_EOF)) {
			if (event.getData() == Boolean.TRUE)
				done();
		}
	}

	abstract void onBeforeSend(HttpRequest client) throws Exception;
	abstract void onUpdate(int r) throws IOException;
	abstract void onDone() throws IOException;

	void onClose() {}

	final void done() throws IOException {
		synchronized (client) {
			if (state >= SUCCESS) return;
			state = SUCCESS;
		}
		onDone();
		if (progress != null) progress.onFinish(this);
		owner.onSubDone();
		close();
	}

	int idle, retry;

	private void retry() throws IOException {
		idle = 0;

		if ((progress == null || !progress.wasShutdown()) && retry-- > 0) {
			synchronized (client) {
				if (state >= SUCCESS) {
					close();
					return;
				}
				state = DISCONNECTED;
			}

			ch.close();
			DownloadTask.QUERY.submit(this);
		} else {
			if (progress != null) progress.shutdown();
			close();
		}
	}

	public final void waitFor() throws InterruptedException {
		synchronized (client) {
			while (state < SUCCESS) client.wait();
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
		if (fail) owner.cancel();

		if (ch != null) {
			try {
				ch.close();
			} catch (IOException ignored) {}
		}

		try {
			file.close();
		} catch (IOException ignored) {}

		onClose();

		synchronized (client) {
			client.notifyAll();
		}
	}

	public final boolean isDone() { return state >= SUCCESS; }

	@Override
	public final void execute() throws Exception {
		if (progress != null && progress.wasShutdown()) {
			close();
			return;
		}

		try {
			onBeforeSend(client);
			synchronized (client) {
				if (state >= SUCCESS) {
					close();
					return;
				}
				state = CONNECTING;
			}

			MyChannel ctx = ch;
			if (ctx != null) ctx.close();

			ch = ctx = MyChannel.openTCP();
			ctx.addLast("Downloader", this);

			client.connect(ctx, timeout);
			ServerLaunch.DEFAULT_LOOPER.register(ctx, null);
		} catch (Exception e) {
			e.printStackTrace();
			close();
		}
	}

	@Override
	public String toString() {
		return "D{Ste="+state+"/Fin="+getDownloaded()+"/Rem="+getRemain()+"/Tot="+getTotal()+"}";
	}
}