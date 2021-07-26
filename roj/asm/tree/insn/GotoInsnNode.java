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
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

import java.util.function.ToIntFunction;

import static roj.asm.Opcodes.*;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/24 23:21
 */
public class GotoInsnNode extends InsnNode implements IJumpInsnNode {
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

    public InsnNode target;

    @Override
    public InsnNode getTarget() {
        return this.target = validate(target);
    }

    @Override
    public void setTarget(InsnNode target) {
        this.target = target;
    }

    int delta;

    private int cache_id_4_verify;

    @Override
    public boolean handlePCRev(ToIntFunction<InsnNode> pcRev) {
        int i = pcRev.applyAsInt(target = validate(target));

        cache_id_4_verify = i;

        if(i == -1) {
            delta = 0;
            return true;
        }

        int od = delta;
        int nd = delta = (i - pcRev.applyAsInt(this));
        return ( ((short) od) == od ) != ( ((short) nd) == nd );
    }

    @Override
    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
        if(cache_id_4_verify > 0 && list.get(cache_id_4_verify - 1).code == WIDE)
            throw new IllegalArgumentException("Jump target must not \"after\" wide instruction");
    }

    public void toByteArray(ByteWriter w) {
        int delta = this.delta;

        byte code = this.code;
        if (((short) delta) != delta && code == Opcodes.GOTO) {
            code = Opcodes.GOTO_W;
        } else if (code == Opcodes.GOTO_W) {
            code = Opcodes.GOTO;
        }

        w.writeByte(code);

        if (((short) delta) != delta) {
            w.writeInt(delta);
        } else {
            w.writeShort(delta);
        }
    }

    public String toString() {
        return super.toString() + " => " + target;
    }
}