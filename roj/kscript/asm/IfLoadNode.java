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

import roj.kscript.parser.Symbol;
import roj.kscript.type.KBool;

/**
 * @author Roj234
 * @since  2020/9/27 18:50
 */
public final class IfLoadNode extends Node {
    final byte type;

    public IfLoadNode(short type) {
        if(type == IfNode.TRUE - 53)
            throw new IllegalArgumentException("NO IS_TRUE available");
        this.type = (byte) (type - 53);
    }

    @Override
    public Opcode getCode() {
        return Opcode.IF_LOAD;
    }

    @Override
    public Node exec(Frame frame) {
        frame.push(KBool.valueOf(IfNode.calcIf(frame, type)));
        return next;
    }

    @Override
    public String toString() {
        return "If_Load " + type + Symbol.byId((short) (type + 53));
    }
}
