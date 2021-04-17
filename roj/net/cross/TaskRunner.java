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
package roj.net.cross;

import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/10/13 0:16
 */
final class TaskRunner extends TaskPool {
    public TaskRunner() {
        super(1, Integer.parseInt(System.getProperty("ae.pooled_threads", "10")), 1, 1, pool -> new TaskExecutor(pool, "Worker-Idle", 120000));
    }

    @Override
    public void pushTask(ITask task) {
        if (task == null) return;
        super.pushTask(task);
    }

    @Override
    protected void onReject(ITask task, int minPending) {
        Thread w = (Thread) task;
        w.start();
    }
}
