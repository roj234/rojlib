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

public final class TraceUtil extends SecurityManager {
    public static final TraceUtil INSTANCE = new TraceUtil();
    static final boolean ok;

    static {
        boolean a;
        try {
            H.JLA.getClass();
            a = true;
        } catch (Throwable e) {
            a = false;
        }
        ok = a;
    }

    public Class<?> getCallerClass() {
        Class<?>[] ctx = super.getClassContext();
        return ctx.length < 3 ? null : ctx[2];
    }

    @Override
    public Class<?>[] getClassContext() {
        return super.getClassContext();
    }

    @Override
    public int classDepth(String name) {
        return super.classDepth(name);
    }

    public static StackTraceElement[] getTraces(Throwable t) {
        if(ok) {
            StackTraceElement[] arr = new StackTraceElement[H.JLA.getStackTraceDepth(t)];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = H.JLA.getStackTraceElement(t, i);
            }
            return arr;
        }
        return t.getStackTrace();
    }

    public static int stackDepth(Throwable t) {
        if(ok)
            return H.JLA.getStackTraceDepth(t);
        return t.getStackTrace().length;
    }

    static class H {
        static final sun.misc.JavaLangAccess JLA = sun.misc.SharedSecrets.getJavaLangAccess();
    }
}