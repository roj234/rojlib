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
package roj.asm.mapper.util;

import roj.concurrent.task.AbstractExecutionTask;

import java.util.List;
import java.util.function.Consumer;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public final class Worker extends AbstractExecutionTask {
    private final List<Context> files;
    private final Consumer<Context> consumer;

    public Worker(List<Context> files, Consumer<Context> consumer, String name) {
        this.files = files;
        this.consumer = consumer;
    }

    public void run() {
        List<Context> f = this.files;
        Consumer<Context> c = this.consumer;
        for (int i = 0; i < f.size(); i++) {
            c.accept(f.get(i));
        }
    }
}
