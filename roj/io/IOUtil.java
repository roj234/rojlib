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

import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/26 0:13
 */
public class IOUtil {
    static final ThreadLocal<ByteList> BYTE_BUFFER = new ThreadLocal<ByteList>() {
        @Override
        public ByteList get() {
            ByteList list = super.get();
            list.clear();
            return list;
        }

        @Override
        protected ByteList initialValue() {
            return new ByteList();
        }
    };

    public static ByteList getSharedByteBuf() {
        return BYTE_BUFFER.get();
    }

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

    private static ByteList getBytes0(Class<?> provider, String pathName, ByteList list) throws FileNotFoundException {
        InputStream stream = provider.getClassLoader().getResourceAsStream(pathName);
        if (stream == null)
            throw new FileNotFoundException(pathName);
        try {
            return readFully0(stream, list);
        } catch (IOException e) {
            e.printStackTrace();
        }
        throw new FileNotFoundException(pathName);
    }

    public static byte[] getBytes(Class<?> provider, String pathName) throws FileNotFoundException {
        return getBytes0(provider, pathName, BYTE_BUFFER.get()).toByteArray();
    }

    public static String readAsUTF(InputStream stream) throws IOException {
        CharList cl = new CharList();
        ByteReader.decodeUTF(-1, cl, readFully0(stream, BYTE_BUFFER.get()));
        return cl.toString();
    }

    public static String readAs(InputStream in, String encoding) throws UnsupportedCharsetException, IOException {
        if(encoding.equalsIgnoreCase("UTF-8") || encoding.equalsIgnoreCase("UTF8")) {
            return readAsUTF(in);
        } else {
            ByteList bl = readFully0(in, BYTE_BUFFER.get());
            return new String(bl.list, 0, bl.pos(), Charset.forName(encoding));
        }
    }

    static ByteList readFully0(InputStream stream, ByteList list) throws IOException {
        int except = stream.available();
        if (except < 0) {
            throw new IOException("available < 0");
        }
        list.ensureCapacity(except);
        list.readStreamArrayFully(stream);

        stream.close();

        return list;
    }

    public static byte[] readFully(InputStream stream) throws IOException {
        return readFully0(stream, BYTE_BUFFER.get()).getByteArray();
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
        ByteReader.decodeUTF(-1, cl, getBytes0(provider, pathName, BYTE_BUFFER.get()));
        return cl.toString();
    }
}