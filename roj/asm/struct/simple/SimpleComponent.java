package roj.asm.struct.simple;

import roj.asm.constant.CstUTF;
import roj.asm.struct.attr.Attribute;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.ConstantWriter;
import roj.asm.util.FlagList;
import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: SimpleComponent.java
 */
public abstract class SimpleComponent implements IConstantSerializable {
    SimpleComponent(int accesses, CstUTF name, CstUTF type) {
        this.accesses = AccessFlag.parse((short) accesses);
        this.name = name;
        this.type = type;
    }

    public CstUTF name, type;
    public FlagList accesses;

    public String name() {
        return name.getString();
    }

    public String rawDesc() {
        return type.getString();
    }

    public final AttributeList attributes = new AttributeList();

    public void toByteArray(ConstantWriter pool, ByteWriter w) {
        w.writeShort(accesses.flag).writeShort(pool.reset(name).getIndex()).writeShort(pool.reset(type).getIndex()).writeShort(attributes.size());
        for (Attribute attr : attributes) {
            attr.toByteArray(pool, w);
        }
    }

    public Attribute attrByName(String name) {
        return (Attribute) attributes.getByName(name);
    }

    @Override
    public String toString() {
        return name.getString() + ' ' + type.getString();
    }
}
