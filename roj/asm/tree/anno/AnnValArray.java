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

package roj.asm.tree.anno;

import roj.asm.util.ConstantPool;
import roj.util.ByteWriter;

import java.util.List;

/**
 * @author Roj234
 * @version 0.1
 * @since 2021/1/9 14:23
 */
public final class AnnValArray extends AnnVal {
    public AnnValArray(List<AnnVal> value) {
        this.value = value;
    }

    public List<AnnVal> value;

    public void toByteArray(ConstantPool pool, ByteWriter w) {
        w.writeByte((byte) ARRAY).writeShort(value.size());
        for (int i = 0; i < value.size(); i++) {
            value.get(i).toByteArray(pool, w);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        if (value.size() > 0) {
            for (int i = 0; i < value.size(); i++) {
                AnnVal val = value.get(i);
                sb.append(val).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append('}').toString();
    }

    @Override
    public byte type() {
        return ARRAY;
    }
}