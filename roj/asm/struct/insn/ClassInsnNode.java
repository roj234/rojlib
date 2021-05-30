/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ClassInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstClass;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

// new / instanceof
public final class ClassInsnNode extends InsnNode implements IClassInsnNode {
    public ClassInsnNode(byte code) {
        super(code);
    }

    public ClassInsnNode(byte code, String name) {
        super(code);
        this.name = name;
    }

    public ClassInsnNode(byte code, CstClass clazz) {
        super(code);
        this.name = clazz.getValue().getString();
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case Opcodes.NEW:
            case Opcodes.CHECKCAST:
            case Opcodes.INSTANCEOF:
            case Opcodes.ANEWARRAY:
                return true;
            default:
                return false;
        }
    }

    public String name;

    public String owner() {
        return name;
    }

    @Override
    public void owner(String clazz) {
        this.name = clazz;
    }

    private int cid;

    @Override
    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(cid);
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        super.toByteArray(w);

        if(code == Opcodes.NEW) {
            if(name.startsWith("[")) {
                throw new IllegalArgumentException("The new instruction cannot be used to create an array.");
            }
        }

        w.writeShort(this.cid = pool.getClassId(name));
    }

    @Override
    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {

    }

    public String toString() {
        return Opcodes.toString0(code, name.substring(name.lastIndexOf('/') + 1));
    }
}