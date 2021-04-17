/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.net.tcp.serv.util;

import roj.collect.TimedHashMap;
import roj.concurrent.task.ITaskNaCl;
import roj.net.tcp.serv.HttpServer;
import roj.net.tcp.serv.Reply;
import roj.net.tcp.serv.Response;
import roj.net.tcp.serv.Router;
import roj.net.tcp.serv.response.EmptyResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.util.Code;
import roj.net.tcp.util.IllegalRequestException;
import roj.net.tcp.util.Notify;
import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Logger;

public class ChannelRouterSync implements ITaskNaCl {
    protected WrappedSocket channel;
    protected final Router router;

    protected static Logger logger = Logger.getLogger("ChannelRouter");

    public ChannelRouterSync(WrappedSocket channel, Router router) {
        this.channel = channel;
        this.router = router;
    }

    public void calculate(Thread thread) throws IOException {
        Response reply = null;
        Request request = null;
        try {
            Socket socket = this.channel.socket();
            socket.setSoTimeout(router.readTimeout());

            InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();
            int visited;
            if(HttpServer.THROTTLING_CHECK_ENABLED) {
                final TimedHashMap<String, AtomicInteger> addresses = HttpServer.CONNECTING_ADDRESSES;
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
                        logger.warning(System.currentTimeMillis() + ":" + remote.getHostString() + ": Connection throttling.");
                    if (visited > 300) {
                        socket.shutdownInput();
                        socket.shutdownOutput();
                        // socket.close();
                        // 连接挂起
                        return;
                    }
                } else {
                    reply = new Reply(Code.UNAVAILABLE, new StringResponse("DDoS detected"));
                }
            } else {
                final long time = System.currentTimeMillis() + router.readTimeout();

                while (!channel.handShake()) {
                    if (System.currentTimeMillis() > time) {
                        reply = new Reply(Code.TIMEOUT, StringResponse.errorResponse(Code.TIMEOUT, null));
                        break;
                    }
                    LockSupport.parkNanos(50);
                }
                if(reply == null) {
                    try {
                        request = Request.parse(channel, router);
                        try {
                            reply = router.response(socket, request);
                            reply.getClass(); // checkNull
                            reply.prepare();
                        } catch (Throwable e) {
                            reply = new Reply(Code.INTERNAL_ERROR, StringResponse.errorResponse(null, e));
                        }
                    } catch (IllegalRequestException e) {
                        final Throwable cause = e.getCause();
                        reply = new Reply(e.code, cause instanceof Notify ?
                                StringResponse.errorResponse(e.code, e.code == Code.INTERNAL_ERROR ? cause.getCause() : null) :
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
                    logger.warning("[Send] " + System.currentTimeMillis() + ": Timeout while sending " + reply + " to " + remote);
                    break;
                }
                LockSupport.parkNanos(1000);
            }

            while (!channel.shutdown()) {
                LockSupport.parkNanos(1000);
            }
            channel.close();
            reply.release();

        } catch (IOException e) {
            if (reply != null) {
                reply.release();
            }

            reply = new Reply(Code.INTERNAL_ERROR, StringResponse.errorResponse(null, e));
            try {
                long time = System.currentTimeMillis();
                long timeout = router.writeTimeout(request);

                while (reply.send(this.channel)) {
                    if (System.currentTimeMillis() - time >= timeout) {
                        break;
                    }
                    LockSupport.parkNanos(1000);
                }
            } catch (Throwable ignored) {

            } finally {
                reply.release();
            }

            String msg = e.getMessage();

            if (!"Broken pipe".equals(msg) && !"Connection reset by peer".equals(msg)) {
                logger.warning("Terminated: " + e);
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
