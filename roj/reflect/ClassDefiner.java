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

import roj.asm.SharedBuf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/16 1:31
 */
public final class ClassDefiner extends ClassLoader {
    private interface FastDef {
        Class<?> defineClass(ClassLoader loader, String name, byte[] b, int off, int len,
                ProtectionDomain pd);
        Class<?> defineClass1(ClassLoader loader, String name, byte[] b, int off, int len,
                ProtectionDomain pd, String source);
        List<Class<?>> getClasses(ClassLoader loader);
        Class<?> findLoadedClass(ClassLoader loader, String name);
    }

    private static final ClassLoader SELF_LOADER = getParentClassLoader(ClassDefiner.class);
    public static final ClassDefiner INSTANCE    = new ClassDefiner(SELF_LOADER);

    public static ClassDefiner getFor(ClassLoader loader) {
        return new ClassDefiner(getParentClassLoader(loader.getClass()));
    }

    public static boolean debug = System.getProperty("roj.reflect.debugClass") != null;

    private static final FastDef def;
    private static final Method  slowDef;
    static {
        FastDef fi = null;
        try {
            SharedBuf.alloc().setLevel(true);
            fi = DirectAccessor.builder(FastDef.class)
                               .delegate(ClassLoader.class, new String[]{ "defineClass", "defineClass1", "findLoadedClass" })
                               .access(ClassLoader.class, "classes", "getClasses", null)
                               .build();
            SharedBuf.alloc().setLevel(false);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        def = fi;

        Method slowDef1 = null;
        if (fi == null) {
            try {
                slowDef1 = getDefineClassMethod();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        slowDef = slowDef1;

        ClassLoader.registerAsParallelCapable();
    }

    public static Class<?> findLoadedClass(ClassLoader loader, String name) {
        return def.findLoadedClass(loader, name);
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

    public Class<?> defineClassUnloadable(String name, byte[] bytes, int off, int len) throws ClassFormatError {
        return new ClassDefiner(null).defineClassC(name, bytes, off, len);
    }

    public Class<?> defineClassC(String name, byte[] bytes, int off, int len) throws ClassFormatError {
        if (debug) {
            File f = new File("./class_Definer_out");
            f.mkdir();
            try (FileOutputStream fos = new FileOutputStream(new File(f, name + ".class"))) {
                fos.write(bytes, off, len);
            } catch (IOException ignored) {}
        }

        if(def != null) def.getClasses(this).clear();

        try {
            // ???????????????????????????????????????Access
            return def == null ?
                    (Class<?>) slowDef.invoke(getParent(), name, bytes, off, len, getClass().getProtectionDomain())
                    : def.defineClass(getParent(), name, bytes, off, len, getClass().getProtectionDomain());
        } catch (Exception ignored) {
            // ????????????????????????????????????protected?????????!???
        }

        return defineClass(name, bytes, off, len, getClass().getProtectionDomain());
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
        Method slowDef = ClassLoader.class.getDeclaredMethod("defineClass",
                         String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
        try {
            slowDef.setAccessible(true);
        } catch (Exception ignored) {}
        return slowDef;
    }
}
