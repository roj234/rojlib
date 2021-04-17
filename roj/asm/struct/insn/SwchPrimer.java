/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: BeforeInitializeSwitch.java
 */
package roj.asm.struct.insn;

import roj.annotation.Internal;
import roj.asm.Opcodes;
import roj.asm.util.ConstantWriter;
import roj.collect.LinkedIntMap;
import roj.util.ByteWriter;

@Internal
public final class SwchPrimer extends InsnNode {
    public SwchPrimer(byte code, int defaultValue, LinkedIntMap<Integer> mapping) {
        super(code);
        this.defaultValue = defaultValue;
        this.mapping = mapping;
    }

    @Override
    protected boolean validate() {
        switch (code) {
            case Opcodes.TABLESWITCH:
            case Opcodes.LOOKUPSWITCH:
                return true;
        }
        return false;
    }

    public int defaultValue;
    public LinkedIntMap<Integer> mapping;

    @Override
    public void toByteArray(ByteWriter w) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void preToByteArray(ConstantWriter pool, ByteWriter w) {
        throw new UnsupportedOperationException();
    }

}