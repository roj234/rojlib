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

import java.io.*;
import java.util.Enumeration;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/2/24 14:46
 */
public final class ZipUtil {

    public static void zip(Map<String, byte[]> map, File file) {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            for (Map.Entry<String, byte[]> entry : map.entrySet()) {
                ZipEntry zp = new ZipEntry(entry.getKey());
                zos.putNextEntry(zp);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void zip(String[] paths, String[] relativePaths, String fileName, Map<File, byte[]> bytecodeMap) {

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(fileName))) {
            int i = 0;
            for (String filePath : paths) {
                //递归压缩文件
                File file = new File(filePath);
                String relativePath = relativePaths[i++];
                if (file.isDirectory()) {
                    relativePath += File.separator;
                }
                zipFile(file, relativePath, zos, bytecodeMap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void zipFile(File file, String relativePath, ZipOutputStream zos, Map<File, byte[]> bytecodeMap) throws IOException {
        if (!file.isDirectory()) {
            ZipEntry zp = new ZipEntry(relativePath);
            zos.putNextEntry(zp);
            byte[] buffer = bytecodeMap.get(file);
            if (buffer == null) {
                try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
                    buffer = IOUtil.read(is);
                }
            }
            zos.write(buffer);
            zos.flush();
            zos.closeEntry();
        } else {
            String tempPath;
            for (File f : file.listFiles()) {
                tempPath = relativePath + f.getName();
                if (f.isDirectory()) {
                    tempPath += File.separator;
                }
                zipFile(f, tempPath, zos, bytecodeMap);
            }
        }
    }

    public static void writeZipIntoZip(ZipOutputStream zos, ZipFile zip, Predicate<ZipEntry> predicate) throws IOException {
        Enumeration<? extends ZipEntry> enumeration;
        enumeration = zip.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry ze = enumeration.nextElement();
            if (!ze.isDirectory() && predicate.test(ze)) {
                zos.putNextEntry(new ZipEntry(ze.getName()));
                zos.write(IOUtil.read(zip.getInputStream(ze)));
                zos.closeEntry();
            }
        }
    }

    public static void close(ZipOutputStream zos) throws IOException {
        zos.flush();
        zos.finish();
        zos.close();
    }

    public interface ICallback {
        void onRead(String fileName, InputStream stream) throws IOException;
    }

    public static void unzip(File file, ICallback callback, Predicate<ZipEntry> namePredicate) {
        if (file.length() == 0) return;
        ZipFile zf;
        try {
            zf = new ZipFile(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("文件 " + file.getAbsolutePath() + " 无法正常读取!");
        }
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn = en.nextElement();
            if (!zn.isDirectory() && namePredicate.test(zn)) {
                try (InputStream is = zf.getInputStream(zn)) {
                    callback.onRead(zn.getName(), is);
                } catch (IOException e) {
                    throw new IllegalArgumentException("文件 " + file.getAbsolutePath() + " 中的 " + zn.getName() + " 无法正常读取!");
                }
            }
        }
    }

    public static void unzip(File file, ICallback callback) {
        if (file.length() == 0) return;
        ZipFile zf;
        try {
            zf = new ZipFile(file);
        } catch (IOException e) {
            System.err.println("文件 " + file.getAbsolutePath() + " 无法正常读取!");
            return;
        }
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn = en.nextElement();
            if (!zn.isDirectory()) {
                try (InputStream is = zf.getInputStream(zn)) {
                    callback.onRead(zn.getName(), is);
                } catch (IOException e) {
                    System.err.println("文件 " + file.getAbsolutePath() + " 中的 " + zn.getName() + " 无法正常读取!");
                    e.printStackTrace();
                }
            }
        }
    }

    public static void unzip(String fileName, String path) {
        ZipFile zf;
        try {
            zf = new ZipFile(new File(fileName));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Enumeration<? extends ZipEntry> en = zf.entries();
        while (en.hasMoreElements()) {
            ZipEntry zn = en.nextElement();
            if (!zn.isDirectory()) {
                try (InputStream is = zf.getInputStream(zn)) {
                    try (FileOutputStream fos = new FileOutputStream(path + zn.getName())) {
                        fos.write(IOUtil.read(is));
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write file data ", e);
                }
            } else {
                File dir = new File(path + zn.getName());
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new RuntimeException("Failed to create dir " + dir);
                }
            }
        }
    }
}