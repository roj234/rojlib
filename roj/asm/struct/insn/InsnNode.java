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

package roj.asm.struct.insn;

import roj.annotation.Internal;
import roj.asm.Opcodes;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.util.function.ToIntFunction;

/**
 * QUESTION: native.hashCode好慢？
 *//**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/27 1:12
 */
public abstract class InsnNode {
    protected InsnNode(byte code) {
        setOpcode(code);
    }

    public byte code;

    public void setOpcode(byte code) {
        this.code = code;
        if (!validate()) {
            throw new IllegalArgumentException("Unsupported opcode " + Integer.toHexString(this.code & 0xFF));
        }
    }

    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {}

    /**
     * 保证这是一个连接在表内的节点
     */
    public static InsnNode validate(InsnNode node) {
        int i = 0;
        while (node.next != null) {
            node = node.next;

            if(i++ > 100) {
                System.err.println(node);
                if(i > 120)
                    throw new StackOverflowError("Node circular reference, dumped upper: " + node);
            }
        }
        return node;
    }

    protected InsnNode next = null;

    public boolean isJumpSource() {
        return this instanceof IJumpInsnNode || getClass() == SwitchInsnNode.class;
    }

    /**
     * 替换
     */
    @Internal
    public void onReplace(InsnNode now) {
        if(now != this)
            this.next = now;
    }

    /**
     * 移除
     */
    @Internal
    public void onRemove(InsnList insnList, int pos) {
        if(next == null && insnList.size() > pos)
            next = insnList.get(pos);
    }

    public final byte getOpcode() {
        return code;
    }

    protected boolean validate() {
        return true;
    }

    @Internal
    public void toByteArray(ByteWriter w) {
        w.writeByte(code);
    }

    /**
     * 在toBytearray之前调用，刷新index
     *
     * @param pool 常量池
     * @param w    只写的ByteWriter (由{@link ByteList.EmptyByteList}构造)
     */
    @Internal
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        toByteArray(w);
    }

    public String toString() {
        return Opcodes.toString0(code);
    }

    public boolean handlePCRev(ToIntFunction<InsnNode> pcRev) {
        return false;
    }
}