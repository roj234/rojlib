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

import roj.asm.cst.CstClass;
import roj.asm.cst.CstNameAndType;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AttrEnclosingMethod extends Attribute {
    public static final String PREDEFINED = new String("[]");

    public AttrEnclosingMethod(CstClass clazz, CstNameAndType method) {
        super("EnclosingMethod");
        // In particular, method_index must be zero if the current class was immediately enclosed in source code by an instance initializer, static initializer, instance variable initializer, or class variable initializer. 
        this.owner = clazz.getValue().getString();
        if (method == null) {
            this.name = PREDEFINED;
        } else {
            this.name = method.getName().getString();
            this.parameters = ParamHelper.parseMethod(method.getType().getString());
            this.returnType = this.parameters.remove(this.parameters.size() - 1);
        }
    }

    public String owner, name;
    public List<Type> parameters;
    public Type returnType;

    @Override
    protected void toByteArray1(ConstantPool pool, ByteList w) {
        w.putShort(pool.getClassId(this.owner));
        if (PREDEFINED == this.name) {
            w.putShort(0);
        } else {
            this.parameters.add(this.returnType);
            w.putShort(pool.getDescId(this.name, ParamHelper.getMethod(this.parameters)));
            this.parameters.remove(this.parameters.size() - 1);
        }
    }

    public String toString() {
        if (PREDEFINED == this.name)
            return "EnclosingMethod: " + "Immediately";
        final StringBuilder sb = new StringBuilder().append("EnclosingMethod: ").append(returnType).append(' ').append(this.owner).append('.').append(name).append('(');
        for (Type par : parameters) {
            sb.append(par).append(", ");
        }
        if (!parameters.isEmpty())
            sb.delete(sb.length() - 2, sb.length());
        return sb.append(')').toString();
    }
}