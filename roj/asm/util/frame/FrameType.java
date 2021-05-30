package roj.asm.util.frame;

import roj.concurrent.OperationDone;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: FrameType.java
 */
public class FrameType {
    public static final int same = (0),
    same_local_1_stack = (64),
    same_local_1_stack_ex = (247),
    chop = (248),
    same_ex = (251),
    append = (252),
    full = (255);

    public static int byId(int b) {
        final int b1 = b & 0xFF;
        if((b1 & 128) == 0) {
                                            // 64 - 127
            return (b1 & 64) == 0 ? same : same_local_1_stack;
                                 // 0 - 63
        }
        switch (b1) {
            case 247:
                return same_local_1_stack_ex;
            case 248:
            case 249:
            case 250:
                return chop;
            case 251:
                return same_ex;
            case 252:
            case 253:
            case 254:
                return append;
            case 255:
                return full;
        }
        throw OperationDone.NEVER;
    }
}
