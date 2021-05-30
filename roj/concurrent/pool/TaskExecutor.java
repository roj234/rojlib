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
