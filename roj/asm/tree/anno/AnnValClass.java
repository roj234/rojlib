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

package roj.asm.tree.anno;

import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AnnValClass extends AnnVal {
    public AnnValClass(String className) {
        this.value = ParamHelper.parseField(className);
    }

    public Type value;

    @Override
    public Type asClass() {
        return value;
    }

    public void toByteArray(ConstantPool pool, ByteList w) {
        w.put((byte) CLASS).putShort(pool.getUtfId(ParamHelper.getField(value)));
    }

    public String toString() {
        return value.toString() + ".class";
    }

    @Override
    public byte type() {
        return CLASS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnValClass aClass = (AnnValClass) o;

        return value.equals(aClass.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}