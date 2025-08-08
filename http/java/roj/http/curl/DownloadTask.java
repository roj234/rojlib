package roj.http.curl;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.concurrent.Executor;
import roj.concurrent.TaskPool;
import roj.http.AutoRedirect;
import roj.http.HttpHead;
import roj.http.HttpRequest;
import roj.http.hCE;
import roj.util.FastFailException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.net.*;
import roj.net.handler.Timeout;
import roj.text.DateFormat;

import java.io.*;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * @author solo6975
 * @since 2022/5/1 0:55
 */
public final class DownloadTask implements ChannelHandler, Runnable, Future<File> {
	public static final Executor QUERY = new TaskPool(0, 2, 50, 60000, "NIO连接器");

	public static int defChunkStart = 65536;
	public static int defMaxChunks = 16;
	public static int defMinChunkSize = 4096;
	public static boolean useETag = false;
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

	public static Future<File> download(String url, File file) throws IOException {
		return download(url, file, new DownloadListener.Multi());
	}

	/**
	 * 单线程下载文件
	 */
	public static Future<File> download(String url, File file, DownloadListener handler) throws IOException {
		if (file.isFile()) throw new IllegalStateException("文件已存在");

		DownloadTask ad = createTask(url, file, handler);
		ad.operation = STREAM_DOWNLOAD;
		QUERY.execute(ad);
		return ad;
	}

	public static Future<File> downloadMTD(String url, File file) throws IOException {
		return downloadMTD(url, file, new DownloadListener.Multi());
	}

	/**
	 * 多线程下载文件
	 */
	public static Future<File> downloadMTD(String url, File file, DownloadListener pg) throws IOException {
		if (file.isFile()) throw new IllegalStateException("文件已存在");

		DownloadTask ad = createTask(url, file, pg);
		QUERY.execute(ad);
		return ad;
	}

	public static DownloadTask createTask(String url, File file, DownloadListener pg) throws IOException {
		File parent = file.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建下载目录");

		File info = new File(file.getAbsolutePath() + ".nfo");
		if (info.isFile() && !IOUtil.isReallyWritable(info)) {
			throw new IOException("下载进度文件无法写入");
		}

		return new DownloadTask(URI.create(url), file, pg, info);
	}

	volatile Throwable ex;

	final File file;
	private final DownloadListener handler;
	private final File info;
	private final AtomicInteger done = new AtomicInteger();
	private long time;

	public int chunkStart = defChunkStart;
	public int maxChunks = defMaxChunks;
	public int minChunkSize = defMinChunkSize;
	public int retryCount = 3;
	public int maxRedirect = 5;
	public boolean ETag = useETag, checkTime = true;
	public boolean acceptDecompress;

	public int operation;
	public static final int NORMAL_DOWNLOAD = 0, STREAM_DOWNLOAD = 1;

	public final HttpRequest client;

	public DownloadTask(URI address, File file, DownloadListener handler, File info) {
		client = HttpRequest.builder().url(address).headers(defHeaders);
		this.file = file;
		this.handler = handler;
		this.info = info;
	}

	@Override
	public void run() {
		if (done.get() == -1) {
			guardedFinish();
			return;
		}

		MyChannel ch = null;
		try {
			client.header("User-Agent", userAgent).header("range", "bytes=0-");

			ch = MyChannel.openTCP();

			ch.addLast("Redirect", new AutoRedirect(client, Downloader.timeout, maxRedirect))
			  .addLast("Checker", this);

			client.attach(ch, Downloader.timeout);

			ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		} catch (Exception e) {
			ex = e;
			done.compareAndSet(0, -3);
			try {
				if (ch != null) ch.close();
			} catch (Exception ignored) {}
		}
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		ctx.channel().addBefore(ctx, "Timer", new Timeout(Downloader.timeout, 2000));

		HttpHead h = client.response();
		String lastMod = h.header("last-modified");
		if (!lastMod.isEmpty()) time = DateFormat.parseRFCDate(lastMod);

		long len = h.getContentLength();
		String encoding = null;
		if(!h.getContentEncoding().equals("identity")) {
			if (!acceptDecompress) len = -1;
			else encoding = h.getContentEncoding();
		}
		this.encoding = encoding;

		File tmpFile = new File(file.getAbsolutePath()+".tmp");
		if (len > 0) IOUtil.createSparseFile(tmpFile, len);

		try (RandomAccessFile raf = new RandomAccessFile(info, "rw")) {
			int chunks;
			if (chunkStart < 0 || len < chunkStart) chunks = len==0?0:1;
			else chunks = Math.min(maxChunks, (int) (len/minChunkSize));

			_continue: {
			_retry: {
				if (raf.length() <= 8) break _retry;
				if (raf.readLong() != len) break _retry;
				raf.seek((chunks+1) << 3);

				String s = raf.readUTF();
				if (!s.equals(h.header("encoding"))) break _retry;

				s = raf.readUTF();
				if (checkTime && !s.equals(h.header("last-modified"))) break _retry;

				s = raf.readUTF();
				if (ETag && !s.equals(h.header("etag"))) break _retry;

				break _continue;
			}

			raf.seek(0);
			raf.writeLong(len);
			raf.write(new byte[(chunks)<<3]);

			raf.writeUTF(h.header("encoding"));
			raf.writeUTF(h.header("last-modified"));
			raf.writeUTF(h.header("etag"));
			}
		}

		List<Downloader> tasks = new ArrayList<>();

		if (operation == STREAM_DOWNLOAD || len < 0 || (
			!"bytes".equals(h.header("accept-ranges")) &&
			h.header("content-range").isEmpty()
		)) {
			Source tmp = new FileSource(tmpFile);
			Downloader d = len < 0 ? new Streaming(tmp) : new Chunked(1, tmp, info,0, len);
			if (len < 0 || d.getRemain() > 0) {
				tasks = Collections.singletonList(d);
			}
		} else {
			int id = 1;
			long off = 0;

			if (chunkStart >= 0 && len >= chunkStart) {
				long each = Math.max(len / maxChunks, minChunkSize);
				while (len >= each) {
					Chunked d = new Chunked(id++, new FileSource(tmpFile), info, off, each);

					off += each;
					len -= each;

					if (len < each && len < minChunkSize) {
						// 如果下载完毕
						if (d.len > 0) d.len += len;
						len = 0;
					}

					if (d.getRemain() > 0) tasks.add(d);
				}
			}
			if (len > 0) {
				Chunked d = new Chunked(id, new FileSource(tmpFile), info, off, len);
				if (d.getRemain() > 0) done.incrementAndGet();
			}
		}

		if (tasks.isEmpty()) {
			done.set(-1);
			this.tasks = Collections.emptyList();
			QUERY.execute(this);
		} else {
			done.set(tasks.size());
			this.tasks = tasks;
			for (int i = 0; i < tasks.size(); i++) execute(tasks.get(i));
		}
	}
	private void execute(Downloader d) {
		d.owner = this;
		d.progress = handler;
		d.retry = retryCount;
		d.client = client.clone();
		if (handler != null) handler.onJoin(d);
		QUERY.execute(d);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		synchronized (this) { notifyAll(); }
	}
	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable e) throws Exception {
		ex = e; cancel();
	}
	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(Timeout.READ_TIMEOUT)) ex = new FastFailException("Read Timeout");
	}

	private List<Downloader> tasks;
	private String encoding;

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
		if (handler != null && handler.isClosed() || !tempFile.isFile()) {
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
					inf = hCE.checkWrap(header);
					inf.setInput(new byte[] {(byte) (header>>>8), (byte) header});
					in = new InflaterInputStream(in, inf, 1024);
				} else {
					throw new FastFailException("Unknown compress method " + encoding + " in " + tempFile);
				}
				try (FileOutputStream fos = new FileOutputStream(file)) {
					IOUtil.copyStream(in, fos);
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

	@Override
	public boolean isDone() {return done.get() < -1;}
	@Override
	public boolean isCancelled() {return done.get() == -4;}
	public boolean isSuccessful() {return done.get() == -2;}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {return cancel();}
	public boolean cancel() {
		if (ex == null) ex = new CancellationException();
		if (done.getAndSet(-4) == -4) return true;

		if (handler != null) handler.close();
		if (tasks != null) {
			for (int i = 0; i < tasks.size(); i++) {
				tasks.get(i).close();
			}
		}
		synchronized (this) {notifyAll();}
		return true;
	}

	@Override
	public File get() throws InterruptedException, ExecutionException {
		while (true) {
			if (ex != null) throw new ExecutionException(ex);
			if (done.get() < -1) break;
			synchronized (this) {wait();}
		}
		return file;
	}

	@Override
	public File get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (ex != null) throw new ExecutionException(ex);
		if (!isDone()) {
			synchronized (this) {wait(unit.toMillis(timeout));}
			if (ex != null) throw new ExecutionException(ex);
			if (!isDone()) throw new TimeoutException();
		}
		return file;
	}

	void onSubDone() {
		if (done.decrementAndGet() == 0) {
			if (done.compareAndSet(0, -1)) {
				QUERY.execute(this);
			}
		}
	}
}