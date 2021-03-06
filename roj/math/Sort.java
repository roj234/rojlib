/*
 * This file is a part of MoreItems
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
package roj.math;

/**
 * 各种排序算法
 *
 * @author Roj233
 * @since 2021/9/1 23:14
 */
public class Sort {
    /**
     * 冒泡排序
     */
    public static void bubbleSort(int[] arr) {
        int len = arr.length, tmp;
        for (int i = 0; i < len; i++) {
            for (int j = i + 1; j < len; j++) {
                // 非稳定
                // 稳定：相等元素的顺序不变
                if (arr[i] > (tmp = arr[j])) {
                    arr[j] = arr[i];
                    arr[i] = tmp;
                }
            }
        }
    }

    /**
     * 选择排序 <br>
     * 与冒泡排序的区别：冒泡排序立即交换，选择排序延迟交换
     */
    public static void selectSort(int[] arr) {
        int len = arr.length;
        int min, tmp;
        for (int i = 0; i < len; i++) {
            min = i;
            for (int j = i + 1; j < len; j++) {
                // 稳定
                if (arr[min] > arr[j]) {
                    min = j;
                }
            }

            if(min != i) {
                tmp = arr[min];
                arr[min] = arr[i];
                arr[i] = tmp;
            }
        }
    }

    /**
     * 堆排序
     */
    public static void heapSort(int[] arr) {
        int end = arr.length - 1;
        for(int p = end >> 1; p > 0; p--) {//创建大根堆，a[0]不参与建堆
            heap_push(arr, p, end);
        }
        int tmp;
        while(end > 0) {//a[0]不参与排序
            tmp = arr[1];
            arr[1] = arr[end];
            arr[end] = tmp;
            heap_push(arr, 1, --end);
        }
    }
    private static void heap_push(int[] arr, int p, int end) {
        int ch, tmp;
        while(true){
            ch = p << 1;
            if (ch > end) break;
            // ch为两个子节点中较大的下标
            if (ch < end && arr[ch] < arr[ch + 1]) ch++;
            if (arr[ch] > arr[p]) {
                tmp = arr[ch];
                arr[ch] = arr[p];
                arr[p] = tmp;
            } else break;
            p = ch;
        }
    }
    // 小根
    private static void heap_push_2(int[] arr, int p, int end) {
        int ch, tmp;
        while(true) {
            ch = p << 1;
            if (ch > end) break;
            // ch为两个子节点中较小的下标
            if (ch < end && arr[ch] > arr[ch + 1]) ch++;
            if (arr[ch] < arr[p]) {
                tmp = arr[ch];
                arr[ch] = arr[p];
                arr[p] = tmp;
            } else break;
            p = ch;
        }
    }

    /**
     * 插入排序, O(n)
     */
    public static void insertSort(int[] arr) {
        int len = arr.length;
        int tmp, i2 = -1;
        for (int i = 1; i < len; i++) {
            tmp = arr[i];
            // 向左遍历
            for (int j = i; j > 0; j--) {
                if (tmp < arr[j - 1]) {
                    arr[j] = arr[j - 1];
                    i2 = j - 1;
                } else break;
            }
            // ifgt opcode
            if(i2 >= 0) {
                arr[i2] = tmp;
                i2 = -1;
            }
        }
    }

    /**
     * 归并排序
     */
    // 自顶向下, 分治法
    // 切成 n / 2
    public static void mergeSort(int[] arr) {
        mergeSort(arr, 0, arr.length);
    }
    public static void mergeSort(int[] arr, int low, int high) {
        if (low >= high) return;// 递归终止条件
        // 子数组Left尾的下标 (low + left.length - 1) left.length in 2^n
        int mid = low + ((high - low) >> 1);
        mergeSort(arr, low, mid);
        mergeSort(arr, mid + 1, high);
        merge(arr, low, mid, high);
    }
    private static void merge(int[] arr, int low, int mid, int high) {
        int[] dst = new int[high - low + 1];
        int j = mid + 1;
        System.arraycopy(arr, low, dst, low, dst.length);
        for (int m = low; m <= high; m++) {
            arr[m] = (low > mid) ?
                    dst[j++] :
                    (j > high || /* <=: 稳定 */dst[low] <= dst[j]) ?
                            dst[low++] : dst[j++];
        }
    }


    /**
     * 快速排序
     * <br>
     * 在平均状况下，排序n个项目要O(n * log (n))次比较。在最坏状况下则需要O(n^2) 次比较
     */
    public static void quickShort(int[] array) {
        quickSort(array, 0, array.length);
    }
    public static void quickSort(int[] array, int left, int right) {
        if (left >= right) return;
        int i = left;
        int j = right + 1;
        int pivot = array[left];
        while (i < j) {
            while (i < j && array[j] >= pivot) {
                j--;
            }
            if (i < j) {
                array[i] = array[j];
                i++;
            }
            while (i < j && array[i] < pivot) {
                i++;
            }
            if (i < j) {
                array[j] = array[i];
                j--;
            }
        }
        array[i] = pivot;

        // 递归
        quickSort(array, left, i - 1);
        quickSort(array, i + 1, right);
    }

    /**
     * 希尔排序
     */
    public static void shellSort(int[] arr) {
        int len = arr.length;
        int h = 1;
        while(h < len / 3) {
            h = h * 3 + 1;
        }
        int tmp;
        while(h >= 1) {
            for (int i = h; i < len; i++) {
                for (int j = i; j >= h ; j -= h) {
                    if (arr[j] < arr[j - h]) {
                        tmp = arr[j];
                        arr[j] = arr[j - h];
                        arr[j - h] = tmp;
                    } else break;
                }
            }
            h /= 3;
        }
    }
}
