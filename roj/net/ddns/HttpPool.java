package roj.net.ddns;

import roj.collect.SimpleList;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.MyChannel;
import roj.net.ch.handler.Timeout;
import roj.net.http.HttpClient11;
import roj.net.http.IHttpClient;
import roj.net.http.SyncHttpClient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/1/28 0028 1:24
 */
public class HttpPool implements ChannelHandler {
	private final ConcurrentHashMap<MyChannel, InetSocketAddress> available = new ConcurrentHashMap<>();
	private final Semaphore parallel;
	private final int timeout;

	public HttpPool(int maxParallel, int timeout) {
		if (maxParallel <= 0) parallel = null;
		else parallel = new Semaphore(maxParallel);
		this.timeout = timeout;
	}

	public void closeAll() {
		for (MyChannel ch : new SimpleList<>(available.keySet())) {
			try {
				ch.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id == SyncHttpClient.SHC_DONE) {
			if (available.size() < 4) {
				event.setResult(Event.RESULT_DENY);
				available.put(ctx.channel(), (InetSocketAddress) ctx.channel().remoteAddress());
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		available.remove(ctx.channel());
		parallel.release();
	}

	public SyncHttpClient request(URL url, Consumer<IHttpClient> modifier) throws IOException {
		SyncHttpClient shc;

		MyChannel ch = null;
		if (!available.isEmpty()) {
			InetAddress host = InetAddress.getByName(url.getHost());
			int port = url.getPort() < 0 ? url.getDefaultPort() : url.getPort();

			Iterator<Map.Entry<MyChannel, InetSocketAddress>> itr = available.entrySet().iterator();
			while (itr.hasNext()) {
				Map.Entry<MyChannel, InetSocketAddress> entry = itr.next();
				if (port == entry.getValue().getPort() && host.equals(entry.getValue().getAddress())) {
					ch = entry.getKey();
					itr.remove();
					break;
				}
			}

			if (ch != null) {
				// reset SHC
				ChannelCtx ctx = ch.handler("syncer");
				shc = (SyncHttpClient) ctx.handler();
				shc.channelClosed(ctx);
				shc.handlerAdded(ctx);
				shc.channelOpened(ctx);

				// reset IHttpClient
				ctx = ch.handler("HH");
				IHttpClient hc = (IHttpClient) ctx.handler();
				hc.headers().clear();
				initClient(hc.url(url).body(null));
				if (modifier != null) modifier.accept(hc);

				ctx.handler().channelOpened(ctx);
				return shc;
			}
		}

		parallel.acquireUninterruptibly();

		HttpClient11 hc = new HttpClient11();
		initClient(hc.url(url));
		if (modifier != null) modifier.accept(hc);

		ch = MyChannel.openTCP();
		ch.addLast("HH", hc.asChannelHandler()).addLast("timer", new Timeout(timeout, 1000));

		shc = new SyncHttpClient();
		ch.addLast("syncer", shc).addLast("on_close", this);

		hc.connect(ch, timeout);
		IHttpClient.POLLER.register(ch, null);

		return shc;
	}

	protected void initClient(IHttpClient hc) {
		hc.urlPreEncoded = true;
		hc.header("User-Agent", "R-Aliyun4J");
	}
}