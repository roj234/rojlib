/*
 * This file is a part of MoreItems
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
package roj.asm.visitor;

import roj.asm.cst.CstUTF;
import roj.asm.util.ConstantPool;
import roj.asm.util.ConstantWriter;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/16 18:12
 */
public abstract class IVisitor {
    protected ByteWriter     bw;
    protected ConstantWriter cw;
    protected ByteReader     br;

    protected int amountIndex, amount;
    protected int attrAmountIndex, attrAmount;

    public void visit(ByteReader br, ConstantPool cp, ByteWriter bw, ConstantWriter cw) {
        this.br = br;
        this.bw = bw;
        this.cw = cw;

        int len = br.readUnsignedShort();
        visitNodes(len);
        for (int i = 0; i < len; i++) {
            int access = br.readUnsignedShort();
            String name = ((CstUTF) cp.get(br)).getString();
            String desc = ((CstUTF) cp.get(br)).getString();
            int attr = br.readUnsignedShort();
            visitNode(access, name, desc, attr);

            for (int j = 0; j < attr; j++) {
                String name0 = ((CstUTF) cp.get(br)).getString();
                int len1 = br.readInt();
                visitAttribute(name0, len1);
            }

            visitEndNode();
        }
        visitEndNodes();
    }


    public void visitNodes(int count) {
        amountIndex = bw.list.pos();
        amount = 0;
        bw.writeShort(0);
    }

    public void visitNode(int acc, String name, String desc, int count) {
        bw.writeShort(acc).writeShort(cw.getUtfId(name)).writeShort(cw.getUtfId(desc));
        attrAmountIndex = bw.list.pos();
        attrAmount = 0;
        bw.writeShort(0);
    }

    public void visitAttribute(String name, int length) {
        bw.writeShort(cw.getUtfId(name)).writeInt(length).writeBytes(br.getBytes().list, br.index, length);
        br.index += length;
        attrAmount++;
    }

    public void visitEndNode() {
        int pos = bw.list.pos();
        bw.list.pos(attrAmountIndex);
        bw.writeShort(attrAmount);
        bw.list.pos(pos);
        amount++;
    }

    public void visitEndNodes() {
        int pos = bw.list.pos();
        bw.list.pos(amountIndex);
        bw.writeShort(amount);
        bw.list.pos(pos);
    }

    public void visitEndError(Throwable e) {
        bw = null;
        cw = null;
        br = null;
        Helpers.throwAny(e);
    }
}
