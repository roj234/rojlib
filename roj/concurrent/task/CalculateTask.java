package roj.concurrent.task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public final class CalculateTask<T> extends AbstractCalcTask<T> {
    private final Callable<T> supplier;

    public CalculateTask(Callable<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public void calculate(Thread thread) {
        executing = true;
        try {
            this.out = supplier.call();
        } catch (Throwable e) {
            exception = new ExecutionException(e);
        }
        executing = false;

        synchronized (this) {
            notifyAll();
        }
    }

    public static CalculateTask<Void> fromVoid(Runnable runnable) {
        return new CalculateTask<>(() -> {
            runnable.run();
            return null;
        });
    }

    @Override
    public String toString() {
        return "Task{executing=" + executing +
                ", canceled=" + canceled +
                '}';
    }
}
