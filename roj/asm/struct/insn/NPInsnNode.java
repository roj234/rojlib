/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: NPInsnNode.java
 */
package roj.asm.struct.insn;

import roj.asm.util.ConstantWriter;
import roj.asm.util.NodeHelper;
import roj.util.ByteWriter;

public final class NPInsnNode extends InsnNode {
    /**
     * 推荐使用{@link NodeHelper#cached(byte)}
     *
     * @param code The code
     */
    public NPInsnNode(byte code) {
        super(code);
    }

    public static NPInsnNode of(byte code) {
        return new NPInsnNode(code);
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        super.toByteArray(w);
    }
}