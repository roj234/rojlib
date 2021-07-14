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

import roj.concurrent.task.ITask;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
class IntervalTask implements ITask {
    private final Runnable runnable;
    private final long interval;
    private long timestamp;
    private int times;
    private boolean canceled;

    public IntervalTask(Runnable runnable, long interval, long timestamp, int times) {
        this.runnable = runnable;
        this.interval = interval;
        this.timestamp = timestamp;
        this.times = times;
    }

    public IntervalTask(Runnable runnable, long interval, long timestamp) {
        this.runnable = runnable;
        this.interval = interval;
        this.timestamp = timestamp;
        this.times = Integer.MAX_VALUE;
    }

    public long run(long currentTime) {
        if (times <= 0) return Long.MAX_VALUE;
        if (currentTime - timestamp >= interval) {
            runnable.run();
            timestamp = currentTime;
            times--;
            return interval;
        }
        return interval - (currentTime - timestamp);
    }

    @Override
    public boolean equals(Object o) {
        return this.runnable.equals(o);
    }

    @Override
    public int hashCode() {
        return runnable.hashCode();
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public boolean cancel(boolean force) {
        return canceled = true;
    }

    @Override
    public void calculate(Thread thread) {
        throw new IllegalStateException();
    }

    @Override
    public boolean isDone() {
        return canceled || times <= 0;
    }
}
