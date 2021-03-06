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
 * As-is Attribute Visitor !! Require NOT clearConstant mode
 *
 * @author solo6975
 * @since 2021/10/4 12:35
 */
public class AsIsAttributeVisitor extends AttributeVisitor {
    public AsIsAttributeVisitor() {
        super();
    }

    public AsIsAttributeVisitor(ClassVisitor cv) {
        super(cv);
    }

    @Override
    public boolean visit(String name, int length) {
        ByteList bb = br.bytes;
        bw.putShort(cw.getUtfId(name)).putInt(length).put(bb.list, br.rIndex + bb.arrayOffset(), length);
        return true;
    }
}
