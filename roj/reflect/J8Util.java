package roj.reflect;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 警告: 请小心使用
 */
public final class J8Util {
    /**
     * 使线程开始运行
     *
     * @param thread The thread to run
     */
    public static void unfreezeThread(@Nonnull Thread thread) {
        try {
            Int.U.unpark(thread);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Unsafe is not exist!", e);
        }
    }

    public static long getFieldOffset(Field field) {
        try {
            if (Modifier.isStatic(field.getModifiers())) {
                return Int.U.staticFieldOffset(field);
            } else {
                return Int.U.objectFieldOffset(field);
            }
        } catch (Throwable e) {
            return -1;
        }
    }

    public static long getObjectHeaderSize() {
        // java.lang.Integer's sole instance field is:
        //   private int value
        // So we can make an educated guess that its offset equals to
        // the size of object header.
        try {
            return getFieldOffset(Integer.class.getDeclaredField("value"));
        } catch (NoSuchFieldException e) {
            return -1;
        }
    }

    /**
     * 实例化对象
     * 警告: 这个方法不会调用构造函数
     */
    @Deprecated
    public static Object instantiateObject(Class<?> clazz) throws InstantiationException {
        try {
            return Int.U.allocateInstance(clazz);
        } catch (InstantiationException e) {
            throw e;
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Unsafe is not exist!", e);
        }
    }

    public static StackTraceElement[] getTraces(Throwable t) {
        try {
            StackTraceElement[] arr = new StackTraceElement[Int.JLA.getStackTraceDepth(t)];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = Int.JLA.getStackTraceElement(t, i);
            }
            return arr;
        } catch (Throwable ignored) {}
        return t.getStackTrace();
    }

    public static int stackDepth(Throwable t) {
        try {
            return Int.JLA.getStackTraceDepth(t);
        } catch (Throwable ignored) {}
        return t.getStackTrace().length;
    }
}
