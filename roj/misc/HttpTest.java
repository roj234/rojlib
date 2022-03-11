package roj.misc;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.net.http.HttpClient;
import roj.net.http.HttpHead;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj234
 * @since  2021/1/22 5:38
 */
public class HttpTest {
    public static void main(String[] args) throws IOException, ParseException {
        Map<String, String> headers = new MyHashMap<>();
        headers.put("Host", "127.0.0.1");
        headers.put("Connection", "keep-alive");
        headers.put("User-Agent", "Roj234'sHttpClient");
        headers.put("Accept", "text/html");
        headers.put("Accept-Encoding", "gzip, deflate, identity");

        HttpClient c = new HttpClient();
        c.readTimeout(2000);
        c.method("GET").headers(headers);
        c.url(new URL("http://127.0.0.1:1999/att/1"));

        int i = 0;
        while (true) {
            c.send();
            HttpHead header = c.response();
            System.out.println(header);
            IOUtil.getSharedByteBuf().readStreamFully(c.getInputStream());;
            LockSupport.parkNanos(500_000_000L);

            if (++i == 1000) {
                System.out.println("1000!");
                i = 0;
            }
        }
    }
}
