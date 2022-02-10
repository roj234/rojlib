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

import roj.net.WrappedSocket;
import roj.net.http.Code;
import roj.net.http.IllegalRequestException;
import roj.net.misc.FDChannel;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.function.Consumer;

public final class RequestHandler extends FDChannel {
    public static final int KEEP_ALIVE_TIMEOUT = 300;

    final Router router;

    static final byte HANDSHAKE = 0, PARSING_REPLY = 1, SENDING_REPLY = 2, HANGING = 3, CLOSING = 4, CLOSED = 5, EXCEPTION_CLOSED = 6;
    boolean enter;
    byte state;
    long time;

    Request request;
    Reply   reply;
    Consumer<WrappedSocket> cb;

    final Object[] holder = new Object[3];

    public RequestHandler(WrappedSocket ch, Router router) {
        super(ch);
        this.router = router;
    }

    @Override
    public void tick() {
        if (state == HANGING && System.currentTimeMillis() > time) {
            enter = false;
            state = CLOSING;
        }
    }

    public void waitAnd(Consumer<WrappedSocket> o) {
        cb = o;
    }

    @Override
    public void close() throws IOException {
        if (ch == null) return;

        try {
            rs();
        } catch (IOException ignored) {}

        state = CLOSED;
        key.cancel();

        WrappedSocket ch = this.ch;
        this.ch = null;

        try {
            ch.shutdown();
        } catch (IOException ignored) {}

        ch.close();
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void selected(int readyOps) throws Exception {
        try {
            switch (state) {
                case HANDSHAKE:
                    if (!enter) {
                        time = System.currentTimeMillis() + router.readTimeout();
                        enter = true;
                    }
                    if (System.currentTimeMillis() > time) {
                        enter = false;
                        state = CLOSING;
                        return;
                    }

                    if (!ch.handShake()) {
                        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        return;
                    }
                    key.interestOps(SelectionKey.OP_READ);

                    enter = false;
                    state = PARSING_REPLY;
                case PARSING_REPLY:
                    Reply reply;
                    try {
                        if((request = Request.parse(ch, router, holder)) == null) return;
                        try {
                            reply = router.response(ch, request, this);
                        } catch (Throwable e) {
                            e.printStackTrace();
                            reply = new Reply(Code.INTERNAL_ERROR, StringResponse.forError(0, e));
                        }
                    } catch (IllegalRequestException e) {
                        e.printStackTrace();
                        reply = new Reply(e.code, StringResponse.forError(0, e));
                    }
                    if (reply != null) reply.prepare();
                    this.reply = reply;

                    enter = false;
                    state = reply == null ? CLOSING : SENDING_REPLY;
                case SENDING_REPLY:
                    if (!enter) {
                        key.interestOps(SelectionKey.OP_WRITE);
                        time = System.currentTimeMillis() + router.writeTimeout(request);
                        enter = true;
                    }

                    reply = this.reply;
                    if (reply.send(ch)) {
                        if (System.currentTimeMillis() > time) {
                            enter = false;
                            state = CLOSING;
                            key.interestOps(SelectionKey.OP_WRITE);
                        }
                        return;
                    } else {
                        enter = false;
                        state = request.headers().get("Connection").equalsIgnoreCase("keep-alive") && reply.keepAlive() ? HANGING : CLOSING;
                    }
                case HANGING:
                    if (cb != null) {
                        cb.accept(ch);
                        key.cancel();
                        ch = null;
                        state = CLOSED;
                        return;
                    }
                    if (!enter) {
                        rs();
                        key.interestOps(SelectionKey.OP_READ);
                        time = System.currentTimeMillis() + KEEP_ALIVE_TIMEOUT * 1000;
                        enter = true;
                    } else {
                        int r = ch.read();
                        if (r > 0) {
                            enter = false;
                            state = HANDSHAKE;
                            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                            break;
                        }
                    }
                case CLOSING:
                    if (!enter) {
                        key.interestOps(SelectionKey.OP_WRITE);
                        time = System.currentTimeMillis() + router.writeTimeout(null);
                        enter = true;
                        rs();
                    }

                    if (!ch.shutdown() && System.currentTimeMillis() <= time) {
                        return;
                    }

                    ch.close();
                    enter = false;
                    state = CLOSED;
                    key.cancel();
                    break;
                case CLOSED:
                case EXCEPTION_CLOSED:
                default:
                    throw new IllegalStateException();
            }
        } catch (Throwable e) {
            if (e.getClass() != IOException.class) e.printStackTrace();
            else System.out.println("异常 " + e.getMessage());

            if (ch.isOpen()) {
                try {
                    Reply reply = new Reply(Code.INTERNAL_ERROR, StringResponse.forError(0, e));
                    reply.send(ch);
                    reply.release();
                } catch (Throwable ignored) {}
            }

            try {
                close();
            } finally {
                state = EXCEPTION_CLOSED;
            }
        }
    }

    private void rs() throws IOException {
        if (reply != null) {
            reply.release();
            reply = null;
        }
        request = null;
        ch.buffer().clear();
    }
}
