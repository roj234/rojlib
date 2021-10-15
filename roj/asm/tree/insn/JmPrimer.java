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

package roj.asm.tree.insn;

import org.jetbrains.annotations.ApiStatus.Internal;
import roj.asm.util.ConstantWriter;
import roj.collect.LinkedIntMap;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

@Internal
public final class JmPrimer extends InsnNode {
    public JmPrimer(byte code, int def) {
        super(code);
        this.def = def;
        this.switcher = null;
    }

    public JmPrimer(byte code, int def, LinkedIntMap<Integer> switcher) {
        super(code);
        this.def = def;
        this.switcher = switcher;
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case TABLESWITCH:
            case LOOKUPSWITCH:
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_icmpeq:
            case IF_icmpne:
            case IF_icmplt:
            case IF_icmpge:
            case IF_icmpgt:
            case IF_icmple:
            case IF_acmpeq:
            case IF_acmpne:
            case IFNULL:
            case IFNONNULL:
            case GOTO:
            case GOTO_W:
                return true;
        }
        return false;
    }

    @Override
    public int nodeType() {
        return 123;
    }

    public int selfIndex, arrayIndex;
    public int                         def;
    public final LinkedIntMap<Integer> switcher;

    @Override
    public String toString() {
        return "Jump => " + def + " / " + switcher;
    }

    @Override
    public void toByteArray(ConstantWriter cw, ByteWriter w) {
        throw new UnsupportedOperationException();
    }

    public InsnNode bake(InsnNode target) {
        return code == GOTO || code == GOTO_W ?
                new GotoInsnNode(code, target) :
                new IfInsnNode(code, target);
    }
}