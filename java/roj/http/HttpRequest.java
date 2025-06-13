package roj.http;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import roj.collect.RingBuffer;
import roj.collect.ArrayList;
import roj.concurrent.OperationDone;
import roj.http.server.HSConfig;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.*;
import roj.net.handler.JSslClient;
import roj.net.handler.Timeout;
import roj.text.CharList;
import roj.text.URICoder;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
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

	public static final String DOWNLOAD_EOF = "httpReq:dataEnd";

	private static final String DefGet = new String("GET");
	private String action = DefGet;

	private String protocol, site, path = "/";
	private volatile Object query;

	private Object body;
	private byte bodyType;
	protected Object _body;

	private Headers headers;
	private final ArrayList<Map.Entry<String, String>> autoHeaders = new ArrayList<>(4);

	private URI proxy;
	InetSocketAddress _address;

	protected long responseBodyLimit = Long.MAX_VALUE;

	protected volatile byte state;

	protected static final int SKIP_CE = 1;
	protected byte flag;

	protected HttpRequest() { this(true); }
	protected HttpRequest(boolean inheritDefaultHeader) {
		headers = inheritDefaultHeader ? new Headers(DEFAULT_HEADERS) : new Headers();
	}

	// region 设置请求参数
	public final HttpRequest method(String type) {
		if (HttpUtil.getMethodId(type) < 0) throw new IllegalArgumentException(type);
		action = type;
		return this;
	}
	public final String method() { return action; }

	public final HttpRequest withProxy(@Nullable URI uri) { proxy = uri; return this; }

	public final HttpRequest header(CharSequence k, String v) { headers.put(k, v); return this; }
	public final HttpRequest headers(Map<? extends CharSequence, String> map) { headers.putAll(map); return this; }
	public final HttpRequest headers(Map<? extends CharSequence, String> map, boolean clear) {
		if (clear) headers.clear();
		headers.putAll(map);
		return this;
	}

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

		this._address = null;
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
	public final URI url() {
		String site = this.site;
		int port = site.lastIndexOf(':');
		if (port > 0) {
			site = site.substring(0, port);
			port = Integer.parseInt(this.site.substring(port+1));
		}
		try {
			return new URI(protocol, null, site, port, encodeQuery(IOUtil.getSharedByteBuf()).toString(), null, null);
		} catch (URISyntaxException e) {
			Helpers.athrow(e);
			throw OperationDone.NEVER;
		}
	}

	public final HttpRequest query(Map<String, String> q) { query = q; return this; }
	public final HttpRequest query(List<Map.Entry<String, String>> q) { query = q; return this; }
	public final HttpRequest query(String q) { query = q; return this; }

	public final HttpRequest body(DynByteBuf b) { return setBody(b,0); }
	public final HttpRequest body1(Function<ChannelCtx, Boolean> b) { return setBody(b,1); }
	public final HttpRequest body2(Supplier<InputStream> b) { return setBody(b,2); }
	private HttpRequest setBody(Object b, int i) {
		if (b == null) i = 0;
		else if (action == DefGet) action = "POST";

		body = b;
		bodyType = (byte) i;
		return this;
	}
	public final Object body() { return body; }

	public final HttpRequest cookies(Collection<Cookie> cookies) {
		if (cookies.isEmpty()) return this;

		var itr = cookies.iterator();

		var sb = new CharList();
		while (true) {
			itr.next().write(sb, false);
			if (!itr.hasNext()) break;
			sb.append("; ");
		}

		headers.add("cookie", sb.toStringAndFree());
		return this;
	}
	public final HttpRequest cookies(Cookie cookie) {return cookies(Collections.singletonList(cookie));}
	public final HttpRequest cookies(Cookie... cookies) {return cookies(Arrays.asList(cookies));}

	public final HttpRequest bodyLimit(long bodyLimit) {
		responseBodyLimit = bodyLimit;
		return this;
	}
	public final HttpRequest unzip(boolean unzip) {
		if (unzip) flag &= ~SKIP_CE;
		else flag |= SKIP_CE;
		return this;
	}
	// endregion
	//region 读取请求参数
	final Headers getHeaders() {
		for (int i = 0; i < autoHeaders.size(); i++) {
			Map.Entry<String, String> entry = autoHeaders.get(i);
			headers.remove(entry.getKey(), entry.getValue());
		}
		autoHeaders.clear();

		_put("host", site);

		if (body instanceof DynByteBuf) {
			_put("content-length", Integer.toString(((DynByteBuf) body).readableBytes()));
		} else if (body != null) {
			_put("transfer-encoding", "chunked");
		}
		return headers;
	}
	private void _put(String k, String v) {
		String prev = headers.putIfAbsent(k, v);
		if (prev == null) {
			autoHeaders.add(new AbstractMap.SimpleImmutableEntry<>(k, v));
		}
	}

	private InetSocketAddress getAddress() throws IOException {
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

	final ByteList encodeQuery(ByteList sb) {
		sb.append(path.isEmpty() ? "/" : path);
		if (query == null) return sb;

		sb.append('?');
		if (query instanceof String) return sb.append(query.toString());

		int begin = sb.length();
		Iterable<Map.Entry<String, String>> q;
		if (query instanceof List) q = Helpers.cast(query);
		else if (query instanceof Map) q = Helpers.<Map<String, String>>cast(query).entrySet();
		else throw new IllegalArgumentException("query string inconvertible: " + query.getClass().getName());

		ByteList b = new ByteList();
		int i = 0;
		for (Map.Entry<String, String> entry : q) {
			if (i != 0) sb.append('&');

			b.clear();
			URICoder.pEncodeW(sb, b.putUTFData(entry.getKey()), URICoder.URI_COMPONENT_SAFE).append('=');
			b.clear();
			URICoder.pEncodeW(sb, b.putUTFData(entry.getValue()), URICoder.URI_COMPONENT_SAFE);
			i = 1;
		}
		b._free();
		query = sb.subSequence(begin,sb.length()).toString();
		return sb;
	}

	final Object initBody() {
		return _body = body instanceof Supplier<?> supplier ? supplier.get() : body;
	}
	@SuppressWarnings("unchecked")
	final boolean writeBody(ChannelCtx ctx, Object body) throws IOException {
		switch (bodyType) {
			default -> {
				return false;
			}
			case 1 -> {
				return ((Function<ChannelCtx, Boolean>) body).apply(ctx);
			}
			case 2 -> {
				InputStream in = (InputStream) body;
				ByteList buf = (ByteList) ctx.allocate(false, 4096);
				try {
					buf.readStream(in, buf.writableBytes());
					if (!buf.isReadable()) return false;
					ctx.channelWrite(buf);
				} finally {
					BufferPool.reserve(buf);
				}
				return true;
			}
		}
	}
	final void closeBody() {
		if (_body instanceof AutoCloseable c)
			IOUtil.closeSilently(c);
		_body = null;
	}
	//endregion
	public boolean attach(MyChannel ch, int timeout) throws IOException {
		ch.addFirst("h11@client", (ChannelHandler) this);
		if ("https".equals(protocol)) {
			// todo now we supported HTTP/2, but still not TLS1.3(with my own implementation)
			ch.addFirst("h11@tls", /*RojLib.EXTRA_BUG_CHECK ? new MSSCipher().sslMode() : */new JSslClient());
		}

		var addr = Net.applyProxy(proxy, getAddress(), ch);
		return ch.connect(addr, timeout);
	}
	// region 一次连接
	public final HttpClient execute() throws IOException { return execute(DEFAULT_TIMEOUT); }
	public final HttpClient execute(int timeout) throws IOException {
		headers.putIfAbsent("connection", "close");

		var ch = MyChannel.openTCP();
		var client = new HttpClient();
		ch.addLast("h11@timer", new Timeout(timeout, 1000))
		  .addLast("h11@merger", client);
		attach(ch, timeout);
		ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		return client;
	}
	//endregion
	//region 池化连接
	public static int POOL_KEEPALIVE = 60000;

	public final HttpClient executePooled() throws IOException { return executePooled(DEFAULT_TIMEOUT); }
	public final HttpClient executePooled(int timeout) throws IOException { return executePooled(timeout, action.equals("GET") || action.equals("HEAD") || action.equals("OPTIONS") ? 1 : -1); }
	public final HttpClient executePooled(int timeout, int maxRedirect) throws IOException { return executePooled(timeout, maxRedirect, 0); }
	public final HttpClient executePooled(int timeout, int maxRedirect, int maxRetry) throws IOException {
		headers.putIfAbsent("connection", "keep-alive");

		HttpClient client = new HttpClient();

		Pool pool = POOLS.computeIfAbsent(getAddress(), NEW_POOL);
		pool.executePooled(this, client, timeout, new AutoRedirect(this, timeout, maxRedirect, maxRetry));

		return client;
	}

	private static final Map<InetSocketAddress, Pool> POOLS = new ConcurrentHashMap<>();
	private static final Function<InetSocketAddress, Pool> NEW_POOL = (x) -> new Pool(8);

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
				if (aLong != null && System.currentTimeMillis() - aLong.get() < POOL_KEEPALIVE) {
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
							request.attach(ch, timeout);
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
					throw IOUtil.rethrowAsIOException(e);
				} finally {
					lock.unlock();
				}
			}
		}
	}
	//endregion
	//region WebSocket客户端
	@ApiStatus.Experimental
	public WSClient openWebSocket(int timeout, WSClient handler) throws IOException {
		var randKey = IOUtil.getSharedByteBuf().putLong(ThreadLocalRandom.current().nextLong()).base64UrlSafe();

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
		attach(ch, timeout);
		ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		return handler;
	}

	public abstract static class WSClient extends WebSocket {
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
						throw IOUtil.rethrowAsIOException(e);
					}
				}
			}

			if (exception != null) throw new IOException("连接失败: "+this, exception);
		}
	}
	// endregion
	// region Internal
	void _redirect(MyChannel ch, URI url, int timeout) throws IOException {
		var oldAddr = _address;
		var newAddr = url(url).getAddress();

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

			var addr = Net.applyProxy(proxy, newAddr, ch);
			ch.connect(addr, timeout);

			ServerLaunch.DEFAULT_LOOPER.register(ch, null);
		}
	}

	// endregion
	final HttpRequest copyTo(HttpRequest to) {
		to.action = action;
		to.protocol = protocol;
		to.site = site;
		to.path = path;
		to.query = query;
		to.body = body;
		to.bodyType = bodyType;
		to.headers = new Headers(headers);
		to.autoHeaders.addAll(autoHeaders);
		to.proxy = proxy;
		to.responseBodyLimit = responseBodyLimit;
		to.flag = flag;
		return to;
	}
	public abstract HttpRequest clone();

	public abstract HttpHead response();
	public abstract void waitFor() throws InterruptedException;

	public static HttpRequest builder() { return new HttpClient11(); }
}