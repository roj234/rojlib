package roj.util;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.reflect.J8Util;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Predicate;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: Helpers.java
 */
public class Helpers {
    /**
     * 在不可能的地方丢出异常 <BR>
     * throwAny(new IOException());
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void throwAny(Throwable e) throws T {
        throw (T)e;
    }

    /**
     * 强制转换
     */
    @SuppressWarnings("unchecked")
    public static <T> T cast(Object c) {
        return (T) c;
    }

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

    public static StringBuilder getMethodTrace() {
        return getMethodTrace(new Throwable(), 1);
    }

    /**
     * 是否运行过{@param className}的{@param methodName}方法
     *
     * @return 运行过
     */
    public static boolean noBurstStackHelper(String className, String methodName) {
        StackTraceElement[] elements = J8Util.getTraces(new Throwable());
        int i = 2;
        for (StackTraceElement element : elements) {
            if (i-- > 0) continue;
            if (methodName.equals(element.getMethodName()) && className.equals(element.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public static <T> T nonnull() {
        return null;
    }

    public static <K, V> void filter(Map<K, V> source, Map<K, V> destination, Predicate<K> predicate) {
        for (Iterator<Map.Entry<K, V>> iterator = source.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<K, V> entry = iterator.next();
            if (predicate.test(entry.getKey())) {
                destination.put(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
    }

    @Deprecated
    public static StringBuilder getMethodTrace(Throwable err) {
        return getMethodTrace(err, 0);
    }

    private static StringBuilder getMethodTrace(Throwable err, int i) {
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] elements = err.getStackTrace();
        sb.append(err.getClass().getName()).append(':').append(err.getMessage());
        for (StackTraceElement element : elements) {
            if (i-- > 0) continue;
            sb.append("    at ").append(element.getClassName()).append('.').append(element.getMethodName()).append(" line ").append(element.getLineNumber()).append('\n');
        }
        return sb;
    }

    public static <K, V> Map<K, V> newMyHashMap(Object s) {
        return new MyHashMap<>();
    }

    public static <T> LinkedList<T> newLinkedList(String s) {
        return new LinkedList<>();
    }

    public static <T> Set<T> newMyHashSet(Object o) {
        return new MyHashSet<>();
    }

    public static <T> List<T> newArrayList(Object o) {
        return new ArrayList<>();
    }
}
