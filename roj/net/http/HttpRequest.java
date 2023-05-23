package roj.net.http;

import roj.NativeLibrary;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.net.ch.*;
import roj.net.ch.handler.JSslClient;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.text.CharList;
import roj.util.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.ClosedByInterruptException;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class HttpRequest {
	public static int DEFAULT_TIMEOUT = 10000;
	public static final Headers DEFAULT_HEADERS = new Headers();
	static {
		DEFAULT_HEADERS.put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8");
		DEFAULT_HEADERS.put("connection", "keep-alive");
		DEFAULT_HEADERS.put("user-agent", "Java/1.8.0_201");
		DEFAULT_HEADERS.put("accept-encoding", "gzip, deflate");
	}

	// region Query parameter
	private String action = DefGet;
	private static final String DefGet = new String("GET");

	private String protocol, site, path = "/";
	private volatile Object query;

	private Object body;
	private byte bodyType;

	private Headers headers;
	private final SimpleList<Map.Entry<String, String>> addHeader = new SimpleList<>(4);

	InetSocketAddress _address;

	public HttpRequest() { this(true); }
	public HttpRequest(boolean inherit) {
		headers = inherit ? new Headers(DEFAULT_HEADERS) : new Headers();
	}

	public final HttpRequest method(String type) {
		if (Action.valueOf(type) < 0) throw new IllegalArgumentException(type);
		action = type;
		return this;
	}
	public final String method() { return action; }

	public final HttpRequest header(CharSequence k, String v) { headers.put(k, v); return this; }
	public final HttpRequest headers(Map<String, String> map) { headers.putAll(map); return this; }
	public final HttpRequest headers(Map<String, String> map, boolean clear) {
		if (clear) headers.clear();
		headers.putAll(map);
		return this;
	}

	public final HttpRequest body(DynByteBuf b) { return setBody(b,0); }
	public final HttpRequest bodyG1(Function<ChannelCtx, Boolean> b) { return setBody(b,1); }
	public final HttpRequest bodyG3(Supplier<InputStream> b) { return setBody(b,2); }
	private HttpRequest setBody(Object b, int i) {
		if (action == DefGet) action = "POST";

		body = b;
		bodyType = (byte) i;
		return this;
	}
	public final Object body() { return body; }

	public final HttpRequest url(URL url) {
		this.protocol = url.getProtocol().toLowerCase();

		String host = url.getHost();
		if (url.getPort() >= 0) host += ":"+url.getPort();
		this.site = host;

		this.path = url.getPath();
		this.query = url.getQuery();

		_address = null;
		return this;
	}
	public final HttpRequest url(String protocol, String site, String path) { return url(protocol, site, path, null); }

	/**
	 * @param protocol http/https
	 * @param site 站点(Host|Address)
	 * @param path 路径
	 * @param query 不以?开始的请求参数
	 */
	public final HttpRequest url(String protocol, String site, String path, String query) {
		this.protocol = protocol.toLowerCase();
		this.site = site;
		if (!path.startsWith("/")) throw new IllegalArgumentException("path must start with /");
		this.path = path;
		this.query = query;

		_address = null;
		return this;
	}
	public final HttpRequest query(Map<String, String> q) { query = q; return this; }
	public final HttpRequest query(List<Map.Entry<String, String>> q) { query = q; return this; }
	public final HttpRequest query(String q) { query = q; return this; }

	public final URL url() {
		String site = this.site;
		int port = site.lastIndexOf(':');
		if (port > 0) {
			site = site.substring(0, port);
			port = Integer.parseInt(this.site.substring(port+1));
		}
		try {
			return new URL(protocol, site, port, _appendPath(new CharList()).toStringAndFree());
		} catch (MalformedURLException e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}
	// endregion

	public boolean connect(MyChannel ch, int timeout) throws IOException {
		ch.addFirst("h11@client", (ChannelHandler) this);

		ch.remove("h11@tls");
		if ("https".equals(protocol)) {
			// todo: ALPN and HTTP/2
			ch.addFirst("h11@tls", NativeLibrary.IN_DEV ? new MSSCipher().sslMode() : new JSslClient());
		}

		return ch.connect(_getAddress(), timeout);
	}

	public void _redirect(MyChannel ch, URL url, int timeout) throws IOException {
		InetSocketAddress prev = _address;
		InetSocketAddress addr = url(url)._getAddress();

		if (addr.equals(prev)) {
			ChannelCtx h = ch.handler("h11@client");
			h.handler().channelOpened(h);
		} else {
			// 暂时没法放回去..
			ChannelCtx cc = ch.handler("h11@pool");
			if (cc != null) {
				cc.handler().channelClosed(cc);
				ch.remove(cc);
			}

			if (ch.isOpen()) {
				ch.disconnect();
			} else {
				MyChannel ch1 = MyChannel.openTCP();
				ch1.movePipeFrom(ch);
				ch.close();
				ch = ch1;
			}
			ch.connect(_address, timeout);

			POLLER.register(ch, null);
		}
	}

	// region execute simple
	public final SyncHttpClient executeThreadSafe() throws IOException { return clone().execute(); }
	public final SyncHttpClient executeThreadSafe(int timeout) throws IOException { return clone().execute(timeout); }

	public final SyncHttpClient execute() throws IOException { return execute(DEFAULT_TIMEOUT); }
	public final SyncHttpClient execute(int timeout) throws IOException {
		MyChannel ch = MyChannel.openTCP();
		SyncHttpClient client = new SyncHttpClient();
		ch.addLast("h11@timer", new Timeout(timeout, 1000))
		  .addLast("h11@merger", client);
		connect(ch, timeout);
		POLLER.register(ch, null);
		return client;
	}

	public final SyncHttpClient executePooled() throws IOException { return executePooled(DEFAULT_TIMEOUT); }
	public final SyncHttpClient executePooled(int timeout) throws IOException { return executePooled(timeout, 1); }
	public final SyncHttpClient executePooled(int timeout, int maxRedirect) throws IOException { return executePooled(timeout, maxRedirect, 0); }
	public final SyncHttpClient executePooled(int timeout, int maxRedirect, int maxRetry) throws IOException {
		SyncHttpClient client = new SyncHttpClient();

		Pool man = pool.computeIfAbsent(_getAddress(), fn);
		man.executePooled(this, client, timeout, new AutoRedirect(this, timeout, maxRedirect, maxRetry));

		return client;
	}
	// endregion
	// region Internal
	final InetSocketAddress _getAddress() throws IOException {
		if (_address != null) return _address;

		int port = site.lastIndexOf(':');
		InetAddress host = InetAddress.getByName(port < 0 ? site : site.substring(0, port));
		InetSocketAddress addr;
		if (port < 0) {
			switch (protocol) {
				case "https": port = 443; break;
				case "http": port = 80; break;
				case "ftp": port = 21; break;
				default: throw new IOException("Unknown protocol");
			}
		} else {
			port = Integer.parseInt(site.substring(port+1));
		}

		return _address = new InetSocketAddress(host, port);
	}
	@SuppressWarnings("unchecked")
	final <T extends Appendable&CharSequence> T _appendPath(T sb) {
		try {
			sb.append(path.isEmpty() ? "/" : path);
			if (query == null) return sb;

			sb.append('?');
			if (query instanceof String) return (T) sb.append(query.toString());

			int begin = sb.length();
			Iterable<Map.Entry<String, String>> q;
			if (query instanceof List) q = Helpers.cast(query);
			else if (query instanceof Map) q = Helpers.<Map<String, String>>cast(query).entrySet();
			else throw new IllegalArgumentException("query string inconvertible: " + query.getClass().getName());

			ByteList b = IOUtil.getSharedByteBuf();
			int i = 0;
			for (Map.Entry<String, String> entry : q) {
				if (i != 0) sb.append('&');

				URIUtil.encodeURI(b.putUTFData(entry.getKey()), sb, URIUtil.URI_COMPONENT_SAFE).append('=');
				b.clear();
				URIUtil.encodeURI(b.putUTFData(entry.getValue()), sb, URIUtil.URI_COMPONENT_SAFE);
				i = 1;
			}
			query = sb.subSequence(begin,sb.length()).toString();
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}
	final Headers _getHeaders() {
		for (int i = 0; i < addHeader.size(); i++) {
			Map.Entry<String, String> entry = addHeader.get(i);
			headers.remove(entry.getKey(), entry.getValue());
		}
		addHeader.clear();

		tryAdd("host", site);

		if (body instanceof DynByteBuf) {
			tryAdd("content-length", Integer.toString(((DynByteBuf) body).readableBytes()));
		} else if (body != null) {
			tryAdd("transfer-encoding", "chunked");
		}
		return headers;
	}
	private void tryAdd(String k, String v) {
		String prev = headers.putIfAbsent(k, v);
		if (prev == null) {
			addHeader.add(new AbstractMap.SimpleImmutableEntry<>(k, v));
		}
	}
	final void _getBody() {
		if (bodyType == 1) _body = ((Supplier<?>) body).get();
		else _body = body;
	}
	@SuppressWarnings("unchecked")
	final Object _write(ChannelCtx ctx, Object body) throws IOException {
		switch (bodyType) {
			case 0: break;

			case 1: {
				Boolean hasMore = ((Function<ChannelCtx, Boolean>) body).apply(ctx);
				if (hasMore) return body;
				break;
			}
			case 2: {
				InputStream in = (InputStream) body;
				ByteList buf = (ByteList) ctx.allocate(false, 1024);
				try {
					buf.readStream(in, buf.readableBytes());
					if (!buf.isWritable()) return null;
					ctx.channelWrite(buf);
				} finally {
					ctx.reserve(buf);
				}
			}
		}
		return null;
	}
	final void _close() throws IOException {
		if (_body instanceof AutoCloseable) {
			try {
				((AutoCloseable) _body).close();
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		}
		_body = null;
	}
	// endregion

	final HttpRequest _copyTo(HttpRequest t) {
		t.action = action;
		t.protocol = protocol;
		t.site = site;
		t.path = path;
		t.query = query;
		t.body = body;
		t.bodyType = bodyType;
		t.headers = new Headers(headers);
		t.addHeader.addAll(addHeader);

		t._address = _address;
		return t;
	}
	public abstract HttpRequest clone();

	public static final NamespaceKey DOWNLOAD_EOF = NamespaceKey.of("hc","data_eof");

	protected Object _body;
	protected byte state;

	public abstract HttpHead response();
	public abstract void waitFor() throws InterruptedException;

	public static HttpRequest nts() { return new HttpClient11(); }

	public static final SelectorLoop POLLER = new SelectorLoop(null, "NIO请求池", 0, 4, 60000, 100);

	public static int POOLED_KEEPALIVE_TIMEOUT = 60000;
	private static final Function<InetSocketAddress, Pool> fn = (x) -> new Pool(8);
	private static final Map<InetSocketAddress, Pool> pool = new ConcurrentHashMap<>();

	private static final class Pool extends RingBuffer<MyChannel> implements ChannelHandler {
		static final TypedName<AtomicLong> SLEEP = new TypedName<>("_sleep");

		final ReentrantLock lock = new ReentrantLock();
		final Condition available = lock.newCondition();
		final AtomicInteger freeConnectionSlot;
		int maxConnections;

		Pool(int count) {
			super(count);
			this.freeConnectionSlot = new AtomicInteger(count);
			this.maxConnections = count;
		}

		public void setMaxConnections(int conn) {
			int delta;
			synchronized (this) {
				delta = conn - maxConnections;
				maxConnections = conn;
			}
			freeConnectionSlot.getAndAdd(delta);
		}

		@Override
		public void onEvent(ChannelCtx ctx, Event event) {
			if (event.id == SyncHttpClient.SHC_CLOSE_CHECK) {
				_add(ctx, event);
			} else if (event.id == Timeout.READ_TIMEOUT) {
				AtomicLong aLong = ctx.attachment(SLEEP);
				if (aLong != null && System.currentTimeMillis() - aLong.get() < POOLED_KEEPALIVE_TIMEOUT) {
					event.setResult(Event.RESULT_DENY);
				}
			}
		}
		@Override
		public void channelClosed(ChannelCtx ctx) {
			lock.lock();
			try {
				removeFirstOccurrence(ctx.channel());
				freeConnectionSlot.getAndIncrement();
				available.signal();
			} finally {
				lock.unlock();
			}
		}

		final void _add(ChannelCtx ctx, Event event) {
			if (size < maxCap) {
				lock.lock();
				try {
					if (size < maxCap) {
						ctx.channel().remove("async_handler");
						if (event != null) event.setResult(Event.RESULT_DENY);
						ctx.attachment(SLEEP, new AtomicLong(System.currentTimeMillis()));
						ringAddLast(ctx.channel());
					}
					available.signal();
				} finally {
					lock.unlock();
				}
			}
		}

		void executePooled(HttpRequest request, SyncHttpClient client, int timeout, ChannelHandler timer) throws IOException {
			while (true) {
				if (size > 0) {
					lock.lock();
					try {
						while (true) {
							MyChannel some = pollFirst();
							if (some == null) break;

							some.remove("super_timer");
							some.addBefore("h11@merger", "super_timer", timer);
							SyncHttpClient shc = (SyncHttpClient) some.handler("h11@merger").handler();
							if (shc.queue(request, client) != null) return;
						}
					} finally {
						lock.unlock();
					}
				}

				while (true) {
					int i = freeConnectionSlot.get();
					if (i <= 0) break;

					if (freeConnectionSlot.compareAndSet(i, i-1)) {
						try {
							MyChannel ch = MyChannel.openTCP();
							ch.addLast("super_timer", timer)
							  .addLast("h11@merger", client);
							request.connect(ch, timeout);
							ch.addFirst("h11@pool", this);
							POLLER.register(ch, null);
						} catch (Throwable e) {
							freeConnectionSlot.getAndIncrement();
							throw e;
						}
						return;
					}
				}

				lock.lock();
				try {
					available.await();
				} catch (InterruptedException e) {
					throw new ClosedByInterruptException();
				} finally {
					lock.unlock();
				}
			}
		}
	}
}
