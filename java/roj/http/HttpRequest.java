package roj.http;

import org.jetbrains.annotations.ApiStatus;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.http.server.HSConfig;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.*;
import roj.net.handler.JSslClient;
import roj.net.handler.Timeout;
import roj.text.CharList;
import roj.text.Escape;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
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
		DEFAULT_HEADERS.put("accept", "*/*");
		//DEFAULT_HEADERS.put("connection", "keep-alive");
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

	private URI proxy;

	InetSocketAddress _address;

	public HttpRequest() { this(true); }
	public HttpRequest(boolean inherit) {
		headers = inherit ? new Headers(DEFAULT_HEADERS) : new Headers();
	}

	public final HttpRequest method(String type) {
		if (HttpUtil.getMethodId(type) < 0) throw new IllegalArgumentException(type);
		action = type;
		return this;
	}
	public final String method() { return action; }

	public final HttpRequest withProxy(URI uri) { proxy = uri; return this; }

	public final HttpRequest header(CharSequence k, String v) { headers.put(k, v); return this; }
	public final HttpRequest headers(Map<? extends CharSequence, String> map) { headers.putAll(map); return this; }
	public final HttpRequest headers(Map<? extends CharSequence, String> map, boolean clear) {
		if (clear) headers.clear();
		headers.putAll(map);
		return this;
	}

	public final HttpRequest body(DynByteBuf b) { return setBody(b,0); }
	//public final HttpRequest body(Headers b) { return setBody(b,0); }
	public final HttpRequest bodyG1(Function<ChannelCtx, Boolean> b) { return setBody(b,1); }
	public final HttpRequest bodyG3(Supplier<InputStream> b) { return setBody(b,2); }
	private HttpRequest setBody(Object b, int i) {
		if (action == DefGet) action = "POST";

		body = b;
		bodyType = (byte) i;
		return this;
	}
	public final Object body() { return body; }

	@Deprecated
	public final HttpRequest url(URL url) {
		try {
			return url(url.toURI());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}
	public final HttpRequest url(URI url) {
		this.protocol = url.getScheme().toLowerCase();

		String host = url.getHost();
		if (url.getPort() >= 0) host += ":"+url.getPort();
		this.site = host;

		this.path = url.getPath();
		this.query = url.getQuery();

		_address = null;
		return this;
	}
	public final HttpRequest url(String url) {
		/*this.protocol = protocol.toLowerCase();
		this.site = site;
		if (!path.startsWith("/")) path = "/".concat(path);
		this.path = path;
		this.query = query;

		_address = null;*/
		return url(URI.create(url));
	}
	public final HttpRequest query(Map<String, String> q) { query = q; return this; }
	public final HttpRequest query(List<Map.Entry<String, String>> q) { query = q; return this; }
	public final HttpRequest query(String q) { query = q; return this; }

	public final URI url() {
		String site = this.site;
		int port = site.lastIndexOf(':');
		if (port > 0) {
			site = site.substring(0, port);
			port = Integer.parseInt(this.site.substring(port+1));
		}
		try {
			return new URI(protocol, null, site, port, _appendPath(new CharList()).toStringAndFree(), null, null);
		} catch (URISyntaxException e) {
			Helpers.athrow(e);
			return Helpers.nonnull();
		}
	}
	// endregion

	public boolean connect(MyChannel ch, int timeout) throws IOException {
		ch.addFirst("h11@client", (ChannelHandler) this);

		ch.remove("h11@tls");
		if ("https".equals(protocol)) {
			// todo now we supported HTTP/2, but still not TLS1.3(with my own implementation)
			ch.addFirst("h11@tls", /*RojLib.EXTRA_BUG_CHECK ? new MSSCipher().sslMode() : */new JSslClient());
		}

		var addr = NetUtil.applyProxy(proxy, _getAddress(), ch);
		return ch.connect(addr, timeout);
	}

	void _redirect(MyChannel ch, URI url, int timeout) throws IOException {
		var oldAddr = _address;
		var newAddr = url(url)._getAddress();

		if (newAddr.equals(oldAddr)) {
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

			var addr = NetUtil.applyProxy(proxy, _address, ch);
			ch.connect(addr, timeout);

			ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		}
	}

	// region execute simple
	public final HttpClient execute() throws IOException { return execute(DEFAULT_TIMEOUT); }
	public final HttpClient execute(int timeout) throws IOException {
		headers.putIfAbsent("connection", "close");

		var ch = MyChannel.openTCP();
		var client = new HttpClient();
		ch.addLast("h11@timer", new Timeout(timeout, 1000))
		  .addLast("h11@merger", client);
		connect(ch, timeout);
		ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		return client;
	}

	@ApiStatus.Experimental
	public WSClient executeWebsocket(int timeout, WSClient handler) throws IOException {
		var randKey = IOUtil.getSharedByteBuf().putLong(System.currentTimeMillis() ^ hashCode() ^ ((long) handler.hashCode() << 32)).base64UrlSafe();

		var buf = IOUtil.getSharedByteBuf();
		var sha1 = HSConfig.getInstance().sha1();

		sha1.update(buf.putAscii(randKey).putAscii("258EAFA5-E914-47DA-95CA-C5AB0DC85B11").list, 0, buf.wIndex());
		handler.acceptKey = IOUtil.encodeBase64(sha1.digest());

		headers.put("connection", "upgrade");
		headers.put("upgrade", "websocket");
		headers.putIfAbsent("sec-webSocket-extensions", "permessage-deflate; client_max_window_bits");
		headers.putIfAbsent("sec-webSocket-key", randKey);
		headers.putIfAbsent("sec-webSocket-version", "13");

		var ch = MyChannel.openTCP();
		ch.addLast("h11@timer", new Timeout(timeout, 1000))
		  .addLast("h11@merger", handler);
		connect(ch, timeout);
		ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		return handler;
	}

	public abstract static class WSClient extends WebSocketConnection {
		String acceptKey;
		byte state;
		Throwable exception;

		{flag = 0;}

		@Override public final void channelOpened(ChannelCtx ctx) throws IOException {
			ctx.channel().handler("h11@timer").removeSelf();

			var httpClient = HttpClient.findHandler(ctx);
			var head = ((HttpRequest) httpClient.handler()).response();
			httpClient.removeSelf();

			var accept = head.get("sec-websocket-accept");
			if (accept == null || !accept.equals(acceptKey)) throw new FastFailException("对等端不是websocket("+head.getCode()+")", head);

			var deflate = head.getHeaderValue("sec-websocket-extensions", "permessage-deflate");
			if (deflate != null) enableZip();

			onOpened(head);
			state = 1;
			synchronized (this) {notifyAll();}
		}
		protected void onOpened(HttpHead head) throws IOException {}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			super.channelClosed(ctx);
			state = 2;
			if (exception == null) exception = new IllegalStateException("未预料的连接关闭");
			synchronized (this) {notifyAll();}
		}

		@Override
		public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
			if (exception == null) exception = ex;
			ctx.close();
		}

		public final void awaitOpen() throws IOException {
			while (state == 0) {
				synchronized (this) {
					try {
						wait();
					} catch (InterruptedException e) {
						ch.close();
						throw new ClosedByInterruptException();
					}
				}
			}

			if (exception != null) throw new IOException("请求失败: "+this, exception);
		}
	}

	public final HttpClient executePooled() throws IOException { return executePooled(DEFAULT_TIMEOUT); }
	public final HttpClient executePooled(int timeout) throws IOException { return executePooled(timeout, action.equals("GET") || action.equals("HEAD") || action.equals("OPTIONS") ? 1 : -1); }
	public final HttpClient executePooled(int timeout, int maxRedirect) throws IOException { return executePooled(timeout, maxRedirect, 0); }
	public final HttpClient executePooled(int timeout, int maxRedirect, int maxRetry) throws IOException {
		headers.putIfAbsent("connection", "keep-alive");

		HttpClient client = new HttpClient();

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
		if (port < 0) {
			port = switch (protocol) {
				case "https" -> 443;
				case "http" -> 80;
				case "ftp" -> 21;
				default -> throw new IOException("Unknown protocol");
			};
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

				b.clear();
				Escape.escape(sb, b.putUTFData(entry.getKey()), Escape.URI_COMPONENT_SAFE).append('=');
				b.clear();
				Escape.escape(sb, b.putUTFData(entry.getValue()), Escape.URI_COMPONENT_SAFE);
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
		if (bodyType == 1 || bodyType == 2) _body = ((Supplier<?>) body).get();
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
					buf.readStream(in, buf.writableBytes());
					if (!buf.isReadable()) return null;
					ctx.channelWrite(buf);
				} finally {
					BufferPool.reserve(buf);
				}
				return body;
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

	public static final String DOWNLOAD_EOF = "httpReq:dataEnd";

	protected Object _body;
	protected byte state;

	public abstract HttpHead response();
	public abstract void waitFor() throws InterruptedException;

	public static HttpRequest builder() { return new HttpClient11(); }

	public static int POOLED_KEEPALIVE_TIMEOUT = 60000;
	private static final Function<InetSocketAddress, Pool> fn = (x) -> new Pool(8);
	private static final Map<InetSocketAddress, Pool> pool = new ConcurrentHashMap<>();

	private static final class Pool extends RingBuffer<MyChannel> implements ChannelHandler {
		static final TypedKey<AtomicLong> SLEEP = new TypedKey<>("_sleep");

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
			if (event.id.equals(HttpClient.SHC_FINISH)) {
				_add(ctx, event);
			} else if (event.id.equals(Timeout.READ_TIMEOUT)) {
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

		void executePooled(HttpRequest request, HttpClient client, int timeout, ChannelHandler timer) throws IOException {
			while (true) {
				if (size > 0) {
					lock.lock();
					while (true) {
						MyChannel ch = pollFirst();
						if (ch == null) break;
						if (!(ch.isOpen() & ch.isInputOpen() & ch.isOutputOpen())) continue;
						lock.unlock();

						HttpClient shc = (HttpClient) ch.handler("h11@merger").handler();
						if (shc.retain(request, client)) {
							ch.remove("super_timer");
							ch.addBefore("h11@merger", "super_timer", timer);
							return;
						} else {
							IOUtil.closeSilently(ch);
							lock.lock();
						}
					}
					lock.unlock();
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
							ServerLaunch.DEFAULT_LOOPER.register(ch, null);
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