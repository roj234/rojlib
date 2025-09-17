package roj.http.server;

import roj.ci.annotation.IndirectReference;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.RingBuffer;
import roj.http.Headers;
import roj.http.HttpUtil;
import roj.http.h2.H2Connection;
import roj.net.ChannelHandler;
import roj.net.ServerLaunch;
import roj.text.logging.Level;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.regex.Pattern;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2024/7/13 17:34
 */
public final class HttpServer implements BiConsumer<String, String> {
	public static final String SERVER_NAME = "openresty";
	private static final int KEEPALIVE_POOL = 32;
	private static final int REQUEST_POOL = 10;

	private static final ThreadLocal<HttpServer> TL = ThreadLocal.withInitial(HttpServer::new);
	public static HttpServer getInstance() {return TL.get();}

	/**
	 * 设置日志级别
	 * @param level 日志级别
	 */
	public static void setLevel(Level level) {
		HttpServer11.LOGGER.setLevel(level);
		H2Connection.LOGGER.setLevel(level);
	}

	/**
	 * 创建HTTP服务器实例
	 * @param addr 服务器绑定地址
	 * @param backlog 连接队列长度
	 * @param router 请求路由器
	 * @return ServerLaunch实例
	 * @throws IOException 如果绑定地址失败
	 */
	public static ServerLaunch simple(InetSocketAddress addr, int backlog, Router router) throws IOException {return simple(addr, backlog, router, null);}

	/**
	 * 创建HTTP服务器实例（带别名）
	 * @param addr 服务器绑定地址
	 * @param backlog 连接队列长度
	 * @param router 请求路由器
	 * @param alias 服务器别名
	 * @return ServerLaunch实例
	 * @throws IOException 如果绑定地址失败
	 */
	public static ServerLaunch simple(InetSocketAddress addr, int backlog, Router router, String alias) throws IOException {
		return ServerLaunch.tcp(alias)
				.bind(addr, backlog)
				.option(StandardSocketOptions.SO_REUSEADDR, true)
				.initializator(ctx -> ctx.addLast("http:server", http2(router)));
	}

	/**
	 * 创建一个新的HTTP1.1请求处理器
	 * @param router 请求路由器
	 * @return 用于添加到MyChannel实例的请求处理器实例
	 */
	public static ChannelHandler http(Router router) {return new HttpServer11(router);}
	/**
	 * 创建一个新的HTTP2请求处理器
	 * @param router 请求路由器
	 * @return 用于添加到MyChannel实例的请求处理器实例
	 */
	public static ChannelHandler http2(Router router) {return new H2Upgrade(router);}


	private static final Pattern MOBILE = Pattern.compile("(mobile|wap|phone|ios|android)", Pattern.CASE_INSENSITIVE);
	public static boolean isMobile(Request req) {return "?1".equals(req.header("sec-ch-ua-mobile")) || MOBILE.matcher(req.header("user-agent")).matches();}

	/**
	 * 这个函数可能会在运行时被CodeWeaver注入，从而生成更好看的错误页面
	 */
	@IndirectReference
	static Content onUncaughtError(Request req, Throwable e) {return Content.httpError(500);}

	/** 代理密钥，用于验证来源请求的字段是否可信 */
	public static String proxySecret;

	public static IntFunction<Content> globalHttpError;
	public IntFunction<Content> httpError;

	public static SessionStorage globalSessionStorage;
	public SessionStorage sessionStorage;

	public final HashMap<String, Object> ctx = new HashMap<>();

	private HttpServer() {}

	public Content createHttpError(int code) {
		var fn = httpError;
		if (fn == null) fn = globalHttpError;
		if (fn != null) return fn.apply(code);

		String desc = code+" "+HttpUtil.getCodeDescription(code);
		return Content.html("<title>"+desc+"</title><center><h1>"+desc+"</h1><hr/><div>"+SERVER_NAME+"</div></center>");
	}

	public SessionStorage getSessionStorage() {return sessionStorage == null ? globalSessionStorage : sessionStorage;}

	final RingBuffer<HttpServer11> keepalive = RingBuffer.lazy(KEEPALIVE_POOL);

	private final ArrayList<Request> requests = new ArrayList<>(REQUEST_POOL);
	final Request request() {
		var req = requests.pop();
		if (req == null) req = new Request(this);
		return req;
	}
	final void reserve(Request req) {
		if (req.externalRef != 0) return;

		if (req.ctx != this) throw new IllegalArgumentException("请求"+req+"不属于这个线程");
		req.free();
		if (requests.size() < REQUEST_POOL) requests.add(req);
	}

	final ArrayList<Deflater> gzDef = new ArrayList<>(10);
	final ArrayList<Deflater> deDef = new ArrayList<>(10);
	final Deflater deflater(boolean nowrap) {
		var def = (nowrap ? gzDef : deDef).pop();
		if (def == null) def = new Deflater(Deflater.DEFAULT_COMPRESSION, nowrap);
		else def.reset();
		return def;
	}
	final void reserve(Deflater def, boolean nowrap) {
		var defs = nowrap ? gzDef : deDef;
		if (defs.size() < 10) {
			def.reset();
			defs.add(def);
		} else def.end();
	}

	static final int ENC_PLAIN = 0, ENC_DEFLATE = 1, ENC_GZIP = 2;
	int parseAcceptEncoding(String field) throws IllegalRequestException {
		_maxQ = 0;
		_mustCompress = false;
		Headers.complexValue(field, this, false);
		if (_mustCompress && _enc == ENC_PLAIN) throw new IllegalRequestException(406);
		return _enc;
	}

	private float _maxQ;
	private byte _enc;
	private boolean _mustCompress;

	private static int isTypeSupported(String type) {
		return switch (type) {
			case "*", "deflate" -> ENC_DEFLATE;
			case "gzip" -> ENC_GZIP;
			//case "br":
			default -> -1;
		};
	}
	@Override
	public void accept(String k, String v) {
		int sup = isTypeSupported(k);
		if (sup >= 0) {
			float Q = 1;
			if (!v.isEmpty()) {
				try {
					Q = Float.parseFloat(v);
				} catch (NumberFormatException e) {
					Q = 0;
				}
			}

			if (Q > _maxQ || (_enc == ENC_GZIP && sup == ENC_DEFLATE)) {
				_enc = (byte) sup;
				_maxQ = Q;
			}

			if (Q == 0 && (k.equals("*") || k.equals("identity"))) {
				_mustCompress = true;
			}
		}
	}
}