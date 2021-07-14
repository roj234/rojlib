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
package roj.asm.util;

import roj.asm.cst.CstClass;
import roj.asm.struct.insn.InsnNode;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class ExceptionEntry {
    public InsnNode start, end, handler;
    public String type;

    public static final String ANY_TYPE = new String("[ANY]");

    public ExceptionEntry(InsnNode start, InsnNode end, InsnNode handler, CstClass catchType) {
        this.start = start;
        this.end = end;
        this.handler = handler;
        // 如果catch_type项的值为零，则为所有异常调用此异常处理程序。
        // 0 => Constant.null
        this.type = catchType == null ? ANY_TYPE : catchType.getValue().getString();
    }

    @Override
    public String toString() {
        return "ExceptionEntry{" +
                "start=" + start +
                ", end=" + end +
                ", handler=" + handler +
                ", type='" + type + '\'' +
                '}';
    }
}
