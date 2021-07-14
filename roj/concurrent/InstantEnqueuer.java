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
package roj.concurrent;

import roj.reflect.Instanced;
import roj.reflect.PackagePrivateProxy;
import roj.reflect.SunReflection;
import roj.util.Wrapped;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.InvocationTargetException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/16 14:23
 */
public class InstantEnqueuer {
    @SuppressWarnings("rawtypes")
    static Class<ReferenceQueue> enqueuer = PackagePrivateProxy.proxyIt(ReferenceQueue.class, Proxy.class, "java.lang.ref", "enqueue");

    @SuppressWarnings("unchecked")
    public static <T> ReferenceQueue<T> set(Proxy<T> impl) {
        try {
            Instanced iproxy = (Instanced) SunReflection.createClass(enqueuer);
            iproxy.setInstance(impl);
            return (ReferenceQueue<T>) iproxy;
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new Wrapped("", e);
        }
    }


    public interface Proxy<T> {
        /**
         * Proxy method to {@link ReferenceQueue#enqueue(Reference)}
         */
        boolean enqueue(Reference<?> r);
    }
}
