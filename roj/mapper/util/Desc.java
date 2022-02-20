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
package roj.mapper.util;

import roj.asm.cst.CstNameAndType;
import roj.asm.cst.CstRef;
import roj.asm.tree.MoFNode;
import roj.asm.util.AccessFlag;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * 对象描述符
 *
 * @author Roj233
 * @version 2.8
 * @since ?
 */
public class Desc implements MoFNode {
    public static final char NULL_FLAG = AccessFlag.PUBLIC | AccessFlag.PRIVATE;

    public String owner, name, param;
    public char flags;

    public Desc() {
        owner = name = param = "";
        flags = NULL_FLAG;
    }

    // As a Wildcard Field Descriptor
    public Desc(String owner, String name) {
        this.owner = owner;
        this.name = name;
        this.param = "";
        this.flags = NULL_FLAG;
    }

    public Desc(String owner, String name, String param) {
        this.owner = owner;
        this.name = name;
        this.param = param;
        this.flags = NULL_FLAG;
    }

    public Desc(String owner, String name, String param, int flags) {
        this.owner = owner;
        this.name = name;
        this.param = param;
        this.flags = (char) flags;
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
    public final void toByteArray(ConstantPool pool, ByteList w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final String rawDesc() {
        return param;
    }

    @Override
    public void accessFlag(int flag) {
        this.flags = (char) flag;
    }

    @Override
    public final char accessFlag() {
        return flags;
    }

    @Override
    public final int type() {
        return 5;
    }
}