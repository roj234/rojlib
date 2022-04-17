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

import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.collect.IntBiMap;
import roj.collect.SimpleList;

import java.util.ArrayList;

/**
 * @author Roj234
 * @since 2021/5/24 23:21
 */
public final class InsnList extends SimpleList<InsnNode> {
    static final long serialVersionUID = 0L;

    ArrayList<InsnNode> labels = new ArrayList<>();

    @Override
    public boolean add(InsnNode node) {
        if(node instanceof LabelInsnNode) {
            labels.add(node);
            return true;
        } else {
            for (int i = 0; i < labels.size(); i++) {
                labels.get(i)._i_replace(node);
            }
            labels.clear();
            return super.add(node);
        }
    }

    @Override
    public void add(int i, InsnNode node) {
        if(node instanceof LabelInsnNode) {
            throw new IllegalArgumentException("Label insert not rewindable");
        } else {
            if (i == size) {
                for (int j = 0; j < labels.size(); j++) {
                    labels.get(j)._i_replace(node);
                }
                labels.clear();
            }
            super.add(i, node);
        }
    }

    public IntBiMap<InsnNode> getPCMap() {
        IntBiMap<InsnNode> rev = new IntBiMap<>();
        AttrCode.reIndex(this, new ConstantPoolEmpty(), rev);
        return rev;
    }

    public void removeRange(int fromIndex, int toIndex) {
        super.removeRange(fromIndex, toIndex);
    }
}
