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

import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class AnnValEnum extends AnnVal {
    public AnnValEnum(String type, String value) {
        // 当你已知这不可能是基本类型...
        this.clazz = type.substring(1, type.length() - 1);
        this.value = value;
    }

    public String clazz, value;

    @Override
    public AnnValEnum asEnum() {
        return this;
    }

    public void toByteArray(ConstantPool pool, ByteList w) {
        w.put((byte) ENUM)
         .putShort(pool.getUtfId("L" + this.clazz + ';'))
         .putShort(pool.getUtfId(value));
    }

    public String toString() {
        return String.valueOf(clazz) + '.' + value;
    }

    @Override
    public byte type() {
        return ENUM;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnValEnum anEnum = (AnnValEnum) o;

        if (!clazz.equals(anEnum.clazz)) return false;
        return value.equals(anEnum.value);
    }

    @Override
    public int hashCode() {
        int result = clazz.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }
}