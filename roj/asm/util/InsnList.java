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

import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.collect.IntBiMap;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.util.ArrayList;

import static roj.asm.tree.attr.AttrCode.METHOD_END_MARK;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/24 23:21
 */
public final class InsnList extends ArrayList<InsnNode> {
    static final long serialVersionUID = 0L;

    ArrayList<InsnNode> labels = new ArrayList<>();

    @Override
    public boolean add(InsnNode node) {
        if(node instanceof LabelInsnNode) {
            labels.add(node);
            return false;
        } else {
            for (int i = 0; i < labels.size(); i++) {
                labels.get(i)._i_replace(node);
            }
            labels.clear();
            return super.add(node);
        }
    }

    public InsnNode set(int pos, InsnNode node) {
        InsnNode node1 = get(pos);
        node1._i_replace(node);
        return super.set(pos, node);
    }

    public InsnNode remove(int pos) {
        InsnNode node = super.remove(pos);
        node._i_replace(pos != size() ? get(pos) : METHOD_END_MARK);
        return node;
    }

    public IntBiMap<InsnNode> getPCMap() {
        final InsnList insn = this;
        ConstantWriter pool = new ConstantWriter();

        InsnNode last = insn.remove(insn.size() - 1);
        if (last != METHOD_END_MARK)
            throw new IllegalArgumentException("End must be a METHOD_END_MARK");

        ByteWriter w = new ByteWriter(new ByteList.EmptyByteList());
        IntBiMap<InsnNode> pcRev = new IntBiMap<>(size());

        InsnNode node;
        pcRev.putByValue(0, node = insn.get(0));

        int reccIdx = -1, reccPos = 0;
        int i, j = 0;

        do {
            i = reccIdx + 1;
            reccIdx = -1;
            w.list.pos(reccPos);

            for (int e = insn.size() - 1; i < e; i++) {
                pcRev.putByValue(w.list.pos(), node);
                node = insn.get(i);

                // 简化的终止条件: 此轮node return false并且长度不变
                if (node.handlePCRev(pcRev) && reccIdx == -1) {
                    reccIdx = i - 1;
                    reccPos = w.list.pos();
                }

                node.preToByteArray(pool, w);
            }

            if(j++ > 5) {
                throw new IllegalArgumentException("Unable to correct bytecode order");
            }
        } while (reccIdx != -1 && insn.get(reccIdx + 1).handlePCRev(pcRev));

        pcRev.putByValue(pcRev.getByValue(insn.get(insn.size() - 1)) + 1, METHOD_END_MARK);

        add(METHOD_END_MARK);

        return pcRev;
    }
}
