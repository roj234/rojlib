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
package roj.misc;

import roj.net.http.Code;
import roj.net.http.HttpServer;
import roj.net.http.serv.ChunkedFile;
import roj.net.http.serv.FileResponse;
import roj.net.http.serv.Response;
import roj.net.http.serv.StringResponse;
import roj.util.SleepingBeauty;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;

/**
 * @author Roj234
 * @since  2020/11/29 0:51
 */
public class HttpExample {
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Response gzc = new FileResponse(new File("E:/章节修复.txt"));
        ChunkedFile pt = new ChunkedFile(new File("E:/1.bin"));

        SleepingBeauty.sleep();
        HttpServer server = new HttpServer(new InetSocketAddress(2333), 2048,
                                           (ch, request, handle) -> {
            int action = request.action();

            switch (request.path()) {
                case "/favicon.ico":
                    return handle.reply(404).nullResponse();
                case "/":
                case "":
                    StringBuilder sb = new StringBuilder().append("<h1>Server: Async/2.0</h1>");
                    sb.append("欢迎您,您访问的路径是:").append(request.host()).append(request.path()).append("<br/ >")
                      .append(request.path())
                      .append("<br/>HEADER:<pre>").append(request.headers()).append("</pre><br/ >")
                      .append("<br/>GET:").append(request.getFields()).append("<br/ >")
                      .append("<br/>POST:").append(request.payloadFields()).append("<br/ >")
                      .append("<br/><h2 style='color:#eecc44'>Server: Async/2.1</h2>现已支持keep-alive和WebSocket!<form method=post><input type=text name=myname /><input type=text name=myname22 /><input type=submit /></form>");

                    return new StringResponse(sb, "text/html");
                case "/file":
                    return gzc;
                case "/pt":
                    return pt.response(ch, request, handle);
                case "/mem":
                    return new StringResponse(getMemory(), "text/plain");
                default:
                    handle.reply(404);
                    return StringResponse.forError(Code.NOT_FOUND, "未定义的路由");
            }
        });
        System.out.println("Listening on " + server.getSocket().getLocalSocketAddress());
        server.start();
    }

    private static CharSequence getMemory() {
        return roj.manage.SystemInfo.getMemoryUsage();
    }
}
