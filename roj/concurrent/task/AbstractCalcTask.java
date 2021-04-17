package roj.concurrent.task;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/19 1:01
 */
public abstract class AbstractCalcTask<T> implements Future<T>, ITask {
    protected volatile T out;
    protected boolean canceled, executing;
    protected ExecutionException exception;

    @SuppressWarnings("unchecked")
    public AbstractCalcTask() {
        out = (T) this;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (executing) {
            return false;
        } else {
            canceled = true;
            return true;
        }
    }

    /**
     * Returns {@code true} if this task was cancelled before it completed
     * normally.
     */
    @Override
    public boolean isCancelled() {
        return canceled;
    }

    /**
     * Returns {@code true} if this task completed.
     * <p>
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    @Override
    public boolean isDone() {
        return canceled || out != this || exception != null;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        if (this.isCancelled()) {
            throw new CancellationException();
        }

        if (this.exception != null)
            throw exception;
        synchronized (this) {
            if (this.out == this)
                this.wait();
        }
        if (this.exception != null)
            throw exception;

        return out;
    }

    @Override
    public T get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
        if (this.isCancelled()) {
            throw new CancellationException();
        }

        if (this.exception != null)
            throw exception;
        synchronized (this) {
            if (out == this) {
                this.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
            }
        }

        if (this.isCancelled()) {
            throw new CancellationException();
        }

        synchronized (this) {
            if (this.exception != null)
                throw exception;
            if (out == this) {
                throw new TimeoutException();
            }
        }

        return out;
    }
}
