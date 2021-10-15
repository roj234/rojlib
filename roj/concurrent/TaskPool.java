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

import org.jetbrains.annotations.Async;
import roj.concurrent.task.ExecutionTask;
import roj.concurrent.task.ITask;
import roj.util.ArrayUtil;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class TaskPool implements ThreadStateMonitor, TaskHandler {
    protected final TaskExecutor[] thread;
    protected final MyThreadFactory factory;

    public static final MyThreadFactory DEFAULT = TaskExecutor::new;

    @FunctionalInterface
    public interface MyThreadFactory {
        TaskExecutor get(TaskPool pool);
    }

    protected int core, max, addThr, maxThr;
    protected int running;

    protected final AtomicInteger lock = new AtomicInteger();

    public TaskPool(int coreThreads, int maxThreads, int threshold, int maxTaskPerThread, MyThreadFactory factory) {
        this.core = coreThreads;
        this.max = maxThreads;
        this.addThr = threshold;
        this.thread = new TaskExecutor[maxThreads];
        this.maxThr = maxTaskPerThread;
        this.factory = factory;
    }

    public TaskPool(int coreThreads, int maxThreads, int threshold, int stopTimeout, String namePrefix) {
        this.core = coreThreads;
        this.max = maxThreads;
        this.addThr = threshold;
        this.thread = new TaskExecutor[maxThreads];
        this.maxThr = 1024;

        this.factory = new PrefixFactory(namePrefix, stopTimeout);
    }

    public TaskPool(int coreThreads, int maxThreads, int threshold) {
        this.core = coreThreads;
        this.max = maxThreads;
        this.addThr = threshold;
        this.thread = new TaskExecutor[maxThreads];
        this.factory = DEFAULT;
    }

    @Override
    @Async.Schedule
    public void pushTask(ITask task) {
        if (running == -1) {
            throw new RejectedExecutionException("TaskPool was shutdown.");
        }
        int minPending = Integer.MAX_VALUE;
        TaskExecutor th = null;

        final TaskExecutor[] processors = this.thread;

        int ov = lightLock(lock);

        int len = running;
        if (len <= 0) {
            if(len < 0) {
                throw new RejectedExecutionException("TaskPool was shutdown.");
            }

            newWorker(task, ov);
            return;
        }

        for (int i = 0; i < len; i++) {
            TaskExecutor p = processors[i];
            if (p.getTaskAmount() < minPending) {
                th = p;
                minPending = p.getTaskAmount();
            }
        }

        if(len < core && minPending > 0) {
            newWorker(task, ov);
            return;
        }

        if (minPending >= addThr) {
            if (len < max) {
                newWorker(task, ov);
                return;
            } else if (minPending >= maxThr) {
                onReject(task, minPending);
                return;
            }
        }

        untilCas(lock, ov + 1, ov);

        th.pushTask(task);
    }

    protected void onReject(ITask task, int minPending) {
        // maybe execution by caller
        throw new RejectedExecutionException("Minimum tasks on thread (" + minPending + ") is larger than limit (" + maxThr + ")");
    }

    private void newWorker(ITask task, int ov) {
        untilCas(lock, ov + 1, -2); // -2 new thread, -1 stop , -3 shutdown

        TaskExecutor pc = factory.get(this);
        pc.getClass();
        thread[running++] = pc;
        pc.setDaemon(true);
        pc.start();
        pc.pushTask(task);

        lock.set(ov);
    }

    private static void untilCas(AtomicInteger lock, int from, int to) {
        int i = 0;
        while (!lock.compareAndSet(from, to)) {
            Thread.yield();
            if (lock.compareAndSet(from, to)) break;
            LockSupport.parkNanos(20);
            if (i++ > 1000)
                throw new Error("Failed to get lock " + from + " => " + to + ": " + lock.getAndSet(0));
        }
    }

    private static int lightLock(AtomicInteger lock) {
        int ov;
        do {
            ov = lock.get();
            if (ov < 0) {
                Thread.yield();
            }

            ov = lock.get();
            if (ov < 0) {
                LockSupport.parkNanos(20);
            } else {
                untilCas(lock, ov, ov + 1);
                break;
            }
        } while (true);
        return ov;
    }

    @Async.Schedule
    public ExecutionTask pushRunnable(Runnable runnable) {
        ExecutionTask task = new ExecutionTask(runnable);
        pushTask(task);
        return task;
    }

    @Override
    public void clearTasks() {
        if (running == -1) return;
        final TaskExecutor[] processors = this.thread;

        int ov = lightLock(lock);
        for (int i = 0, len = running; i < len; i++) {
            TaskExecutor executor = processors[i];
            executor.clearTasks();
        }

        untilCas(lock, ov + 1, ov);
    }

    @Override
    public boolean threadDeath(TaskExecutor executor) {
        if (running > core && taskLength() / running < addThr) {
            int len = this.running - 1;
            final TaskExecutor last = this.thread[len];

            if (last != executor) {
                return false;
            }

            untilCas(lock, 0, -1);

            thread[len] = null;
            this.running = len;

            lock.set(0);

            return true;
        }

        return running < 0;
    }

    public void interruptAll() {
        for (TaskExecutor te : thread) {
            if (te != null) {
                te.interrupt();
            }
        }
    }

    public void shutdown() {
        untilCas(lock, 0, -3);
        int running = this.running;
        this.running = -1;
        lock.set(0);

        final TaskExecutor[] processors = this.thread;
        for (int i = 0; i < running; i++) {
            TaskExecutor th = processors[i];
            th.clearTasks();
            th.interrupt();
        }
    }

    @Override
    public String toString() {
        return "TaskPool{" + "thr=" + ArrayUtil.toString(thread, 0, running) + ", range=[" + core + ',' + max + "], lock=" + lock.get() + '}';
    }

    @Override
    public boolean working() {
        return running >= 0;
    }

    public TaskExecutor[] threads() {
        synchronized (this) {
            int l = running;
            TaskExecutor[] res = new TaskExecutor[l];
            System.arraycopy(thread, 0, res, 0, l);
            return res;
        }
    }

    public void waitUntilFinish() {
        while (taskLength() > 0) {
            LockSupport.parkNanos(100);
        }
    }

    public void waitUntilFinish(long timeout) {
        if(timeout <= 0) {
            waitUntilFinish();
            return;
        }
        long time = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < time && taskLength() > 0) {
            LockSupport.parkNanos(100);
        }
    }

    public int taskLength() {
        int sum = 0;

        final TaskExecutor[] processors = this.thread;
        for (int i = 0, len = running; i < len; i++) {
            TaskExecutor executor = processors[i];
            if(executor != null)
                sum += executor.getTaskAmount();
        }

        return sum;
    }

}