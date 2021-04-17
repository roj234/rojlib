package roj.asm.struct.insn;

import roj.annotation.Internal;
import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

import static roj.asm.Opcodes.*;

@Internal
public final class JumpPrimer extends InsnNode {
    public JumpPrimer(byte code, int offset) {
        super(code);
        this.targetIndex = offset;
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_icmpeq:
            case IF_icmpne:
            case IF_icmplt:
            case IF_icmpge:
            case IF_icmpgt:
            case IF_icmple:
            case IF_acmpeq:
            case IF_acmpne:
            case IFNULL:
            case IFNONNULL:
            case GOTO:
            case GOTO_W:
                return true;
        }
        return false;
    }

    public int targetIndex;

    @Override
    public void toByteArray(ByteWriter w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        throw new UnsupportedOperationException();
    }

    public InsnNode bake(InsnNode target) {
        if(code == GOTO || code == GOTO_W)
            return new GotoInsnNode(code, target);
        return new IfInsnNode(code, target);
    }
}