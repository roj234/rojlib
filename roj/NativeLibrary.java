/*
 * This file is a part of MoreItems
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
package roj;

import roj.util.NativeException;
import roj.util.OS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Native library loader
 *
 * @author Roj233
 * @since 2021/10/15 12:57
 */
public class NativeLibrary {
    public static final boolean inited;

    static {
        boolean t;
        try {
            loadLibrary();
            registerNatives();
            t = true;
        } catch (Throwable e) {
            t = false;
            e.printStackTrace();
        }
        inited = t;
    }

    private static void loadLibrary() throws Exception {
        String lib = System.getProperty("os.arch").contains("64") ? "RL64" : "RL";
        String appendix = OS.CURRENT == OS.WINDOWS ? ".dll" : ".so";

        File tmp = new File(System.getProperty("java.io.tmpdir"));
        try {
            for (String s : tmp.list()) {
                if (s.startsWith(lib) && s.endsWith(appendix)) {
                    System.load(new File(tmp, s).getAbsolutePath());
                    return;
                }
            }
        } catch (UnsatisfiedLinkError ex) {
            if (ex.getMessage().contains("Can't find dependent libraries"))
                throw ex;
        }
        File tempFile = new File(tmp, lib + "-" + Long.toHexString(-Math.abs(System.nanoTime())) + appendix);
        InputStream in = NativeLibrary.class.getResourceAsStream(lib + appendix);
        if (in == null)
            throw new NativeException("Failed to load RojLib Native");
        byte[] buf = new byte[4096];
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            do {
                int read = in.read(buf);
                if (read <= 0) break;
                out.write(buf, 0, read);
            } while (true);
            in.close();
        }
        System.load(tempFile.getAbsolutePath());
    }

    private static native void registerNatives();
}
