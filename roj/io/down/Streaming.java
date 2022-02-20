package roj.io.down;

import roj.io.FileUtil;
import roj.net.http.HttpClient;
import roj.net.http.HttpHead;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.channels.SelectionKey;

import static roj.io.down.Downloader.HELP;

/**
 * @author Roj233
 * @since 2022/2/28 21:38
 */
final class Streaming extends IDown {
    private long downloaded;
    private InputStream in;

    Streaming(File file, URL url, IProgress progress) throws IOException {
        this.file = new RandomAccessFile(file, "rw");
        this.file.seek(0);
        this.client = new HttpClient();
        this.url = url;

        this.buf = new byte[4096];
        this.progress = progress;

        HttpClient client = this.client;
        client.method("GET")
              .header("User-Agent", FileUtil.userAgent)
              .connectTimeout(FileUtil.timeout);
        client.readTimeout(FileUtil.timeout);
    }

    public long getDownloaded() {
        return downloaded;
    }

    public long getRemain() {
        return -1;
    }

    public long getTotal() {
        return -1;
    }

    // Unit: byte per second
    public long getAverageSpeed() {
        return (long) ((double) downloaded / (System.currentTimeMillis() - begin) * 1000);
    }

    void selected0() throws Exception {
        int count = in.read(buf);
        if (count >= 0) {
            downloaded += count;
            file.write(buf, 0, count);
        }

        if (count < buf.length) {
            file.setLength(file.getFilePointer());
            close();
        }

        if (progress != null) progress.onChange(this);
    }

    @Override
    public void close() {
        try {
            ch.close();
        } catch (IOException ignored) {}
        try {
            file.close();
        } catch (IOException ignored) {}
        if (key != null) key.cancel();
        in = null;

        idle = -999;
        synchronized (client) {
            client.notifyAll();
        }
    }

    @Override
    void retry0() throws IOException {
        downloaded = 0;
        file.seek(0);
    }

    @Override
    public void calculate() throws Exception {
        if (!client.connected()) {
            client.url(url).send();
            ch = client.getChannel();

            if (ch.read() == 0) {
                init = true;
                reg();
            } else {
                HELP.pushTask(this);
            }
        } else {
            HttpHead r = client.response();
            if (r.getCode() > 299) {
                System.out.println("范围错误 code=" + r.getCodeString());
            }

            in = client.getInputStream();

            if (key == null) reg();

            key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            init = false;
        }
    }
}
