package roj.net.http.srv;

import roj.collect.MyHashMap;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.net.http.IllegalRequestException;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Roj234
 * @since 2023/2/3 0003 17:18
 */
public class FileResponse implements Response {
	static final class Mime {
		String type;
		boolean zip;

		public Mime(String type) {
			this.type = type;
		}
	}

	static MyHashMap<String, Mime> mimeTypes;
	static {
		mimeTypes = new MyHashMap<>();
		mimeTypes.put("*", new FileResponse.Mime("text/plain"));
	}

	public static synchronized void loadMimeMap(CharSequence text) {
		ArrayList<String> arr = new ArrayList<>();
		mimeTypes.clear();
		for (String line : new LineReader(text)) {
			arr.clear();
			TextUtil.split(arr, line, ' ');
			Mime mime = new Mime(arr.get(1).toLowerCase(Locale.ROOT));
			mime.zip = arr.size() > 2 && arr.get(2).equalsIgnoreCase("gz");
			mimeTypes.put(arr.get(0).toLowerCase(Locale.ROOT), mime);
		}
		mimeTypes.putIfAbsent("*", new Mime("application/octet-stream"));
	}

	public static String getMimeType(String ext) {
		ext = ext.substring(ext.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		String type = mimeTypes.getOrDefault(ext, mimeTypes.get("*")).type;
		switch (ext) {
			case "html":
			case "css":
			case "js":
				type += "; charset=UTF-8";
				break;
		}
		return type;
	}

	private FileInfo file;

	private byte def;

	private long[] ranges;
	private int off = 0;

	private InputStream in;
	private long remain;

	private String splitter;

	FileResponse() {}

	public static Response response(Request req, FileInfo info) {
		Map<String, Object> map = req.threadContext();

		FileResponse resp = (FileResponse) map.get("h11:send_file");
		if (resp == null) map.put("h11:send_file", resp = new FileResponse());
		if (resp.file != null) resp = new FileResponse();

		return resp.init(req, info);
	}

	private Response init(Request req, FileInfo info) {
		def = 0;
		file = info;

		int stat = info.stats();

		if (req.getFieldValue("accept-encoding", "deflate") != null) {
			if ((stat & FileInfo.FILE_DEFLATED) != 0) {
				def = 1;
				req.handler.header("Content-Encoding", "deflate");
			} else if ((stat & FileInfo.FILE_WANT_DEFLATE) != 0) {
				def = 2;
				req.handler.compressed();
			}
		}

		// 当与 If-Modified-Since 一同使用的时候，If-None-Match 优先级更高（假如服务器支持的话）。
		if (req.containsKey("If-Match")) {
			List<String> tags = TextUtil.split(req.get("If-Match"), ", ");
			if (!tags.contains(file.getETag())) {
				// Cache-Control、Content-Location、Date、ETag、Expires 和 Vary 。
				return plus(304, req);
			}
		} else if (req.containsKey("If-None-Match")) {
			// 当且仅当服务器上没有任何资源的 ETag 属性值与这个首部中列出的相匹配的时候，服务器端才会返回所请求的资源
			List<String> tags = TextUtil.split(req.get("If-None-Match"), ", ");
			if (tags.contains(file.getETag())) {
				return plus(304, req);
			}
		}

		ResponseHeader rh = req.handler;
		if (req.containsKey("If-Modified-Since")) {
			long time;
			try {
				time = ACalendar.parseRFCDate(req.get("If-Modified-Since")) / 1000;
			} catch (Exception e) {
				rh.code(400);
				return null;
			}

			if (file.lastModified() / 1000 <= time) {
				return plus(304, req);
			}
		} else if (req.containsKey("If-Unmodified-Since")) {
			long time;
			try {
				time = ACalendar.parseRFCDate(req.get("If-Unmodified-Since")) / 1000;
			} catch (Exception e) {
				rh.code(400);
				return null;
			}
			// 当资源在指定的时间之后没有修改，服务器才会返回请求的资源
			if (file.lastModified() / 1000 > time) {
				rh.code(412);
				return null;
			}
		}

		ranges = ArrayCache.LONGS;

		if (req.containsKey("If-Range")) {
			String s = req.get("If-Range");
			if (s.endsWith("\"")) {
				if (!s.equals(file.getETag())) {
					plus(200, req);
					return this;
				}
			} else {
				long time;
				try {
					time = ACalendar.parseRFCDate(s) / 1000;
				} catch (Exception e) {
					rh.code(400);
					return null;
				}
				if (file.lastModified() / 1000 > time) {
					plus(200, req);
					return this;
				}
			}
		}

		if (!req.containsKey("Range") || (stat & (def==1? FileInfo.FILE_RA_DEFLATE : FileInfo.FILE_RA)) == 0) {
			plus(200, req);
			return this;
		}

		String s = req.get("Range");
		if (!s.startsWith("bytes=")) {
			rh.code(400);
			return StringResponse.of("not 'bytes' range");
		}

		long len = file.length(def == 1);

		List<String> ranges = TextUtil.split(s.substring(6), ", ");
		long[] data = this.ranges = new long[ranges.size() << 1];

		long total = 0;
		for (int i = 0; i < ranges.size(); i++) {
			s = ranges.get(i);
			int j = s.indexOf('-');
			if (j == 0) {
				data[(i << 1)] = 0;
				data[(i << 1) + 1] = parseLong(s.substring(1), len);
			} else {
				// start, end
				long o = data[i << 1] = parseLong(s.substring(0, j), len);
				data[(i << 1) + 1] = j == s.length() - 1 ? len - 1 : parseLong(s.substring(j + 1), len);
			}

			long seglen = data[(i << 1) + 1] - data[i << 1] + 1;
			if (seglen < 0) Helpers.athrow(new IllegalRequestException(416, (Response) null));
			total += seglen;
		}

		if (data.length == 2) {
			req.responseHeader.put("content-range", "bytes " + data[0] + '-' + data[1] + '/' + len);
			req.responseHeader.put("content-length", Long.toString(total));
		} else if (data.length > 2) {
			ByteList rnd = ByteList.allocate(16);
			ThreadLocalRandom.current().nextBytes(rnd.list);
			rnd.wIndex(rnd.capacity());

			CharList b = new CharList().append("multipart/byteranges; boundary=\"BARFOO");
			TextUtil.bytes2hex(rnd.list,0,16,b).append("\"");

			req.responseHeader.put("content-type", b.toString());

			// 看了一眼标准，第一个可以不加\r\n，不过无所谓
			b.replace(28, 32, "\r\n--");
			splitter = b.toString(28, b.length()-1);
			b._free();

			req.handler.chunked();
		}

		return plus(206, req);
	}

	private static long parseLong(String num, long max) {
		aa: {
			if (TextUtil.isNumber(num) != 0) break aa;
			long s = Long.parseLong(num);
			if (s < 0 || s >= max) break aa;
			return s;
		}
		Helpers.athrow(new IllegalRequestException(416, (Response) null));
		return 0;
	}

	private Response plus(int r, Request req) {
		ResponseHeader h = req.handler;
		h.code(r).date();
		if (r != 304) {
			h.header("last-modified", HttpServer11.TSO.get().toRFC(file.lastModified()));
			if (r == 206) return this;

			if (file.getETag() != null) h.header("etag", file.getETag());
			if (def != 2 && (file.stats() & (1<<(2+def))) != 0) h.header("accept-ranges", "bytes");
		}
		return null;
	}

	@Override
	public void prepare(ResponseHeader srv, Headers h) throws IOException {
		boolean def = this.def == 1;
		if (def) srv.uncompressed();

		off = 0;
		if (ranges.length == 0) {
			in = file.get(def, 0);
			remain = file.length(def);
			if (this.def != 2) h.put("content-length", Long.toString(remain));
		} else {
			if (ranges.length > 2) {
				off = -2;
				remain = 0;
			} else {
				in = file.get(def, ranges[0]);
				remain = ranges[1]-ranges[0]+1;
			}
		}

		file.prepare(srv, h);
	}

	@Override
	public boolean send(ResponseWriter rh) throws IOException {
		if (remain < 0) return false;

		if (remain == 0) {
			if (in != null) in.close();
			off += 2;

			if (ranges.length > 2) {
				DynByteBuf t = rh.ch().allocate(true, splitter.length()+128).putAscii(splitter);
				if (off == ranges.length) t.putAscii("--");
				t.putAscii("\r\n");

				if (off < ranges.length) {
					t.putAscii("content-range: byte "+ranges[off]+"-"+ranges[off+1]+"/"+file.length(def==1)+"\r\n\r\n");
				}

				try {
					rh.write(t);
				} finally {
					BufferPool.reserve(t);
				}
			}

			if (off >= ranges.length) {
				remain = -1;
				in = null;
				return false;
			}

			in = file.get(def == 1, ranges[off]);
			remain = ranges[off+1] - ranges[off] + 1;
		}

		remain -= rh.write(in, (int) MathUtils.clamp(remain, 0, Integer.MAX_VALUE));

		return true;
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		if (in != null) {
			in.close();
			in = null;
		}

		if (file != null) {
			file.release(ctx);
			file = null;
		}
	}
}
