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
import roj.asm.Opcodes;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;
import roj.util.Helpers;

/**
 * QUESTION: native.hashCode好慢？
 */
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/27 1:12
 */
public abstract class InsnNode implements Helpers.Node {
    public static final int T_OTHER      = 0;
    public static final int T_LOAD_STORE = 1;
    public static final int T_CLASS      = 2;
    public static final int T_FIELD    = 3;
    public static final int T_INVOKE = 4;
    public static final int T_INVOKE_DYNAMIC = 5;
    public static final int T_GOTO_IF        = 6;
    public static final int T_LABEL        = 7;
    public static final int T_LDC   = 8;
    public static final int T_IINC  = 9;
    public static final int T_SWITCH = 10;
    public static final int T_MULTIANEWARRAY = 11;

    protected InsnNode(byte code) {
        setOpcode(code);
    }

    public byte code;

    public void setOpcode(byte code) {
        byte o = this.code;
        this.code = code;
        if (!validate()) {
            this.code = o;
            throw new IllegalArgumentException("Unsupported opcode 0x" + Integer.toHexString(code & 0xFF));
        }
    }

    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {}

    public final Helpers.Node next() {
        return next;
    }

    /**
     * 保证这是一个连接在表内的节点
     */
    public static InsnNode validate(InsnNode node) {
        int i = 0;
        while (node.next != null) {
            node = node.next;

            if(i++ > 10) {
                if(Helpers.hasCircle(node)) {
                    throw new IllegalStateException("Circular reference: " + node);
                } else {
                    i = Integer.MIN_VALUE;
                }
            }
        }
        return node;
    }

    protected InsnNode next = null;

    public boolean isJumpSource() {
        return false;
    }

    /**
     * 替换
     */
    @Internal
    public void _i_replace(InsnNode now) {
        if(now != this)
            this.next = now;
    }

    public final byte getOpcode() {
        return code;
    }

    public final int getOpcodeInt() {
        return code & 0xFF;
    }

    protected boolean validate() {
        return true;
    }

    public int nodeType() {
        return T_OTHER;
    }

    public abstract void toByteArray(ConstantWriter cw, ByteWriter w);

    public int nodeSize() { return -1; }

    public String toString() {
        return Opcodes.toString0(code);
    }
}