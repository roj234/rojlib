package roj.net.mychat;

import roj.net.http.HttpServer;
import roj.net.http.serv.*;
import roj.util.SleepingBeauty;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @author solo6975
 * @since 2022/1/25 2:34
 */
@Deprecated
public class Client implements Router {
    public static void main(String[] args) throws IOException {
        SleepingBeauty.sleep();

        int port = 1999;
        if (args.length > 0) port = Integer.parseInt(args[0]);

        HttpServer server = new HttpServer(new InetSocketAddress(port), 127, new Client());
        System.out.println("访问地址: " + server.getSocket().getLocalSocketAddress());
        server.start();
    }

    static final DirRouter dir = new DirRouter(new File("D:\\S\\Server\\htdocs\\chat"));

    @Override
    public Response response(Request request, RequestHandler rh) throws IOException {
        return dir.response(request, rh);
    }
}
