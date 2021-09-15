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
import roj.util.FastThreadLocal;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/26 0:13
 */
public class IOUtil {
    private static final FastThreadLocal<Object[]> BYTE_BUFFER = new FastThreadLocal<Object[]>() {
        @Override
        protected Object[] initialValue() {
            return new Object[] {
                    new ByteList(),
                    new CharList()
            };
        }
    };

    public static ByteList getSharedByteBuf() {
        ByteList o = (ByteList) BYTE_BUFFER.get()[0];
        o.clear();
        return o;
    }

    private static ByteList read1(Class<?> jar, String path, ByteList list) throws IOException {
        InputStream stream = jar.getClassLoader().getResourceAsStream(path);
        if (stream == null)
            throw new FileNotFoundException(path);
        return read0(stream, list);
    }

    public static String readAs(InputStream in, String encoding) throws UnsupportedCharsetException, IOException {
        if(encoding.equalsIgnoreCase("UTF-8") || encoding.equalsIgnoreCase("UTF8")) {
            return readUTF(in);
        } else {
            ByteList bl = read0(in, getSharedByteBuf());
            return new String(bl.list, 0, bl.pos(), Charset.forName(encoding));
        }
    }

    private static ByteList read0(InputStream stream, ByteList list) throws IOException {
        int except = stream.available();
        if (except < 0) {
            throw new IOException("available < 0");
        }
        list.ensureCapacity(except);
        list.readStreamArrayFully(stream);

        stream.close();

        return list;
    }

    public static byte[] read(String path) throws IOException {
        return read1(IOUtil.class, path, getSharedByteBuf()).toByteArray();
    }

    public static byte[] read(Class<?> provider, String path) throws IOException {
        return read1(provider, path, getSharedByteBuf()).toByteArray();
    }

    public static byte[] read(File file) throws IOException {
        return read0(new FileInputStream(file), getSharedByteBuf()).toByteArray();
    }

    public static byte[] read(InputStream in) throws IOException {
        return read0(in, getSharedByteBuf()).toByteArray();
    }

    public static String readUTF(Class<?> jar, String path) throws IOException {
        Object[] x = BYTE_BUFFER.get();
        ByteList bl = (ByteList) x[0];
        bl.clear();
        CharList cl = (CharList) x[1];
        cl.clear();
        ByteReader.decodeUTF(-1, cl, read1(jar, path, bl));
        if(cl.length() > 8192) {
            x[1] = new CharList(8192);
        }
        return cl.toString();
    }

    public static String readUTF(String path) throws IOException {
        return readUTF(IOUtil.class, path);
    }

    public static String readUTF(File f) throws IOException {
        Object[] x = BYTE_BUFFER.get();
        ByteList bl = (ByteList) x[0];
        bl.clear();
        CharList cl = (CharList) x[1];
        cl.clear();
        ByteReader.decodeUTF(-1, cl, read0(new FileInputStream(f), bl));
        if(cl.length() > 8192) {
            x[1] = new CharList(8192);
        }
        return cl.toString();
    }

    public static String readUTF(InputStream in) throws IOException {
        Object[] x = BYTE_BUFFER.get();
        ByteList bl = (ByteList) x[0];
        bl.clear();
        CharList cl = (CharList) x[1];
        cl.clear();
        ByteReader.decodeUTF(-1, cl, read0(in, bl));
        if(cl.length() > 8192) {
            x[1] = new CharList(8192);
        }
        return cl.toString();
    }
}