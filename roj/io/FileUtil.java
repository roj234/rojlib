package roj.io;

import org.jetbrains.annotations.Async;
import roj.concurrent.Holder;
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
import roj.util.Operation;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: FileUtil.java
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

    public static String USER_AGENT = "FileUtil-2.3.2";
    public static int MIN_ASYNC_SIZE = 1024 * 512;
    public static boolean ENABLE_ENDPOINT_RECOVERY = true;
    public static int BUFFER_SIZE = 1024 * 4;
    public static int TIMEOUT = 10 * 1000;

    public static void copyFile(File source, File target) throws IOException {
        try (FileInputStream fis = new FileInputStream(source)) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                IOUtil.readFully0(fis).writeToStream(fos);
            }
        }
    }

    public static final Predicate<File> TRUE_PREDICT = (f) -> true;

    public static List<File> findAllFiles(File file) {
        return findAllFiles(file, new ArrayList<>(), (s) -> true);
    }

    public static List<File> findAllFiles(File file, Predicate<File> predicate) {
        return findAllFiles(file, new ArrayList<>(), predicate);
    }

    public static Map<String, InputStream> findAndOpenStream(File path) throws FileNotFoundException {
        return findAndOpenStream(path, new HashMap<>(), "", TRUE_PREDICT);
    }

    public static Map<String, InputStream> findAndOpenStream(File path, Predicate<File> predicate) throws FileNotFoundException {
        return findAndOpenStream(path, new HashMap<>(), "", predicate);
    }

    public static Map<String, InputStream> findAndOpenStream(File file, Map<String, InputStream> map, Predicate<File> predicate) throws FileNotFoundException {
        return findAndOpenStream(file, map, "", predicate);
    }

    private static Map<String, InputStream> findAndOpenStream(File file, Map<String, InputStream> map, String relative, Predicate<File> predicate) throws FileNotFoundException {
        File[] files1 = file.listFiles();
        if (files1 != null) {
            for (File file1 : files1) {
                if (file1.isDirectory()) {
                    findAndOpenStream(file1, map, relative + file1.getName() + File.separatorChar, predicate);
                } else {
                    if (predicate.test(file1)) {
                        map.put(relative + file1.getName(), new FileInputStream(file1));
                    }
                }
            }
        }
        return map;
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

    public static void prepareInputFromTxt(File input, String workPath, Charset charset, Map<String, InputStream> streams) throws IOException {
        StringBuilder sb = new StringBuilder(new String(IOUtil.readFully(new FileInputStream(input)), charset).replace(workPath, ""));
        CharList charList = new CharList(40);

        boolean flag = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            switch (c) {
                case '\r':
                case '\n':
                    flag = true;
                    break;
                default:
                    if (flag) {
                        // only 1 \r or \n
                        handleFileList(streams, replaceFileDesc(charList));
                        charList.clear();
                    }
                    charList.append(c);
                    flag = false;
            }
        }
        if (charList.length() != 0) {
            handleFileList(streams, replaceFileDesc(charList));
        }
    }

    private static void handleFileList(Map<String, InputStream> streams, String name) {
        if (name.endsWith("/") || name.endsWith("\\")) return;
        try {
            streams.put(name, new FileInputStream(new File(name)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String replaceFileDesc(CharList charList) {
        int flag = 0;
        for (int i = 0; i < charList.length(); i++) {
            char c = charList.charAt(i);
            if (c == '\\') {
                if (++flag > 1) {
                    charList.delete(i);
                } else {
                    charList.set(i, '/');
                }
            } else {
                flag = 0;
            }
        }
        return charList.toString();
    }

    public static byte[] downloadFileToMemory(String url) throws IOException {
        Holder<byte[]> holder = Holder.from(null);

        try {
            process302(new URL(url), (httpConn) -> {
                try (InputStream is = httpConn.getInputStream()) {
                    int length = httpConn.getContentLength();

                    holder.set(new ByteList(length).readStreamArrayFully(is).toByteArray());
                }
            });
        } catch (UnknownHostException e) {
            throw new FileNotFoundException("网址不存在: " + url);
        }

        return holder.get();
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

        if(infoFile != null) {
            File lockTest = new File(infoFile.getAbsolutePath() + System.currentTimeMillis());
            if(!infoFile.renameTo(lockTest) || !lockTest.renameTo(infoFile))
                throw new IOException("文件已被占用");
        }

        File fInfoFile = infoFile;

        try {

            process302(new URL(url), (httpConn) -> {
                long length = httpConn.getContentLengthLong();

                httpConn.disconnect();

                if (tempFile.length() != length) {
                    RandomAccessFile raf = new RandomAccessFile(tempFile, "rw");
                    raf.setLength(length);
                    raf.close();
                }

                final Downloader downloader = new Downloader(pid, tempFile, fInfoFile, httpConn.getURL(), 0, length, handler);
                handler.onInitial(1);
                downloader.run();
                handler.onReturn();

                StringBuilder err = new StringBuilder();

                if (!tempFile.renameTo(file)) {
                    err.append("; 文件重命名失败, 请手动把 ").append(tempFile.getName()).append(" 重命名为 ").append(file.getName());
                }
                if (deleteInfo && (fInfoFile != null && !fInfoFile.delete())) {
                    err.append("; 下载进度删除失败 ").append(fInfoFile.getName());
                }

                if(err.length() > 0)
                    throw new IOException(err.toString());
            });

        } catch (UnknownHostException e) {
            throw new FileNotFoundException("网址不存在: " + url);
        }
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
            throw new IOException("文件已存在");
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
            File lockTest = new File(infoFile.getAbsolutePath() + System.currentTimeMillis());
            if(!infoFile.renameTo(lockTest) || !lockTest.renameTo(infoFile))
                throw new IOException("文件已被占用");
        }

        File fInfoFile = infoFile;

        List<AbstractCalcTask<Void>> tasks = new ArrayList<>(threadMax);
        try {
            process302(url, (conn) -> {
                long length = conn.getContentLengthLong();

                conn.disconnect();

                URL url1 = conn.getURL();

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
            });
        } catch (UnknownHostException e) {
            throw new FileNotFoundException("网址不存在: " + address);
        }

        handler.onInitial(tasks.size());

        for (AbstractCalcTask<Void> task : tasks) {
            ioPool.pushTask(task);
        }

        return new WaitingIOFuture() {
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

                if (!tempFile.renameTo(file)) {
                    err.append("; 文件重命名失败, 请手动把 ").append(tempFile.getName()).append(" 重命名为 ").append(file.getName());
                }
                if (deleteInfo && (fInfoFile != null && !fInfoFile.delete())) {
                    err.append("; 下载进度删除失败 ").append(fInfoFile.getName());
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
        };
    }

    public static void process302(URL url, Operation<IOException, HttpURLConnection> operation) throws IOException {
        HttpURLConnection conn;
        do {
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15 * 1000);
            conn.setRequestProperty("User-Agent", USER_AGENT);
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

}