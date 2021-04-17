package roj.asm.struct.attr;

import roj.asm.util.ConstantWriter;
import roj.util.ByteList;
import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/10/21 22:45
 */
public final class AttrUnknown extends Attribute {
    public AttrUnknown(String attrName, ByteList bytes) {
        super(attrName);
        this.data = bytes;
    }

    private ByteList data;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeBytes(data);
    }

    public String toString() {
        return name + ": " + data.toString();
    }

    public ByteList getRawData() {
        return data;
    }

    public void setRawData(ByteList data) {
        this.data = data;
    }
}
