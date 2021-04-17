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
package roj.asm.frame;

import roj.asm.tree.insn.InsnNode;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:28
 */
public final class Frame {
    public static final Frame EMPTY = new Frame(-1);

    /**
     * @see FrameType
     */
    public char type;
    public InsnNode target;
    public VarList
            locals = new VarList(),
            stacks = new VarList();

    public Frame(int type) {
        this.type = (char) type;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("        ").append(FrameType.toString(type)).append(" #").append(target);
        if (locals.size > 0) {
            sb.append("\n            Local: ");
            for (int i = 0; i < locals.size; i++) {
                sb.append(locals.get(i)).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append('\n');
        if (stacks.size > 0) {
            sb.append("\n            Stack: ");
            for (int i = 0; i < stacks.size; i++) {
                sb.append(stacks.get(i)).append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append('\n').toString();
    }

    public void pack() {
        locals = locals.stripTops();
    }
}
