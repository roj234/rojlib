/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GotoInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.Opcodes;
import roj.asm.util.InsnList;
import roj.util.ByteWriter;

import java.util.function.ToIntFunction;

import static roj.asm.Opcodes.*;

public class GotoInsnNode extends InsnNode implements IJumpInsnNode {
    public GotoInsnNode(byte code, InsnNode target) {
        super(code);
        setTarget(target);
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case GOTO:
            case GOTO_W:
                return true;
        }
        return false;
    }

    public InsnNode target;

    @Override
    public InsnNode getTarget() {
        return this.target = validate(target);
    }

    @Override
    public void setTarget(InsnNode target) {
        this.target = target;
    }

    int delta;

    private int cache_id_4_verify;

    @Override
    public boolean handlePCRev(ToIntFunction<InsnNode> pcRev) {
        int i = pcRev.applyAsInt(target = validate(target));

        cache_id_4_verify = i;

        if(i == -1) {
            delta = 0;
            return true;
        }

        int od = delta;
        int nd = delta = (i - pcRev.applyAsInt(this));
        return ( ((short) od) == od ) != ( ((short) nd) == nd );
    }

    @Override
    public void verify(InsnList list, int index, int mainVer) throws IllegalArgumentException {
        if(cache_id_4_verify > 0 && list.get(cache_id_4_verify - 1).code == WIDE)
            throw new IllegalArgumentException("Jump target must not \"after\" wide instruction");
    }

    public void toByteArray(ByteWriter w) {
        int delta = this.delta;

        byte code = this.code;
        if (((short) delta) != delta && code == Opcodes.GOTO) {
            code = Opcodes.GOTO_W;
        } else if (code == Opcodes.GOTO_W) {
            code = Opcodes.GOTO;
        }

        w.writeByte(code);

        if (((short) delta) != delta) {
            w.writeInt(delta);
        } else {
            w.writeShort(delta);
        }
    }

    public String toString() {
        return super.toString() + " => " + target;
    }
}