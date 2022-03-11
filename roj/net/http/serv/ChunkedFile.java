package roj.net.http.serv;

import roj.crypt.SM3;
import roj.io.IOUtil;
import roj.net.http.Headers;
import roj.text.RFCDate;
import roj.text.TextUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author solo6975
 * @since 2022/3/8 0:21
 */
public class ChunkedFile implements Router {
    private static final AtomicLong identity = new AtomicLong();

    private final File file;
    private String eTag;

    public ChunkedFile(File file) {
        this(file, false);
    }

    public ChunkedFile(File file, boolean eTag) {
        this.file = file;
        if (eTag)
        this.eTag = '"' +
                IOUtil.SharedCoder.get().encodeHex(new SM3().digest(file.getAbsolutePath().getBytes())) +
                Long.toUnsignedString(identity.getAndIncrement(), 36) + '"';
    }

    @Override
    public Response response(Request req, RequestHandler rh) {
        Headers h = req.headers();

        // 当与  If-Modified-Since  一同使用的时候，If-None-Match 优先级更高（假如服务器支持的话）。
        if (h.containsKey("If-Match")) {
            List<String> tags = TextUtil.split(new ArrayList<>(), h.get("If-Match"), ", ");
            if (!tags.contains(eTag)) {
                // Cache-Control、Content-Location、Date、ETag、Expires 和 Vary 。
                return plus(304, req);
            }
        } else if (h.containsKey("If-None-Match")) {
            // 当且仅当服务器上没有任何资源的 ETag 属性值与这个首部中列出的相匹配的时候，服务器端才会返回所请求的资源
            List<String> tags = TextUtil.split(new ArrayList<>(), h.get("If-None-Match"), ", ");
            if (tags.contains(eTag)) {
                return plus(304, req);
            }
        }

        if (h.containsKey("If-Modified-Since")) {
            long time;
            try {
                time = RFCDate.parse(h.get("If-Modified-Since")) / 1000;
            } catch (Exception e) {
                rh.reply(400);
                return null;
            }

            if (file.lastModified() / 1000 <= time) {
                return plus(304, req);
            }
        } else if (h.containsKey("If-Unmodified-Since")) {
            long time;
            try {
                time = RFCDate.parse(h.get("If-Unmodified-Since")) / 1000;
            } catch (Exception e) {
                rh.reply(400);
                return null;
            }
            // 当资源在指定的时间之后没有修改，服务器才会返回请求的资源
            if (file.lastModified() / 1000 > time) {
                rh.reply(412);
                return null;
            }
        }

        if (h.containsKey("If-Range")) {
            String s = h.get("If-Range");
            if (s.endsWith("\"")) {
                if (!eTag.equals(s)) {
                    plus(200, req);
                    return new FileResponse(file);
                }
            } else {
                long time;
                try {
                    time = RFCDate.parse(s) / 1000;
                } catch (Exception e) {
                    rh.reply(400);
                    return null;
                }
                if (file.lastModified() / 1000 > time) {
                    plus(200, req);
                    return new FileResponse(file);
                }
            }
        }

        if (!h.containsKey("Range")) {
            plus(200, req);
            return new FileResponse(file);
        }

        String s = h.get("Range");
        if (!s.startsWith("bytes=")) {
            rh.reply(400);
            return StringResponse.forError(0, "range not in bytes");
        }
        List<String> ranges = TextUtil.split(new ArrayList<>(), s.substring(6), ", ");
        long[] data = new long[ranges.size() << 1];
        for (int i = 0; i < ranges.size(); i++) {
            s = ranges.get(i);
            int j = s.indexOf('-');
            // start, end
            long o = data[ i<<1   ] = Long.parseLong(s.substring(0, j));
                     data[(i<<1)+1] = j == s.length() - 1 ? file.length() - 1 : Long.parseLong(s.substring(j+1));
        }
        rh.reply(206).withDate();
        return new PartialContentMulti(file, data);
    }

    private Response plus(int r, Request req) {
        RequestHandler h = req.handler;
        h.reply(r).withDate();
        if (r != 304) {
            h.getRawHeaders()
             .putAscii("Last-Modified: ").putAscii(req.local.date.toRFCDate(file.lastModified()))
             .putAscii("\r\n");
            if (eTag != null) h.header("ETag", eTag);
        }
        return null;
    }
}
