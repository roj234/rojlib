package roj.asm.struct.insn;

public interface IJumpInsnNode {
    byte getOpcode();

    void setOpcode(byte opcode);

    InsnNode getTarget();

    void setTarget(InsnNode target);
}