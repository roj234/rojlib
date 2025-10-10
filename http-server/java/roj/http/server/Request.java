package roj.http.server;

import org.jetbrains.annotations.*;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.Multimap;
import roj.crypt.Base64;
import roj.http.Cookie;
import roj.http.Headers;
import roj.http.HttpUtil;
import roj.http.hCE;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.EmbeddedChannel;
import roj.net.Event;
import roj.net.MyChannel;
import roj.net.handler.PacketMerger;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.Tokenizer;
import roj.text.URICoder;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class Request extends Headers {
	public static final String JSESSIONID = "JSESSIONID";

	private byte action;

	private String path, rawPath, version, query;

	Response response;
	byte externalRef;

	final HttpServer ctx;
	Request(HttpServer ctx) {this.ctx = ctx;}

	public Map<String, Object> threadLocal() {return ctx.ctx;}

	public MyChannel connection() { return response.connection(); }

	Request init(byte action, String path, String query, String version) throws IllegalRequestException {
		this.action = action;
		this.version = version;
		try {
			this.path = rawPath = IOUtil.normalizePath(URICoder.decodeURI(path));
			this.query = query.isEmpty() ? "" : query;
		} catch (MalformedURLException e) {
			throw IllegalRequestException.badRequest(e.getMessage());
		}
		return this;
	}

	void free() {
		path = rawPath = query = null;
		response = null;
		cookie = null;
		bodyData = queryParam = null;
		if (arguments != null) arguments.clear();
		clear();
		session_write_close();
		sessionName = JSESSIONID;
		responseHeader.clear();
	}

	/**
	 * 获取完整URL（路径 + 查询参数）。
	 * @return URL字符串
	 */
	public String getURL() {
		var sb = new CharList().append('/').append(rawPath);
		return (query.isEmpty() ? sb : sb.append('?').append(query)).toStringAndFree();
	}

	/**
	 * @see HttpUtil#getMethodName(int)
	 */
	public int action() { return action; }
	/**
	 * 获取当前路径。
	 */
	public String path() {return path;}
	/**
	 * 设置当前路径（路由器代理）。
	 */
	public void setPath(String path) {this.path = path;}
	/**
	 * 获取未修改的初始路径。
	 */
	public String rawPath() {return rawPath;}

	private Multimap<String, String> arguments;
	public String argument(String name) {return arguments().get(name);}
	public Multimap<String, String> arguments() {
		if (arguments == null) arguments = new Multimap<>();
		return arguments;
	}

	public boolean isExpecting() {return "100-continue".equalsIgnoreCase(get("Expect"));}
	/**
	 * 获取主机名（从Host或:authority头部，或本地地址）。
	 */
	public String host() {
		String host = get("host");
		if (host == null) host = get(":authority");
		return host == null ? ((InetSocketAddress) response.connection().localAddress()).getHostString() : host;
	}

	/**
	 * 检查请求来源（CORS）策略。
	 * 如果策略为null，则允许所有来源但禁用凭证（如Cookie）。
	 *
	 * @param policy 跨源策略（可为空）
	 * @return Content 如果需要立即返回响应（如预检403），否则null
	 */
	@CheckReturnValue
	public Content checkOrigin(@Nullable CrossOriginPolicy policy) {
		if (get("origin") == null) return null;
		if (policy == null) policy = CrossOriginPolicy.NO_LIMIT;

		// 预检请求
		if (action() == HttpUtil.OPTIONS && (containsKey("access-control-request-method") || containsKey("access-control-request-headers"))) {
			response.code(policy.preflightRequest(this) ? 204 : 403);
			return Content.EMPTY;
		} else {
			return policy.simpleRequest(this);
		}
	}

	// region 请求参数
	Object bodyData, queryParam;
	private Map<String, Cookie> cookie;

	public String query() {return query;}
	// 注意，所有Map<String, String>类型的返回类型都是MultiMap | Collections.emptyMap
	@UnmodifiableView
	public Map<String, String> queryParam() throws IllegalRequestException {
		if (queryParam == null) {
			if (query.isEmpty()) return Collections.emptyMap();
			try {
				queryParam = simpleValue(query, "&", true);
			} catch (MalformedURLException e) {
				throw IllegalRequestException.badRequest(e.getMessage());
			}
		}
		return Helpers.cast(queryParam);
	}

	public BodyParser bodyParser() {return (BodyParser) bodyData;}
	public ByteList body() {return (ByteList) bodyData;}

	public Map<String, String> formData() throws IllegalRequestException {
		var charset = getCharset(this);
		return formData(charset);
	}

	@NotNull
	private static Charset getCharset(Headers headers) throws IllegalRequestException {
		String charsetName = headers.getHeaderValue("content-type", "charset");
		Charset charset;
		if (charsetName == null) charset = StandardCharsets.UTF_8;
		else try {
			charset = Charset.forName(charsetName);
		} catch (Exception e) {
			throw IllegalRequestException.badRequest("不支持的字符集"+charsetName);
		}
		return charset;
	}

	public Map<String, String> formData(Charset charset) throws IllegalRequestException {
		if (bodyData instanceof ByteList data) {
			try (var ch = EmbeddedChannel.createReadonly()) {
				BodyParser parser;

				if (header("content-type").startsWith("multipart/")) {
					parser = new MultipartParser(this) {
						Charset charset;

						@Override protected @NotNull Object begin(ChannelCtx ctx, Headers header) throws IOException {
							charset = header == null ? StandardCharsets.UTF_8 : getCharset(header);
							String name = header.getHeaderValue("content-disposition", "name");
							if (name == null) throw new FastFailException("不支持的分块头");
							return name;
						}

						@Override protected void data(ChannelCtx ctx, DynByteBuf buf) {
							data.put(value.toString(), charset != StandardCharsets.UTF_8 ? new String(buf.toByteArray(), charset) : buf.readUTF(buf.readableBytes()));
						}
					};
				} else {
					if (charset != StandardCharsets.UTF_8) {
						var fc = FastCharset.getInstance(charset);
						if (fc == null) throw new FastFailException("不支持的字符集"+charset);
						URICoder.CHARSET.set(fc);
					}

					// 处理加号这种遗留行为
					parser = new UrlEncodedParser();
				}

				ch.addLast("body", parser);

				boolean isHCE = !get("content-encoding", "identity").equals("identity");
				if (isHCE) {
					ch.addBefore("body", "readLimit", new PacketMerger() {
						int readLimit = data.capacity();

						@Override
						public void onEvent(ChannelCtx ctx, Event event) throws IOException {
							if (event.id.equals(hCE.IN_END)) {
								parser.onSuccess(merged);
								parser.onComplete();
							}
						}

						@Override
						public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
							DynByteBuf buf = (DynByteBuf) msg;
							readLimit -= buf.readableBytes();
							if (readLimit < 0) throw new FastFailException("解压后数据太多");

							mergedRead(ctx, buf);
						}
					});
					hCE.apply(ch.handler("readLimit"), this, false, Long.MAX_VALUE);
				}

				ch.fireChannelRead(data);

				if (isHCE) {
					ch.postEvent(new Event(MyChannel.IN_EOF));
				} else {
					parser.onSuccess(data);
					parser.onComplete();
				}

				bodyData = parser.getMapLikeResult();
			} catch (IllegalArgumentException e) {
				throw new IllegalRequestException(500, "Request.formData()不支持二进制(文件)数据,请自定义BodyParser");
			} catch (Exception e) {
				throw IllegalRequestException.badRequest(e.getMessage());
			} finally {
				URICoder.CHARSET.remove();
			}
		}
		return bodyData == null ? Collections.emptyMap() : Helpers.cast(bodyData);
	}

	/**
	 * PHP $_REQUEST，先GET后POST
	 * @return 合并的值
	 */
	public Map<String, String> requestFields() throws IllegalRequestException {
		var pf = formData();
		if (pf == null) return queryParam();
		var gf = new HashMap<>(queryParam());
		gf.putAll(pf);
		return gf;
	}

	public final Map<String, String> rawCookie() throws MalformedURLException {
		String field = header("cookie");
		if (field.isEmpty()) return Collections.emptyMap();
		return simpleValue(field, "; ", true);
	}
	public Map<String, Cookie> cookie() throws IllegalRequestException {
		if (cookie == null) {
			Map<String, String> fromClient;
			try {
				fromClient = rawCookie();
				if (fromClient.isEmpty()) return cookie = new HashMap<>();
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
	@Contract(pure = true)
	public Response response() {return response;}
	@Contract(pure = true)
	public Headers responseHeader() {return responseHeader;}

	/**
	 * 发送Cookie到客户端（Set-Cookie头部）。
	 *
	 * @param cookies Cookie列表
	 */
	public final void sendCookieToClient(List<Cookie> cookies) {
		var sb = new CharList();

		for (int i = 0; i < cookies.size(); i++) {
			cookies.get(i).write(sb, true);
			responseHeader.add("set-cookie", sb.toString());
			sb.clear();
		}

		sb._free();
	}
	void packToHeader() {
		if (cookie != null) {
			List<Cookie> modified = new ArrayList<>();
			for (var entry : cookie.entrySet()) {
				Cookie c = entry.getValue();
				if (c.isDirty()) modified.add(c);
			}
			if (!modified.isEmpty()) sendCookieToClient(modified);
		}
	}

	//region session
	private String sessionName = JSESSIONID, sessionId;
	private Map<String,Object> session;

	public Request sessionName(String prefix) {this.sessionName = prefix; return this;}
	public Map<String,Object> session() throws IllegalRequestException { return session(true); }
	public Map<String,Object> session(boolean createIfNonExist) throws IllegalRequestException {
		var storage = ctx.getSessionStorage();
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
			if (session == null && createIfNonExist) session = new HashMap<>();
		}
		return session;
	}
	public void session_write_close() {
		if (session != null) {
			ctx.getSessionStorage().put(sessionId, session);
			session = null;
			sessionId = null;
		}
	}
	public void session_destroy() throws IllegalRequestException {
		session(false);
		if (sessionId != null) {
			ctx.getSessionStorage().remove(sessionId);
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
		String auth = header("authorization");
		if (auth.regionMatches(true, 0, "basic ", 0, 6)) {
			try {
				var out = new ByteList();
				auth = Base64.decode(auth.substring(6), out).readUTF(out.readableBytes());
				out.release();

				int pos = auth.indexOf(':');
				var user = auth.substring(0,pos);
				var pass = auth.substring(pos+1);
				var result = verifier.apply(user, pass);
				if (result) return;
			} catch (Exception e) {
				throw new IllegalRequestException(400);
			}
		}

		CharList sb = new CharList().append("Basic realm=\"");
		Tokenizer.escape(sb, URICoder.encodeURI(message));
		responseHeader.put("www-authenticate", sb.append("\"").toStringAndFree());

		throw new IllegalRequestException(401);
	}

	@Nullable
	@Contract(pure = true)
	public String bearerAuthorization() {
		String auth = header("authorization");
		return auth.regionMatches(true, 0, "bearer ", 0, 7) ? auth.substring(7) : null;
	}

	private boolean checkProxyToken() {
		String proxySec = get("x-proxy-sec");
		if (proxySec == null || HttpServer.proxySecret == null) return false;
		if (proxySec.equals(HttpServer.proxySecret)) return true;
		Helpers.athrow(new IllegalRequestException(400, "Bad proxy-sec"));
		return false;
	}

	public boolean isSecure() {
		var conn = response;
		if (conn == null) return false;

		if (checkProxyToken()) {
			String field = header("x-proxy-https");
			return !field.isEmpty() && !field.equalsIgnoreCase("off") && !field.equalsIgnoreCase("false");
		}

		return response.connection().handler("h11@tls") != null;
	}

	public InetSocketAddress remoteAddress() {
		var conn = response;
		if (conn == null) return null;

		if (checkProxyToken()) {
			var forwardedFor = header("x-proxy-addr");
			int i = forwardedFor.indexOf(',');
			if (i < 0) i = forwardedFor.length();
			return new InetSocketAddress(forwardedFor.substring(0, i), Integer.parseInt(header("x-proxy-port")));
		}

		return (InetSocketAddress) conn.connection().remoteAddress();
	}

	public CharList firstLine(CharList sb) {
		sb.append(HttpUtil.getMethodName(action)).append(isSecure()?" https://":" http://").append(host()).append('/').append(rawPath);
		if (query != "") sb.append('?').append(query);
		return sb.append(' ').append(version);
	}
	public String toString() {
		var sb = firstLine(new CharList()).append('\n');
		encode(sb);
		return sb.toStringAndFree();
	}

	/**
	 * (默认)该请求对应的Request对象会在连接关闭后共享给其它请求使用
	 */
	public void shared() {externalRef = 0;}
	/**
	 * Request对象不会在连接关闭后被共享
	 */
	public void unshared() {externalRef = 1;}
}