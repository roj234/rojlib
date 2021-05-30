/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: MDArrayInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.cst.CstClass;
import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

import java.util.Objects;

public final class MDArrayInsnNode extends InsnNode implements IIndexInsnNode, IClassInsnNode {
    public MDArrayInsnNode() {
        super(Opcodes.MULTIANEWARRAY);
    }

    public MDArrayInsnNode(CstClass clazz, int length) {
        super(Opcodes.MULTIANEWARRAY);
        this.name = clazz.getValue().getString();
        this.length = length;
    }

    private String name;

    public int length;

    public int getIndex() {
        return length;
    }

    @Override
    protected boolean validate() {
        return code == Opcodes.MULTIANEWARRAY;
    }

    @Override
    public void owner(String clazz) {
        this.name = Objects.requireNonNull(clazz, "className");
    }

    public String owner() {
        return name;
    }

    private int cid;

    @Override
    public void toByteArray(ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(cid)
                .writeByte((byte) this.length);
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        super.toByteArray(w);
        w.writeShort(this.cid = pool.getClassId(name))
                .writeByte((byte) this.length);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString()).append(' ').append(name);
        //for (int i = 0; i < length; i++) {
        //    sb.append("[]");
        //}
        return sb.toString();
    }
}