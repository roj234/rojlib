/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GetSetFieldInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.constant.CstRefField;
import roj.asm.struct.Clazz;
import roj.asm.struct.Field;
import roj.asm.util.ConstantWriter;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

//getstatic
//putstatic
//getfield
//putfield
public final class FieldInsnNode extends InsnNode implements IClassInsnNode {
    public FieldInsnNode(byte code) {
        super(code);
    }

    public FieldInsnNode(byte code, String owner, String name, Type type) {
        super(code);

        this.owner = owner;
        this.name = name;
        this.type = type;
    }

    // net/xxx/abc.DEF:LXXXX javap
    public FieldInsnNode(byte code, String desc) {
        super(code);
        int index = desc.indexOf(".");
        String tmp = desc.substring(index + 1);
        int index2 = tmp.indexOf(":");

        this.owner = desc.substring(0, index);
        this.name = tmp.substring(0, index2);
        if (name.contains("\"")) {
            name = name.substring(1, name.length() - 1);
        }

        this.type = ParamHelper.parseField(tmp.substring(index2 + 1));
    }

    public FieldInsnNode(byte code, CstRefField ref) {
        super(code);
        this.owner = ref.getClassName();
        this.name = ref.desc().getName().getString();
        String s = ref.desc().getType().getString();
        this.type = ParamHelper.parseField(s);
    }

    public String owner, name;
    public Type type;

    public FieldInsnNode(byte code, Clazz clazz, int i) {
        super(code);
        Field field = clazz.fields.get(i);
        this.owner = clazz.name;
        this.name = field.name;
        this.type = field.type;
    }

    @Override
    public void owner(String clazz) {
        this.owner = clazz;
    }

    public String owner() {
        return owner;
    }

    private int fid;

    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(this.fid);
    }

    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(this.fid = pool.getFieldRefId(owner, name, ParamHelper.getField(type)));
    }

    @Override
    protected boolean validate() {
        switch (getOpcode()) {
            case GETFIELD:
            case GETSTATIC:
            case PUTFIELD:
            case PUTSTATIC:
                return true;
        }
        return false;
    }

    public String toString() {
        return "#" + (int)bci + ' ' + Opcodes.toString0(code, type, owner.substring(owner.lastIndexOf('/') + 1), name);
    }
}