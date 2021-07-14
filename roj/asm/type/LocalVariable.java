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
package roj.asm.type;

import roj.asm.struct.insn.InsnNode;
import roj.asm.util.IType;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/10 17:53
 */
public final class LocalVariable {
    public LocalVariable(int slot, String name, IType type, InsnNode start, InsnNode end) {
        this.slot = slot;
        this.name = name;
        this.type = type;
        this.start = start;
        this.end = end;
    }

    public String name;
    public IType type;
    public InsnNode start, end;
    public int slot;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariable that = (LocalVariable) o;
        return slot == that.slot && start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return slot * 31 + start.hashCode();
    }

    public String toString() {
        return String.valueOf(slot) + '\t' + type + '\t' + name + '\t' + start + '\t' + end;
    }
}
