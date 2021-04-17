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
package roj.concurrent.task;

import javax.annotation.Nonnull;
import java.util.concurrent.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/19 1:01
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
