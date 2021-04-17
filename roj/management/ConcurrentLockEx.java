package roj.management;

import roj.math.MutableInt;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/11 17:00
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
