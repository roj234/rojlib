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
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TaskPool implements ThreadStateMonitor, TaskHandler {
    protected final TaskExecutor[] thread;
    protected final MyThreadFactory factory;
    protected RejectPolicy policy;

    public static final MyThreadFactory DEFAULT = TaskExecutor::new;

    @FunctionalInterface
    public interface MyThreadFactory {
        TaskExecutor get(TaskPool pool);
    }
    @FunctionalInterface
    public interface RejectPolicy {
        void onReject(ITask task, int minPending);
    }

    protected int core, max, addThr, maxThr;
    protected int running;

    protected final ReadWriteLock lock = new ReentrantReadWriteLock();

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
        final TaskExecutor[] processors = this.thread;

        int len = running;
        if (len <= 0) {
            if(len < 0) {
                throw new RejectedExecutionException("TaskPool was shutdown.");
            }

            newWorker(task);
            return;
        }

        TaskExecutor th = null;
        int minPending = Integer.MAX_VALUE;
        for (int i = 0; i < len; i++) {
            TaskExecutor p = processors[i];
            if (p != null && p.getTaskAmount() < minPending) {
                th = p;
                minPending = p.getTaskAmount();
            }
        }

        if(len < core && minPending > 0) {
            newWorker(task);
            return;
        }

        if (minPending >= addThr) {
            if (len < max) {
                newWorker(task);
                return;
            } else if (minPending >= maxThr) {
                onReject(task, minPending);
                return;
            }
        }

        th.pushTask(task);
    }

    public void setRejectPolicy(RejectPolicy policy) {
        this.policy = policy;
    }

    protected void onReject(ITask task, int minPending) {
        if (policy == null) {
            throw new RejectedExecutionException("Minimum tasks on thread (" + minPending + ") is larger than limit (" + maxThr + ")");
        } else {
            policy.onReject(task, minPending);
        }
    }

    private void newWorker(ITask task) {
        lock.writeLock().lock();

        try {
            TaskExecutor pc = factory.get(this);
            pc.getClass();
            thread[running++] = pc;
            pc.setDaemon(true);
            pc.start();
            pc.pushTask(task);
        } finally {
            lock.writeLock().unlock();
        }

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

        lock.writeLock().lock();
        try {
            for (int i = 0, len = running; i < len; i++) {
                TaskExecutor executor = processors[i];
                executor.clearTasks();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean threadDeath(TaskExecutor executor) {
        if (running > core && taskLength() / running < addThr) {
            int len = this.running - 1;
            final TaskExecutor last = this.thread[len];

            if (last != executor) {
                return false;
            }

            lock.writeLock().lock();

            thread[len] = null;
            this.running = len;

            lock.writeLock().unlock();

            return true;
        }

        return running < 0;
    }

    public void wakeupAll() {
        for (TaskExecutor te : thread) {
            if (te != null) {
                LockSupport.unpark(te);
            }
        }
    }

    public void shutdown() {
        lock.writeLock().lock();
        int running = this.running;
        this.running = -1;
        lock.writeLock().unlock();

        final TaskExecutor[] processors = this.thread;
        for (int i = 0; i < running; i++) {
            TaskExecutor th = processors[i];
            th.clearTasks();
            th.interrupt();
        }
    }

    @Override
    public String toString() {
        return "TaskPool{" + "thr=" + ArrayUtil.toString(thread, 0, running) + ", range=[" + core + ',' + max + "], lock=" + lock + '}';
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