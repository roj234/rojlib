package roj.asm.frame.node;

import roj.asm.Opcodes;
import roj.asm.frame.MethodPoet;
import roj.asm.tree.insn.IIndexInsnNode;
import roj.asm.tree.insn.InsnNode;
import roj.asm.util.ConstantPool;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2022/2/5 12:22
 */
public class VarIncrNode extends InsnNode implements IIndexInsnNode {
    public final MethodPoet.Variable v;
    final short                      amount;

    public VarIncrNode(MethodPoet.Variable v, int amount) {
        super(Opcodes.IINC);
        this.v = v;
        this.amount = (short) amount;
    }

    @Override
    public int nodeType() {
        return T_IINC;
    }

    @Override
    public int getIndex() {
        return v.slot;
    }

    @Override
    public void setIndex(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void toByteArray(ConstantPool cw, ByteList w) {
        int vid = v.slot;
        if (vid > 255 || amount != (byte) amount) {
            w.put(Opcodes.WIDE).put(Opcodes.IINC).putShort(vid).putShort(amount);
        } else {
            w.put(Opcodes.IINC).put((byte) vid).put((byte) amount);
        }
    }

    @Override
    public int nodeSize() {
        return v.slot > 255 || amount != (byte) amount ? 6 : 3;
    }
}
