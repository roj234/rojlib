/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: Attribute.java
 */
package roj.asm.struct.attr;

import roj.asm.util.ConstantWriter;
import roj.util.ByteList;
import roj.util.ByteWriter;

public abstract class Attribute {
    protected Attribute(String name) {
        this.name = name;
    }

    public final String name;

    public final ByteWriter toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(name)).writeInt(-1);
        ByteList list = w.list;

        int lenIdx = list.pos();
        toByteArray1(pool, w);
        int cp = list.pos();

        list.pos(lenIdx - 4);
        w.writeInt(cp - lenIdx);
        list.pos(cp);

        return w;
    }

    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        throw new InternalError("Subclasses should rewrite this: " + this.getClass().getName());
    }

    public String toString() {
        throw new InternalError("Subclasses should rewrite this: " + this.getClass().getName());
    }

    public ByteList getRawData() {
        return null;
    }

    public void setRawData(ByteList data) {
        throw new UnsupportedOperationException();
    }
}