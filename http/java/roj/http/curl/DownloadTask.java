package roj.http.curl;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.concurrent.TaskThread;
import roj.http.*;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.net.*;
import roj.net.handler.Timeout;
import roj.text.DateFormat;
import roj.text.logging.Logger;
import roj.util.FastFailException;

import java.io.*;
import java.net.URI;
import java.util.Collections;
import java.util.List;
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
	public static final Logger LOGGER = Logger.getLogger(DownloadTask.class.getSimpleName());

	static final TaskThread POOL = new TaskThread("RojLib - 多线程下载器");
	static {POOL.start();}

	public static int defChunkStart = 65536;
	public static int defMaxChunks = 16;
	public static int defMinChunkSize = 65536;
	public static boolean useETag = false;
	public static Headers defHeaders = new Headers(HttpRequest.DEFAULT_HEADERS);
	public static int defMaxRedirect = Integer.getInteger("http.maxRedirects", 20);
	public static int timeout = 10000;

	public static Future<File> downloadSingleThreaded(String url, File file) throws IOException {return downloadSingleThreaded(url, file, new DownloadListener.Single());}
	/**
	 * 单线程下载文件
	 */
	public static Future<File> downloadSingleThreaded(String url, File file, DownloadListener listener) throws IOException {
		var task = createTask(url, file, listener);
		task.chunkStart = Integer.MAX_VALUE;
		task.run();
		return task;
	}

	public static Future<File> download(String url, File file) throws IOException {return download(url, file, new DownloadListener.Single());}
	/**
	 * 多线程下载文件
	 */
	public static Future<File> download(String url, File file, DownloadListener listener) throws IOException {
		var task = createTask(url, file, listener);
		task.run();
		return task;
	}

	public static DownloadTask createTask(String url, File file, DownloadListener pg) throws IOException {
		if (file.isFile()) throw new IllegalStateException("文件已存在");

		File parent = file.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建下载目录");

		File info = new File(file.getAbsolutePath()+".nfo");
		if (info.isFile() && !IOUtil.isReallyWritable(info)) {
			throw new IOException("下载进度文件无法写入");
		}

		return new DownloadTask(URI.create(url), file, pg, info);
	}

	volatile Throwable error;

	final File file;
	private final DownloadListener handler;
	private final File info;
	private final AtomicInteger done = new AtomicInteger();
	private long time;

	public int chunkStart = defChunkStart;
	public int maxChunks = defMaxChunks;
	public int minChunkSize = defMinChunkSize;
	public int retryCount = 5;
	public int maxRedirect = defMaxRedirect;
	public boolean ETag = useETag, checkTime = true;
	public boolean allowDecompressAfterDownload;
	public final HttpRequest client;

	public DownloadTask(URI address, File file, DownloadListener handler, File info) {
		client = HttpRequest.builder().url(address).headers(defHeaders);
		this.file = file;
		this.handler = handler;
		this.info = info;
	}

	@Override
	public void run() {
		MyChannel ch = null;
		try {
			client.header("range", "bytes=0-");

			ch = MyChannel.openTCP();
			ch.addLast("Redirect", new AutoRedirect(client, timeout, maxRedirect))
			  .addLast("Checker", this);

			client.attach(ch, timeout);

			ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		} catch (Exception e) {
			error = e;
			done.compareAndSet(0, -3);
			IOUtil.closeSilently(ch);
		}
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		HttpHead head = client.response();
		int code = head.getCode();
		if (code < 200 || code > 299) throw new FileNotFoundException("远程返回: "+head);

		LOGGER.debug("{}: 收到初始响应头 {}", this, head);

		String lastMod = head.header("last-modified");
		if (!lastMod.isEmpty()) time = DateFormat.parseRFC5322Datetime(lastMod);

		long len = head.getContentLength();
		String encoding = null;
		if(!head.getContentEncoding().equals("identity")) {
			if (!allowDecompressAfterDownload) len = -1;
			else encoding = head.getContentEncoding();
		}
		this.encoding = encoding;

		File tmpFile = new File(file.getAbsolutePath()+".tmp");
		if (len > 0) IOUtil.createSparseFile(tmpFile, len);

		try (var raf = new RandomAccessFile(info, "rw")) {
			int chunks;
			if (chunkStart < 0 || len < chunkStart) chunks = len==0?0:1;
			else chunks = Math.min(maxChunks, (int) (len/minChunkSize));

			_continue: {
			_retry: {
				if (raf.length() <= 8) break _retry;
				if (raf.readLong() != len) break _retry;
				raf.seek((long) (chunks + 1) << 3);

				String s = raf.readUTF();
				if (!s.equals(head.header("encoding"))) break _retry;

				s = raf.readUTF();
				if (checkTime && !s.equals(head.header("last-modified"))) break _retry;

				s = raf.readUTF();
				if (ETag && !s.equals(head.header("etag"))) break _retry;

				break _continue;
			}

			LOGGER.debug("{}: 需要从头开始", this);
			raf.seek(0);
			raf.writeLong(len);
			raf.write(new byte[(chunks)<<3]);

			raf.writeUTF(head.header("encoding"));
			raf.writeUTF(head.header("last-modified"));
			raf.writeUTF(head.header("etag"));
			}
		}

		List<Downloader> tasks = new ArrayList<>();

		if (len < 0 || !head.header("content-range").startsWith("bytes ")) {
			LOGGER.debug("{}: 单线程模式", this);
			Source tmp = new FileSource(tmpFile);
			Downloader task = len < 0 ? new Downloader.Streaming(tmp) : new Downloader.Chunked(tmp, 0, len, 1, info);
			if (len < 0 || !task.isDone()) {
				tasks = Collections.singletonList(task);
			}
		} else {
			int id = 1;
			long off = 0;

			if (chunkStart >= 0 && len >= chunkStart) {
				long each = Math.max(len / maxChunks, minChunkSize);
				while (len >= each) {
					var task = new Downloader.Chunked(new FileSource(tmpFile), off, each, id++, info);

					off += each;
					len -= each;

					if (len < each && len < minChunkSize) {
						// 如果下载完毕
						if (task.remaining > 0) task.remaining += len;
						len = 0;
					}

					if (!task.isDone()) tasks.add(task);
				}
			}
			if (len > 0) {
				var task = new Downloader.Chunked(new FileSource(tmpFile), off, len, id, info);
				if (!task.isDone()) tasks.add(task);
			}
			LOGGER.debug("{}: 多线程模式()", this, tasks.size());
		}

		if (tasks.isEmpty()) {
			done.set(-1);
			this.tasks = Collections.emptyList();
			guardedFinish();
		} else {
			done.set(tasks.size());
			this.tasks = tasks;
			for (int i = 0; i < tasks.size(); i++) {
				var task = tasks.get(i);
				task.owner = this;
				task.listener = handler;
				task.retry = retryCount;
				task.client = client.clone();
				if (handler != null) handler.onStart(task);
				POOL.execute(task);
			}
		}

		ctx.close();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		synchronized (this) { notifyAll(); }
	}
	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable e) throws Exception {
		error = e;
		cancel();
	}
	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(Timeout.READ_TIMEOUT)) error = new FastFailException("Read Timeout");
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
		LOGGER.debug("{}: 任务完成", this);

		File tempFile = new File(file.getAbsolutePath()+".tmp");
		if (handler != null && handler.isCancelled() || !tempFile.isFile()) {
			throw new FastFailException("下载失败/Shutdown||NoFile");
		}

		if (encoding != null) {
			LOGGER.debug("{}: 正在解压文件", this);
			try (var fis = new FileInputStream(tempFile)) {
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
					throw new FastFailException("Unknown compress method "+encoding+" in "+tempFile);
				}
				try (var fos = new FileOutputStream(file)) {
					IOUtil.copyStream(in, fos);
				} finally {
					if (inf != null) inf.end();
				}
				tempFile.delete();
			} catch (IOException e) {
				throw new FastFailException(e.getMessage());
			}
			LOGGER.debug("{}: 解压完成", this);
		}

		String err = null;

		File info = new File(file.getAbsolutePath()+".nfo");
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
		if (error == null) error = new CancellationException();
		if (done.getAndSet(-4) == -4) return true;

		if (handler != null) handler.cancel();
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
			mayThrow();
			if (done.get() < -1) break;
			synchronized (this) {wait();}
		}
		return file;
	}

	@Override
	public File get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		mayThrow();
		if (!isDone()) {
			synchronized (this) {wait(unit.toMillis(timeout));}
			mayThrow();
			if (!isDone()) throw new TimeoutException();
		}
		return file;
	}

	private void mayThrow() throws ExecutionException {
		if (error != null) throw new ExecutionException(client.url()+"下载失败", error);
	}

	void onChunkSuccess() {
		if (done.decrementAndGet() == 0) {
			if (done.compareAndSet(0, -1)) {
				POOL.execute(this::guardedFinish);
			}
		}
	}
}