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
package roj.concurrent.pool;

import roj.concurrent.TaskHandler;
import roj.concurrent.task.ExecutionTask;
import roj.concurrent.task.ITask;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public class TaskExecutor extends Thread implements TaskHandler, Executor {
    ConcurrentLinkedQueue<ITask> tasks = new ConcurrentLinkedQueue<>();
    final ThreadStateMonitor monitor;
    final int timeout;
    boolean sleeping;

    public TaskExecutor() {
        this(null);
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

    public boolean sleeping() {
        return sleeping;
    }
    
    @Override
    public void run() {
        out:
        while (!tasks.isEmpty() || (monitor == null || monitor.working())) {
            if (tasks.isEmpty()) {
                synchronized (this) {
                    notifyAll();
                }

                sleeping = true;
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    // maybe there's something to do now.
                }
                sleeping = false;

                if (tasks.isEmpty()) {
                    synchronized (this) {
                        notifyAll();
                    }
                    if (tasks.isEmpty() && monitor != null && monitor.threadDeath(this)) {
                        break;
                    }
                }
            } else {
                ITask task;
                while (true) {
                    task = tasks.peek();
                    if (task == null) continue out;
                    if (task.isCancelled()) {
                        tasks.poll();
                    } else {
                        break;
                    }
                }

                try {
                    task.calculate(this);
                } catch (Throwable e) {
                    if(!(e instanceof InterruptedException))
                        e.printStackTrace();
                }

                try {
                    if (task.continueExecuting()) {
                        tasks.add(task);
                    }
                } catch (Throwable ignored) {}

                tasks.poll();
            }
        }
    }

    @Override
    public void pushTask(ITask task) {
        task.onJoin();
        tasks.add(task);

        final State state = getState();
        if(state == State.TIMED_WAITING || state == State.RUNNABLE) {
            // wake this thread up.
            interrupt();
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

        final State state = getState();
        if(state == State.TIMED_WAITING || state == State.RUNNABLE) {
            // wake this thread up.
            interrupt();
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
