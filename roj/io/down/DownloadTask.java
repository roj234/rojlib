package roj.io.down;

import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.Waitable;
import roj.concurrent.task.ITask;
import roj.io.FastFailException;
import roj.io.FileUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.MyChannel;
import roj.net.ch.handler.Timeout;
import roj.net.http.HttpClient11;
import roj.net.http.HttpHead;
import roj.net.http.IHttpClient;
import roj.text.ACalendar;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author solo6975
 * @since 2022/5/1 0:55
 */
public final class DownloadTask implements ChannelHandler, ITask, Waitable {
	public static final TaskHandler QUERY = new TaskPool(0, 2, 50, 60000, "NIO连接器");

	public static int defChunkStart = 1024 * 512;
	public static int defMaxChunks = 16;
	public static int defMinChunkSize = 1024;
	public static boolean defETag = false;
	public static String userAgent;
	public static Map<String, String> defHeaders = Collections.emptyMap();
	static {
		String version = System.getProperty("java.version");
		String agent = System.getProperty("http.agent");
		if (agent == null) {
			agent = "Java/" + version;
		} else {
			agent = agent + " Java/" + version;
		}
		userAgent = agent;
	}

	public static Waitable download(String url, File file) throws IOException {
		return download(url, file, new ProgressMulti());
	}

	/**
	 * 单线程下载文件
	 */
	public static Waitable download(String url, File file, IProgress handler) throws IOException {
		if (file.isFile()) return new FileUtil.ImmediateFuture();

		DownloadTask ad = createTask(url, file, handler);
		ad.operation = STREAM_DOWNLOAD;
		QUERY.pushTask(ad);
		return ad;
	}

	public static Waitable downloadMTD(String url, File file) throws IOException {
		return downloadMTD(url, file, new ProgressMulti());
	}

	/**
	 * 多线程下载文件
	 */
	public static Waitable downloadMTD(String url, File file, IProgress pg) throws IOException {
		if (file.isFile()) return new FileUtil.ImmediateFuture();

		DownloadTask ad = createTask(url, file, pg);
		QUERY.pushTask(ad);
		return ad;
	}

	public static DownloadTask createTask(String url, File file, IProgress pg) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建下载目录");

		File info = new File(file.getAbsolutePath() + ".nfo");
		if (info.isFile() && !FileUtil.checkTotalWritePermission(info)) {
			throw new IOException("下载进度文件无法写入");
		}

		return new DownloadTask(new URL(url), file, pg, info);
	}

	volatile Throwable ex;

	final File file;
	private final IProgress handler;
	private final File info;
	private final AtomicInteger done = new AtomicInteger();
	private long time;

	public int chunkStart = defChunkStart;
	public int maxChunks = defMaxChunks;
	public int minChunkSize = defMinChunkSize;
	public int retryCount = 3;
	public int maxRedirect = 5;
	public boolean ETag = defETag, checkTime = true;
	public boolean acceptDecompress;

	public Map<String, String> headers = defHeaders;

	public int operation;
	public static final int NORMAL_DOWNLOAD = 0, STREAM_DOWNLOAD = 1, REDIRECT_ONLY = 2;

	private final IHttpClient client = IHttpClient.create(IHttpClient.V1_1);
	private MyChannel ch;

	public DownloadTask(URL address, File file, IProgress handler, File info) {
		client.url(address);
		this.file = file;
		this.handler = handler;
		this.info = info;
	}

	public URL getURL() {
		return client.url();
	}

	public IHttpClient getClient() {
		return client;
	}

	@Override
	public void execute() {
		try {
			if (done.get() == -1) {
				guardedFinish();
				return;
			}

			client.header("User-Agent", userAgent);
			client.headers(headers, false);

			MyChannel ctx = ch;
			if (ctx == null || !ctx.isOpen()) {
				ch = ctx = MyChannel.openTCP();
			} else {
				ctx.disconnect();
			}

			ctx.removeAll();
			ctx.addLast("h11@client", client.asChannelHandler())
			   .addLast("Timer", new Timeout(FileUtil.timeout, 2000))
			   .addLast("Checker", this);

			client.connect(ctx, FileUtil.timeout);

			IHttpClient.POLLER.register(ctx, null);
		} catch (Exception e) {
			ex = e;
			try {
				ch.close();
			} catch (Exception ignored) {}
		}
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		Timeout h = (Timeout) ctx.channel().handler("Timer").handler();
		h.lastRead = System.currentTimeMillis();

		HttpHead header = client.response();
		int code = header.getCode();
		if (code >= 200 && code < 400) {
			String location = header.get("Location");
			if (location != null) {
				if (maxRedirect-- < 0) throw new FastFailException("重定向过多");
				client.url(new URL(location));
				QUERY.pushTask(this);
				return;
			} else if (code >= 300) {
				throw new FastFailException("远程返回状态码: " + header);
			}

			done.set(0);
			beginDownload();
			ctx.close();
		} else {
			throw new FastFailException("远程返回状态码: " + header);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		synchronized (this) {
			notifyAll();
		}
	}

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		this.ex = ex;
		ex.printStackTrace();
		cancel();
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id == Timeout.READ_TIMEOUT) {
			ex = new FastFailException("Read Timeout");
		}
	}

	private void beginDownload() throws IOException {
		if (operation == REDIRECT_ONLY) {
			tasks = Collections.emptyList();
			return;
		}

		HttpHead conn = client.response();
		String lastMod = conn.getHeaderField("last-modified");
		if (!lastMod.isEmpty()) time = ACalendar.parseRFCDate(lastMod);

		long len = conn.getContentLengthLong();
		String encoding = null;
		if(!conn.getContentEncoding().equals("identity")) {
			if (!acceptDecompress) len = -1;
			else encoding = conn.getContentEncoding();
		}
		this.encoding = encoding;

		File tmpFile = new File(file.getAbsolutePath() + ".tmp");
		if (len > 0) FileUtil.allocSparseFile(tmpFile, len);

		if (operation == STREAM_DOWNLOAD || len < 0 || !"bytes".equals(conn.getHeaderField("accept-ranges"))) {
			Source tmp = new FileSource(tmpFile);
			Downloader d = len < 0 ? new Streaming(tmp, client.url(), handler) : new Chunked(0, tmp, info, client.url(), 0, len, handler);
			if (len < 0 || d.getRemain() > 0) {
				done.incrementAndGet();
				d.owner = this;
				d.retry = retryCount;
				d.client.headers(headers, false);

				QUERY.pushTask(d);
			}
			if (done.get() == 0) {
				tasks = Collections.emptyList();
				guardedFinish();
			} else {
				tasks = Collections.singletonList(d);
			}
			return;
		}

		File info = new File(file.getAbsolutePath() + ".nfo");
		try (RandomAccessFile raf = new RandomAccessFile(info, "rw")) {
			int chk;
			if (chunkStart < 0 || len < chunkStart) {
				chk = len==0?0:1;
			} else {
				chk = Math.min(maxChunks, (int) (len/minChunkSize));
			}

			reset: {
				canContinue: {
					if (raf.length() <= 8) break canContinue;
					if (raf.readLong() != len) break canContinue;
					raf.seek((chk + 1) << 3);

					String s = raf.readUTF();
					if (!s.equals(conn.getHeaderField("encoding"))) break canContinue;

					s = raf.readUTF();
					if (checkTime && !s.equals(conn.getHeaderField("last-modified"))) break canContinue;

					s = raf.readUTF();
					if (ETag && !s.equals(conn.getHeaderField("etag"))) break canContinue;

					break reset;
				}

				raf.seek(0);
				raf.writeLong(len);
				raf.write(new byte[(chk)<<3]);

				raf.writeUTF(conn.getHeaderField("encoding"));
				raf.writeUTF(conn.getHeaderField("last-modified"));
				raf.writeUTF(conn.getHeaderField("etag"));
			}
		}

		int id = 1;
		long off = 0;

		List<Downloader> tasks = new ArrayList<>(maxChunks);
		URL url = client.url();
		if (chunkStart >= 0 && len >= chunkStart) {
			long each = Math.max(len / maxChunks, minChunkSize);
			while (len >= each) {
				Chunked d = new Chunked(id++, new FileSource(tmpFile), info, url, off, each, handler);

				off += each;
				len -= each;

				if (len < each && len < minChunkSize) {
					// 如果下载完毕
					if (d.len > 0) d.len += len;
					len = 0;
				}

				if (d.getRemain() > 0) {
					done.incrementAndGet();
					d.owner = this;
					d.retry = retryCount;
					d.client.headers(headers, false);
					QUERY.pushTask(d);
					tasks.add(d);
				}
			}
		}
		if (len > 0) {
			Chunked d = new Chunked(id, new FileSource(tmpFile), info, url, off, len, handler);
			if (d.getRemain() > 0) {
				done.incrementAndGet();
				d.owner = this;
				d.retry = retryCount;
				d.client.headers(headers, false);
				QUERY.pushTask(d);
				tasks.add(d);
			}
		}

		if (done.get() == 0) {
			this.tasks = Collections.emptyList();
			guardedFinish();
		} else {
			this.tasks = tasks;
		}
	}

	private List<Downloader> tasks;
	private String encoding;

	@Override
	public void waitFor() throws IOException {
		while (true) {
			if (ex != null) {
				IOException e = new IOException(ex);
				e.setStackTrace(new StackTraceElement[0]);
				throw e;
			}

			if (done.get() < -1) break;
			synchronized (this) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new IOException(e);
				}
			}
		}
	}

	private void guardedFinish() {
		try {
			finish();
			done.set(-2);
		} catch (Exception e) {
			done.set(-3);
			throw e;
		} finally {
			synchronized (this) {
				notifyAll();
			}
		}
	}
	private void finish() {
		File tempFile = new File(file.getAbsolutePath() + ".tmp");
		if (handler != null && handler.wasShutdown() || !tempFile.isFile()) {
			throw new FastFailException("下载失败/Shutdown||NoFile");
		}

		if (encoding != null) {
			System.out.println("正在解压文件...");
			try (FileInputStream fis = new FileInputStream(tempFile)) {
				InputStream in = fis;
				Inflater inf = null;
				if (encoding.equalsIgnoreCase("gzip")) {
					in = new GZIPInputStream(in, 1024);
				} else if (encoding.equalsIgnoreCase("deflate")) {
					int header = (fis.read() << 8) | fis.read();
					inf = HttpClient11.checkWrap(header);
					inf.setInput(new byte[] {(byte) (header>>>8), (byte) header});
					in = new InflaterInputStream(in, inf, 1024);
				} else {
					throw new FastFailException("Unknown compress method " + encoding + " in " + tempFile);
				}
				try (FileOutputStream fos = new FileOutputStream(file)) {
					FileUtil.copyStream(in, fos);
				} finally {
					if (inf != null) inf.end();
				}
				tempFile.delete();
			} catch (IOException e) {
				throw new FastFailException(e.getMessage());
			}
			System.out.println("解压完成...");
		}

		String err = null;

		File info = new File(file.getAbsolutePath() + ".nfo");
		for (int i = 0; i < 3; i++) {
			err = null;

			if (encoding == null) {
				if (file.isFile()) {
					err = "文件已被另一个线程完成(请检查调用代码). " + file.getName();
				} else if (!tempFile.renameTo(file)) {
					err = "文件重命名失败." + tempFile.getName();
				}
			}
			if (info.isFile() && !info.delete()) {
				info.deleteOnExit();
				err = "下载进度删除失败.";
			} else {
				break;
			}

			LockSupport.parkNanos(2_000_000);
		}

		if (err != null) throw new FastFailException(err);

		if (time != 0) file.setLastModified(time);
	}

	public boolean isSuccessful() {
		return done.get() == -2;
	}

	@Override
	public boolean isDone() {
		return done.get() < -1;
	}

	@Override
	public void cancel() {
		if (ex == null) ex = new CancellationException();
		if (done.getAndSet(-4) == -4) return;

		try {
			if (ch != null) ch.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (handler != null) handler.shutdown();
		if (tasks != null) {
			for (int i = 0; i < tasks.size(); i++) {
				tasks.get(i).close();
			}
		}
		synchronized (this) {
			notifyAll();
		}
	}

	void onSubDone() {
		if (done.decrementAndGet() == 0) {
			if (done.compareAndSet(0, -1)) {
				QUERY.pushTask(this);
			}
		}
	}
}
