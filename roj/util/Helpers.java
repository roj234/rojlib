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
package roj.util;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 小工具
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/17 1:43
 */
public class Helpers {
    /**
     * 在不可能的地方丢出异常 <BR>
     * athrow(new IOException());
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void athrow(Throwable e) throws T {
        throw (T)e;
    }

    public interface Node {
        Node next();
    }

    public static boolean hasCircle(Node begin) {
        if(begin == null)
            return false;

        Node slow = begin, fast1, fast2 = begin;

        while (/*slow != null && */(fast1 = fast2.next()) != null && (fast2 = fast1.next()) != null) {
            if (slow == fast1 || slow == fast2) return true;
            slow = slow.next();
        }

        return false;
    }

    /**
     * 强制转换
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object c) {
        return (T) c;
    }

    @Deprecated
    public static <K, V> Map<K, V> subMap(Map<K, V> map, int start, int end) {
        int i = -1;
        Map<K, V> subMap = new HashMap<>();

        for (K key : map.keySet()) {
            if (i++ < start) continue;
            subMap.put(key, map.get(key));
            if (i == end) break;
        }

        return subMap;
    }

    @Nonnull
    public static <T> T nonnull() {
        return null;
    }

    public static StringBuilder traceString(Throwable err, int i) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = err.getStackTrace();
        sb.append(err.getClass().getName()).append(':').append(err.getMessage());
        for (StackTraceElement element : elements) {
            if (i-- > 0) continue;
            sb.append("    at ").append(element.getClassName()).append('.').append(element.getMethodName()).append(" line ").append(element.getLineNumber()).append('\n');
        }
        return sb;
    }

    public static final Predicate<?> alwaystrue = (a) -> true;
    public static final Function<?, ?> arraylistfn = (a) -> new ArrayList<>();
    public static final Function<?, ?> linkedlistfn = (a) -> new LinkedList<>();
    public static final Function<?, ?> myhashmapfn = (a) -> new MyHashMap<>();
    public static final Function<?, ?> myhashsetfn = (a) -> new MyHashSet<>();

    public static <T> Predicate<T> alwaysTrue() {
        return cast(alwaystrue);
    }
    public static <T,R> Function<T, List<R>> fnArrayList() {
        return cast(arraylistfn);
    }
    public static <T,R> Function<T, List<R>> fnLinkedList() {
        return cast(linkedlistfn);
    }
    public static <T,R,E> Function<T, Map<R, E>> fnMyHashMap() {
        return cast(myhashmapfn);
    }
    public static <T,R> Function<T, Set<R>> fnMyHashSet() {
        return cast(myhashsetfn);
    }
}
