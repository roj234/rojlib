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

package roj.asm.cst;

import roj.util.ByteWriter;
/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/29 17:16
 */
public final class CstDynamic extends Constant {
    public int bootstrapTableIndex;
    private int descIndex;
    private final boolean method;

    private CstNameAndType desc;

    public CstDynamic(boolean method, int tableIndex, int descIndex) {
        this.method = method;
        this.bootstrapTableIndex = tableIndex;
        this.descIndex = descIndex;
    }

    public final void setDesc(CstNameAndType desc) {
        if (desc == null)
            throw new NullPointerException("desc");
        this.desc = desc;
        this.descIndex = desc.getIndex();
    }

    @Override
    public byte type() {
        return method ? CstType.INVOKE_DYNAMIC : CstType.DYNAMIC;
    }

    @Override
    protected final void write0(ByteWriter w) {
        w.writeShort(bootstrapTableIndex);
        w.writeShort(getDescIndex());
    }

    public final String toString() {
        return super.toString() + "BTableIdx: #" + bootstrapTableIndex + ", //" + desc + "]";
    }

    public final CstNameAndType getDesc() {
        return desc;
    }

    public final int getDescIndex() {
        return desc == null ? descIndex : desc.getIndex();
    }

    public final int hashCode() {
        return (desc.hashCode() << 16 ^ bootstrapTableIndex) + type();
    }

    public final boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null || o.getClass() != getClass())
            return false;
        CstDynamic ref = (CstDynamic) o;
        return ref.type() == this.type() && ref.bootstrapTableIndex == this.bootstrapTableIndex && ref.getDescIndex() == this.getDescIndex();
    }
}