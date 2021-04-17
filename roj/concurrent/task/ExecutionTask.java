package roj.concurrent.task;

public final class ExecutionTask implements ITask {
    private final Runnable runnable;
    private boolean done, canceled;

    public ExecutionTask(Runnable runnable) {
        runnable.getClass();
        this.runnable = runnable;
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
        runnable.run();
        synchronized (this) {
            done = true;
            notifyAll();
        }
    }

    @Override
    public boolean isDone() {
        return canceled || done;
    }
}
