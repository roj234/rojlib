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

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 警告: 请小心使用
 */
public final class J8Util {
    static final boolean uav, jav;

    static {
        boolean a;
        try {
            U.U.getClass();
            a = true;
        } catch (Throwable e) {
            a = false;
        }
        uav = a;
        try {
            JLA.JLA.getClass();
            a = true;
        } catch (Throwable e) {
            a = false;
        }
        jav = a;
    }

    /**
     * 使线程开始运行
     *
     * @param thread The thread to run
     */
    public static void unfreezeThread(@Nonnull Thread thread) {
        if(uav)
            U.U.unpark(thread);
        else
            throw new UnsupportedOperationException("Unsafe is not exist!");
    }

    public static long getFieldOffset(Field field) {
        if(uav)
            if (Modifier.isStatic(field.getModifiers())) {
                return U.U.staticFieldOffset(field);
            } else {
                return U.U.objectFieldOffset(field);
            }
        return -1;
    }

    public static long getObjectHeaderSize() {
        // java.lang.Integer's sole instance field is:
        //   private int value
        // So we can make an educated guess that its offset equals to
        // the size of object header.
        if(uav)
            try {
                return getFieldOffset(Integer.class.getDeclaredField("value"));
            } catch (NoSuchFieldException ignored) {}
        return -1;
    }

    public static StackTraceElement[] getTraces(Throwable t) {
        if(jav) {
            StackTraceElement[] arr = new StackTraceElement[JLA.JLA.getStackTraceDepth(t)];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = JLA.JLA.getStackTraceElement(t, i);
            }
            return arr;
        }
        return t.getStackTrace();
    }

    public static int stackDepth(Throwable t) {
        if(jav)
            return JLA.JLA.getStackTraceDepth(t);
        return t.getStackTrace().length;
    }
}