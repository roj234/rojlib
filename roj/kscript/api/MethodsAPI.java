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
package roj.kscript.api;

import roj.kscript.parser.ast.DedicatedMethod;
import roj.kscript.parser.ast.Method;
import roj.kscript.type.KType;

import java.util.List;

/**
 * @author Roj234
 * @since  2020/10/14 22:46
 */
public abstract class MethodsAPI {
    public static final ThreadLocal<MethodsAPI> PF = new ThreadLocal<>();

    public static DedicatedMethod getDedicated(CharSequence name, Method method) {
        MethodsAPI inst = PF.get();
        if (inst == null) {
            return null;
        } else {
            Computer computer = inst.getDedicated0(name);
            return computer == null ? null : new DedicatedMethod(method.args, computer);
        }
    }

    public static KType preCompute(CharSequence name, List<KType> cst) {
        MethodsAPI inst = PF.get();
        return inst == null ? null : inst.preCompute0(name, cst);
    }

    protected abstract Computer getDedicated0(CharSequence name);
    protected abstract KType preCompute0(CharSequence name, List<KType> cst);
}
