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

package roj.reflect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/16 1:31
 */
public final class ClassDefiner extends ClassLoader {
    private interface FastInvoke {
        Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len,
                ProtectionDomain protectionDomain);
        Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len,
                ProtectionDomain protectionDomain, String source);
    }

    private static final ClassLoader SELF_LOADER = getParentClassLoader(ClassDefiner.class);
    public static final ClassDefiner INSTANCE    = new ClassDefiner(SELF_LOADER);

    private static Method defineClassMethod;
    public static boolean debug = System.getProperty("roj.reflect.debugClass") != null;

    private static final FastInvoke invoker;
    static {
        ClassLoader.registerAsParallelCapable();
        FastInvoke fi = null;
        try {
            fi = DirectAccessor.builder(FastInvoke.class).delegate(ClassLoader.class, new String[]{ "defineClass", "defineClass1" }).build();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        invoker = fi;
    }

    private ClassDefiner(ClassLoader parent) {
        super(parent);
    }

    public Class<?> loadClass(String className, boolean init) throws ClassNotFoundException {
        return super.loadClass(className, init);
    }

    public Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError {
        return defineClassC(name, bytes, 0, bytes.length);
    }

    public Class<?> defineClassC(String name, byte[] bytes, int off, int len) throws ClassFormatError {
        if (debug) {
            try (FileOutputStream fos = new FileOutputStream(new File(name + ".class"))) {
                fos.write(bytes, off, len);
            } catch (IOException ignored) {}
        }
        try {
            // 使用同样的加载器加载，保证Access
            return invoker == null ?
                    (Class<?>) getDefineClassMethod().invoke(getParent(), name, bytes, off, len, getClass().getProtectionDomain())
                    : invoker.defineClass(getParent(), name, bytes, off, len, getClass().getProtectionDomain());
        } catch (Exception ignored) {
            // 使用自己加载（这样会没有protected的权限!）
        }

        try {
            return defineClass(name, bytes, off, len, getClass().getProtectionDomain());
        } catch (Exception e1) {
            if(invoker == null)
                throw e1;
        }

        ProtectionDomain pd = getClass().getProtectionDomain();

        Class<?> clazz = invoker.defineClass1(getParent(), name, bytes, off, len, pd, defineClassSourceLocation(pd));

        if (pd.getCodeSource() != null) {
            Certificate[] certs = pd.getCodeSource().getCertificates();
            if (certs != null)
                setSigners(clazz, certs);
        }

        return clazz;
    }

    private static String defineClassSourceLocation(ProtectionDomain pd) {
        CodeSource cs = pd.getCodeSource();
        String source = null;
        if (cs != null && cs.getLocation() != null) {
            source = cs.getLocation().toString();
        }
        return source;
    }

    private static ClassLoader getParentClassLoader(Class<?> type) {
        ClassLoader parent = type.getClassLoader();
        if (parent == null) parent = ClassLoader.getSystemClassLoader();
        return parent;
    }

    private static Method getDefineClassMethod() throws Exception {
        if (defineClassMethod == null) {
            defineClassMethod = ClassLoader.class.getDeclaredMethod("defineClass",
                    String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
            try {
                defineClassMethod.setAccessible(true);
            } catch (Exception ignored) {
            }
        }
        return defineClassMethod;
    }
}
