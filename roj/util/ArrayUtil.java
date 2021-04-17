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

import java.util.List;
import java.util.Random;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/15 0:43
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

    public static <T> void shuffle(List<T> arr, Random random) {
        for (int i = 0; i < arr.size(); i++) {
            T a = arr.get(i);
            int an = random.nextInt(arr.size());
            arr.set(i, arr.get(an));
            arr.set(an, a);
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
