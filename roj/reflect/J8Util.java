package roj.reflect;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

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
            InternalAPI.U.unpark(thread);
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Unsafe is not exist!", e);
        }
    }

    public static long objectFieldOffset(Field field) {
        try {
            return InternalAPI.U.objectFieldOffset(field);
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
            return objectFieldOffset(Integer.class.getDeclaredField("value"));
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
            return InternalAPI.U.allocateInstance(clazz);
        } catch (InstantiationException e) {
            throw e;
        } catch (Throwable e) {
            throw new UnsupportedOperationException("Unsafe is not exist!", e);
        }
    }

    public static StackTraceElement[] getTraces(Throwable t) {
        try {
            StackTraceElement[] arr = new StackTraceElement[InternalAPI.JLA.getStackTraceDepth(t)];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = InternalAPI.JLA.getStackTraceElement(t, i);
            }
            return arr;
        } catch (Throwable ignored) {
        }
        return t.getStackTrace();
    }

    public static int stackDepth(Throwable t) {
        try {
            return InternalAPI.JLA.getStackTraceDepth(t);
        } catch (Throwable ignored) {
        }
        return t.getStackTrace().length;
    }
}
