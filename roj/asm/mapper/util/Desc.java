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
package roj.asm.mapper.util;

import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstRef;
import roj.asm.tree.MoFNode;
import roj.asm.util.ConstantPool;
import roj.asm.util.FlagList;
import roj.util.ByteWriter;

/**
 * 对象描述符
 *
 * @author Roj233
 * @version 2.8
 * @since ?
 */
public final class Desc implements MoFNode {
    public String owner, name, param;
    public FlagList flags;

    // As a Wildcard Field Descriptor
    public Desc(String owner, String name) {
        this.owner = owner;
        this.name = name;
        this.param = "";
    }

    public Desc(String owner, String name, String param) {
        this.owner = owner;
        this.name = name;
        this.param = param;
    }

    public Desc(String owner, String name, String param, FlagList flags) {
        this.owner = owner;
        this.name = name;
        this.param = param;
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "{" + owner + '.' + name + ' ' + param + '}';
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof Desc))
            return false;
        Desc other = (Desc)o;
        return this.param.equals(other.param) && other.owner.equals(this.owner) && other.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() ^ param.hashCode() ^ name.hashCode();
    }

    public final Desc read(CstRef ref) {
        this.owner = ref.getClassName();
        CstNameAndType a = ref.desc();
        this.name = a.getName().getString();
        this.param = a.getType().getString();
        return this;
    }

    public final Desc copy() {
        return new Desc(owner, name, param, flags);
    }

    @Override
    public void toByteArray(ConstantPool pool, ByteWriter w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String rawDesc() {
        return param;
    }

    @Override
    public FlagList accessFlag() {
        return flags;
    }

    @Override
    public int type() {
        return 5;
    }
}