package roj.kscript.vm;

import roj.kscript.type.KInt;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * @since 2021/5/29 1:31
 */
public final class VM_Int extends KInt {
    ResourceManager M;

    VM_Int(ResourceManager resourceManager) {
        super(0);
        M = resourceManager;
    }

    @Override
    public KType setFlag(int kind) {
        return M.allocI(value, kind);
    }
}
