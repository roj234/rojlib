package roj.net.tcp.serv.util;

import roj.collect.TimedHashMap;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.EmptyResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.IllegalRequestException;
import roj.net.tcp.util.ResponseCode;
import roj.net.tcp.util.SharedConfig;
import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class ChannelRouter extends ChannelRouterSync {
    protected byte stage;
    protected Request request;
    protected Response reply;
    protected long time;
    protected final Object[] lexerHolder = new Object[1];

    public ChannelRouter(WrappedSocket channel, Router router) {
        super(channel, router);
    }

    @Override
    public boolean continueExecuting() {
        return stage < 5;
    }

    public final void calculate(Thread thread) throws IOException {
        Response reply = this.reply;
        try {
            final SocketChannel socket = this.channel.socket();
            InetSocketAddress remote = (InetSocketAddress) socket.getRemoteAddress();

            if (stage < 3) {
                if (stage == 0) {
                    //socket.setOption(StandardSocketOptions.SO_LINGER, router.readTimeout());

                    if(SharedConfig.THROTTLING_CHECK_ENABLED) {
                        final TimedHashMap<String, AtomicInteger> addresses = SharedConfig.CONNECTING_ADDRESSES;
                        AtomicInteger integer = addresses.get(remote.getHostString());
                        if (integer == null) {
                            synchronized (addresses) {
                                addresses.put(remote.getHostString(), integer = new AtomicInteger());
                            }
                        }
                        int visited = integer.incrementAndGet();

                        stage = 1;
                        if (visited > 100) {
                            if (visited > 120) {
                                reply = EmptyResponse.INSTANCE;
                                if (visited % 128 == 0) logger.warn(System.currentTimeMillis() + ":" + remote.getHostString() + ": Connection throttling.");
                                if (visited > 300) {
                                    socket.shutdownInput();
                                    socket.shutdownOutput();
                                    // 连接挂起
                                    stage = 5;
                                    return;
                                }
                            } else {
                                reply = SharedConfig.CONNECTION_THROTTLING;
                                reply.prepare();
                            }
                            this.reply = reply;
                            return;
                        }
                    }
                }

                if(stage < 2) {
                    long time = System.currentTimeMillis() + router.readTimeout();

                    while (!channel.handShake()) {
                        if (System.currentTimeMillis() > time) {
                            this.reply = EmptyResponse.INSTANCE;
                            this.stage = 3;
                            return;
                        }
                    }

                    this.stage = 2;
                }

                if(reply == null) {
                    try {
                        if((request = Request.parseAsync(channel, router, lexerHolder)) == null)
                            return;
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

                reply.prepare();
                this.reply = reply;
                this.stage = 3;
                return;
            }

            long time = this.time;
            if (time == 0)
                time = this.time = System.currentTimeMillis() + router.writeTimeout(request);

            if (stage == 3) {
                if (reply.send(this.channel)) {
                    if (System.currentTimeMillis() > time) {
                        System.err.println("[Warn] " + System.currentTimeMillis() + ": Timeout while sending " + reply + " to " + remote);
                    } else {
                        return;
                    }
                }
                this.stage = 4;
            } else {
                if (channel.shutdown()) {
                    channel.close();
                    reply.release();
                    this.reply = null;
                    this.stage = 5;
                }
            }
            return;

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
                System.err.println("Unpredictable termination: " + e);
            }

            try {
                /*
                 * Only try once
                 */
                channel.shutdown();
            } catch (IOException ignored) {}

            channel.close();

            stage = 6;
        }
        channel = null;
    }
}
