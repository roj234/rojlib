package roj.net.http.srv;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.io.session.SessionProvider;
import roj.net.URIUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.CtxEmbedded;
import roj.net.ch.MyChannel;
import roj.net.http.Action;
import roj.net.http.Cookie;
import roj.net.http.Headers;
import roj.net.http.IllegalRequestException;
import roj.net.http.auth.AuthScheme;
import roj.security.SipHashMap;
import roj.text.CharList;
import roj.text.StreamReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Request extends Headers {
	private int action;

	private String path, initPath;

	Object postFields, getFields;
	private Map<String, Cookie> cookie;

	Map<String, Object> threadCtx;
	HttpServer11 handler;

	Request() {}

	public Map<String, Object> threadContext() { return threadCtx; }
	@Deprecated
	public HttpServer11 handler() { return handler; }
	public MyChannel connection() { return handler.ch.channel(); }

	Request init(int action, String path, String query) throws IllegalRequestException {
		this.action = action;
		try {
			this.path = initPath = IOUtil.safePath(URIUtil.decodeURI(path));
		} catch (MalformedURLException e) {
			throw new IllegalRequestException(400, "bad query");
		}
		this.getFields = query.isEmpty() ? Collections.emptyMap() : query;
		return this;
	}

	public void free() {
		path = initPath = null;
		handler = null;
		cookie = null;
		postFields = getFields = null;
		clear();
		session_write_close();
		responseHeader.clear();
	}

	public int action() { return action; }

	public String path() { return path; }
	public Request resetPath() { path = initPath; return this; }

	public Request subDirectory(int i) {
		while (i-- > 0) path = path.substring(0, path.lastIndexOf('/'));
		return this;
	}

	public List<String> directories() {
		List<String> paths = TextUtil.split(new SimpleList<>(), path(), '/', -1, true);
		if (paths.isEmpty()) paths.add("");
		else if (paths.get(0).isEmpty()) paths.remove(0);
		return paths;
	}

	// region request info
	public Map<String, String> postFields() throws IllegalRequestException {
		if (postFields instanceof ByteList) {
			ByteList pf = (ByteList) postFields;

			String ct = getOrDefault("content-type", "");
			if (ct.startsWith("multipart/")) {
				try {
					MultipartFormHandler handler = new MultipartFormHandler(this) {
						@Override
						protected void onKey(ChannelCtx ctx, String name) {}
						@Override
						protected void onValue(ChannelCtx ctx, DynByteBuf buf) { map.put(name, buf.readUTF(buf.readableBytes())); }
					};

					CtxEmbedded ch = new CtxEmbedded();
					ch.addLast("_", handler);
					ch.fireChannelRead(pf);
					handler.onSuccess();
					handler.onComplete();
					ch.close();

					postFields = handler.map;
				} catch (IOException e) {
					IllegalRequestException ex = new IllegalRequestException(400, e.getMessage());
					ex.setStackTrace(e.getStackTrace());
					throw ex;
				} catch (IllegalArgumentException e) {
					throw new IllegalRequestException(400, "Request.postFields()不支持二进制(文件)数据,请使用PostHandler");
				}
			} else {
				try {
					postFields = simpleValue(pf, "&", true);
				} catch (MalformedURLException e) {
					throw new IllegalRequestException(400, e.getMessage());
				}
			}
		}
		return postFields == null ? Collections.emptyMap() : Helpers.cast(postFields);
	}
	public String postString() {
		if (postFields instanceof ByteList) {
			try (StreamReader sr = new StreamReader((ByteList) postFields, null)) {
				postFields = IOUtil.read(sr);
			} catch (IOException e) {
				return null;
			}
		}
		return (String) postFields;
	}
	public ByteList postBuffer() { return (ByteList) postFields; }
	public HPostHandler postHandler() { return ((HPostHandler) postFields); }

	public Map<String, String> getFields() throws IllegalRequestException {
		if (getFields instanceof CharSequence) {
			try {
				getFields = simpleValue((CharSequence) getFields, "&", true);
			} catch (MalformedURLException e) {
				throw new IllegalRequestException(400, e.getMessage());
			}
		}
		return Helpers.cast(getFields);
	}

	public String getFieldsRaw() {
		if (getFields == null) {
			return null;
		} else if (getFields instanceof String) {
			return (String) getFields;
		} else if (getFields == Collections.EMPTY_LIST) {
			return "";
		}
		throw new IllegalStateException("Parsed");
	}

	public Map<String, String> fields() throws IllegalRequestException {
		Map<String, String> map1 = postFields();
		if (map1 == null) return getFields();
		SipHashMap<String, String> map = new SipHashMap<>(getFields());
		map.putAll(map1);
		return map;
	}

	public String field(String key) throws IllegalRequestException {
		Map<String, String> map = postFields();
		if (map != null && map.containsKey(key)) return map.get(key);

		map = getFields();
		if (map.containsKey(key)) return map.get(key);
		return "";
	}

	public String host() {
		String host = get("host");
		return host == null ? ((InetSocketAddress)handler.ch.remoteAddress()).getHostString() : host;
	}

	public Map<String, Cookie> cookie() throws IllegalRequestException {
		if (cookie == null) {
			Map<String, String> fromClient;
			try {
				fromClient = getCookieFromClient();
				if (fromClient.isEmpty()) return cookie = new MyHashMap<>();
			} catch (MalformedURLException e) {
				throw new IllegalRequestException(400, e.getMessage());
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
	public Headers responseHeader() { return responseHeader; }

	void packToHeader() {
		if (cookie != null) {
			List<Cookie> modified = new SimpleList<>();
			for (Map.Entry<String, Cookie> entry : cookie.entrySet()) {
				Cookie c = entry.getValue();
				if (c.isDirty()) modified.add(c);
			}
			if (!modified.isEmpty()) responseHeader.sendCookieToClient(modified);
		}
	}

	private String sessionName = "JSESSIONID", sessionId;
	private Map<String,Object> session;

	public Request sessionName(String prefix) {
		this.sessionName = prefix; return this;
	}

	public Map<String,Object> session() throws IllegalRequestException { return session(true); }
	public Map<String,Object> session(boolean createIfNonExist) throws IllegalRequestException {
		SessionProvider provider = SessionProvider.getDefault();
		if (provider == null) throw new IllegalStateException("没有session provider");

		if (session == null) {
			Cookie c = cookie().get(sessionName);
			if (c != null && SessionProvider.isValid(c.value())) sessionId = c.value();
			else if (!createIfNonExist) return null;
			else {
				c = new Cookie(sessionName, sessionId = provider.createSession()).httpOnly(true);
				cookie.put(sessionName, c);
			}
			session = provider.loadSession(sessionId);
			if (session == null && createIfNonExist) session = new MyHashMap<>();
		}
		return session;
	}

	public void session_write_close() {
		if (session != null) {
			SessionProvider.getDefault().saveSession(sessionId, session);
			session = null;
		}
	}

	public void session_destroy() throws IllegalRequestException {
		session(false);
		if (sessionId != null) {
			SessionProvider.getDefault().destroySession(sessionId);
			session = null;
			sessionId = null;

			Cookie ck = new Cookie(sessionName, "");
			ck.expires(System.currentTimeMillis()-1);
			cookie.put(sessionName, ck);
		}
	}

	public void authorize(String message, AuthScheme... schemes) throws IllegalRequestException {
		String auth = getField("authorization");
		if (!auth.isEmpty()) {
			int i = auth.indexOf(' ');
			String type = auth.substring(0,i);
			for (AuthScheme alg : schemes) {
				if (alg.type().equals(type)) {
					try {
						String str = alg.check(auth.substring(i+1));
						if (str == null) return;
						message = str;
					} catch (Exception e) {
						message = "无效的请求";
					}
					break;
				}
			}
		}
		// WWW-Authenticate: Basic realm="Fantasy"
		CharList sb = IOUtil.ddLayeredCharBuf();
		for (AuthScheme authScheme : schemes) sb.append(authScheme.type()).append(' ');
		ITokenizer.addSlashes(sb.append("realm=\""), URIUtil.encodeURI(message)).append("\"");
		responseHeader.put("www-authenticate", sb.toStringAndFree());

		throw new IllegalRequestException(401, StringResponse.httpErr(401));
	}

	public String toString() {
		StringBuilder sb = new StringBuilder().append(Action.toString(action)).append(' ').append(host()).append(path).append(" HTTP/1.1\n");
		encode(sb);
		return sb.toString();
	}
}
