package roj.net.http;

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.MyChannel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2022/11/19 0019 23:38
 */
public class HttpRequestPool extends RingBuffer<MyChannel> {
	private static final int MAX_ONE_SITE = 8, MAX_ALL = 256;
	// not need to be very precision
	private static final int KEEP_ALIVE = 60000;

	private static final Map<InetSocketAddress, HttpRequestPool> free = new MyHashMap<>();
	private static final Function<InetSocketAddress, HttpRequestPool> newRingBuffer = (s) -> new HttpRequestPool();
	private static final ReentrantLock globalLock = new ReentrantLock();
	private static int freeRequests;

	private final ReentrantLock lock = new ReentrantLock();

	HttpRequestPool() {
		super(1, MAX_ONE_SITE);
	}

	public static MyChannel getFor(URL url, int timeout) throws IOException {
		return getFor(new InetSocketAddress(url.getHost(), url.getPort() < 0 ? url.getDefaultPort() : url.getPort()), timeout);
	}

	public static MyChannel getFor(InetSocketAddress address, int timeout) throws IOException {
		MyChannel ch = null;

		globalLock.lock();
		HttpRequestPool pool = free.computeIfAbsent(address, newRingBuffer);
		globalLock.unlock();

		if (!pool.isEmpty()) {
			pool.lock.lock();
			try {
				if (!pool.isEmpty()) {
					ch = pool.removeFirst();
					Timer t = (Timer) ch.handler("Keep-Alive timer").handler();
					System.out.println("REUSE, remain="+t.remain + ",handlers=" + ch.handlers());
					t.remain = 0;
				}
			} finally {
				pool.lock.unlock();
			}
		}
		if (ch == null) {
			ch = MyChannel.openTCP();
			IHttpClient client = IHttpClient.create(IHttpClient.V1_1);
			client.connect(ch, timeout);
			ch.addLast("Keep-Alive timer", pool.new Timer());
			System.out.println("NEW");
		}

		return ch;
	}

	private final class Timer implements ChannelHandler {
		int remain;

		private Timer() {}

		@Override
		public void channelTick(ChannelCtx ctx) throws IOException {
			lock.lock();
			try {
				int r = remain-1;
				if (r == 0) {
					rm(ctx);
					ctx.close();
				} else if (r > 0) {
					remain = r;
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		public void onEvent(ChannelCtx ctx, Event event) {
			if (event.id == IHttpClient.DOWNLOAD_EOF && event.getData() == Boolean.TRUE) {
				globalLock.lock();
				lock.lock();
				if (freeRequests > 0) {
					freeRequests--;
					for (ChannelCtx ctx1 : ctx.channel().handlers()) {
						switch (ctx1.name) {
							case "HH":
							case "HCompr":
							case "HChunk":
							case "SSL":
							case "Keep-Alive timer": continue;
						}
						ctx.channel().remove(ctx1);
					}
					ringAddLast(ctx.channel());
					remain = KEEP_ALIVE;
				}
				lock.unlock();
				globalLock.unlock();
			}
		}

		public void channelClosed(ChannelCtx ctx) {
			rm(ctx);
		}

		private void rm(ChannelCtx ctx) {
			lock.lock();
			if (remain > 0) remove(ctx.channel());
			lock.unlock();
		}
	}

}
