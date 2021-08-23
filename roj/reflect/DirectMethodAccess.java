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

import roj.collect.IBitSet;

import java.util.Collections;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/18 9:51
 */
@Deprecated
public final class DirectMethodAccess {
    /**
     * @param i_Cls         实现IInvoker的class
     * @param i_Method      IInvoker的方法名字
     * @param t_Cls_or_Inst 目标的实例or类
     * @param t_Method      方法名字
     */
    public static <T extends Instanced> T get(Class<T> i_Cls, String i_Method, Object t_Cls_or_Inst, String t_Method) {
        Class<?> target = t_Cls_or_Inst.getClass() == Class.class ? (Class<?>) t_Cls_or_Inst : t_Cls_or_Inst.getClass();
        T t = DirectAccessor
                .builder(i_Cls)
                .makeCache(target).useCache()
                .delegate(target, t_Method, i_Method)
                .build();

        if (t_Cls_or_Inst.getClass() != Class.class)
            t.setInstance(t_Cls_or_Inst);
        return t;
    }

    /**
     * @param i_Cls    实现IInvoker的class
     * @param i_Method IInvoker的方法名字
     * @param t_Cls    目标的类
     * @param t_Method 方法名字
     */
    public static <T extends Instanced> T get(Class<T> i_Cls, String i_Method, Class<?> t_Cls, String t_Method) {
        return DirectAccessor
                .builder(i_Cls)
                .makeCache(t_Cls).useCache()
                .delegate(t_Cls, t_Method, i_Method)
                .build();
    }

    public static <T> T getNCI(Class<T> i_Cls, String[] i_Method, IBitSet i_Flag, Class<?> t_Cls, String[] t_Method) {
        return DirectAccessor
                .builder(i_Cls)
                .delegate(t_Cls, t_Method, i_Flag, i_Method, Collections.emptyList())
                .build();
    }

    /**
     * @param invoker          实现IInvoker的class
     * @param invokeMethodName IInvoker的方法名字
     * @param tClass           目标的类
     * @param methodName       方法名字
     */
    public static <T> T getStatic(Class<T> invoker, String invokeMethodName, Class<?> tClass, String methodName) {
        return DirectAccessor
                .builder(invoker)
                .delegate(tClass, new String[]{methodName}, DirectAccessor.EMPTY_BITS, new String[]{invokeMethodName},
                          null)
                .build();
    }

    /**
     * @param invoker          实现IInvoker的class
     * @param invokeMethodName IInvoker的方法名字
     * @param tClass           目标的类
     * @param methodName       方法名字
     */
    public static <T> T getStatic(Class<T> invoker, String[] invokeMethodName, Class<?> tClass, String[] methodName) {
        return DirectAccessor
                .builder(invoker)
                .delegate(tClass, methodName, DirectAccessor.EMPTY_BITS, invokeMethodName, null)
                .build();
    }

    public static <T> T getDMA(IBitSet opcode, Class<T> invoker, String[] invokeMethodName, Class<?> tClass, String[] methodName, int flag) {
        return DirectAccessor
                .builder(invoker)
                .delegate(tClass, methodName, opcode, invokeMethodName,
                          (flag & 2) == 0 ? null : Collections.emptyList())
                .build();
    }
}