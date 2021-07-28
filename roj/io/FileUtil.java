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

import org.jetbrains.annotations.Async;
import roj.collect.MyHashMap;
import roj.concurrent.Ref;
import roj.concurrent.WaitingIOFuture;
import roj.concurrent.pool.PrefixFactory;
import roj.concurrent.pool.TaskPool;
import roj.concurrent.task.AbstractCalcTask;
import roj.io.down.Downloader;
import roj.io.down.IProgressHandler;
import roj.io.down.MTDProgress;
import roj.io.down.STDProgress;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteWriter;
import roj.util.Helpers;
import roj.util.Operation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    public static TaskPool ioPool = new TaskPool(0, Runtime.getRuntime().availableProcessors() * 8, 1, 512,
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
    public static boolean ENABLE_ENDPOINT_RECOVERY = true;
    public static int BUFFER_SIZE = 1024 * 4;
    public static int TIMEOUT = 10 * 1000;

    public static void copyFile(File source, File target) throws IOException {
        try (FileInputStream fis = new FileInputStream(source)) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                IOUtil.readFully0(fis, IOUtil.BYTE_BUFFER.get()).writeToStream(fos);
            }
        }
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
                    findAndOpenStream(file1, map, relative.append(file1.getName()).append(File.separatorChar), predicate);
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
        Ref<byte[]> ref = Ref.from();

        try {
            process302(new URL(url), (httpConn) -> {
                try (InputStream is = httpConn.getInputStream()) {
                    int length = httpConn.getContentLength();

                    ref.set(new ByteList(length).readStreamArrayFully(is).toByteArray());
                }
            }, false);
        } catch (UnknownHostException e) {
            throw new FileNotFoundException("网址不存在: " + url);
        }

        return ref.get();
    }

    /**
     * 单线程下载文件
     *
     * @param url  文件的网络地址
     * @param file 保存的文件地址
     */
    public static void downloadFile(String url, File file) throws IOException {
        downloadFile(url, file, new File(file.getAbsolutePath() + ".nfo"), new STDProgress(), 0, true);
    }

    public static void downloadFile(String url, File file, File infoFile, IProgressHandler handler, int pid, boolean deleteInfo) throws IOException {
        if (file.isFile())
            throw new IOException("文件已存在");

        final File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建目录");

        File tempFile = new File(file.getAbsolutePath() + ".downing");

        boolean flag = infoFile.isFile();

        if (!flag || !ENABLE_ENDPOINT_RECOVERY) {
            if (!tempFile.isFile() && !(tempFile.delete() || tempFile.createNewFile())) {
                //lockFile.delete();
                throw new IOException("无法创建文件");
            }
            if (!ENABLE_ENDPOINT_RECOVERY)
                infoFile = null;
        }

        if(infoFile != null && infoFile.isFile()) {
            File lockTest = new File(infoFile.getAbsolutePath() + System.currentTimeMillis());
            if(!infoFile.renameTo(lockTest) || !lockTest.renameTo(infoFile))
                throw new IOException("文件已被占用");
        }

        File fInfoFile = infoFile;

        try {

            process302(new URL(url), (httpConn) -> {
                singleThread0(file, handler, pid, deleteInfo, tempFile, fInfoFile, httpConn);
            }, false);

        } catch (UnknownHostException e) {
            throw new FileNotFoundException("网址不存在: " + url);
        }
    }

    private static void singleThread0(File file, IProgressHandler handler, int pid, boolean deleteInfo, File tempFile, File fInfoFile, HttpURLConnection httpConn) throws IOException {
        long length = httpConn.getContentLengthLong();

        httpConn.disconnect();

        if (tempFile.length() != length) {
            RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
            raf.setLength(length);
            raf.close();
        }

        final Downloader downloader = new Downloader(pid, tempFile, fInfoFile, httpConn.getURL(), 0, length, handler);
        handler.onInitial(1);
        downloader.waitFor();
        handler.onReturn();

        StringBuilder err = new StringBuilder();

        if (!tempFile.renameTo(file)) {
            err.append("文件重命名失败, 请手动把 ").append(tempFile.getName()).append(" 重命名为 ").append(file.getName());
        }
        if (deleteInfo && (fInfoFile != null && !fInfoFile.delete())) {
            err.append("; 下载进度删除失败 ").append(fInfoFile.getName());
        }

        if(err.length() > 0)
            throw new IOException(err.toString());
    }

    /**
     * @see #downloadFileAsync(String, File, File, IProgressHandler, int, boolean, int)
     */
    @Async.Execute
    public static WaitingIOFuture downloadFileAsync(String address, File file, IProgressHandler handler) throws IOException {
        return downloadFileAsync(address, file, new File(file.getAbsolutePath() + ".mtd.nfo"), handler, 0, true);
    }

    /**
     * @see #downloadFileAsync(String, File, File, IProgressHandler, int, boolean, int)
     */
    @Async.Execute
    public static WaitingIOFuture downloadFileAsync(String address, File file) throws IOException {
        return downloadFileAsync(address, file, new File(file.getAbsolutePath() + ".mtd.nfo"), new MTDProgress(), 0, true);
    }

    /**
     * @see #downloadFileAsync(String, File, File, IProgressHandler, int, boolean, int)
     */
    @Async.Execute
    public static WaitingIOFuture downloadFileAsync(String address, File file, int max) throws IOException {
        return downloadFileAsync(address, file, new File(file.getAbsolutePath() + ".mtd.nfo"), new MTDProgress(), 0, true, max);
    }

    /**
     * @see #downloadFileAsync(String, File, File, IProgressHandler, int, boolean, int)
     */
    @Async.Execute
    public static WaitingIOFuture downloadFileAsync(String address, File file, File infoFile, IProgressHandler handler) throws IOException {
        return downloadFileAsync(address, file, infoFile, handler, 0, true);
    }

    /**
     * @see #downloadFileAsync(String, File, File, IProgressHandler, int, boolean, int)
     */
    @Async.Execute
    public static WaitingIOFuture downloadFileAsync(String address, File file, File infoFile, IProgressHandler handler, int startPid, boolean deleteInfoFile) throws IOException {
        return downloadFileAsync(address, file, infoFile, handler, startPid, deleteInfoFile, Runtime.getRuntime().availableProcessors() << 2);
    }

    /**
     * 多线程下载文件
     *
     * @param address        文件的网络地址
     * @param file           保存的文件地址
     * @param infoFile       下载进度文件
     * @param handler        进度条处理器
     * @param startPid       线程起始ID
     * @param deleteInfo 完成后删除进度
     * @param threadMax      最大线程数
     */
    @Async.Execute
    public static WaitingIOFuture downloadFileAsync(String address, File file, File infoFile, IProgressHandler handler, int startPid, boolean deleteInfo, final int threadMax) throws IOException {
        if(file.isFile()) {
            return new ImmediateFuture("done");
        }

        final File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建目录");

        File tempFile = new File(file.getAbsolutePath() + ".downloading");

        URL url = new URL(address);

        boolean flag = infoFile.isFile() && ENABLE_ENDPOINT_RECOVERY;

        if (!flag && tempFile.isFile() && !tempFile.delete()) {
            //lockFile.delete();
            throw new IOException("无法删除零食文件");
        }
        if (!ENABLE_ENDPOINT_RECOVERY) {
            infoFile = null;
        }

        if(infoFile != null && infoFile.isFile()) {
            if(!checkTotalWritePermission(infoFile)) {
                throw new IOException("文件已被占用");
            }
        }

        File fInfoFile = infoFile;

        List<AbstractCalcTask<Void>> tasks = new ArrayList<>(threadMax);
        try {
            process302(url, (conn) -> {

                URL url1 = conn.getURL();

                if(/*conn.getResponseCode() != 206*/conn.getHeaderField("ETag") == null) { // Partial content
                    singleThread0(file, handler, 0, deleteInfo, tempFile, fInfoFile, conn);
                    if(!fInfoFile.delete()) {
                        fInfoFile.deleteOnExit();
                        //throw new IOException("fInfoFile文件已被占用");
                    }
                    System.out.println("不支持多线程！");
                    return;
                }

                File tagFile = new File(fInfoFile.getAbsolutePath() + ".tag");
                AppendOnlyCache aoc = new AppendOnlyCache(tagFile);
                aoc.read();
                if(!aoc.contains("ETag")) {
                    aoc.append("ETag", ByteWriter.encodeUTF(conn.getHeaderField("ETag")));
                    aoc.append("Last-Modified", ByteWriter.encodeUTF(conn.getHeaderField("Last-Modified")));
                } else {
                    String ck = conn.getHeaderField("ETag");
                    if (!aoc.getUTF("ETag").equals(ck)) {
                        if(!fInfoFile.delete()) {
                            throw new IOException("fInfoFile文件已被占用");
                        }
                    } else {
                        ck = conn.getHeaderField("Last-Modified");
                        if (!aoc.getUTF("Last-Modified").equals(ck)) {
                            if(!fInfoFile.delete()) {
                                throw new IOException("fInfoFile文件已被占用");
                            }
                        }
                    }
                }
                aoc.close();

                conn.disconnect();

                long length = conn.getContentLengthLong();
                if (tempFile.length() != length) {
                    RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
                    raf.setLength(length); // alloc
                    raf.close();
                }

                int remain = threadMax;

                long chip = Math.max(length / threadMax, threadMax);
                long off = 0;

                if (chip > MIN_ASYNC_SIZE)
                    while (length >= chip && remain-- > 0) {
                        Downloader downloader = new Downloader(remain + startPid, tempFile, fInfoFile, url1, off, chip, handler);
                        if (downloader.getDownloaded() != -1)
                            tasks.add(downloader);

                        off += chip;
                        length -= chip;
                    }
                if (length > 0) {
                    Downloader downloader = new Downloader(threadMax + startPid, tempFile, fInfoFile, url1, off, length, handler);
                    if (downloader.getDownloaded() != -1)
                        tasks.add(downloader);
                }
            }, true);
        } catch (UnknownHostException e) {
            throw new FileNotFoundException("网址不存在: " + address);
        }

        if(tasks.isEmpty()) {
            return new ImmediateFuture("unsupported");
        }

        handler.onInitial(tasks.size());

        for (AbstractCalcTask<Void> task : tasks) {
            ioPool.pushTask(task);
        }

        return new MTD(tasks, handler, tempFile, file, deleteInfo, fInfoFile);
    }

    public static void process302(URL url, Operation<IOException, HttpURLConnection> operation, boolean headOnly) throws IOException {
        HttpURLConnection conn;
        do {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(headOnly ? "HEAD" : "GET");
            conn.setConnectTimeout(TIMEOUT);
            conn.setReadTimeout(TIMEOUT);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Range", "bytes=0-");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                String location = conn.getHeaderField("Location");
                if (location != null) {
                    conn.disconnect();
                    url = new URL(location);
                    continue;
                } else if (code >= 300) {
                    throw new FileNotFoundException("远程返回状态码: " + code);
                }

                operation.work(conn);

                break;
            } else {
                throw new FileNotFoundException("远程返回状态码: " + code);
            }
        } while (true);
    }

    public static boolean checkTotalWritePermission(File file) {
        try(RandomAccessFile f = new RandomAccessFile(file, "rw")) {
            f.setLength(f.length() + 1);
            f.seek(f.length());
            f.writeByte(1);
            f.setLength(f.length() - 1);
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

    private static class MTD implements WaitingIOFuture {
        private final List<AbstractCalcTask<Void>> tasks;
        private final IProgressHandler handler;
        private final File tempFile;
        private final File file;
        private final boolean deleteInfo;
        private final File fInfoFile;

        public MTD(List<AbstractCalcTask<Void>> tasks, IProgressHandler handler, File tempFile, File file, boolean deleteInfo, File fInfoFile) {
            this.tasks = tasks;
            this.handler = handler;
            this.tempFile = tempFile;
            this.file = file;
            this.deleteInfo = deleteInfo;
            this.fInfoFile = fInfoFile;
        }

        @Override
        public void waitFor() throws IOException {
            for (AbstractCalcTask<Void> task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException e) {
                    System.err.println("That shouldn't happen!");
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    handler.errorCaught();

                    Throwable cause = e.getCause();
                    if(cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    }

                    if(cause instanceof IOException) {
                        throw (IOException) cause;
                    }

                    throw new RuntimeException(cause);
                }
            }

            handler.onReturn();

            StringBuilder err = new StringBuilder();

            if(fInfoFile != null) {
                File tagFile = new File(fInfoFile.getAbsolutePath() + ".tag");
                if(tagFile.isFile() && !tagFile.delete()) {
                    tagFile.deleteOnExit();
                    err.append("; ETag缓存删除失败 ").append(tagFile.getAbsolutePath());
                }
            }

            if (!tempFile.renameTo(file)) {
                if(file.isFile()) {
                    err.append("; 文件已被另一个线程完成.");
                } else {
                    err.append("; 文件重命名失败, 请手动把 ").append(tempFile.getAbsolutePath()).append(" 重命名为 ").append(file.getAbsolutePath());
                }
            }
            if (deleteInfo && (fInfoFile != null && fInfoFile.isFile() && !fInfoFile.delete())) {
                err.append("; 下载进度删除失败 ").append(fInfoFile.getAbsolutePath());
                fInfoFile.deleteOnExit();
            }
            //if (lockFile.isFile() && !lockFile.delete()) {
            //    err.append("; 文件锁删除失败 ").append(lockFile.getName());
            //}

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