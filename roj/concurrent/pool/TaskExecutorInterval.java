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

import roj.util.Helpers;

import java.util.Iterator;
import java.util.Queue;

@Deprecated
public class TaskExecutorInterval extends TaskExecutor {
    public TaskExecutorInterval() {
        super();
    }

    public TaskExecutorInterval(ThreadStateMonitor pool) {
        super(pool);
    }

    public TaskExecutorInterval(ThreadStateMonitor pool, String name, int timeout) {
        super(pool, name, timeout);
    }

    @Override
    public void run() {
        Queue<IntervalTask> tasks = Helpers.cast(this.tasks);
        while (monitor == null || monitor.working()) {
            long nextTime = timeout;
            for (Iterator<IntervalTask> iterator = tasks.iterator(); iterator.hasNext(); ) {
                IntervalTask task = iterator.next();
                long next = task.run(System.currentTimeMillis());
                if (next < nextTime) {
                    nextTime = next;
                }
                if (task.isCancelled() || task.isDone())
                    iterator.remove();
            }
            if (nextTime > 0) {
                try {
                    Thread.sleep(nextTime);
                } catch (InterruptedException ignored) {
                }
                if (tasks.isEmpty() && monitor != null && monitor.threadDeath(this))
                    break;
            }
        }
    }

    public void removeTask(Runnable task) {
        tasks.remove(task);
        if (monitor == null || monitor.working()) {
            // wake this thread up.
            interrupt();
        }
    }

    public void clearTasks() {
        tasks.clear();
        if (monitor == null || monitor.working()) {
            interrupt();
        }
    }

    public void pushTask(Runnable task, long interval, long delay) {
        tasks.add(new IntervalTask(task, interval, System.currentTimeMillis() + delay));
        if (monitor == null || monitor.working()) {
            // wake this thread up.
            interrupt();
        }
    }

    public void pushTask(Runnable task, long interval, long delay, int maxExecution) {
        tasks.add(new IntervalTask(task, interval, System.currentTimeMillis() + delay, maxExecution));
        if (monitor == null || monitor.working()) {
            // wake this thread up.
            interrupt();
        }
    }
}
