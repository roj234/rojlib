package roj.concurrent;

import javax.annotation.Nonnull;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Roj233
 * @since 2022/3/11 20:58
 */
public class ImmediateFuture<T> implements Future<T> {
    public final T t;

    public ImmediateFuture(T t) {
        this.t = t;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    @Override
    public T get() {
        return t;
    }

    @Override
    public T get(long timeout, @Nonnull TimeUnit unit) {
        return t;
    }
}
