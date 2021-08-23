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

import roj.util.ByteList;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/8/16 18:11
 */
public class AsIsVisitor extends IVisitor {
    public void visitNodes(int count) {
        bw.writeShort(count);
    }

    public void visitNode(int acc, String name, String desc, int count) {
        bw.writeShort(acc).writeShort(cw.getUtfId(name)).writeShort(cw.getUtfId(desc)).writeShort(count);
    }

    public void visitAttribute(String name, ByteList data) {
        bw.writeShort(cw.getUtfId(name)).writeInt(data.pos()).writeBytes(data);
    }

    public void visitEndNode() {}

    public void visitEndNodes() {}
}
