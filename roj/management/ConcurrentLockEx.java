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
package roj.management;

import roj.math.MutableInt;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/11 17:00
 */
public class ConcurrentLockEx {
    public static void main(String[] args) throws InterruptedException {
        main1();
        main2();
    }


    public static void main1() throws InterruptedException {
        MutableInt number = new MutableInt(0);

        MutableInt idAndLock = new MutableInt(0);

        Thread a = new Thread(getRunnable(number, idAndLock, 1, 2), "A");
        a.start();

        Thread b = new Thread(getRunnable(number, idAndLock, 2, 3), "B");
        b.start();

        Thread c = new Thread(getRunnable(number, idAndLock, 3, 1), "C");
        c.start();

        Thread.sleep(100);
        idAndLock.setValue(1);
        synchronized (idAndLock) {
            idAndLock.notifyAll();
        }

        Thread.sleep(1000);
        a.stop();
        b.stop();
        c.stop();
    }

    public static Runnable getRunnable(MutableInt number, MutableInt idAndLock, int myId, int nextId) {
        return () -> {
            while (true) {
                synchronized (idAndLock) {
                    if (idAndLock.getValue() == myId) {
                        System.out.println(Thread.currentThread().getName() + "->" + number);
                    }
                    idAndLock.setValue(nextId);
                    idAndLock.notifyAll();
                    try {
                        idAndLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        };
    }

    public static void main2() throws InterruptedException {
        Object lock1 = new Object();
        Object lock2 = new Object();
        Object lock3 = new Object();

        MutableInt number = new MutableInt(0);

        Thread a = new Thread(getRunnable(lock2, lock1, number), "A");
        a.start();

        Thread b = new Thread(getRunnable(lock3, lock2, number), "B");
        b.start();

        Thread c = new Thread(getRunnable(lock1, lock3, number), "C");
        c.start();

        Thread.sleep(100);
        synchronized (lock1) {
            lock1.notifyAll();
        }

        Thread.sleep(1000);
        a.stop();
        b.stop();
        c.stop();
    }

    public static Runnable getRunnable(Object nextLock, Object currLock, MutableInt number) {
        return () -> {
            while (true) {
                synchronized (currLock) {
                    try {
                        currLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println(Thread.currentThread().getName() + "->" + number.getAndIncrement());
                }

                synchronized (nextLock) {
                    nextLock.notifyAll();
                }
            }
        };
    }
}
