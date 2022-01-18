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
package roj.net.misc;

import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;

/**
 * @author Roj233
 * @since 2021/10/13 0:16
 */
public final class TaskManager extends TaskPool {
    public TaskManager() {
        super(0, Integer.parseInt(System.getProperty("tm.pooled", "6")), 1, 1, new MyThreadFactory() {
            int i = 1;
            @Override
            public TaskExecutor get(TaskPool pool) {
                return new TaskExecutor(pool, "Executor #" + i++, 120000);
            }
        });
    }

    @Override
    protected void onReject(ITask task, int minPending) {
        ((Thread) task).start();
    }
}
