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

import roj.asm.cst.CstClass;
import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.util.ByteList;

/**
 * Class attribute visitor
 * 默认实现都是As-is的，你需要自己修改
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/4 12:03
 */
public class ClassAttributeVisitor extends AttributeVisitor {
    public ClassAttributeVisitor() {
        super();
    }

    public ClassAttributeVisitor(ClassVisitor cv) {
        super(cv);
    }

    @Override
    public boolean visit(String name, int length) {
        Attribute attr;
        switch (name) {
            case "RuntimeVisibleTypeAnnotations":
            case "RuntimeInvisibleTypeAnnotations":
                attr = new AttrTypeAnnotation(name, br, cp, this);
                return visitKnown(attr);
            case "InnerClasses":
                attr = new AttrInnerClasses(br, cp);
                return visitKnown(attr);
            case "Module":
                attr = new AttrModule(br, cp);
                return visitKnown(attr);
            case "ModulePackages":
                attr = new AttrModulePackages(br, cp);
                return visitKnown(attr);
            case "ModuleMainClass":
                attr = new AttrUTFRef(name, br, cp);
                return visitKnown(attr);
            case "NestHost":
                attr = new AttrUTF(name, br, cp);
                return visitKnown(attr);
            case "NestMembers":
                attr = new AttrStringList(name, br, cp, 1);
                return visitKnown(attr);
            case "RuntimeInvisibleAnnotations":
            case "RuntimeVisibleAnnotations":
                attr = new AttrAnnotation(name, br, cp);
                return visitKnown(attr);
            case "SourceFile":
                attr = new AttrSourceFile(((CstUTF) cp.get(br)).getString());
                return visitKnown(attr);
            case "BootstrapMethods":
                attr = new AttrBootstrapMethods(br, cp);
                return visitKnown(attr);
            case "EnclosingMethod":
                attr = new AttrEnclosingMethod((CstClass) cp.get(br), (CstNameAndType) cp.get(br));
                return visitKnown(attr);
            case "Signature":
                return visitSignature(((CstUTF) cp.get(br)).getString());
            case "Deprecated":
                if(length != 0)
                    throw new IllegalArgumentException("Deprecated.length must be zero");
                return visitKnown(new AttrUnknown("Deprecated", new ByteList.EmptyByteList()));
            case "SourceDebugExtension":
            default:
                return visitUnknown(name, length);
        }
    }
}
