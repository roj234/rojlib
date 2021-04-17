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

import roj.collect.BSLowHeap;
import roj.concurrent.task.ITask;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@Deprecated
public class TaskExecutorInterval {
    private static final Comparator<IntervalTask> CPR = (o1, o2) -> Long.compare(o1.nextRun, o2.nextRun);
    BSLowHeap<IntervalTask> tasks, backup;
    AtomicInteger lock;

    public TaskExecutorInterval() {
        this.tasks = new BSLowHeap<>(CPR);
        this.backup = new BSLowHeap<>(CPR);
        this.lock = new AtomicInteger();
    }

    /**
     * @return Minimum task run later
     */
    public long run() {
        if (tasks.isEmpty()) {
            return -1L;
        } else {
            Thread t = Thread.currentThread();

            long time = System.currentTimeMillis();
            while (!lock.compareAndSet(0, 1))
                LockSupport.parkNanos(100);
            BSLowHeap<IntervalTask> tt = this.tasks;
            int i;
            for (i = 0; i < tt.size(); i++) {
                IntervalTask task = this.tasks.get(i);
                if(task.nextRun <= time) {
                    if (!task.run.isCancelled() && task.maxRun-- != 0) {
                        try {
                            task.run.calculate(t);
                        } catch (Throwable e) {
                            if(!(e instanceof InterruptedException))
                                e.printStackTrace();
                        }
                        task.nextRun = task.interval + time;
                        backup.add(task);
                    }
                } else {
                    break;
                }
            }
            for (; i < tt.size(); i++) {
                backup.add(tt.get(i));
            }
            tasks = backup;
            backup = tt;
            tt.clear();
            time = tasks.get(0).nextRun - System.currentTimeMillis();
            lock.set(0);
            return time;
        }
    }

    public void add(ITask task, long interval, long delay, int maxRun) {
        IntervalTask t = new IntervalTask(task, interval, delay, maxRun);
        while (!lock.compareAndSet(1, 2))
            LockSupport.parkNanos(75);
        tasks.add(t);
        lock.set(0);
    }
}
