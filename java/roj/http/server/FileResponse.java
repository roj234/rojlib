package roj.http.server;

import roj.http.Headers;
import roj.http.IllegalRequestException;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.net.ChannelCtx;
import roj.net.SendfilePkt;
import roj.text.CharList;
import roj.text.DateFormat;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Roj234
 * @since 2023/2/3 0003 17:18
 */
final class FileResponse implements Response {
	private FileInfo file;

	//def标记 2bit
	// 0 不能压缩
	// 1 已经压缩 (支持随机访问)
	// 2 允许压缩
	//sendfile标记 1bit
	// 4 禁用sendfile (由于带宽限制)
	private byte flag;

	private long[] ranges;
	private int off = 0;

	private SendfilePkt sendfile;
	private InputStream in;
	private long remain;

	private String splitter;

	FileResponse() {}
	Response init(int flag, Request req, FileInfo info) {
		file = info;

		String v;
		var rh = req.handler;
		String eTag = file.getETag();

		checkIfModified: {
			// 当与修改时间一同使用的时候，ETag优先级更高。
			if (eTag != null) {
				if ((v = req.get("If-Match")) != null) {
					List<String> tags = TextUtil.split(v, ", ");
					if (!hasETag(tags, eTag) && !tags.contains("*")) return plus(304, req);
					break checkIfModified;
				} else if ((v = req.get("If-None-Match")) != null) {
					// 当且仅当服务器上没有任何资源的 ETag 属性值与这个首部中列出的相匹配的时候，服务器端才会返回所请求的资源
					if (hasETag(TextUtil.split(v, ", "), eTag)) return plus(304, req);
					break checkIfModified;
				}
			}

			if ((v = req.get("If-Modified-Since")) != null) {
				long time;
				try {
					time = DateFormat.parseRFCDate(v) / 1000;
				} catch (Exception e) {
					rh.code(400);
					return Response.httpError(400);
				}

				if (file.lastModified() / 1000 <= time) return plus(304, req);
			} else if ((v = req.get("If-Unmodified-Since")) != null) {
				long time;
				try {
					time = DateFormat.parseRFCDate(v) / 1000;
				} catch (Exception e) {
					rh.code(400);
					return Response.httpError(400);
				}
				// 当资源在指定的时间之后没有修改，服务器才会返回请求的资源
				// 除以 1000 是RFC时间戳精度问题
				if (file.lastModified() / 1000 > time) {
					rh.code(412);
					return null;
				}
			}
		}

		int stat = info.stats();

		int def = 0;
		if (req.getFieldValue("accept-encoding", "deflate") != null) {
			if ((stat & FileInfo.FILE_DEFLATED) != 0) {
				def = 1;
				req.responseHeader.put("content-encoding", "deflate");
			} else if ((stat & FileInfo.FILE_CAN_DEFLATE) != 0) {
				def = 2;
				rh.enableCompression();
			}
		}
		this.flag = (byte) (flag | def);

		ranges = ArrayCache.LONGS;

		if ((v = req.get("If-Range")) != null) {
			if (v.endsWith("\"")) {
				if (eTag == null || !v.regionMatches(1, eTag, 0, v.length()-2)) {
					plus(200, req);
					return this;
				}
			} else {
				long time;
				try {
					time = DateFormat.parseRFCDate(v) / 1000;
				} catch (Exception e) {
					rh.code(400);
					return Response.httpError(400);
				}
				if (file.lastModified() / 1000 > time) {
					plus(200, req);
					return this;
				}
			}
		}

		if ((v = req.get("Range")) == null || (stat & (def ==1 ? FileInfo.FILE_DEFLATED : FileInfo.FILE_RA)) == 0) {
			plus(200, req);
			return this;
		}

		if (!v.startsWith("bytes=")) {
			rh.code(416);
			return Response.httpError(416);
		}

		long len = file.length(def == 1);

		List<String> ranges = TextUtil.split(v.substring(6), ", ");
		if (ranges.size() > 16) {
			rh.code(416);
			return Response.httpError(416);
		}
		long[] data = this.ranges = new long[ranges.size() << 1];

		long total = 0;
		for (int i = 0; i < ranges.size(); i++) {
			v = ranges.get(i);
			int j = v.indexOf('-');
			long start, end;
			if (j == 0) {
				start = len - parseLong(v.substring(1), len);
				end = len - 1;
			} else {
				// start, end
				// 加1是允许长度为0时的[Range: 0-]请求
				start = parseLong(v.substring(0, j), len+1);
				end = j == v.length() - 1 ? len - 1 : parseLong(v.substring(j + 1), len);
			}

			data[i << 1] = start;
			data[(i << 1) + 1] = end;

			long seglen = end - start + 1;
			if (seglen <= 0) {
				rh.code(416);
				return Response.httpError(416);
			}
			total += seglen;
		}

		if (data.length == 2) {
			req.responseHeader.put("content-range", "bytes "+data[0]+'-'+data[1]+'/'+len);
			req.responseHeader.put("content-length", Long.toString(total));
		} else if (data.length > 2) {
			ByteList rnd = ByteList.allocate(16);
			ThreadLocalRandom.current().nextBytes(rnd.list);
			rnd.wIndex(rnd.capacity());

			CharList b = new CharList().append("multipart/byteranges; boundary=\"");
			rnd.hex(b).append("\"");

			req.responseHeader.put("content-type", b.toString());

			// 看了一眼标准，第一个可以不加\r\n，不过无所谓
			b.replace(28, 32, "\r\n--");
			splitter = b.substring(28, b.length()-1);
			b._free();
		}

		return plus(206, req);
	}

	private static boolean hasETag(List<String> tags, String eTag) {
		String v;
		for (int i = 0; i < tags.size(); i++) {
			v = tags.get(i);
			if (v.equals(eTag) || (v.startsWith("W/") && !eTag.startsWith("W/") && eTag.regionMatches(0, v, 2, eTag.length())))
				return true;
		}
		return false;
	}

	private static long parseLong(String str, long max) {
		if (TextUtil.isNumber(str) == 0) {
			long num = Long.parseLong(str);
			if (num >= 0 && num < max) return num;
		}
		Helpers.athrow(new IllegalRequestException(416));
		return 0;
	}

	private Response plus(int r, Request req) {
		var h = req.handler;
		h.code(r).date();
		if (r != 304) {
			h.header("last-modified", HttpCache.getInstance().toRFC(file.lastModified()));
			if (r == 206) return this;

			// 需要注意的是，服务器端在生成状态码为 304 的响应的时候，必须同时生成以下会存在于对应的 200 响应中的首部：Cache-Control、Content-Location、Date、ETag、Expires 和 Vary。
			// 我怎么感觉我又没遵循标准了（
			String tag = file.getETag();
			if (tag != null) h.header("etag", tag);
			var def = flag&3;
			if (def != 2 && (file.stats() & (1<<def)) != 0) h.header("accept-ranges", "bytes");
		}
		return null;
	}

	@Override
	public void prepare(ResponseHeader rh, Headers h) throws IOException {
		boolean def = (flag&3) == 1;

		FileChannel fch;
		off = 0;
		if (ranges.length == 0) {
			remain = file.length(def);
			if (cantSendfile(rh) || (fch = file.getSendFile(def)) == null) {
				in = file.get(def, 0);
			} else {
				sendfile = new SendfilePkt(fch);
				sendfile.length = remain;
			}
			if ((flag&3) != 2) h.put("content-length", Long.toString(remain));
		} else {
			if (cantSendfile(rh) || (fch = file.getSendFile(def)) == null) {
				in = file.get(def, ranges[0]);
			} else {
				sendfile = new SendfilePkt(fch, ranges[0], remain);
			}

			if (ranges.length > 2) {
				off = -2;
				remain = 0;
			} else {
				remain = ranges[1]-ranges[0]+1;
			}
		}

		file.prepare(rh, h);
	}
	private boolean cantSendfile(ResponseHeader rh) {return (flag&6) != 0 || !rh.ch().canSendfile();}

	@Override
	public boolean send(ResponseWriter rh) throws IOException {
		if (remain < 0) return false;

		if (remain == 0) {
			off += 2;

			if (ranges.length > 2) {
				String nextHeader = off >= ranges.length ? "--" : "\r\ncontent-range: byte "+ranges[off]+'-'+ranges[off+1]+'/'+file.length((flag&3) == 1)+"\r\n\r\n";
				var t = rh.ch().alloc().allocate(true, splitter.length()+nextHeader.length());
				try {
					rh.write(t.putAscii(splitter).putAscii(nextHeader));
				} finally {
					BufferPool.reserve(t);
				}
			}

			if (off >= ranges.length) {remain = -1;return false;}

			remain = ranges[off+1] - ranges[off] + 1;
			if (sendfile != null) {
				sendfile.offset = ranges[off];
				sendfile.length = remain;
			} else if (off > 0) {
				long delta;
				if ((file.stats()&FileInfo.FILE_RA) == 0 && (delta = ranges[off]/*next position*/ - ranges[off-1]/*in position*/) >= 0) {
					IOUtil.skipFully(in, delta);
				} else {
					IOUtil.closeSilently(in);
					in = file.get((flag&3) == 1, ranges[off]);
				}
			}
		}

		if (sendfile != null) {
			var prevLength = sendfile.length;
			var limiter = rh.getStreamLimiter();
			if (limiter != null) sendfile.length = limiter.limit((int) Math.min(sendfile.length, Integer.MAX_VALUE));
			rh.ch().fireChannelWrite(sendfile);
			sendfile.length = prevLength;
			sendfile.flip();
			remain = sendfile.length;
		} else {
			remain -= rh.write(in, (int) MathUtils.clamp(remain, 0, Integer.MAX_VALUE));
		}

		return true;
	}

	@Override
	public void release(ChannelCtx ctx) {
		if (sendfile != null) {
			IOUtil.closeSilently(sendfile.channel);
			sendfile = null;
		} else {
			IOUtil.closeSilently(in);
			in = null;
		}

		if (file != null) {
			file.release(ctx);
			file = null;
		}
	}
}