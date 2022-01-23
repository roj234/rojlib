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
package roj.util;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * Tail Call Optimization
 *
 * @author solo6975
 * @since 2021/6/27 7:25
 */
public abstract class TCO<T,R> implements Function<T, R> {
    boolean zero;

    private Object[] stack = new Object[4];
    private int stackUsed;

    @Nonnull
    @SuppressWarnings("unchecked")
    public final T pop() {
        T v = (T) stack[--stackUsed];
        stack[stackUsed] = null;
        return v;
    }

    public final void push(@Nonnull T base) {
        if(stackUsed == stack.length) {
            Object[] plus = new Object[(int) (stackUsed * 1.5)];
            System.arraycopy(stack, 0, plus, 0, stackUsed);
            stack = plus;
        }

        stack[stackUsed] = base;

        if (++stackUsed > 2048)
            throw new IllegalStateException("Stack overflow(2048): " + this);
    }

    public final void stackClear() {
        for (int i = 0; i < stackUsed; i++) {
            stack[i] = null;
        }
        stackUsed = 0;
    }

    public R apply(T arg) {
        push(arg);
        if (!zero) {
            zero = true;
            R r = null;
            while (stackUsed > 0) {
                r = recursion(pop());
            }
            zero = false;
            return r;
        }

        return null;
    }

    /**
     * 目标递归函数 <br>
     * requirement: 将其中所有调用自身换为调用apply
     */
    protected abstract R recursion(T t);
}
