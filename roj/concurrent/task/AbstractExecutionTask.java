/*
 * This file is a part of MoreItems
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
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/7/24 10:25
 */
public abstract class AbstractExecutionTask implements ITask, Future<Void>, Runnable {
    protected boolean done, canceled;
    protected ExecutionException exception;

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public boolean cancel(boolean force) {
        canceled = true;
        if(force) {
            synchronized (this) {
                notifyAll();
            }
        }
        return canceled;
    }

    @Override
    public void calculate(Thread thread) {
        try {
            run();
        } catch (Throwable e) {
            exception = new ExecutionException(e);
        }
        synchronized (this) {
            done = true;
            notifyAll();
        }
    }

    public abstract void run();

    @Override
    public boolean isDone() {
        return canceled || done;
    }


    @Override
    public Void get() throws InterruptedException, ExecutionException {
        if (this.isCancelled()) {
            throw new CancellationException();
        }

        if (this.exception != null)
            throw exception;
        synchronized (this) {
            if (!done)
                this.wait();
        }
        if (this.exception != null)
            throw exception;

        return null;
    }

    @Override
    public Void get(long timeout, @Nonnull TimeUnit unit) throws InterruptedException, TimeoutException, ExecutionException {
        if (this.isCancelled()) {
            throw new CancellationException();
        }

        if (this.exception != null)
            throw exception;
        synchronized (this) {
            if (!done) {
                this.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
            }
        }

        if (this.isCancelled()) {
            throw new CancellationException();
        }

        synchronized (this) {
            if (this.exception != null)
                throw exception;
            if (!done) {
                throw new TimeoutException();
            }
        }

        return null;
    }
}
