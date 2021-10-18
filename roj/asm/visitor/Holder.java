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
package roj.asm.visitor;

import roj.asm.util.ConstantPool;
import roj.util.ByteReader;
import roj.util.ByteWriter;

/**
 * 公共数据容器
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/4 11:57
 */
public abstract class Holder {
    protected ByteWriter   bw;
    protected ConstantPool cw;
    protected ByteReader   br;
    protected ConstantPool   cp;

    Holder() {}

    Holder(ClassVisitor cv) {
        preVisit(cv);
    }

    public void preVisit(ClassVisitor cv) {
        this.bw = cv.bw;
        this.cw = cv.cw;
        this.br = cv.br;
    }

    public void postVisit() {
        this.bw = null;
        this.cw = null;
        this.br = null;
        this.cp = null;
    }
}
