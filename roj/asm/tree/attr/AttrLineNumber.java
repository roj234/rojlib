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
import roj.util.ByteList;
import roj.util.ByteReader;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static roj.asm.tree.insn.InsnNode.validate;

/**
 * @author Roj234
 * @since 2021/4/30 19:27
 */
public final class AttrLineNumber extends Attribute {
    public final List<LineNumber> list;

    public AttrLineNumber() {
        super("LineNumberTable");
        this.list = new ArrayList<>();
    }

    public AttrLineNumber(ByteReader r, IntMap<InsnNode> pcCounter) {
        super("LineNumberTable");

        final int tableLen = r.readUnsignedShort();

        List<LineNumber> list = this.list = new ArrayList<>(tableLen);
        for (int i = 0; i < tableLen; i++) {
            int index = r.readUnsignedShort();
            InsnNode node = pcCounter.get(index);
            if (node == null)
                throw new NullPointerException("Couldn't found bytecode offset for line number table: " + index);
            list.add(new LineNumber(node, r.readUnsignedShort()));
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("LineNumberTable:   Node <=======> Line\n");
        for (int i = 0; i < list.size(); i++) {
            LineNumber ln = list.get(i);
            sb.append("                  ").append(ln.node)
              .append(" = ").append(ln.line).append('\n');
        }
        return sb.toString();
    }

    @Override
    void toByteArray1(ConstantPool pool, ByteList w) {
        w.putShort(list.size());
        if (list.isEmpty()) return;
        list.sort(list.get(0));
        for (int i = 0; i < list.size(); i++) {
            LineNumber ln = list.get(i);
            w.putShort((ln.node = validate(ln.node)).bci)
             .putShort(ln.line);
        }
    }

    public static final class LineNumber implements Comparable<LineNumber>, Comparator<LineNumber> {
        public InsnNode node;
        char line;

        public LineNumber(InsnNode node, int i) {
            this.node = node;
            this.line = (char) i;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = (char) line;
        }

        @Override
        public int compareTo(@Nonnull LineNumber o) {
            return Integer.compare(node.bci, o.node.bci);
        }

        @Override
        public int compare(LineNumber o1, LineNumber o2) {
            return Integer.compare(o1.node.bci, o2.node.bci);
        }
    }
}