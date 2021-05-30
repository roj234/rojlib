package roj.net.tcp.serv;

import roj.net.tcp.serv.response.HeadResponse;
import roj.net.tcp.serv.util.Request;
import roj.net.tcp.util.ResponseCode;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/5 17:20
 */
public class RouterImpl implements Router {
    private final Router router;

    public RouterImpl(Router router) {
        if (router.getClass() == RouterImpl.class)
            throw new IllegalStateException();
        this.router = router;
    }

    @Override
    public Response response(SocketChannel socket, Request request) throws IOException {
        String conn = request.headers().getOrDefault("Connection", "");
        switch (conn) {
            case "close":
                break;
            case "keep-alive":
                // ...
                break;
            case "upgrade":
                return tryUpgrade(socket, request);
        }

        return router.response(socket, request);
    }

    /**
     * Connection: upgrade
     * Upgrade: protocol-name[/protocol-version]
     */
    protected Response tryUpgrade(SocketChannel socket, Request request) {
        String newRequest = request.headers("Upgrade");
        if ("h2c".equals(newRequest)) {
            HeadResponse response = new HeadResponse();

            final Map<CharSequence, CharSequence> headers = response.headers();
            headers.put("Upgrade", "h2c");
            headers.put("Connection", "upgrade");

            return new Reply(ResponseCode.SWITCHING_PROTOCOL, response);
        }
        // todo HTTP2.0
        // todo MY SSL
        return null;
    }

    @Override
    public int maxLength() {
        return router.maxLength();
    }

    @Override
    public int readTimeout() {
        return router.readTimeout();
    }

    @Override
    public int writeTimeout(@Nullable Request request) {
        return router.writeTimeout(request);
    }
}
