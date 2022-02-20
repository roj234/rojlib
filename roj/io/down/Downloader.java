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
package roj.io.down;

import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.Waitable;
import roj.io.BoxFile;
import roj.io.FileUtil;
import roj.io.FileUtil.ImmediateFuture;
import roj.net.http.HttpClient;
import roj.net.http.HttpConnection;
import roj.net.http.HttpHead;
import roj.net.misc.FDCLoop;
import roj.net.misc.FDChannel;
import roj.util.ByteList;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since  2020/9/13 12:28
 */
public final class Downloader extends IDown {
    static final TaskHandler HELP = new TaskPool(0, 3, 10, 20000, "下载器帮助线程");
    static final FDCLoop<FDChannel> POOL = new FDCLoop<>(null, "下载器", 0, 4, 60000, 500);

    public static int chunkStartSize = 1024 * 512;
    public static int chunkCount     = 16;
    public static int minChunkSize   = 1024;
    public static int maxRetryCount  = 3;
    public static boolean checkETag  = true;

    public static Waitable download(String url, File file) throws IOException {
        return download(url, file, new STDProgress());
    }

    /**
     * 单线程下载文件
     */
    public static Waitable download(String url, File file, IProgress handler) throws IOException {
        if(file.isFile()) return new ImmediateFuture();

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建下载目录");

        File info = new File(file.getAbsolutePath() + ".nfo");
        if(info.isFile() && !FileUtil.checkTotalWritePermission(info)) {
            throw new IOException("下载进度文件无法写入");
        }

        HttpConnection conn = FileUtil.process302(new URL(url), false);
        return singleThread0(file, handler, info, conn);
    }

    private static Waitable singleThread0(File file, IProgress handler, File info, HttpConnection o) throws IOException {
        File tmp = new File(file.getAbsolutePath() + ".tmp");

        IDown d;
        long len = o.getContentLengthLong();
        o.disconnect();
        if (len < 0) {
            d = new Streaming(tmp, o.getURL(), handler);
        } else {
            d = new Downloader(0, tmp, info, o.getURL(), 0, len, handler);
        }
        HELP.pushTask(d);
        return new D(Collections.singletonList(d), handler, file);
    }

    public static Waitable downloadMTD(String address, File file) throws IOException {
        return downloadMTD(address, file, new MTDProgress());
    }

    /**
     * 多线程下载文件
     */
    public static Waitable downloadMTD(String address, File file, IProgress pg) throws IOException {
        if(file.isFile()) {
            return new ImmediateFuture();
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建下载目录");

        File info = new File(file.getAbsolutePath() + ".nfo");
        if(info.isFile() && !FileUtil.checkTotalWritePermission(info)) {
            throw new IOException("下载进度文件无法写入");
        }

        HttpConnection conn = FileUtil.process302(new URL(address), true);

        long remain = conn.getContentLengthLong();

        if(remain < 0 || (conn.getHeaderField("ETag") == null && conn.getHeaderField("Last-Modified") == null)) {
            return singleThread0(file, pg, info, conn);
        }

        if (checkETag) {
            File tagFile = new File(file.getAbsolutePath() + ".tag");
            BoxFile aoc = new BoxFile(tagFile);
            aoc.load();
            if ((aoc.contains("ETag") && !conn.getHeaderField("ETag").equals(aoc.getUTF("ETag"))) ||
                    !conn.getHeaderField("Last-Modified").equals(aoc.getUTF("Last-Modified"))) {
                if (aoc.contains("ETag") && !info.delete()) {
                    throw new IOException("fInfoFile文件已被占用");
                }
                if (!conn.getHeaderField("ETag").startsWith("W/"))
                aoc.append("ETag", ByteList.encodeUTF(conn.getHeaderField("ETag")));
                aoc.append("Last-Modified", ByteList.encodeUTF(conn.getHeaderField("Last-Modified")));
            }
            aoc.close();
        }

        File tmp = new File(file.getAbsolutePath() + ".tmp");
        FileUtil.allocSparseFile(tmp, remain);

        conn.disconnect();

        int id = 0;
        long off = 0;

        List<IDown> tasks = new ArrayList<>(chunkCount);
        URL url = conn.getURL();
        if (remain > chunkStartSize) {
            long each = Math.max(remain / chunkCount, minChunkSize);
            while (remain >= each) {
                Downloader dn = new Downloader(id++, tmp, info, url, off, each, pg);
                if (dn.getRemain() > 0) {
                    HELP.pushTask(dn);
                    tasks.add(dn);
                }

                off += each;
                remain -= each;
            }
        }
        if (remain > 0) {
            Downloader dn = new Downloader(id, tmp, info, url, off, remain, pg);
            if (dn.getRemain() > 0) {
                HELP.pushTask(dn);
                tasks.add(dn);
            }
        }

        return new D(tasks, pg, file);
    }

    private final RandomAccessFile info;

    private final long beginLen;
    private long off, len;

    public Downloader(int pid, File file, File info, URL url, long off, long len) throws IOException {
        this(pid, file, info, url, off, len, null);
    }

    public Downloader(int pid, File file, File Info, URL url, long off, long len, IProgress progress) throws IOException {
        this.file = new RandomAccessFile(file, "rw");
        this.client = new HttpClient();
        this.progress = progress;
        this.beginLen = len;
        this.url = url;
        if (progress != null) progress.onJoin(this);

        if (Info != null) {
            this.info = new RandomAccessFile(Info, "rw");
            if (this.info.length() < 8 * pid + 8)
                this.info.setLength(8 * pid + 8);
            info.seek(8 * pid);
            long dt = info.readLong();
            off += dt; len -= dt;
            if (len <= 0 || dt < 0) {
                if (dt > 0) writePos(-1);
                close();
                return;
            }
        } else {
            this.info = null;
        }

        this.file.seek(off);
        this.off = off;
        this.len = len;

        this.buf = new byte[Math.min((int) len, 4096)];

        HttpClient client = this.client;
        client.method("GET")
              .header("User-Agent", FileUtil.userAgent)
              .connectTimeout(FileUtil.timeout);
        client.readTimeout(FileUtil.timeout);
    }

    public long getDownloaded() {
        return beginLen - len;
    }

    public long getRemain() {
        return len;
    }

    public long getTotal() {
        return beginLen;
    }

    // Unit: byte per second
    public long getAverageSpeed() {
        return (long) ((double) (beginLen - len) / (System.currentTimeMillis() - begin) * 1000);
    }

    void selected0() throws Exception {
        ByteBuffer in = ch.buffer();
        int r = in.position();

        off += r; len -= r;

        in.flip();
        do {
            int r2 = Math.min(r, buf.length);
            in.get(buf, 0, r2);
            file.write(buf, 0, r2);
            r -= r2;
        } while (r > 0);
        in.clear();

        if (len == 0) {
            // writePos会限制写入速度, 100ms
            // 所以这里赋值, 一定让它写入
            last = 0;
            writePos(-1);
            close();
        } else {
            writePos(beginLen - len);
        }
        if (progress != null) progress.onChange(this);
    }

    @Override
    public void close() {
        if (ch != null) {
            try {
                ch.close();
            } catch (IOException ignored) {}
        }

        if (file != null) {
            try {
                file.close();
            } catch (IOException ignored) {}
        }

        if (info != null) {
            try {
                writePos(beginLen - len);
            } catch (IOException ignored) {}
            try {
                info.close();
            } catch (IOException ignored) {}
        }

        if (key != null) key.cancel();

        idle = -999;
        synchronized (client) {
            client.notifyAll();
        }
    }

    private long last;
    private void writePos(long i) throws IOException {
        if (info != null) {
            long t = System.currentTimeMillis();
            if(t - last > 50) {
                info.writeLong(i);
                info.seek(info.getFilePointer() - 8);
                last = t;
            }
        }
    }

    @Override
    public void calculate() throws Exception {
        try {
            if (!client.connected()) {
                client.header("RANGE", "bytes=" + off + '-' + (off + len - 1));
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
                System.out.println(r);
                if (r.getCode() > 299) {
                    System.out.println("范围错误 " + off + '-' + (off + len - 1) + " , code=" + r.getCodeString());
                }

                ByteBuffer buf = ch.buffer();
                buf.position(buf.limit()).limit(buf.capacity());

                if (key == null) reg();

                // 随着http头接收到的还可能有一些数据,设置为'可写'selected能够被调用
                // 因为这里不是selector线程所以不能直接调用
                key.interestOps(buf.position() > 0 ?
                                    SelectionKey.OP_READ | SelectionKey.OP_WRITE :
                                    SelectionKey.OP_READ);

                init = false;
            }
        } catch (Throwable e) {
            retry();
            throw e;
        }
    }

    private static final class D implements Waitable {
        private final List<IDown> tasks;
        private final IProgress        handler;
        private final File             file;

        D(List<IDown> tasks, IProgress handler, File file) {
            this.tasks = tasks;
            this.handler = handler;
            this.file = file;
        }

        @Override
        public void waitFor() throws IOException {
            for (IDown task : tasks) {
                synchronized (task) {
                    try {
                        task.waitFor();
                    } catch (InterruptedException ignored) {}
                }
            }

            if (handler.wasShutdown()) {
                throw new EOFException("下载失败");
            }

            handler.onFinish();

            StringBuilder err = new StringBuilder();

            File tag = new File(file.getAbsolutePath() + ".tag");
            if(tag.isFile() && !tag.delete()) {
                tag.deleteOnExit();
                err.append("; ETag标记删除失败. ");
            }

            File info = new File(file.getAbsolutePath() + ".nfo");
            if (info.isFile() && !info.delete()) {
                info.deleteOnExit();
                err.append("; 下载进度删除失败. ");
            }

            File tempFile = new File(file.getAbsolutePath() + ".tmp");
            if (!tempFile.renameTo(file)) {
                if(file.isFile()) {
                    err.append("; 文件已被另一个线程完成.");
                } else {
                    err.append("; 文件重命名失败.");
                }
            }

            if(err.length() > 0)
                throw new IOException(err.toString());
        }

        @Override
        public boolean isDone() {
            boolean done = true;
            for (int i = 0; i < tasks.size(); i++) {
                IDown task = tasks.get(i);
                if (task.getRemain() > 0) return false;
            }
            return true;
        }

        @Override
        public void cancel() {
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    tasks.get(i).close();
                } catch (IOException e) {
                    // should not happen
                }
            }
        }

        @Override
        public String toString() {
            return tasks.toString();
        }
    }
}