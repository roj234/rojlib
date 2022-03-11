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
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.FastThreadLocal;
import sun.reflect.Reflection;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

/**
 * @author Roj234
 * @since 2021/5/26 0:13
 */
public class IOUtil {
    public static final FastThreadLocal<UTFCoder> SharedCoder = FastThreadLocal.withInitial(UTFCoder::new);

    public static ByteList getSharedByteBuf() {
        ByteList o = SharedCoder.get().byteBuf;
        o.ensureCapacity(1024);
        o.clear();
        return o;
    }

    public static CharList getSharedCharBuf() {
        CharList o = SharedCoder.get().charBuf;
        o.ensureCapacity(1024);
        o.clear();
        return o;
    }

    private static ByteList read1(Class<?> jar, String path, ByteList list) throws IOException {
        InputStream in = jar.getClassLoader().getResourceAsStream(path);
        if (in == null) throw new FileNotFoundException(path);
        return list.readStreamFully(in);
    }

    public static String readAs(InputStream in, String encoding) throws UnsupportedCharsetException, IOException {
        if(encoding.equalsIgnoreCase("UTF-8") || encoding.equalsIgnoreCase("UTF8")) {
            return readUTF(in);
        } else {
            ByteList bl = getSharedByteBuf().readStreamFully(in);
            return new String(bl.list, 0, bl.wIndex(), Charset.forName(encoding));
        }
    }

    public static byte[] read(String path) throws IOException {
        Class<?> caller = IOUtil.class;
        try {
            caller = Reflection.getCallerClass();
        } catch (Throwable ignored) {}
        return read1(caller, path, getSharedByteBuf()).toByteArray();
    }

    public static byte[] read(Class<?> provider, String path) throws IOException {
        return read1(provider, path, getSharedByteBuf()).toByteArray();
    }

    public static byte[] read(File file) throws IOException {
        return read(new FileInputStream(file));
    }

    public static byte[] read(InputStream in) throws IOException {
        return getSharedByteBuf().readStreamFully(in).toByteArray();
    }

    public static String readUTF(Class<?> jar, String path) throws IOException {
        UTFCoder x = SharedCoder.get();
        x.keep = false;
        x.byteBuf.clear();
        read1(jar, path, x.byteBuf);
        return x.decode();
    }

    public static String readUTF(String path) throws IOException {
        return readUTF(IOUtil.class, path);
    }

    public static String readUTF(File f) throws IOException {
        return readUTF(new FileInputStream(f));
    }

    public static String readUTF(InputStream in) throws IOException {
        UTFCoder x = SharedCoder.get();
        x.keep = false;
        x.byteBuf.clear();
        x.byteBuf.readStreamFully(in);
        return x.decode();
    }
}