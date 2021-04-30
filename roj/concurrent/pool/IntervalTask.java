package roj.concurrent.pool;

import roj.concurrent.task.ITask;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: IntervalTask.java
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
