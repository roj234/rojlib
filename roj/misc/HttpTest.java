package roj.misc;

import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
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
public class HttpTest implements Runnable {
    static Map<String, String> headers;

    public static void main(String[] args) {
        headers = new MyHashMap<>();
        headers.put("Host", "127.0.0.1");
        headers.put("Connection", "keep-alive");
        headers.put("User-Agent", "Roj234'sHttpClient");
        headers.put("Accept", "text/html");
        headers.put("Accept-Encoding", "gzip, deflate, identity");

        TaskPool pool = new TaskPool(8,8,99);
        for (int i = 0; i < 40; i++) {
            pool.pushRunnable(new HttpTest());
        }
        LockSupport.park();
    }

    @Override
    public void run() {
        HttpClient c = new HttpClient();
        c.readTimeout(2000);
        c.method("GET").headers(headers);
        try {
            c.url(new URL("http://127.0.0.1:2333/att/1"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int i = 0;
        do {
            try {
                c.send();
                HttpHead header = c.response();
                IOUtil.getSharedByteBuf().readStreamFully(c.getInputStream());
            } catch (Throwable e) {
                synchronized (System.err) {
                    e.printStackTrace();
                }
                break;
            }
        } while (++i < 1000);
        System.out.println("done");
    }
}
