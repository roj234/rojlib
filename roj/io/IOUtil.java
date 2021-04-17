/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: FileUtil.java
 */
package roj.io;

import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class IOUtil {
    public static byte[] getBytes(JarFile file, JarEntry entry) throws IOException {
        return readFully(new BufferedInputStream(file.getInputStream(entry)));
    }

    public static String classPath(Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    }

    public static byte[] getBytesSilent(Class<?> provider, String pathName) {
        try {
            return getBytes(provider, pathName);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] writeAndReturn(String file, byte[] data) {
        try (FileOutputStream fos = new FileOutputStream(new File(file))) {
            fos.write(data);
        } catch (IOException ignored) {
        }
        return data;
    }

    private static ByteList getBytes0(Class<?> provider, String pathName) throws FileNotFoundException {
        InputStream stream = provider.getClassLoader().getResourceAsStream(pathName);
        if (stream == null)
            throw new FileNotFoundException(pathName);
        try {
            return readFully0(stream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new FileNotFoundException(pathName);
    }

    public static byte[] getBytes(Class<?> provider, String pathName) throws FileNotFoundException {
        return getBytes0(provider, pathName).toByteArray();
    }

    public static String readAsUTF(InputStream stream) throws IOException {
        CharList cl = new CharList();
        ByteReader.decodeUTF(-1, cl, readFully0(stream));
        return cl.toString();
    }

    public static String readAs(InputStream in, String encoding) throws UnsupportedCharsetException, IOException {
        if(encoding.equalsIgnoreCase("UTF-8") || encoding.equalsIgnoreCase("UTF8")) {
            return readAsUTF(in);
        } else {
            ByteList bl = readFully0(in);
            return new String(bl.list, 0, bl.pos(), Charset.forName(encoding));
        }
    }

    static ByteList readFully0(InputStream stream) throws IOException {
        int except = stream.available();
        if (except < 0) {
            throw new IOException("available < 0");
        }
        ByteList list = new ByteList(except);
        list.readStreamArrayFully(stream);

        stream.close();

        return list;
    }

    public static byte[] readFully(InputStream stream) throws IOException {
        return readFully0(stream).getByteArray();
    }

    public static byte[] readFile(File file) throws IOException {
        if (file.isFile()) {
            try (InputStream stream = new FileInputStream(file)) {
                return readFully(stream);
            }
        } else {
            throw new FileNotFoundException(file.getName());
        }
    }

    public static String readAsUTF(Class<?> provider, String pathName) throws IOException {
        CharList cl = new CharList();
        ByteReader.decodeUTF(-1, cl, getBytes0(provider, pathName));
        return cl.toString();
    }
}