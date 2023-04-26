package roj.net.http.srv;

import roj.collect.MyHashMap;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

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

	public static FileResponse cached(Request req) {
		Map<String, Object> map = req.threadContext();

		FileResponse resp = (FileResponse) map.get("FileResp");
		if (resp == null) map.put("FileResp", resp = new FileResponse());
		return resp.file == null ? resp : new FileResponse();
	}

	public Response init(Request req, FileInfo info) {
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
				data[(i << 1)] = Long.parseLong(s.substring(1));
				data[(i << 1) + 1] = len-1;
			} else {
				// start, end
				long o = data[i << 1] = Long.parseLong(s.substring(0, j));
				data[(i << 1) + 1] = j == s.length() - 1 ? len - 1 : Long.parseLong(s.substring(j + 1));
			}

			total += data[(i<<1)+1] - data[i<<1] + 1;
		}

		req.handler.header("Content-Length", Long.toString(total));
		if (data.length == 2) {
			req.handler.header("Content-Range", "bytes " + data[0] + '-' + data[1] + '/' + len);
		} else if (data.length > 2) {
			ByteList rnd = ByteList.allocate(16);
			ThreadLocalRandom.current().nextBytes(rnd.list);
			rnd.wIndex(rnd.capacity());

			CharList b = IOUtil.getSharedCharBuf().append("--MultipartBoundary--");
			Base64.encode(rnd, b, Base64.B64_URL_SAFE).append("----");

			req.handler.header("Content-Type", "multipart/byteranges; boundary="+b);

			splitter = b.insert(0, "--").toString();
		}

		return plus(206, req);
	}

	private Response plus(int r, Request req) {
		ResponseHeader h = req.handler;
		h.code(r).date();
		if (r != 304) {
			h.header("Last-Modified", HttpServer11.TSO.get().toRFC(file.lastModified()));
			if (r == 206) return this;

			if (file.getETag() != null) h.header("ETag", file.getETag());
			if (def != 2 && (file.stats() & (1<<(2+def))) != 0) h.header("Accept-Ranges", "bytes");
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
			if (this.def != 2) h.put("Content-Length", Long.toString(remain));
		} else {
			in = file.get(def, ranges[0]);
			remain = ranges[1]-ranges[0]+1;
		}

		file.prepare(srv, h);
	}

	@Override
	public boolean send(ResponseWriter rh) throws IOException {
		if (remain < 0) return false;

		if (remain == 0) {
			in.close();

			if (ranges.length > 2) {
				DynByteBuf t = rh.ch().allocate(true, splitter.length()+2).putAscii(splitter);
				if (off == ranges.length) t.putAscii("--");

				try {
					rh.write(t);
				} finally {
					rh.ch().reserve(t);
				}
			}
			if (off == ranges.length) {
				remain = -1;
				in = null;
				return false;
			}

			off += 2;
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
