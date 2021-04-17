/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ByteClassLoader.java
 */
package roj.reflect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

public class ClassDefiner extends ClassLoader {
    private static final ClassLoader selfParent = getParentClassLoader(ClassDefiner.class);
    public static volatile ClassDefiner INSTANCE = new ClassDefiner(selfParent);

    private static volatile Method defineClassMethod;

    public static boolean debug;

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
            } catch (IOException ignored) {
            }
        }
        try {
            // 使用同样的加载器加载，保证Access
            return (Class<?>) getDefineClassMethod().invoke(getParent(),
                    new Object[]{name, bytes, off, len, getClass().getProtectionDomain()});
        } catch (Exception ignored) {
            // 使用自己加载（这样会没有protected的权限!）
        }
        return defineClass(name, bytes, off, len, getClass().getProtectionDomain());
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
