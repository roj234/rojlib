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
package roj.net.http.serv;

import roj.concurrent.task.ITaskNaCl;
import roj.net.Notify;
import roj.net.WrappedSocket;
import roj.net.http.Code;
import roj.net.http.HttpServer;
import roj.net.http.IllegalRequestException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class RequestHandler implements ITaskNaCl {
    WrappedSocket channel;
    final Router router;
    byte stage;
    Request request;
    Reply reply;
    long time;
    final Object[] lexerHolder = new Object[1];

    static final Logger L = Logger.getLogger("RequestHandler");

    public RequestHandler(WrappedSocket channel, Router router) {
        this.channel = channel;
        this.router = router;
    }

    @Override
    public boolean continueExecuting() {
        return stage < 5;
    }

    public final void calculate(Thread thread) throws IOException {
        Reply reply = this.reply;
        try {
            Socket socket = this.channel.socket();
            InetSocketAddress remote = (InetSocketAddress) socket.getRemoteSocketAddress();

            if (stage < 3) {
                if (stage == 0) {
                    socket.setSoTimeout(router.readTimeout());

                    if(HttpServer.THROTTLING_CHECK_ENABLED) {
                        final Map<String, AtomicInteger> addresses = HttpServer.CONNECTING_ADDRESSES;
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
                                socket.shutdownInput();
                                socket.shutdownOutput();
                                // 连接挂起
                                stage = 5;
                                if (visited % 128 == 0)
                                    L.warning(System.currentTimeMillis() + ":" + remote.getHostString() + ": Connection throttling.");
                                return;
                            } else {
                                reply = new Reply(Code.UNAVAILABLE, new StringResponse("DDoS detected"));
                                reply.prepare();
                            }
                            this.reply = reply;
                            return;
                        }
                    } else {
                        stage = 1;
                    }
                }

                if(stage < 2) {
                    long time = System.currentTimeMillis() + router.readTimeout();

                    if (!channel.handShake()) {
                        return;
                    }
                    if (System.currentTimeMillis() > time) {
                        this.stage = 4;
                        return;
                    }

                    this.stage = 2;
                }

                if(reply == null) {
                    try {
                        if((request = Request.parseAsync(channel, router, lexerHolder)) == null)
                            return;
                        try {
                            reply = router.response(socket, request);
                        } catch (Throwable e) {
                            reply = new Reply(Code.INTERNAL_ERROR, StringResponse.forError(0, e));
                        }
                    } catch (IllegalRequestException e) {
                        final Throwable cause = e.getCause();
                        reply = new Reply(e.code, cause instanceof Notify ?
                                StringResponse.forError(e.code, e.code == Code.INTERNAL_ERROR ? cause.getCause() : null) :
                                StringResponse.forError(0, e)
                        );
                    }
                }

                if (reply != null) reply.prepare();
                this.reply = reply;
                this.stage = (byte) (reply == null ? 4 : 3);
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
                    if (reply != null) reply.release();
                    this.reply = null;
                    this.stage = 5;
                }
            }
            return;

        } catch (Throwable e) {
            if (reply != null) {
                reply.release();
            }

            reply = new Reply(Code.INTERNAL_ERROR, StringResponse.forError(0, e));
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

            System.out.println("异常 " + e.getMessage());
            if (e.getClass() != IOException.class)
                e.printStackTrace();

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

    @Override
    public final boolean isDone() {
        return channel == null;
    }
}
