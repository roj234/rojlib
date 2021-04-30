package roj.util;

import java.util.List;
import java.util.Random;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/15 0:43
 */
public final class ArrayUtil {
    public static <T> T[] inverse(T[] arr) {
        return inverse(arr, 0, arr.length);
    }

    public static <T> T[] inverse(T[] arr, int i, int length) {
        if (--length <= 0)
            return arr; // empty or one
        // i = 0, arr.length = 4, e = 2
        // swap 0 and 3 swap 1 and 2
        for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
            T a = arr[i];
            arr[i] = arr[length - i];
            arr[length - i] = a;
        }
        return arr;
    }

    public static <T> T[] inverse(T[] arr, int size) {
        return inverse(arr, 0, size);
    }

    public static <T> List<T> inverse(List<T> arr) {
        return inverse(arr, 0, arr.size());
    }

    public static <T> List<T> inverse(List<T> arr, int i, int length) {
        if (--length <= 0)
            return arr; // empty or one
        // i = 0, arr.length = 4, e = 2
        // swap 0 and 3 swap 1 and 2
        for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
            T a = arr.get(i);
            arr.set(i, arr.get(length - i));
            arr.set(length - i, a);
        }
        return arr;
    }

    public static void shuffle(Object[] arr, Random random) {
        for (int i = 0; i < arr.length; i++) {
            Object a = arr[i];
            int an = random.nextInt(arr.length);
            arr[i] = arr[an];
            arr[an] = a;
        }
    }

    public static boolean rangedEquals(byte[] list, int offset, int pos, byte[] list1, int offset1, int pos1) {
        if(pos - offset != pos1 - offset1)
            return false;
        while (offset < pos) {
            if(list[offset++] != list1[offset1++])
                return false;
        }
        return true;
    }

    public static String toString(Object[] list, int i, int length) {
        StringBuilder sb = new StringBuilder();
        if (length - i <= 0)
            return "";

        for (; i < length; i++) {
            sb.append(list[i]).append(", ");
        }
        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

    public static int rangedHashCode(byte[] list, int offset, int pos) {
        int hash = 0;
        while (offset < pos) {
            hash = list[offset++] + 31 * hash;
        }
        return hash;
    }
}
