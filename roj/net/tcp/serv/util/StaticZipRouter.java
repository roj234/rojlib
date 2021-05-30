package roj.net.tcp.serv.util;

import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.StreamResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.ResponseCode;
import roj.text.CharList;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/16 11:17
 */
public class StaticZipRouter implements Router {
    public final ZipFile zipFs;

    public StaticZipRouter(ZipFile zipFs) {
        this.zipFs = zipFs;
    }

    @Override
    public int readTimeout() {
        return 3000;
    }

    @Override
    public int writeTimeout(@Nullable Request request) {
        return 5000;
    }

    @Override
    public Response response(SocketChannel socket, Request request) throws IOException {
        String url = request.path().substring(1);

        boolean flag = url.endsWith("/");
        ZipEntry ze = zipFs.getEntry(flag ? url + "index.html" : url);
        if(ze == null) {
            if(flag) {
                ZipEntry dir = zipFs.getEntry(url);
                if(dir != null && dir.isDirectory())
                    return new Reply(ResponseCode.FORBIDDEN, StringResponse.errorResponse(ResponseCode.FORBIDDEN,null));
            }
            return new Reply(ResponseCode.NOT_FOUND, StringResponse.errorResponse(ResponseCode.NOT_FOUND, null));
        }
        return new Reply(ResponseCode.OK, new ZipResponse(url, zipFs.getInputStream(ze)));
    }

    private static class ZipResponse extends StreamResponse {
        private final String url;
        private final InputStream in;

        public ZipResponse(String url, InputStream in) {
            this.url = url;
            this.in = in;
        }

        @Override
        public void writeHeader(CharList list) {
            String type;
            if (url.endsWith(".html"))
                type = "text/html; charset=UTF-8";
            else
                type = "application/octet-stream";

            list.append("Content-Type: ").append(type).append(CRLF)
                    .append("Content-Length: ").append(Long.toString(length)).append(CRLF);
        }

        @Override
        protected InputStream getStream() throws IOException {
            length = in.available();
            return in;
        }
    }
}
