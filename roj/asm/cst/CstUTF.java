/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantUTF8.java
 */
package roj.asm.cst;

import roj.util.ByteWriter;

import static roj.asm.cst.CstRefUTF.NULL_CHECK;

public class CstUTF extends Constant {
    private String data;

    public CstUTF() {}

    public CstUTF(String data) {
        this.data = data;
    }

    public String getString() {
        return this.data;
    }

    public void setString(String s) {
        if (s == null) {
            if (NULL_CHECK) {
                throw new NullPointerException("string");
            } else {
                System.err.println("Warning: String is null but ignored");
                this.data = "";
                return;
            }
        }
        this.data = s;
    }

    @Override
    protected void write0(ByteWriter w) {
        w.writeJavaUTF(data);
    }

    public String toString() {
        return super.toString() + ' ' + data;
    }

    @Override
    public byte type() {
        return CstType.UTF;
    }

    public int hashCode() {
        return data == null ? 0 : data.hashCode();
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof CstUTF))
            return false;
        CstUTF ref = (CstUTF) o;
        return this.data.equals(ref.data);
    }
}