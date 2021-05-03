/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: InsnNode.java
 */
package roj.asm.struct.insn;

import roj.annotation.Internal;
import roj.asm.Opcodes;
import roj.asm.util.ConstantWriter;
import roj.asm.util.InsnList;
import roj.util.ByteList;
import roj.util.ByteWriter;

import java.util.function.ToIntFunction;

public abstract class InsnNode {
    protected InsnNode(byte code) {
        setOpcode(code);
    }

    public byte code;

    @Deprecated
    protected char bci;

    public void setOpcode(byte code) {
        this.code = code;
        if (!validate()) {
            throw new IllegalArgumentException("Unsupported opcode " + Integer.toHexString(this.code & 0xFF));
        }
    }

    //@Deprecated
    public void setBci(int index) {
        this.bci = (char) index;
    }

    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {}

    /**
     * 保证这是一个连接在表内的节点
     */
    public static InsnNode validate(InsnNode node) {
        int i = 0;
        while (node.next != null) {
            node = node.next;

            if(i++ > 100) {
                System.err.println(node);
                if(i > 120)
                    throw new StackOverflowError("Node circular reference, dumped upper: " + node);
            }
        }
        return node;
    }

    InsnNode next = null;

    public boolean isJumpSource() {
        return this instanceof IJumpInsnNode || getClass() == SwitchInsnNode.class;
    }

    /**
     * 替换
     */
    @Internal
    public void onReplace(InsnNode now) {
        if(now != this)
            this.next = now;
    }

    /**
     * 移除
     */
    @Internal
    public void onRemove(InsnList insnList, int pos) {
        if(next == null && insnList.size() > pos)
            next = insnList.get(pos);
    }

    public byte getOpcode() {
        return this.code;
    }

    protected boolean validate() {
        return true;
    }

    @Internal
    public void toByteArray(ByteWriter w) {
        w.writeByte(getOpcode());
    }

    /**
     * 在toBytearray之前调用，刷新index
     *
     * @param pool 常量池
     * @param w    只写的ByteWriter (由{@link ByteList.EmptyByteList}构造)
     */
    @Internal
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        toByteArray(w);
    }

    public String toString() {
        return "#" + ((int)bci) + ' ' + Opcodes.toString0(code);
    }

    public boolean handlePCRev(ToIntFunction<InsnNode> pcRev) {
        return false;
    }
}