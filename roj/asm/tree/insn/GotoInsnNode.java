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

import roj.asm.Opcodes;
import roj.asm.util.ConstantPool;
import roj.asm.util.InsnList;
import roj.collect.IIntMap;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/24 23:21
 */
public class GotoInsnNode extends InsnNode {
    public GotoInsnNode() {
        super(Opcodes.GOTO);
    }

    public GotoInsnNode(InsnNode target) {
        super(Opcodes.GOTO);
        setTarget(target);
    }

    public GotoInsnNode(byte code, InsnNode target) {
        super(code);
        setTarget(target);
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case GOTO:
            case GOTO_W:
                return true;
        }
        return false;
    }

    @Override
    public final int nodeType() {
        return T_GOTO_IF;
    }

    public InsnNode target;

    public final InsnNode getTarget() {
        return this.target = validate(target);
    }

    public final void setTarget(InsnNode target) {
        this.target = target;
    }

    @Override
    public final boolean isJumpSource() {
        return true;
    }

    int delta;

    public boolean review(IIntMap<InsnNode> pcRev) {
        return (code == GOTO) != ((delta = (pcRev.getInt(target = validate(target)) - pcRev.getInt(this))) == ((short) delta));
    }

    @Override
    public final int nodeSize() {
        return code == GOTO_W ? 5 : 3;
    }

    @Override
    public final void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
        index = list.indexOf(validate(target));
        if(index > 0 && list.get(index - 1).code == WIDE)
            throw new IllegalArgumentException("Jump target must not \"after\" wide instruction");
    }

    public void toByteArray(ConstantPool cw, ByteWriter w) {
        int delta = this.delta;
        if (((short) delta) != delta) {
            w.writeByte(this.code = GOTO_W).writeInt(delta);
        } else {
            w.writeByte(this.code = GOTO).writeShort(delta);
        }
    }

    public final String toString() {
        return super.toString() + " => " + target + "- off " + delta;
    }
}