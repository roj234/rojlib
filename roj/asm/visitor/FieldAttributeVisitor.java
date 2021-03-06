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
import roj.asm.tree.attr.*;

/**
 * Field attribute visitor
 * 默认实现都是As-is的，你需要自己修改
 *
 * @author solo6975
 * @since 2021/10/4 12:03
 */
public class FieldAttributeVisitor extends AttributeVisitor {
    public FieldAttributeVisitor() {
        super();
    }

    public FieldAttributeVisitor(ClassVisitor cv) {
        super(cv);
    }

    @Override
    public boolean visit(String name, int length) {
        Attribute attr;
        switch (name) {
            case "RuntimeVisibleTypeAnnotations":
            case "RuntimeInvisibleTypeAnnotations":
                return visitKnown(new AttrTypeAnnotation(name, br, cp, this));
            case "Signature":
                return visitSignature(((CstUTF) cp.get(br)).getString());
            case "RuntimeVisibleAnnotations":
            case "RuntimeInvisibleAnnotations":
                return visitKnown(new AttrAnnotation(name, br, cp));
            case "ConstantValue":
                return visitKnown(new AttrConstantValue(cp.get(br)));
            case "Synthetic":
            case "Deprecated":
                if(length != 0)
                    throw new IllegalArgumentException("Length must be zero");
                return visitKnown(new AttrUnknown(name, br.slice(length)));
            default:
                return visitUnknown(name, length);
        }
    }
}
