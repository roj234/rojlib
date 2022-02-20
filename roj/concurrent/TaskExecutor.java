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

import roj.concurrent.task.ExecutionTask;
import roj.concurrent.task.ITask;
import roj.util.FastLocalThread;
import roj.util.SleepingBeauty;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;

public class TaskExecutor extends FastLocalThread implements TaskHandler, Executor {
    ConcurrentLinkedQueue<ITask> tasks = new ConcurrentLinkedQueue<>();
    final ThreadStateMonitor monitor;
    final int timeout;
    volatile boolean busy;

    static {
        SleepingBeauty.sleep();
    }

    public TaskExecutor() {
        this(ThreadStateMonitor.EVER);
    }

    public TaskExecutor(ThreadStateMonitor monitor) {
        setName("TaskScheduler-" + hashCode());
        setDaemon(true);
        this.timeout = 30000;
        this.monitor = monitor;
    }

    public TaskExecutor(ThreadStateMonitor monitor, String name, int timeout) {
        setName(name);
        setDaemon(true);
        this.timeout = timeout;
        this.monitor = monitor;
    }

    public TaskExecutor(ThreadGroup tg, ThreadStateMonitor monitor, String name, int timeout) {
        super(tg, name);
        setName(name);
        setDaemon(true);
        this.timeout = timeout;
        this.monitor = monitor;
    }

    public boolean busy() {
        return busy;
    }
    
    @Override
    public void run() {
        out:
        while (monitor.working()) {
            ITask task;
            do {
                task = tasks.peek();
                if (task == null) {
                    synchronized (this) {
                        notifyAll();
                    }

                    long t = System.currentTimeMillis();
                    boolean in = Thread.interrupted();
                    LockSupport.parkNanos(timeout * 1000_000L);
                    if (in) interrupt();
                    // maybe there's something to do now.

                    if (tasks.isEmpty() && System.currentTimeMillis() - t >= timeout) {
                        if (monitor.threadDeath(this)) {
                            break out;
                        }
                    }
                } else if (task.isCancelled()) {
                    tasks.poll();
                } else {
                    break;
                }
            } while (true);

            busy = true;
            try {
                task.calculate();
            } catch (Throwable e) {
                if(!(e instanceof InterruptedException))
                    e.printStackTrace();
            }
            busy = false;
            tasks.poll();
            try {
                if (task.continueExecuting()) {
                    tasks.add(task);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void pushTask(ITask task) {
        tasks.add(task);

        if (!busy) {
            // wake this thread up.
            LockSupport.unpark(this);
        }
    }

    public int getTaskAmount() {
        return tasks.size();
    }

    @Override
    public void clearTasks() {
        ConcurrentLinkedQueue<ITask> queue = tasks;
        tasks = new ConcurrentLinkedQueue<>();
        for (ITask task : queue) {
            task.cancel(true);
        }
        queue.clear();

        if (!busy) {
            // wake this thread up.
            LockSupport.unpark(this);
        }
    }

    @Override
    public String toString() {
        return "TE{" + "task=" + tasks + '}';
    }

    @Override
    public void execute(Runnable command) {
        pushTask(new ExecutionTask(command));
    }
}
