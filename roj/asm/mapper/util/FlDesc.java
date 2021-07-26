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

import roj.asm.cst.CstRef;
import roj.asm.util.FlagList;

public final class FlDesc extends KEntry {
    public FlDesc(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    public FlDesc(String owner, String name, FlagList flags) {
        this.owner = owner;
        this.name = name;
        this.flags = flags;
    }

    public boolean equals(Object o) {
        if(!(o instanceof FlDesc))
            return false;
        FlDesc other = (FlDesc)o;
        return this.name.equals(other.name) && other.owner.equals(this.owner);
    }

    @Override
    public int hashCode() {
        return owner.hashCode() ^ name.hashCode();
    }

    @Override
    public String toString() {
        return "FD{" + owner + '.' + name + '}';
    }

    public FlDesc read(CstRef ref) {
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        return this;
    }

    public FlDesc copy() {
        return new FlDesc(owner, name, flags);
    }
}