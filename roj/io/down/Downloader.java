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
import roj.io.FileUtil;
import roj.io.FileUtil.ImmediateFuture;
import roj.net.http.HttpClient;
import roj.net.http.HttpConnection;
import roj.net.http.HttpHead;
import roj.net.misc.FDCLoop;
import roj.net.misc.FDChannel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Collections;

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

    static Waitable singleThread0(File file, IProgress handler, File info, HttpConnection o) throws IOException {
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
        return new AsyncWait(Collections.singletonList(d), handler, file);
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

        AsyncConnect ad = new AsyncConnect(address, file, pg, info);
        HELP.pushTask(ad);
        return ad;
    }

    private final RandomAccessFile info;

    private final long beginLen;
    long off, len;
    private int delta;

    public Downloader(int pid, File file, File info, URL url, long off, long len) throws IOException {
        this(pid, file, info, url, off, len, null);
    }

    public Downloader(int pid, File file, File Info, URL url, long off, long len, IProgress progress) throws IOException {
        this.beginLen = len;

        if (Info != null) {
            this.info = new RandomAccessFile(Info, "rw");
            if (this.info.length() < 8 * pid + 8)
                this.info.setLength(8 * pid + 8);
            info.seek(8 * pid);
            long dt = info.readLong();
            off += dt; len -= dt;
            if (len <= 0 || dt < 0) {
                if (dt > 0) writePos(-1);
                info.close();
                return;
            }
        } else {
            this.info = null;
        }

        this.file = new RandomAccessFile(file, "rw");
        this.client = new HttpClient();
        this.progress = progress;
        this.url = url;
        if (progress != null) progress.onJoin(this);

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
        long spd = (long) ((double) delta / (System.currentTimeMillis() - begin) * 1000);
        delta = 0;
        begin = System.currentTimeMillis();
        return spd;
    }

    void selected0() throws Exception {
        ByteBuffer in = ch.buffer();
        int r = in.position();

        off += r; len -= r;
        delta += r;

        in.flip();
        do {
            int r2 = Math.min(r, buf.length);
            in.get(buf, 0, r2);
            try {
                file.write(buf, 0, r2);
            } catch (Throwable e) {
                close();
                return;
            }
            r -= r2;
        } while (r > 0);
        in.clear();

        if (len <= 0) {
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
    protected void setClientInfo(HttpClient client) throws Exception {
        switch (state) {
            case 1:
                client.header("RANGE", "bytes=" + off + '-' + (off + len - 1));
                client.url(url).send();
                break;
            case 2:
                HttpHead r = client.response();
                if (r.getCode() > 299) {
                    throw new IOException("范围错误 " + off + '-' + (off + len - 1) + " , code=" + r.getCodeString());
                }
                break;
        }
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

        if (key != null) {
            key.cancel();
            key = null;
        }

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
                info.seek(info.getFilePointer() - 8);
                info.writeLong(i);
                last = t;
            }
        }
    }
}