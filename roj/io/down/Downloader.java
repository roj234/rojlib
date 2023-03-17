package roj.io.down;

import roj.concurrent.task.ITask;
import roj.io.FileUtil;
import roj.io.source.Source;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.MyChannel;
import roj.net.http.HttpHead;
import roj.net.http.IHttpClient;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

/**
 * @author Roj233
 * @since 2022/2/28 21:49
 */
abstract class Downloader implements ITask, Closeable, ChannelHandler {
	Downloader(Source file, URL url) {
		this.file = file;
		client = IHttpClient.create(IHttpClient.V1_1).url(url)
							.method("GET").header("User-Agent", DownloadTask.userAgent);
	}

	final Source file;
	final IHttpClient client;
	MyChannel ch;
	DownloadTask owner;
	//Throwable ex;

	volatile byte state;
	static final byte DISCONNECTED = 0, CONNECTING = 1, DOWNLOADING = 2, SUCCESS = 3, FAILED = 4;

	IProgress progress;

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
		if (++idle > FileUtil.timeout) close();
	}

	@Override
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		idle = 0;

		int len = buf.readableBytes();

		DynByteBuf arrayBuf = buf.hasArray() ? buf : ctx.allocate(false, buf.readableBytes());
		if (buf != arrayBuf) arrayBuf.put(buf);

		try {
			file.write(arrayBuf.array(), arrayBuf.arrayOffset() + arrayBuf.rIndex, arrayBuf.readableBytes());
		} finally {
			if (arrayBuf != buf) ctx.reserve(arrayBuf);
			buf.rIndex = buf.wIndex();

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
		NamespaceKey id = event.id;
		if (id.equals(IHttpClient.DOWNLOAD_EOF)) {
			done();
		}
	}

	abstract void onBeforeSend(IHttpClient client) throws Exception;
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

		IHttpClient client = this.client;
		if ((progress == null || !progress.wasShutdown()) && retry-- > 0 && client != null) {
			synchronized (client) {
				if (state >= SUCCESS) {
					close();
					return;
				}
				state = DISCONNECTED;
			}

			ch.close();
			DownloadTask.QUERY.pushTask(this);
		} else {
			if (progress != null) progress.shutdown();
			close();
		}
	}

	public final void waitFor() throws InterruptedException {
		IHttpClient client = this.client;
		synchronized (client) {
			while (state < SUCCESS) {
				client.wait();
			}
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

	public final boolean isDone() {
		return state >= SUCCESS;
	}

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
			if (ctx == null || !ctx.isOpen() || ctx.isClosePending()) {
				ch = ctx = MyChannel.openTCP();
			} else {
				ctx.disconnect();
				ctx.removeAll();
			}
			ctx.addLast("HH", client.asChannelHandler())
			   .addLast("Downloader", this);

			client.connect(ctx, FileUtil.timeout);
			IHttpClient.POLLER.register(ctx, null);
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
