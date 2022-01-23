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
package roj.kscript.asm;

import roj.kscript.type.KType;

import java.util.Arrays;

/**
 * @author Roj234
 * @since  2021/4/22 12:36
 */
public final class Closure extends IContext {
    KType[] lvt;

    public Closure(IContext ctx) {
        KType[] lvt1;
        if(ctx instanceof Frame) {
            lvt1 = ((Frame) ctx).lvt;
        } else {
            lvt1 = ((Closure) ctx).lvt;
        }

        lvt = new KType[lvt1.length];
        for (int i = 0; i < lvt1.length; i++) {
            lvt[i] = lvt1[i].copy();
        }
    }

    @Override
    public String toString() {
        return "<Closure>: " + Arrays.toString(lvt);
    }

    @Override
    KType getEx(String keys, KType def) { return null; }

    @Override
    public void put(String id, KType val) {}

    @Override
    KType getIdx(int index) {
        return lvt[index];
    }

    @Override
    void putIdx(int index, KType value) {
        lvt[index] = value;
    }
}
