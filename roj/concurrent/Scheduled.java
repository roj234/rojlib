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

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class Scheduled {
    public final int interval;
    long nextRun; int count;
    boolean cancelled;

    public Scheduled(int interval, int delay, int count) {
        if (interval <= 0 && count != 1) throw new IllegalArgumentException("interval <= 0");
        if (delay < 0) throw new IllegalArgumentException("delay < 0");

        this.interval = interval;
        this.nextRun = System.currentTimeMillis() + delay;
        this.count = count;
    }

    public abstract Object getTask();

    public int getRemainCount() {
        return count;
    }

    public final long getNextRun() {
        return nextRun;
    }

    public void cancel() {
        cancelled = true;
        nextRun = -1;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public abstract void compute() throws Exception;
}
