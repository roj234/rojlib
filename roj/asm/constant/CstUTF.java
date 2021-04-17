/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantUTF8.java
 */
package roj.asm.constant;

import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.UTFDataFormatException;

import static roj.asm.constant.CstRefUTF.NULL_CHECK;

public class CstUTF extends Constant {
    private String data;

    public CstUTF() {
        super(CstType.UTF);
    }

    public CstUTF(String data) {
        super(CstType.UTF);
        this.data = data;
    }

    public CstUTF(byte[] bytes) {
        super(CstType.UTF);
        setString(bytes);
    }

    public String getString() {
        return this.data;
    }

    public byte[] getBytes() {
        return ByteWriter.encodeUTF(data).getByteArray();
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

    // Notice that this method can only be used in parseConstants or reusePool == true
    public void setString(byte[] bytes) {
        if (bytes == null) {
            System.err.println("Notice that null byte will be \"\"");
        }
        try {
            this.data = bytes == null ? "" : ByteReader.readUTF(new ByteList(bytes));
        } catch (UTFDataFormatException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void write0(ByteWriter w) {
        byte[] bytes = getBytes();
        w.writeShort(bytes.length);
        w.writeBytes(bytes);
    }

    public String toString() {
        return super.toString() + ' ' + data;
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