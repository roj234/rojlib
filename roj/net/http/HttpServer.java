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
package roj.net.http;

import roj.collect.TimedHashMap;
import roj.math.MutableInt;
import roj.net.SocketFactory;
import roj.net.http.serv.RequestHandler;
import roj.net.http.serv.Router;
import roj.net.misc.FDCLoop;
import roj.net.misc.Listener;
import roj.ui.CmdUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

public class HttpServer extends Listener {
    static final boolean CheckDDOS = false;
    final Map<String, MutableInt> connecting = new TimedHashMap<>(1000);

    private final SocketFactory factory;
    private final Router router;
    private FDCLoop<RequestHandler> loop;

    public HttpServer(InetSocketAddress address, int conn, Router router) throws IOException {
        this(address, conn, router, SocketFactory.PLAIN_FACTORY);
    }

    public HttpServer(InetSocketAddress address, int conn, Router router, SocketFactory factory) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address, conn);
        init(socket);

        this.factory = factory;
        this.router = router;

        int cpus = Runtime.getRuntime().availableProcessors();
        this.loop = new FDCLoop<>(null, "HTTP?????????", 0, cpus, 30000, 100);
    }

    public final ServerSocket getSocket() {
        return socket;
    }

    public FDCLoop<RequestHandler> getLoop() {
        return loop;
    }
    public void setLoop(FDCLoop<RequestHandler> loop) {
        this.loop = loop;
    }

    public void start() throws IOException {
        try {
            loop.registerListener(this);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    public void stop() throws IOException {
        socket.close();
        loop.shutdown();
    }

    @Override
    public void selected(int readyOps) throws Exception {
        Socket c = socket.accept();
        try {
            c.setReuseAddress(true);
            if(CheckDDOS) {
                InetAddress ip = c.getInetAddress();
                int port = c.getPort();

                Map<String, MutableInt> m = connecting;
                String key = String.valueOf(ip) + ':' + port;
                MutableInt i = m.get(key);
                if (i == null) {
                    m.put(key, i = new MutableInt());
                }

                int count = i.incrementAndGet();
                if (count > 100) {
                    if (count % 128 == 0)
                        CmdUtil.warning(System.currentTimeMillis() + ':' + key + ": throttling.");
                    c.close();
                }
            }

            loop.register(new RequestHandler(factory.wrap(c), router), null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
