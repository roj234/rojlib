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
