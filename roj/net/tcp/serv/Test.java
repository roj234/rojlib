package roj.net.tcp.serv;

import roj.collect.MyHashMap;
import roj.management.PerformanceLogger;
import roj.net.tcp.client.HttpClient;
import roj.net.tcp.serv.response.CachedFileResponse;
import roj.net.tcp.serv.response.HTTPResponse;
import roj.net.tcp.serv.response.StringResponse;
import roj.net.tcp.serv.util.Request;
import roj.net.tcp.util.ResponseCode;
import roj.net.tcp.util.SharedConfig;

import java.io.File;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/29 0:51
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
            headers.put("Accept-Encoding", "gzip, deflate, br");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");

            HttpClient client = new HttpClient();
            client.readTimeout(1500);
            client.type("GET").path("/").body("").headers(headers);

            int i = 0;

            //while (true) {
                try {
                    client.createSocket("127.0.0.1", 2333, true);
                    client.send();

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

        HTTPServer server = new HTTPServer(2333, 2048, new RouterImpl(new Router() {
                    @Override
                    public Response response(SocketChannel socket, Request request) throws IOException {
                        Reply reply;
                        int action = request.action();

                        switch (request.path()) {
                            case "/favicon.ico":
                                return new Reply(ResponseCode.NOT_FOUND, StringResponse.errorResponse(ResponseCode.NOT_FOUND, null));
                            case "/":
                            case "":
                                StringBuilder sb = new StringBuilder().append("<h1>Welcome! <br> Asyncorized_MC's HTTP(S) Server</h1>");

                                sb.append("欢迎您,来自").append(socket.getRemoteAddress()).append("的客人! <br/><font color='red'>第二次更新后已支持流式处理请求！</font>" +
                                        "您访问的路径是:").append(request.host()).append(request.path()).append("<br/ >")
                                        .append(request.path())
                                        .append("<br/>HEADER:").append(request.headers()).append("<br/ >")
                                        .append("<br/>GET:").append(request.getFields()).append("<br/ >")
                                        .append("<br/>POST:").append(request.postFields()).append("<br/ >")
                                        .append("<br/><h2 style='color:#eecc44'>Server version 1.2.0</h2><form method=post><input type=text name=myname /><input type=text name=myname22 /><input type=submit /></form>");

                                return new Reply(ResponseCode.OK, new StringResponse(sb, "text/html"), action);
                            case "/file":
                                return new Reply(ResponseCode.OK, gzc);
                            case "/mem":
                                return new Reply(ResponseCode.OK, new StringResponse(getMemory(), "text/plain"));
                            default:
                                return new Reply(ResponseCode.NOT_FOUND, StringResponse.errorResponse(ResponseCode.NOT_FOUND, "未定义的路由"));
                        }
                    }
                }), "server.keystore", "123456".toCharArray());

        System.out.println("Max connection: " + 2048);
        System.out.println("Read buffer: " + SharedConfig.READ_MAX);
        System.out.println("Write buffer: " + SharedConfig.WRITE_MAX);
        System.out.println("UTF-8 decode buffer: " + SharedConfig.MAX_CHAR_BUFFER_CAPACITY);
        System.out.println("Native buffer: " + SharedConfig.DIRECT_CACHE_MAX);
        System.out.println("Listening on " + server.getSocket().getLocalAddress());

        server.run();
    }

    private static CharSequence getMemory() {
        return roj.management.SystemInfo.getMemoryUsage();
    }
}
