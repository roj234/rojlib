package roj.asm.util;

import roj.asm.constant.CstClass;
import roj.asm.struct.insn.InsnNode;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: ExceptionEntry.java
 */
public final class ExceptionEntry {
    public InsnNode start, end, handler;
    public String type;

    public static final String ANY_TYPE = new String("[ANY]");

    public ExceptionEntry(InsnNode start, InsnNode end, InsnNode handler, CstClass catchType) {
        this.start = start;
        this.end = end;
        this.handler = handler;
        // 如果catch_type项的值为零，则为所有异常调用此异常处理程序。
        // 0 => Constant.null
        this.type = catchType == null ? ANY_TYPE : catchType.getValue().getString();
    }

    @Override
    public String toString() {
        return "ExceptionEntry{" +
                "start=" + start +
                ", end=" + end +
                ", handler=" + handler +
                ", type='" + type + '\'' +
                '}';
    }
}
