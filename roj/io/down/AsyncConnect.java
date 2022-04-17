package roj.io.down;

import roj.concurrent.OperationDone;
import roj.concurrent.Waitable;
import roj.concurrent.task.ITask;
import roj.io.BoxFile;
import roj.io.FileUtil;
import roj.io.NIOUtil;
import roj.net.http.HttpClient;
import roj.net.http.HttpConnection;
import roj.net.http.HttpHead;
import roj.net.misc.FDChannel;
import roj.util.ByteList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/5/1 0:55
 */
final class AsyncConnect extends FDChannel implements ITask, Waitable {
    volatile Waitable  result;
    volatile Throwable ex;

    private final File      file;
    private final IProgress pg;
    private final File      info;

    private URL address;
    private int state, max;
    private final HttpConnection conn;
    private long time;

    AsyncConnect(String address, File file, IProgress pg, File info) throws MalformedURLException {
        this.address = new URL(address);
        this.file = file;
        this.pg = pg;
        this.info = info;
        this.conn = new HttpConnection(this.address);
    }

    @Override
    public void calculate() {
        try {
            switch (state) {
                case 0:
                    connect();
                    break;
                case 1:
                    conn.getClient().send();
                    key.interestOps(SelectionKey.OP_READ);
                    state = 2;
                    time = System.currentTimeMillis() + FileUtil.timeout;
                    break;
                case 2:
                    time = Long.MAX_VALUE;
                    close();
                    if (checkConnectResult()) {
                        beginDownload();
                    } else {
                        connect();
                    }
                    break;
            }
        } catch (Throwable e) {
            ex = e;
            synchronized (this) {
                notifyAll();
            }
        }
    }

    private void connect() throws Exception {
        URL url = address;

        this.conn.setURL(url);
        HttpClient conn = this.conn.getClient();
        conn.header("User-Agent", FileUtil.userAgent)
            .method("HEAD").url(url)
            .readTimeout(FileUtil.timeout);
        time = System.currentTimeMillis() + FileUtil.timeout;

        int port = url.getPort();
        conn.createSocket(url.getHost(), port < 0 ? url.getDefaultPort() : port, false);
        ch = conn.getAsyncChannel();

        InetSocketAddress ep = conn.getEndpoint();
        InetAddress addr = ep.getAddress();
        if (addr.isAnyLocalAddress()) {
            addr = InetAddress.getLocalHost();
        }

        NIOUtil.connect(ch.fd(), addr, ep.getPort());
        state = 1;

        Downloader.POOL.register(this, null);
        key.interestOps(SelectionKey.OP_CONNECT);
    }

    @Override
    public void tick(int elapsed) throws IOException {
        if (System.currentTimeMillis() > time) {
            ex = new IOException("连接超时");
            close();
            state = -1;
            synchronized (this) {
                notifyAll();
            }
        }
    }

    public void close() {
        if (key != null) {
            try {
                conn.disconnect();
            } catch (IOException ignored) {}
            key.cancel();
            key = null;
        }
    }

    private boolean checkConnectResult() throws Exception {
        HttpHead header = conn.getResponse();
        int code = header.getCode();
        if (code >= 200 && code < 400) {
            String location = header.headers.get("Location");
            if (location != null) {
                if (max-- < 0) throw new FileNotFoundException("重定向过多");
                address = new URL(location);
                conn.disconnect();
                return false;
            } else if (code >= 300) {
                throw new FileNotFoundException("远程返回状态码: " + code);
            }
            return true;
        } else {
            throw new FileNotFoundException("远程返回状态码: " + code);
        }
    }

    private void beginDownload() throws Exception {
        HttpConnection conn = this.conn;
        if (!conn.getClient().connected()) return;

        long remain = conn.getContentLengthLong();

        if (remain < 0 || (conn.getHeaderField("ETag") == null
            && conn.getHeaderField("Last-Modified") == null)
            && !"bytes".equals(conn.getHeaderField("Accept-Ranges"))) {
            if (ex != null) return;
            result = Downloader.singleThread0(file, pg, info, conn);
            synchronized (this) {
                notifyAll();
            }
            return;
        }

        if (Downloader.checkETag) {
            File tagFile = new File(file.getAbsolutePath() + ".tag");
            BoxFile aoc = new BoxFile(tagFile);
            aoc.load();
            if ((aoc.contains("ETag") && !conn.getHeaderField("ETag").equals(aoc.getUTF("ETag"))) ||
                !conn.getHeaderField("Last-Modified").equals(aoc.getUTF("Last-Modified"))) {
                if (aoc.contains("ETag") && !info.delete()) {
                    throw new IOException("fInfoFile文件已被占用");
                }
                if (!conn.getHeaderField("ETag").startsWith("W/")) {
                    aoc.append("ETag", ByteList.encodeUTF(conn.getHeaderField("ETag")));
                }
                aoc.append("Last-Modified", ByteList.encodeUTF(conn.getHeaderField("Last-Modified")));
            }
            aoc.close();
        }

        File tmp = new File(file.getAbsolutePath() + ".tmp");
        FileUtil.allocSparseFile(tmp, remain);

        conn.disconnect();

        int id = 0;
        long off = 0;

        List<IDown> tasks = new ArrayList<>(Downloader.chunkCount);
        URL url = conn.getURL();
        if (remain > Downloader.chunkStartSize) {
            long each = Math.max(remain / Downloader.chunkCount, Downloader.minChunkSize);
            while (remain >= each) {
                Downloader dn = new Downloader(id++, tmp, info, url, off, each, pg);

                off += each;
                remain -= each;

                if (remain < each && remain < Downloader.minChunkSize) {
                    // 如果下载完毕
                    if (dn.len > 0) dn.len += remain;
                    remain = 0;
                }

                if (dn.getRemain() > 0) {
                    Downloader.HELP.pushTask(dn);
                    tasks.add(dn);
                }
            }
        }
        if (remain > 0) {
            Downloader dn = new Downloader(id, tmp, info, url, off, remain, pg);
            if (dn.getRemain() > 0) {
                Downloader.HELP.pushTask(dn);
                tasks.add(dn);
            }
        }

        if (ex != null) return;
        result = new AsyncWait(tasks, pg, file);
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void waitFor() throws IOException {
        while (true) {
            if (result != null) {
                result.waitFor();
                break;
            } else if (ex != null) {
                throw new IOException(ex);
            }
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
    }

    @Override
    public boolean isDone() {
        if (result != null) {
            return result.isDone();
        } else {
            return ex != null;
        }
    }

    @Override
    public void cancel() {
        close();
        if (result != null) {
            result.cancel();
        } else {
            ex = OperationDone.INSTANCE;
        }
    }

    @Override
    public void selected(int readyOps) throws Exception {
        key.interestOps(0);
        conn.getClient().setConnected();
        Downloader.HELP.pushTask(this);
    }
}
