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

package ilib.util.hook;

import roj.collect.MyHashMap;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public final class Hook {
    public static final String MODEL_REGISTER = "itemModelReg";
    public static final String LANGUAGE_RELOAD = "langReload";

    private final Map<String, FunctionList> hooks = new MyHashMap<>();

    public Hook() {
    }

    public void add(String name, Runnable func) {
        FunctionList list = this.hooks.get(name);
        if (list == null) {
            list = new FunctionList(-1);
        }
        list.add(func);
        this.hooks.put(name, list);
    }

    public void remove(String name) {
        this.hooks.remove(name);
    }

    public boolean trigger(String name) {
        FunctionList list = this.hooks.get(name);
        if (list == null) {
            return false;
        }
        if (list.call())
            remove(name);
        return true;
    }

    public void triggerOnce(String name) {
        FunctionList list = this.hooks.get(name);
        if (list == null) {
            return;
        }
        list.call();
        remove(name);
    }
}