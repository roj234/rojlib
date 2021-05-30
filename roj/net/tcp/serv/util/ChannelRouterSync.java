package roj.net.tcp.serv.util;

import roj.collect.TimedHashMap;
import roj.concurrent.task.ITaskUncancelable;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.EmptyResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.IllegalRequestException;
import roj.net.tcp.util.ResponseCode;
import roj.net.tcp.util.SharedConfig;
import roj.net.tcp.util.WrappedSocket;
import roj.util.log.ILogger;
import roj.util.log.LogManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelRouterSync implements ITaskUncancelable {
    protected WrappedSocket channel;
    protected final Router router;

    protected static ILogger logger = LogManager.getLogger("ChannelRouter");

    public ChannelRouterSync(WrappedSocket channel, Router router) {
        this.channel = channel;
        this.router = router;
    }

    public void calculate(Thread thread) throws IOException {
        Response reply = null;
        Request request = null;
        try {
            final SocketChannel socket = this.channel.socket();
            //socket.setSoTimeout(router.readTimeout());

            InetSocketAddress remote = (InetSocketAddress) socket.getRemoteAddress();
            int visited;
            if(SharedConfig.THROTTLING_CHECK_ENABLED) {
                final TimedHashMap<String, AtomicInteger> addresses = SharedConfig.CONNECTING_ADDRESSES;
                AtomicInteger count = addresses.get(remote.getHostString());
                if (count == null) {
                    synchronized (addresses) {
                        addresses.put(remote.getHostString(), count = new AtomicInteger());
                    }
                }
                visited = count.incrementAndGet();
            } else {
                visited = 0;
            }

            if (visited > 100) {
                if (visited > 120) {
                    reply = EmptyResponse.INSTANCE;
                    if (visited % 128 == 0)
                        logger.warn(System.currentTimeMillis() + ":" + remote.getHostString() + ": Connection throttling.");
                    if (visited > 300) {
                        socket.shutdownInput();
                        socket.shutdownOutput();
                        // socket.close();
                        // 连接挂起
                        return;
                    }
                } else {
                    reply = SharedConfig.CONNECTION_THROTTLING;
                }
            } else {
                final long time = System.currentTimeMillis() + router.readTimeout();

                while (!channel.handShake()) {
                    if (System.currentTimeMillis() > time) {
                        reply = new Reply(ResponseCode.TIMEOUT, StringResponse.errorResponse(ResponseCode.TIMEOUT, null));
                        break;
                    }
                }
                if(reply == null) {
                    try {
                        request = Request.parse(channel, router);
                        try {
                            reply = router.response(socket, request);
                            reply.getClass(); // checkNull
                            reply.prepare();
                        } catch (Throwable e) {
                            reply = new Reply(ResponseCode.INTERNAL_ERROR, StringResponse.errorResponse(null, e));
                        }
                    } catch (IllegalRequestException e) {
                        final Throwable cause = e.getCause();
                        reply = new Reply(e.code, cause instanceof Notify ?
                                StringResponse.errorResponse(e.code, e.code == ResponseCode.INTERNAL_ERROR ? cause.getCause() : null) :
                                StringResponse.errorResponse(null, e)
                        );
                    }
                }
            }

            reply.prepare();

            long time = System.currentTimeMillis();
            long timeout = router.writeTimeout(request);

            while (reply.send(this.channel)) {
                if (System.currentTimeMillis() - time >= timeout) {
                    logger.warn("[Send] " + System.currentTimeMillis() + ": Timeout while sending " + reply + " to " + remote);
                    break;
                }
            }

            while (!channel.shutdown());
            channel.close();
            reply.release();

        } catch (IOException e) {
            if (reply != null) {
                reply.release();
            }

            reply = new Reply(ResponseCode.INTERNAL_ERROR, StringResponse.errorResponse(null, e));
            try {
                long time = System.currentTimeMillis();
                long timeout = router.writeTimeout(request);

                while (reply.send(this.channel)) {
                    if (System.currentTimeMillis() - time >= timeout) {
                        break;
                    }
                }
            } catch (Throwable ignored) {

            } finally {
                reply.release();
            }

            String msg = e.getMessage();

            if (!"Broken pipe".equals(msg) && !"Connection reset by peer".equals(msg)) {
                logger.warn("Terminated: " + e);
            }

            try {
                /*
                 * Only try once
                 */
                channel.shutdown();
            } catch (IOException ignored) {}

            channel.close();
        }
        channel = null;
    }

    @Override
    public final boolean isDone() {
        return channel == null;
    }
}
