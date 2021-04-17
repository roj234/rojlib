package roj.asm.util;

import roj.asm.struct.insn.InsnNode;
import roj.collect.IntBiMap;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.util.ArrayList;

import static roj.asm.struct.attr.AttrCode.METHOD_END_MARK;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: InsnList.java
 */
public final class InsnList extends ArrayList<InsnNode> {
    public static final long serialVersionUID = 20L;

    public boolean add(InsnNode node) {
        return super.add(node);
    }

    public InsnNode set(int pos, InsnNode node) {
        InsnNode node1 = get(pos);
        node1.onReplace(node);
        return super.set(pos, node);
    }

    public InsnNode remove(int pos) {
        InsnNode node = super.remove(pos);
        node.onRemove(this, pos);
        return node;
    }

    public IntBiMap<InsnNode> getPCMap() {
        final InsnList insn = this;
        ConstantWriter pool = new ConstantWriter();

        InsnNode last = insn.remove(insn.size() - 1);
        if (last != METHOD_END_MARK)
            throw new IllegalArgumentException("Endpoint must be METHOD_END_MARK");

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
                node.setBci(w.list.pos());
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
