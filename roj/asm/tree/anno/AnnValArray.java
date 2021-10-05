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

import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/9 14:23
 */
public final class AnnValArray extends roj.asm.tree.anno.AnnVal {
    public AnnValArray(List<roj.asm.tree.anno.AnnVal> value) {
        super(AnnotationType.ARRAY);
        this.value = value;
    }

    public List<roj.asm.tree.anno.AnnVal> value;

    public void _toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort((short) value.size());
        for (roj.asm.tree.anno.AnnVal val : value) {
            val.toByteArray(pool, w);
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (AnnVal val : value) {
            sb.append(val).append(", ");
        }
        if (value.size() > 0)
            sb.delete(sb.length() - 2, sb.length());
        sb.append('}');

        return sb.toString();
    }
}