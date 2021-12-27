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

package roj.asm.cst;

import roj.util.ByteWriter;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class CstUTF extends Constant {
    private String data;

    public CstUTF() {}

    public CstUTF(String data) {
        this.data = data;
    }

    public String getString() {
        return this.data;
    }

    public void setString(String s) {
        // noinspection all
        this.data = s.toString();
    }

    @Override
    public void write(ByteWriter w) {
        w.put(CstType.UTF).putJavaUTF(data);
    }

    public String toString() {
        return super.toString() + ' ' + data;
    }

    @Override
    public byte type() {
        return CstType.UTF;
    }

    public int hashCode() {
        return 1 + (data == null ? 0 : data.hashCode());
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof CstUTF))
            return false;
        CstUTF ref = (CstUTF) o;
        return this.data.equals(ref.data);
    }
}