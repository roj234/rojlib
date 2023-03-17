package roj.net.http.srv;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.net.URIUtil;
import roj.net.http.Action;
import roj.net.http.Headers;
import roj.net.http.IllegalRequestException;
import roj.security.SipHashMap;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.UTFDataFormatException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Request extends Headers {
	public static final String CTX_POST_HANDLER = "PH";

	private int action;

	private String path, safePath;
	private List<String> pushd = new SimpleList<>();

	Object postFields, getFields;
	private Map<String, String> cookie;

	Map<String, Object> ctx, threadCtx;
	HttpServer11 handler;

	Request() {}

	public Map<String, Object> ctx() {
		if (ctx == null) ctx = new MyHashMap<>(4);
		return ctx;
	}
	public Map<String, Object> threadLocalCtx() {
		return threadCtx;
	}

	public HttpServer11 handler() {
		return handler;
	}

	Request init(int action, String path, String query) throws IllegalRequestException {
		this.action = action;
		this.path = path;
		try {
			safePath = FileUtil.safePath(URIUtil.decodeURI(path));
		} catch (MalformedURLException e) {
			throw new IllegalRequestException(400, "bad query");
		}
		this.getFields = query.isEmpty() ? Collections.emptyMap() : query;
		return this;
	}

	public void free() {
		pushd.clear();
		path = safePath = null;
		handler = null;
		cookie = null;
		postFields = getFields = null;
		clear();
		if (ctx != null) ctx.clear();
		threadCtx = null;
	}

	public int action() {
		return action;
	}

	public String rawPath() {
		return path;
	}
	public String path() {
		return safePath;
	}

	public Request subDirectory(int i) {
		CharList buf = IOUtil.getSharedCharBuf();
		List<String> dir = directories();
		if (i < dir.size()) {
			while (true) {
				buf.append(dir.get(i++));
				if (i == dir.size()) break;
				buf.append('/');
			}
		}
		safePath = buf.toString();
		return this;
	}

	public String directory(int i) {
		return directories().get(i);
	}
	public List<String> directories() {
		if (pushd.isEmpty()) {
			List<String> paths = TextUtil.split(pushd, path(), '/', -1, true);
			if (paths.isEmpty()) paths.add("");
			else if (paths.get(0).isEmpty()) paths.remove(0);
		}
		return pushd;
	}

	public Map<String, String> postFields() throws IllegalRequestException {
		if (postFields instanceof ByteList) {
			ByteList pf = (ByteList) postFields;

			String ct = getOrDefault("content-type", "");
			if (ct.startsWith("multipart")) {
				SipHashMap<String, String> map = new SipHashMap<>();
				new MultipartFormHandler(this) {
					String key1;

					@Override
					protected void onKey(CharSequence key) {
						if (key1 != null) map.put(key1, "");
						key1 = key.toString();
					}

					@Override
					protected void onValue(DynByteBuf buf) {
						map.put(key1, buf.readUTF(buf.readableBytes()));
						key1 = null;
					}
				}.onData(pf);
				postFields = map;
			} else {
				postFields = parseSimpleField(pf, "&");
			}
		}
		return Helpers.cast(postFields);
	}

	public String postString() throws UTFDataFormatException {
		if (postFields instanceof ByteList) {
			ByteList pf = (ByteList) postFields;

			String str = pf.readUTF(pf.readableBytes());
			postFields = str;
			return str;
		}
		return Helpers.cast(postFields);
	}

	public ByteList postBuffer() {
		return (ByteList) postFields;
	}

	public Map<String, String> getFields() throws IllegalRequestException {
		if (getFields instanceof CharSequence)
			getFields = parseSimpleField((CharSequence) getFields, "&");
		return Helpers.cast(getFields);
	}

	private static Map<String, String> parseSimpleField(CharSequence query, String splitter) throws IllegalRequestException {
		List<String> queries = TextUtil.split(query, splitter);
		SipHashMap<String, String> map = new SipHashMap<>(queries.size());

		try {
			for (int i = 0; i < queries.size(); i++) {
				String s = queries.get(i);
				int j = s.indexOf('=');
				if (j == -1) map.put(URIUtil.decodeURI(s), "");
				else map.put(URIUtil.decodeURI(s.substring(0, j)), URIUtil.decodeURI(s.substring(j+1)));
			}
		} catch (MalformedURLException e) {
			throw new IllegalRequestException(400, e.getMessage());
		}
		return map;
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

	public Map<String, String> cookie() throws IllegalRequestException {
		if (cookie == null) {
			String str = get("cookie");
			if (str == null) cookie = Collections.emptyMap();
			else cookie = parseSimpleField(str, "; ");
		}
		return cookie;
	}

	@Nonnull
	public String header(String s) {
		return getOrDefault(s, "");
	}

	public String toString() {
		return Action.toString(action) + ' ' + host() + path;
	}
}
