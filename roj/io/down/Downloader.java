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

import roj.concurrent.WaitingIOFuture;
import roj.concurrent.task.AbstractCalcTask;
import roj.io.FileUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/9/13 12:28
 */
public class Downloader extends AbstractCalcTask<Void> implements Runnable, WaitingIOFuture {
    public static final int UPDATE_FREQUENCY = 1000;

    public final File file;
    public final URL url;
    public final long startPos, length;
    private long downloaded;
    public final int id;
    private final RandomAccessFile info;
    private final IProgressHandler progress;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean done = super.cancel(mayInterruptIfRunning);
        if(done && info != null) {
            try {
                info.close();
            } catch (IOException ignored) {}
        }
        return done;
    }

    /**
     * 多线程下载器
     *
     * @see Downloader#Downloader(int, File, File, URL, long, long, IProgressHandler)
     */
    public Downloader(int pid, File downloadTo, @Nullable File infoFile, URL url, long len) throws IOException {
        this(pid, downloadTo, infoFile, url, 0, len, null);
    }

    /**
     * 多线程下载器
     *
     * @see Downloader#Downloader(int, File, File, URL, long, long, IProgressHandler)
     */
    public Downloader(int pid, File downloadTo, @Nullable File infoFile, URL url, long start, long len) throws IOException {
        this(pid, downloadTo, infoFile, url, start, len, null);
    }

    /**
     * 多线程下载器
     *
     * @param pid        多线程ID
     * @param downloadTo 保存目标
     * @param infoFile   进度文件 (null: 不使用)
     * @param url        下载地址
     * @param start      起始位置
     * @param len        文件长度
     * @param progress   进度条监视器
     */
    public Downloader(int pid, File downloadTo, @Nullable File infoFile, URL url, long start, long len, IProgressHandler progress) throws IOException {
        this.id = pid;
        this.file = downloadTo;
        this.url = url;
        this.startPos = start;
        this.length = len;
        this.progress = progress;
        if (progress != null)
            progress.handleJoin(this);

        if (infoFile != null) {
            this.info = new RandomAccessFile(infoFile, "rw");
            if (this.info.length() < 8 * pid + 8)
                this.info.setLength(8 * pid + 8);
            info.seek(8 * pid);
            this.downloaded = info.readLong();
            //if (downloaded > 0)
            //    System.out.println("断点: " + TextUtil.getScaledNumber(downloaded).toUpperCase() + 'B');
            if (downloaded >= length)
                downloaded = -1;
            if (downloaded == -1) {
                writePos(-1);
                info.close();
                if (progress != null)
                    progress.handleDone(this);
            }
        } else {
            this.info = null;
        }
    }

    @Override
    public void calculate(Thread thread) {
        executing = true;
        try {
            waitFor();
        } catch (Throwable e) {
            exception = new ExecutionException(e);
        }
        executing = false;

        synchronized (this) {
            notifyAll();
        }
    }

    public void run() {
        try {
            waitFor();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return "Downloader#" + id + " " + startPos + " => " + (startPos + length - 1) + " (downloaded " + downloaded + ")";
    }

    public long getDownloaded() {
        return downloaded;
    }

    long last;
    
    private void writePos(long i) throws IOException {
        if (info != null) {
            long t = System.currentTimeMillis();
            if(t - last > 100) {
                info.seek(8 * id);
                info.writeLong(i);
                last = t;
            }
        }
    }

    protected InputStream setConnectData(HttpURLConnection conn) throws IOException {
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(FileUtil.TIMEOUT);
        conn.setReadTimeout(FileUtil.TIMEOUT);
        conn.setRequestProperty("User-Agent", FileUtil.USER_AGENT);
        conn.setRequestProperty("RANGE", "bytes=" + (startPos + downloaded) + '-' + (startPos + length - 1));
        //System.out.println(this);
        return conn.getInputStream();
    }

    @Override
    public void waitFor() throws IOException {
        if (this.downloaded == -1 || downloaded >= length) {
            out = null;
            return;
        }

        long lastTime;

        int speedLowCount = 0;
        try {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            InputStream is = setConnectData(conn);

            try {
                try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.seek(startPos + downloaded);

                    byte[] data = new byte[FileUtil.BUFFER_SIZE];

                    long deltaRead = 0;

                    int read;
                    lastTime = System.currentTimeMillis();
                    while (downloaded < length && -1 != (read = is.read(data))) {
                        raf.write(data, 0, read);
                        downloaded += read;
                        deltaRead += read;

                        writePos(downloaded);

                        if(progress != null && !progress.continueDownload())
                            return;

                        double t;
                        if (read != 0 && (t = System.currentTimeMillis() - lastTime) > UPDATE_FREQUENCY) {
                            lastTime = System.currentTimeMillis();

                            long speed = (long) (deltaRead * 1000d / t);

                            if (progress != null)
                                progress.handleProgress(this, downloaded, deltaRead);

                            if (speed < 1024 * 10 && downloaded < length) {
                                if (++speedLowCount == 3) {
                                    if (progress != null)
                                        progress.handleReconnect(this, downloaded);

                                    is.close();
                                    conn.disconnect();

                                    speedLowCount = 0;

                                    is = setConnectData(conn = (HttpURLConnection) url.openConnection());
                                    raf.seek(startPos + this.downloaded);
                                }
                            } else {
                                speedLowCount--;
                            }

                            deltaRead = 0;
                        }
                    }

                    writePos(-1);
                    if (progress != null)
                        progress.handleDone(this);
                }
            } finally {
                is.close();
            }
        } catch (Throwable e) {
            if(progress != null)
                progress.errorCaught();
            throw e;
        } finally {
            if (info != null) {
                try {
                    info.close();
                } catch (IOException ignored) {}
            }
            out = null;
        }
    }
}
