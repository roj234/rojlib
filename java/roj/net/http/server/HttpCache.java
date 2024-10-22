package roj.net.http.server;

import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.io.storage.DataStorage;
import roj.net.http.Headers;
import roj.text.ACalendar;

import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2024/7/13 0013 17:34
 */
public final class HttpCache implements BiConsumer<String, String> {
	public static Function<Request, InetSocketAddress> proxyRequestRetainer;

	private static final int KEEPALIVE_MAX = 32;
	private static final int MAX_REQEUST_CACHE = 10;

	private static final ThreadLocal<HttpCache> TL = ThreadLocal.withInitial(HttpCache::new);
	public static HttpCache getInstance() {return TL.get();}

	public static DataStorage getSessionStorage() {
		DataStorage storage = getInstance().sessionStorage;
		return storage == null ? globalSessionStorage : storage;
	}

	private HttpCache() {}

	public final ACalendar date = new ACalendar(null);
	public String toRFC(long time) {return date.toRFCString(time);}

	public final MyHashMap<String, Object> ctx = new MyHashMap<>();

	public static DataStorage globalSessionStorage;
	public DataStorage sessionStorage;

	public final SimpleList<Object> headers = new SimpleList<>();

	final RingBuffer<HttpServer11> hanging = new RingBuffer<>(KEEPALIVE_MAX);

	final SimpleList<Request> requests = new SimpleList<>(MAX_REQEUST_CACHE);
	final Request request() {
		var req = requests.pop();
		if (req == null) req = new Request(ctx);
		return req;
	}
	public void reserve(Request req) {
		assert req.localCtx() == ctx;
		if (requests.size() < MAX_REQEUST_CACHE) {
			req.free();
			requests.add(req);
		}
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
	public int parseAcceptEncoding(String s) {
		_maxQ = 0;
		Headers.complexValue(s, this, false);
		return _enc;
	}

	private float _maxQ;
	private byte _enc;

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
		}
	}
}