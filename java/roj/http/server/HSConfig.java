package roj.http.server;

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.http.Headers;
import roj.http.HttpUtil;
import roj.text.DateParser;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2024/7/13 17:34
 */
public final class HSConfig implements BiConsumer<String, String> {
	private static final int KEEPALIVE_MAX = 32;
	private static final int REQUEST_CACHE = 10;

	public static String proxySecret;

	public static IntFunction<Content> globalHttpError;
	public IntFunction<Content> httpError;

	public static SessionStorage globalSessionStorage;
	public SessionStorage sessionStorage;

	public final MyHashMap<String, Object> ctx = new MyHashMap<>();

	private static final ThreadLocal<HSConfig> TL = ThreadLocal.withInitial(HSConfig::new);
	public static HSConfig getInstance() {return TL.get();}
	private HSConfig() {}

	public Content createHttpError(int code) {
		var fn = httpError;
		if (fn == null) fn = globalHttpError;
		if (fn != null) return fn.apply(code);

		String desc = code+" "+HttpUtil.getCodeDescription(code);
		return Content.html("<title>"+desc+"</title><center><h1>"+desc+"</h1><hr/><div>"+HttpServer11.SERVER_NAME+"</div></center>");
	}

	public SessionStorage getSessionStorage() {return sessionStorage == null ? globalSessionStorage : sessionStorage;}

	private final DateParser dateParser = DateParser.GMT();
	public String toRFC(long time) {return dateParser.toRFCString(time);}

	final RingBuffer<HttpServer11> keepalive = RingBuffer.lazy(KEEPALIVE_MAX);

	private final SimpleList<Request> requests = new SimpleList<>(REQUEST_CACHE);
	final Request request() {
		var req = requests.pop();
		if (req == null) req = new Request(this);
		return req;
	}
	final void reserve(Request req) {
		if (req.externalRef != 0) return;

		if (req.ctx != this) throw new IllegalArgumentException("请求"+req+"不属于这个线程");
		req.free();
		if (requests.size() < REQUEST_CACHE) requests.add(req);
	}

	private MessageDigest sha1;
	public MessageDigest sha1() {
		if (sha1 == null) {
			try {
				sha1 = MessageDigest.getInstance("SHA1");
			} catch (NoSuchAlgorithmException e) {
				assert false;
			}
		} else {
			sha1.reset();
		}
		return sha1;
	}

	final SimpleList<Deflater> gzDef = new SimpleList<>(10);
	final SimpleList<Deflater> deDef = new SimpleList<>(10);
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