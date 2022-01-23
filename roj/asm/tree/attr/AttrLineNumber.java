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

package roj.asm.tree.attr;

import roj.asm.tree.insn.InsnNode;
import roj.asm.util.ConstantPool;
import roj.collect.IntMap;
import roj.collect.ToIntMap;
import roj.util.ByteList;
import roj.util.ByteReader;

import static roj.asm.tree.insn.InsnNode.validate;

/**
 * @author Roj234
 * @since 2021/4/30 19:27
 */
public final class AttrLineNumber extends Attribute implements ICodeAttribute {
    public final ToIntMap<InsnNode> map;

    public AttrLineNumber() {
        super("LineNumberTable");
        this.map = new ToIntMap<>();
    }

    public AttrLineNumber(ByteReader r, IntMap<InsnNode> pcCounter) {
        super("LineNumberTable");

        final int tableLen = r.readUnsignedShort();

        ToIntMap<InsnNode> map = this.map = new ToIntMap<>(tableLen);
        for (int i = 0; i < tableLen; i++) {
            int index = r.readUnsignedShort();
            InsnNode node = pcCounter.get(index);
            if (node == null)
                throw new NullPointerException("Couldn't found bytecode offset for line number table: " + index);
            map.putInt(node, r.readUnsignedShort());
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("LineNumberTable:   Node <=======> Line\n");
        for (ToIntMap.Entry<InsnNode> entry : map.selfEntrySet()) {
            sb.append("                  ").append(entry.getKey()).append(" = ").append(entry.getInt()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void toByteArray(ConstantPool pool, ByteList w, ToIntMap<InsnNode> pcRev) {
        w.putShort(map.size());
        for (ToIntMap.Entry<InsnNode> entry : map.selfEntrySet()) {
            w.putShort(pcRev.getInt(validate(entry.getKey())))
                    .putShort(entry.getInt());
        }
    }
}