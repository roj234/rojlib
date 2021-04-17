package roj.asm.struct.attr;

import roj.asm.struct.insn.InsnNode;
import roj.asm.util.ConstantWriter;
import roj.collect.ToIntMap;
import roj.util.ByteWriter;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/1/16 22:33
 */
public interface ICodeAttribute {
    void toByteArray(ConstantWriter pool, ByteWriter w, ToIntMap<InsnNode> pcRev);
}
