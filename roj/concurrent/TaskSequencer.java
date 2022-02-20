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
import roj.concurrent.task.ScheduledTask;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * 处理定时任务
 * @author Roj234
 * @since 2022/2/8 8:00
 */
public class TaskSequencer implements Runnable {
    static final Comparator<Scheduled> CPR = (o1, o2) -> Long.compare(o1.nextRun, o2.nextRun);

    private BSLowHeap<Scheduled> tasks, backup;
    private final AtomicInteger lock;
    private Thread worker;

    public TaskSequencer() {
        this.tasks = new BSLowHeap<>(CPR);
        this.backup = new BSLowHeap<>(CPR);
        this.lock = new AtomicInteger();
    }

    @Override
    public void run() {
        worker = Thread.currentThread();
        while (!Thread.interrupted()) {
            long next = work();
            if (next < 0) LockSupport.park();
            else LockSupport.parkNanos(next * 1_000_000L);
        }
    }

    /**
     * @return Minimum task run later
     */
    public long work() {
        if (tasks.isEmpty()) {
            return -1L;
        } else {
            while (!lock.compareAndSet(0, 1)) LockSupport.parkNanos(9999);

            long time = System.currentTimeMillis();
            BSLowHeap<Scheduled> tt = this.tasks;
            int i;
            for (i = 0; i < tt.size(); i++) {
                Scheduled task = tt.get(i);
                if(task.nextRun <= time) {
                    if (!task.isCancelled()) {
                        try {
                            task.compute();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                        if (--task.count == 0) {
                            task.nextRun = -1;
                        } else {
                            task.nextRun = task.interval + time;
                            backup.add(task);
                        }
                    }
                } else {
                    break;
                }
            }
            for (; i < tt.size(); i++) {
                backup.add(tt.get(i));
            }
            tasks = backup;
            lock.set(0);

            (backup = tt).clear();

            Scheduled task = tasks.top();
            return task == null ? -1 : task.nextRun - System.currentTimeMillis();
        }
    }

    public Scheduled register(ITask task, int interval, int delay, int remain) {
        return register(new ScheduledTask(interval, delay, remain, task));
    }

    public Scheduled register(Scheduled t) {
        while (!lock.compareAndSet(0, 2)) LockSupport.parkNanos(9999);
        Scheduled prev = tasks.top();
        tasks.add(t);
        if (tasks.top() != prev) LockSupport.unpark(worker);
        lock.set(0);
        return t;
    }
}
