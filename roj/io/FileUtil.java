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
package roj.io;

import roj.collect.MyHashMap;
import roj.concurrent.PrefixFactory;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.WaitingIOFuture;
import roj.concurrent.task.AbstractCalcTask;
import roj.io.down.Downloader;
import roj.io.down.IProgressHandler;
import roj.io.down.MTDProgress;
import roj.io.down.STDProgress;
import roj.net.http.HttpClient;
import roj.net.http.HttpConnection;
import roj.net.http.HttpHead;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 22:1
 */
public final class FileUtil {
    public static final MessageDigest MD5, SHA1;

    public static TaskHandler ioPool = new TaskPool(0, Runtime.getRuntime().availableProcessors() * 8, 1, 512,
                                                    new PrefixFactory("FLU-ParIO", 20000));

    static {
        MessageDigest MD, SH;
        try {
            MD = MessageDigest.getInstance("md5");
            SH = MessageDigest.getInstance("sha1");
        } catch (NoSuchAlgorithmException e) {
            System.err.println("MD5/SHA1 Algorithm not found!");
            System.exit(-2);
            MD = null;
            SH = null;
        }
        MD5 = MD;
        SHA1 = SH;
    }

    public static String USER_AGENT = "FileUtil-2.4.0";
    public static int MIN_ASYNC_SIZE = 1024 * 512;
    public static boolean CHECK_ETAG = true;
    public static int BUFFER_SIZE = 1024 * 4;
    public static int TIMEOUT = 10 * 1000;

    public static void copyFile(File source, File target) throws IOException {
        // noinspection all
        Thread.interrupted();
        FileChannel src = FileChannel.open(source.toPath(), StandardOpenOption.READ);
        FileChannel dst = FileChannel.open(target.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        src.transferTo(0, source.length(), dst);
        src.close();
        dst.close();
        /*try (FileInputStream fis = new FileInputStream(source)) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                IOUtil.readFully0(fis, IOUtil.BYTE_BUFFER.get()).writeToStream(fos);
            }
        }*/
    }

    public static final Predicate<File> TRUE_PREDICT = Helpers.alwaysTrue();

    public static List<File> findAllFiles(File file) {
        return findAllFiles(file, new ArrayList<>(), TRUE_PREDICT);
    }

    public static List<File> findAllFiles(File file, Predicate<File> predicate) {
        return findAllFiles(file, new ArrayList<>(), predicate);
    }

    private static List<File> findAllFiles(File file, List<File> files, Predicate<File> predicate) {
        File[] files1 = file.listFiles();
        if (files1 != null) {
            for (File file1 : files1) {
                if (file1.isDirectory()) {
                    findAllFiles(file1, files, predicate);
                } else {
                    if (predicate.test(file1)) {
                        files.add(file1);
                    }
                }
            }
        }
        return files;
    }

    public static Map<String, InputStream> findAndOpenStream(File path, Predicate<File> predicate) {
        return findAndOpenStream(path, new MyHashMap<>(), new CharList(), predicate);
    }

    public static Map<String, InputStream> findAndOpenStream(File file, Map<String, InputStream> map, Predicate<File> predicate) {
        return findAndOpenStream(file, map, new CharList(), predicate);
    }

    private static Map<String, InputStream> findAndOpenStream(File file, Map<String, InputStream> map, CharList relative, Predicate<File> predicate) {
        File[] files1 = file.listFiles();
        if (files1 != null) {
            int fl = relative.length();
            for (File file1 : files1) {
                if (file1.isDirectory()) {
                    findAndOpenStream(file1, map, relative.append(file1.getName()).append('/'), predicate);
                } else {
                    if (predicate.test(file1)) {
                        try {
                            map.put(relative.append(file1.getName()).toString(), new FileInputStream(file1));
                        } catch (FileNotFoundException e) {
                            throw new IllegalArgumentException("Not permission to read " + relative, e);
                        }
                    }
                }
                relative.setIndex(fl);
            }
        }
        return map;
    }

    public static boolean deletePath(File file) {
        File[] files = file.listFiles();
        boolean result = true;
        if (files != null) {
            for (File file1 : files) {
                if (file1.isDirectory()) {
                    result &= deletePath(file1);
                    result &= file1.delete();
                }
                result &= deleteFile(file1);
            }
        } else {
            result = false;
        }
        return result;
    }

    public static boolean deleteFile(File file) {
        return !file.exists() || file.delete();
    }

    public static byte[] downloadFileToMemory(String url) throws IOException {
        HttpConnection con = process302(new URL(url), false);
        try (InputStream in = con.getInputStream()) {
            int length = con.getContentLength();
            ByteList buf = IOUtil.getSharedByteBuf();
            buf.clear();
            buf.ensureCapacity(length);
            return buf.readStreamFully(in).toByteArray();
        }
    }

    /**
     * 单线程下载文件
     *
     * @param url  文件的网络地址
     * @param file 保存的文件地址
     */
    public static WaitingIOFuture downloadFile(String url, File file) throws IOException {
        return downloadFile(url, file, new File(file.getAbsolutePath() + ".down.nfo"), new STDProgress(), 0, true);
    }

    public static WaitingIOFuture downloadFile(String url, File file, File info, IProgressHandler handler, int pid, boolean deleteInfo) throws IOException {
        if(file.isFile()) {
            return new ImmediateFuture("done");
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建下载目录");

        if(info.isFile() && !checkTotalWritePermission(info)) {
            throw new IOException("下载进度文件无法写入");
        }

        return singleThread0(file, handler, pid, deleteInfo ? DFLAG_KEEP_INFO_FILE : 0, info, new URL(url));
    }

    private static WaitingIOFuture singleThread0(File file, IProgressHandler handler, int pid, int flag, File info, Object o) {
        File tmp = new File(file.getAbsolutePath() + ".down");
        AbstractCalcTask<Void> task = new AbstractCalcTask<Void>() {
            @Override
            public void calculate(Thread thread) throws Exception {
                HttpConnection conn;
                if (o instanceof HttpConnection) {
                    conn = (HttpConnection) o;
                } else {
                    conn = process302((URL) o, false);
                }

                long length = conn.getContentLengthLong();

                if (length < 0) {
                    if (!conn.getClient().method().equals("GET")) {
                        conn.disconnect();
                        conn.getClient().method("GET");
                    }
                    FileChannel fc = FileChannel.open(tmp.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                    InputStream in = conn.getInputStream();

                    ByteBuffer buf = ByteBuffer.allocate(8192);
                    int read;
                    while ((read = in.read(buf.array())) > 0) {
                        buf.position(0).limit(read);
                        fc.write(buf);
                    }
                } else {
                    if (tmp.length() != length) {
                        allocSparseFile(tmp, length);
                    }
                    new Downloader(pid, tmp, info, conn, 0, length, handler).waitFor();
                }
            }
        };
        ioPool.pushTask(task);

        return new DOWN(Collections.singletonList(task), handler, file, flag).infoSpecified(info);
    }

    /**
     * @see #downloadFileAsync(String, File, IProgressHandler, int, int)
     */
    public static WaitingIOFuture downloadFileAsync(String address, File file, IProgressHandler handler) throws IOException {
        return downloadFileAsync(address, file, handler, 0, Runtime.getRuntime().availableProcessors() << 2);
    }

    /**
     * @see #downloadFileAsync(String, File, IProgressHandler, int, int)
     */
    public static WaitingIOFuture downloadFileAsync(String address, File file) throws IOException {
        return downloadFileAsync(address, file, new MTDProgress(), 0, Runtime.getRuntime().availableProcessors() << 2);
    }

    /**
     * @see #downloadFileAsync(String, File, IProgressHandler, int, int)
     */
    public static WaitingIOFuture downloadFileAsync(String address, File file, int thread) throws IOException {
        return downloadFileAsync(address, file, new MTDProgress(), 0, thread);
    }

    public static final int DFLAG_KEEP_INFO_FILE = 1;

    /**
     * 多线程下载文件
     *
     * @param address    文件的网络地址
     * @param file       文件的保存地址
     * @param handler    进度条处理器
     * @param flag       标记
     * @param threadMax  最大线程数
     */
    public static WaitingIOFuture downloadFileAsync(String address, File file, IProgressHandler handler, int flag, int threadMax) throws IOException {
        if(file.isFile()) {
            return new ImmediateFuture("done");
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建下载目录");

        File infoFile = new File(file.getAbsolutePath() + ".down.nfo");
        if(infoFile.isFile() && !checkTotalWritePermission(infoFile)) {
            throw new IOException("下载进度文件无法写入");
        }

        HttpConnection conn = process302(new URL(address), true);

        long remain = conn.getContentLengthLong();

        if(remain < 0 || (conn.getHeaderField("ETag") == null && conn.getHeaderField("Last-Modified") == null)) {
            return singleThread0(file, handler, 0, flag, infoFile, conn);
        }

        if (CHECK_ETAG) {
            File tagFile = new File(file.getAbsolutePath() + ".down.tag");
            BoxFile aoc = new BoxFile(tagFile);
            aoc.load();
            if (/*!conn.getHeaderField("ETag").equals(aoc.getUTF("ETag")) || */
                    !conn.getHeaderField("Last-Modified").equals(aoc.getUTF("Last-Modified"))) {
                if (aoc.contains("ETag") && !infoFile.delete()) {
                    throw new IOException("fInfoFile文件已被占用");
                }
                aoc.append("ETag", ByteList.encodeUTF(conn.getHeaderField("ETag")));
                aoc.append("Last-Modified", ByteList.encodeUTF(conn.getHeaderField("Last-Modified")));
            }
            aoc.close();
        }

        File downTmp = new File(file.getAbsolutePath() + ".down");
        allocSparseFile(downTmp, remain);

        conn.disconnect();

        int id = 0;

        long each = Math.max(remain / threadMax, 4096);
        long off = 0;

        List<AbstractCalcTask<Void>> tasks = new ArrayList<>(threadMax);
        URL url = conn.getURL();
        if (each > MIN_ASYNC_SIZE)
            while (remain >= each && id < threadMax) {
                Downloader dn = new Downloader(id++, downTmp, infoFile, url, off, each, handler);
                if (dn.getDownloaded() != -1) {
                    ioPool.pushTask(dn);
                    tasks.add(dn);
                }

                off += each;
                remain -= each;
            }
        if (remain > 0) {
            Downloader dn = new Downloader(id, downTmp, infoFile, url, off, remain, handler);
            if (dn.getDownloaded() != -1) {
                ioPool.pushTask(dn);
                tasks.add(dn);
            }
        }

        return new DOWN(tasks, handler, file, flag);
    }

    public static void allocSparseFile(File file, long length) throws IOException {
        if (file.length() != length) {
            if (!file.isFile() || file.length() < length) {
                file.delete();
                FileChannel fc = FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, StandardOpenOption.SPARSE).position(length - 1);
                fc.write(ByteBuffer.wrap(new byte[1]));
                fc.close();
            } else if (length < Integer.MAX_VALUE) {
                RandomAccessFile raf = new RandomAccessFile(file, "rw");
                raf.setLength(length); // alloc
                raf.close();
            }
        }
    }

    public static HttpConnection process302(URL url, boolean headOnly) throws IOException {
        HttpConnection conn = new HttpConnection(url);

        HttpClient client = conn.getClient();
        client.header("User-Agent", USER_AGENT)
              .header("Range", "bytes=0-")
              .method(headOnly ? "HEAD" : "GET")
              .readTimeout(TIMEOUT);
        client.connectTimeout(TIMEOUT);

        int max = 10;
        do {
            conn.setURL(url);
            HttpHead header = conn.getResponse();
            int code = header.getCode();
            if (code >= 200 && code < 400) {
                String location = header.headers.get("Location");
                if (location != null) {
                    if (max-- < 0)
                        throw new FileNotFoundException("重定向过多");
                    url = new URL(location);
                    continue;
                } else if (code >= 300) {
                    throw new FileNotFoundException("远程返回状态码: " + code);
                }

                return conn;
            } else {
                throw new FileNotFoundException("远程返回状态码: " + code);
            }
        } while (true);
    }

    public static boolean checkTotalWritePermission(File file) {
        try(RandomAccessFile f = new RandomAccessFile(file, "rw")) {
            long l = f.length();
            f.setLength(l + 1);
            f.seek(l);
            f.writeByte(1);
            f.setLength(l);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static int removeEmptyPaths(Collection<String> files) {
        boolean oneRemoved = true;
        int i = 0;
        while (oneRemoved) {
            oneRemoved = false;
            for (String path : files) {
                File file = new File(path);
                while ((file = file.getParentFile()) != null) {
                    if (file.isDirectory() && file.list().length == 0) {
                        if (!file.delete())
                            System.err.println("无法删除目录 " + file);
                        oneRemoved = true;
                        i++;
                    }
                }
            }
        }
        return i;
    }

    public static long transferFileSelf(FileChannel cf, long fOffset, long tOffset, long length) throws IOException {
        // noinspection all
        Thread.interrupted();
        if (fOffset == tOffset || length == 0) return length;
        if (fOffset > tOffset ?
                tOffset + length <= fOffset :
                fOffset + length <= tOffset) { // 区间不交叉
            return cf.transferTo(fOffset, length, cf.position(tOffset));
        }
        long pos = cf.position();
        try {
            if (length <= 1048576) {
                ByteBuffer direct = ByteBuffer.allocateDirect((int) length);
                direct.position(0).limit((int) length);
                cf.read(direct, fOffset);
                direct.position(0);
                int amount = cf.position(tOffset).write(direct);
                NIOUtil.clean(direct);
                return amount;
            } else {
                File tmpFile = new File("FUT~" + (System.nanoTime() % 1000000) + ".tmp");
                FileChannel ct = FileChannel.open(tmpFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
                cf.transferTo(fOffset, length, ct.position(0));
                long amount = ct.transferTo(0, length, cf.position(tOffset));
                ct.close();
                return amount;
            }
        } finally {
            cf.position(pos);
        }
    }

    private static class DOWN implements WaitingIOFuture {
        private final List<AbstractCalcTask<Void>> tasks;
        private final IProgressHandler handler;
        private final File file;
        private File info;
        private final boolean deleteInfo;

        public DOWN(List<AbstractCalcTask<Void>> tasks, IProgressHandler handler, File file, int flag) {
            this.tasks = tasks;
            this.handler = handler;
            this.file = file;
            this.deleteInfo = (flag & DFLAG_KEEP_INFO_FILE) == 0;
        }

        @Override
        public void waitFor() throws IOException {
            for (AbstractCalcTask<Void> task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException ignored) {
                } catch (ExecutionException e) {
                    handler.errorCaught();
                    Helpers.athrow(e.getCause());
                }
            }

            handler.onReturn();

            StringBuilder err = new StringBuilder();

            File tag = new File(file.getAbsolutePath() + ".down.tag");
            if(tag.isFile() && !tag.delete()) {
                tag.deleteOnExit();
                err.append("; ETag标记删除失败. ");
            }

            if (deleteInfo) {
                File nfo = info != null ? info : new File(file.getAbsolutePath() + ".down.nfo");
                if (nfo.isFile() && !nfo.delete()) {
                    nfo.deleteOnExit();
                    err.append("; 下载进度删除失败. ");
                }
            }

            File tempFile = new File(file.getAbsolutePath() + ".down");
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
                AbstractCalcTask<Void> task = tasks.get(i);
                if (!task.isDone()) return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return tasks.toString();
        }

        public DOWN infoSpecified(File info) {
            this.info = info;
            return this;
        }
    }

    private static class ImmediateFuture implements WaitingIOFuture {
        final String r;

        public ImmediateFuture(String reason) {
            this.r = reason;
        }

        @Override
        public void waitFor() {}

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public String flag() {
            return r;
        }
    }
}