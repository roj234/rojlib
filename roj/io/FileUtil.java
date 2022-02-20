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

import roj.concurrent.Waitable;
import roj.net.http.HttpClient;
import roj.net.http.HttpConnection;
import roj.net.http.HttpHead;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Roj234
 * @since 2021/5/29 22:1
 */
public final class FileUtil {
    public static final MessageDigest MD5, SHA1;

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

    public static String userAgent;
    static {
        String version = System.getProperty("java.version");
        String agent = System.getProperty("http.agent");
        if (agent == null) {
            agent = "Java/"+version;
        } else {
            agent = agent + " Java/"+version;
        }
        userAgent = agent;
    }
    public static int timeout = 10000;

    public static void copyFile(File source, File target) throws IOException {
        // noinspection all
        Thread.interrupted();
        FileChannel src = FileChannel.open(source.toPath(), StandardOpenOption.READ);
        FileChannel dst = FileChannel.open(target.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        src.transferTo(0, source.length(), dst);
        src.close();
        dst.close();
    }

    public static List<File> findAllFiles(File file) {
        return findAllFiles(file, new ArrayList<>(), Helpers.alwaysTrue());
    }

    public static List<File> findAllFiles(File file, Predicate<File> predicate) {
        return findAllFiles(file, new ArrayList<>(), predicate);
    }

    public static List<File> findAllFiles(File file, List<File> files, Predicate<File> predicate) {
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

    public static ByteList downloadFileToMemory(String url) throws IOException {
        HttpConnection con = process302(new URL(url), false);
        try (InputStream in = con.getInputStream()) {
            int length = con.getContentLength();
            return new ByteList(length).readStreamFully(in);
        } finally {
            con.disconnect();
        }
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
        } else if (length == 0) file.createNewFile();
    }

    public static HttpConnection process302(URL url, boolean headOnly) throws IOException {
        HttpConnection conn = new HttpConnection(url);

        HttpClient client = conn.getClient();
        client.header("User-Agent", userAgent)
              .header("Range", "bytes=0-")
              .method(headOnly ? "HEAD" : "GET")
              .readTimeout(timeout);
        client.connectTimeout(timeout);

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

    public static long transferFileSelf(FileChannel cf, long from, long to, long len) throws IOException {
        // noinspection all
        Thread.interrupted();
        if (from == to || len == 0) return len;
        if (from > to ?
                to + len <= from :
                from + len <= to) { // 区间不交叉
            return cf.transferTo(from, len, cf.position(to));
        }
        long pos = cf.position();
        try {
            if (len <= 1048576) {
                ByteBuffer direct = ByteBuffer.allocateDirect((int) len);
                direct.position(0).limit((int) len);
                cf.read(direct, from);
                direct.position(0);
                int amount = cf.position(to).write(direct);
                NIOUtil.clean(direct);
                return amount;
            } else {
                File tmpFile = new File("FUT~" + (System.nanoTime() % 1000000) + ".tmp");
                FileChannel ct = FileChannel.open(tmpFile.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.DELETE_ON_CLOSE);
                cf.transferTo(from, len, ct.position(0));
                long amount = ct.transferTo(0, len, cf.position(to));
                ct.close();
                return amount;
            }
        } finally {
            cf.position(pos);
        }
    }

    public static final class ImmediateFuture implements Waitable {
        @Override
        public void waitFor() {}

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public void cancel() {}
    }
}