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
import roj.asm.util.FlagList;

public class MtDesc {
    public String owner, name, param;
    public FlagList flags;

    public MtDesc(String owner, String name, String param) {
        this.owner = owner;
        this.name = name;
        this.param = param;
    }

    public MtDesc(String owner, String name, String param, FlagList flags) {
        this.owner = owner;
        this.name = name;
        this.param = param;
        this.flags = flags;
    }

    @Override
    public String toString() {
        return "MD{" + owner + '.' + name + ' ' + param + ", flags=" + flags + '}';
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof MtDesc))
            return false;
        MtDesc other = (MtDesc)o;
        return this.param.equals(other.param) && other.owner.equals(this.owner) && other.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() ^ param.hashCode() ^ name.hashCode();
    }

    public MtDesc read(CstRef ref) {
        this.owner = ref.getClassName();
        CstNameAndType a = ref.desc();
        this.name = a.getName().getString();
        this.param = a.getType().getString();
        return this;
    }

    public MtDesc copy() {
        return new MtDesc(owner, name, param, flags);
    }
}