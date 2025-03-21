package roj.http.server;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.Tokenizer;
import roj.crypt.Base64;
import roj.http.Cookie;
import roj.http.Headers;
import roj.http.HttpUtil;
import roj.http.IllegalRequestException;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.EmbeddedChannel;
import roj.net.MyChannel;
import roj.text.CharList;
import roj.text.Escape;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class Request extends Headers {
	public static final String JSESSIONID = "JSESSIONID";

	private byte action;

	private String path, initPath, version, query;

	ResponseHeader handler;

	private final Map<String, Object> ctx;
	Request(Map<String, Object> ctx) {this.ctx = ctx;}
	public Map<String, Object> localCtx() {return ctx;}

	public ResponseHeader server() { return handler; }
	public MyChannel connection() { return handler.ch(); }

	Request init(byte action, String path, String query, String version) throws IllegalRequestException {
		this.action = action;
		this.version = version;
		try {
			this.path = initPath = IOUtil.safePath(Escape.decodeURI(path));
			this.query = query.isEmpty() ? "" : query;
		} catch (MalformedURLException e) {
			throw IllegalRequestException.badRequest(e.getMessage());
		}
		return this;
	}

	void free() {
		path = initPath = query = null;
		handler = null;
		cookie = null;
		postFields = getFields = null;
		arguments = null;
		clear();
		session_write_close();
		sessionName = JSESSIONID;
		responseHeader.clear();
	}

	public int action() { return action; }
	public String path() { return path; }
	public void setPath(String path) {this.path = path;}
	public String absolutePath() { return initPath; }

	private Headers arguments;
	public String argument(String name) {return arguments.getField(name);}
	public Headers arguments() {return arguments;}
	public void setArguments(Headers arguments) {this.arguments = arguments;}

	public boolean isExpecting() {return "100-continue".equalsIgnoreCase(get("Expect"));}
	public String host() {
		String host = get("host");
		if (host == null) host = get(":authority");
		return host == null ? ((InetSocketAddress)handler.ch().localAddress()).getHostString() : host;
	}

	// region fields
	Object postFields, getFields;
	private Map<String, Cookie> cookie;

	public String query() {return query;}
	public Map<String, String> GetFields() throws IllegalRequestException {
		if (getFields == null) {
			if (query.isEmpty()) return Collections.emptyMap();
			try {
				getFields = simpleValue(query, "&", true);
			} catch (MalformedURLException e) {
				throw IllegalRequestException.badRequest(e.getMessage());
			}
		}
		return Helpers.cast(getFields);
	}

	public ByteList postBuffer() {return (ByteList) postFields;}
	public HPostHandler postHandler() {return (HPostHandler) postFields;}
	public Map<String, String> PostFields() throws IllegalRequestException {
		if (postFields instanceof ByteList pf) {
			if (getField("content-type").startsWith("multipart/")) {
				try {
					var handler = new MultipartFormHandler(this) {
						@Override protected void onKey(ChannelCtx ctx, String name) {}
						@Override protected void onValue(ChannelCtx ctx, DynByteBuf buf) { data.put(name, buf.readUTF(buf.readableBytes())); }
					};

					try (var ch = EmbeddedChannel.createReadonly()) {
						ch.addLast("_", handler);
						ch.fireChannelRead(pf);
						handler.onSuccess();
						handler.onComplete();
					}

					postFields = handler.data;
				} catch (IOException e) {
					throw IllegalRequestException.badRequest(e.getMessage());
				} catch (IllegalArgumentException e) {
					throw new IllegalRequestException(500, "Request.postFields()不支持二进制(文件)数据,请使用PostHandler");
				}
			} else {
				try {
					postFields = simpleValue(pf, "&", true);
				} catch (MalformedURLException e) {
					throw IllegalRequestException.badRequest(e.getMessage());
				}
			}
		}
		return postFields == null ? Collections.emptyMap() : Helpers.cast(postFields);
	}

	public Map<String, String> fields() throws IllegalRequestException {
		var pf = PostFields();
		if (pf == null) return GetFields();
		var gf = new MyHashMap<>(GetFields());
		gf.putAll(pf);
		return gf;
	}

	public Map<String, Cookie> cookie() throws IllegalRequestException {
		if (cookie == null) {
			Map<String, String> fromClient;
			try {
				fromClient = getCookieFromClient();
				if (fromClient.isEmpty()) return cookie = new MyHashMap<>();
			} catch (MalformedURLException e) {
				throw IllegalRequestException.badRequest(e.getMessage());
			}

			for (Map.Entry<String, ?> entry : fromClient.entrySet()) {
				Cookie c = new Cookie(entry.getKey(), entry.getValue().toString());
				c.clearDirty();
				entry.setValue(Helpers.cast(c));
			}
			cookie = Helpers.cast(fromClient);
		}
		return cookie;
	}
	// endregion

	final Headers responseHeader = new Headers();
	public Headers responseHeader() {return responseHeader;}

	void packToHeader() {
		if (cookie != null) {
			List<Cookie> modified = new SimpleList<>();
			for (var entry : cookie.entrySet()) {
				Cookie c = entry.getValue();
				if (c.isDirty()) modified.add(c);
			}
			if (!modified.isEmpty()) responseHeader.sendCookieToClient(modified);
		}
	}

	//region session
	private String sessionName = JSESSIONID, sessionId;
	private Map<String,Object> session;

	public Request sessionName(String prefix) {this.sessionName = prefix; return this;}
	public Map<String,Object> session() throws IllegalRequestException { return session(true); }
	public Map<String,Object> session(boolean createIfNonExist) throws IllegalRequestException {
		var storage = HttpCache.getSessionStorage();
		if (storage == null) throw new IllegalStateException("没有SessionStorage");

		if (session == null) {
			Cookie c = cookie().get(sessionName);
			if (c != null && SessionStorage.isValid(c.value())) sessionId = c.value();
			else if (!createIfNonExist) return null;
			else {
				c = new Cookie(sessionName, sessionId = storage.newId()).httpOnly(true);
				cookie.put(sessionName, c);
			}
			session = storage.get(sessionId);
			if (session == null && createIfNonExist) session = new MyHashMap<>();
		}
		return session;
	}
	public void session_write_close() {
		if (session != null) {
			HttpCache.getSessionStorage().put(sessionId, session);
			session = null;
			sessionId = null;
		}
	}
	public void session_destroy() throws IllegalRequestException {
		session(false);
		if (sessionId != null) {
			HttpCache.getSessionStorage().remove(sessionId);
			session = null;
			sessionId = null;

			Cookie ck = new Cookie(sessionName, "");
			ck.expires(System.currentTimeMillis()-1);
			cookie.put(sessionName, ck);
		}
	}
	//endregion

	/**
	 * @param verifier user,pass => verified
	 */
	public void basicAuthorization(String message, BiFunction<String, String, Boolean> verifier) throws IllegalRequestException {
		String auth = getField("authorization");
		if (!auth.isEmpty()) {
			int i = auth.indexOf(' ');
			String type = auth.substring(0,i);
			if (type.equalsIgnoreCase("basic")) {
				try {
					var out = new ByteList();
					auth = Base64.decode(auth.substring(i+1), out).readUTF(out.readableBytes());
					out._free();

					int pos = auth.indexOf(':');
					var user = auth.substring(0,pos);
					var pass = auth.substring(pos+1);
					var result = verifier.apply(user, pass);
					if (result) return;
				} catch (Exception e) {
					throw new IllegalRequestException(400);
				}
			}
		}

		CharList sb = new CharList().append("Basic realm=\"");
		Tokenizer.addSlashes(sb, Escape.encodeURI(message));
		responseHeader.put("www-authenticate", sb.append("\"").toStringAndFree());

		throw new IllegalRequestException(401);
	}

	private boolean checkProxyToken() {
		String proxySec = get("x-proxy-sec");
		if (proxySec == null || HttpCache.proxySecret == null) return false;
		if (proxySec.equals(HttpCache.proxySecret)) return true;
		Helpers.athrow(new IllegalRequestException(400, "Bad proxy-sec"));
		return false;
	}

	public boolean isSecure() {
		var conn = handler;
		if (conn == null) return false;

		if (checkProxyToken()) {
			String field = getField("x-proxy-https");
			return !field.isEmpty() && !field.equalsIgnoreCase("off") && !field.equalsIgnoreCase("false");
		}

		return handler.ch().handler("h11@tls") != null;
	}

	public InetSocketAddress proxyRemoteAddress() {
		var conn = handler;
		if (conn == null) return null;

		if (checkProxyToken()) {
			var forwardedFor = getField("x-proxy-addr");
			int i = forwardedFor.indexOf(',');
			if (i < 0) i = forwardedFor.length();
			return new InetSocketAddress(forwardedFor.substring(0, i), Integer.parseInt(getField("x-proxy-port")));
		}

		return (InetSocketAddress) conn.ch().remoteAddress();
	}

	public CharList headerLine(CharList sb) {
		sb.append(HttpUtil.getMethodName(action)).append(" http://").append(host()).append('/').append(initPath);
		if (query != "") sb.append('?').append(query);
		return sb.append(' ').append(version);
	}
	public String toString() {
		var sb = headerLine(new CharList()).append('\n');
		encode(sb);
		return sb.toStringAndFree();
	}
}