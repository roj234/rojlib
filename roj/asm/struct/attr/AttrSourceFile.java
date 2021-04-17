/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrSourceFile.java
 */
package roj.asm.struct.attr;

import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

public final class AttrSourceFile extends Attribute {
    public static final String NAME = "SourceFile";

    public AttrSourceFile(String s) {
        super(NAME);
        this.file = s;
    }

    public String file;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getUtfId(this.file));
    }

    public String toString() {
        return "SourceFile: " + this.file;
    }
}