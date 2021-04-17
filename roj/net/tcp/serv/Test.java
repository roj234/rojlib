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
package roj.net.tcp.serv;

import roj.collect.MyHashMap;
import roj.management.PerformanceLogger;
import roj.net.tcp.client.HttpClient;
import roj.net.tcp.client.HttpHeader;
import roj.net.tcp.serv.response.CachedFileResponse;
import roj.net.tcp.serv.response.HTTPResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.serv.util.Request;
import roj.net.tcp.util.Code;
import roj.net.tcp.util.Shared;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/29 0:51
 */
public class Test {
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        Thread thread = new Thread(new PerformanceLogger());
        thread.setDaemon(true);
        thread.start();

        if (args.length > 1) {
            Map<String, String> headers = new MyHashMap<>();
            headers.put("Host", "127.0.0.1");
            headers.put("Connection", "keep-alive");
            headers.put("Pragma", "no-cache");
            headers.put("Cache-Control", "no-cache");
            headers.put("Upgrade-Insecure-Requests", "1");
            headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36");
            headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
            headers.put("Accept-Encoding", "gzip, deflate");//, br);
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");

            HttpClient client = new HttpClient();
            client.readTimeout(5000);
            client.method("GET").headers(headers);

            int i = 0;

            //while (true) {
                try {
                    client.url(new URL("http://127.0.0.1/music/index.html"));
                    client.send();
                    HttpHeader header = client.response();
                    System.out.println(header);
                    ByteList list = new ByteList().readStreamArrayFully(client.getInputStream());
                    System.out.println("DL " + list.pos());
                    System.out.println("Keep-Alive: " + header.headers.get("connection"));
                    LockSupport.parkNanos(2_000_000_000L);
                    client.send();
                    header = client.response();
                    System.out.println(header);

                    if (++i == 1000) {
                        System.out.println("1000!");
                        i = 0;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            //}
            return;
        }

        HTTPResponse gzc = new CachedFileResponse(new File("E:/章节修复.txt"));

        HttpServer server = new HttpServer(2333, 2048, new RouterImpl(new Router() {
                    @Override
                    public Response response(Socket socket, Request request) throws IOException {
                        Reply reply;
                        int action = request.action();

                        switch (request.path()) {
                            case "/favicon.ico":
                                return new Reply(Code.NOT_FOUND, StringResponse.errorResponse(Code.NOT_FOUND, null));
                            case "/":
                            case "":
                                StringBuilder sb = new StringBuilder().append("<h1>Welcome! <br> Asyncorized_MC's HTTP(S) Server</h1>");

                                sb.append("欢迎您,来自").append(socket.getRemoteSocketAddress()).append("的客人! <br/><font color='red'>第二次更新后已支持流式处理请求！</font>" +
                                        "您访问的路径是:").append(request.host()).append(request.path()).append("<br/ >")
                                        .append(request.path())
                                        .append("<br/>HEADER:").append(request.headers()).append("<br/ >")
                                        .append("<br/>GET:").append(request.getFields()).append("<br/ >")
                                        .append("<br/>POST:").append(request.postFields()).append("<br/ >")
                                        .append("<br/><h2 style='color:#eecc44'>Server version 1.2.0</h2><form method=post><input type=text name=myname /><input type=text name=myname22 /><input type=submit /></form>");

                                return new Reply(Code.OK, new StringResponse(sb, "text/html"), action);
                            case "/file":
                                return new Reply(Code.OK, gzc);
                            case "/mem":
                                return new Reply(Code.OK, new StringResponse(getMemory(), "text/plain"));
                            default:
                                return new Reply(Code.NOT_FOUND, StringResponse.errorResponse(Code.NOT_FOUND, "未定义的路由"));
                        }
                    }
                }), "server.ks", "123456".toCharArray());

        System.out.println("Max connection: " + 2048);
        System.out.println("Write buffer: " + Shared.WRITE_MAX);
        System.out.println("UTF-8 decode buffer: " + Shared.MAX_CHAR_BUFFER_CAPACITY);
        System.out.println("Listening on " + server.getSocket().getLocalSocketAddress());

        server.run();
    }

    private static CharSequence getMemory() {
        return roj.management.SystemInfo.getMemoryUsage();
    }
}
