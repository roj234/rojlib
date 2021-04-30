package roj.asm.util.frame;

import roj.asm.struct.insn.InsnNode;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Frame.java
 */
public final class Frame {
    public static final Frame EMPTY = new Frame(null);

    public FrameType type;
    public InsnNode target;
    public final VList
            locals = new VList(),
            stacks = new VList();

    public Frame(FrameType type) {
        this.type = type;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("            ").append(type).append(" #").append(target);
        //if (type != FrameType.same && type != FrameType.same_ex) {
            //if (type != FrameType.same_local_1_stack && type != FrameType.same_local_1_stack_ex) {
                if (locals.size > 0) {
                    sb.append("\n              Local: ");
                    for (int i = 0; i < locals.size; i++) {
                        sb.append(locals.get(i)).append(", ");
                    }
                    sb.delete(sb.length() - 2, sb.length());
                }
            //}
            sb.append('\n');
            if (stacks.size > 0) {
                sb.append("\n              Stack: ");
                for (int i = 0; i < stacks.size; i++) {
                    sb.append(stacks.get(i)).append(", ");
                }
                sb.delete(sb.length() - 2, sb.length());
            }
        //}
        return sb.append('\n').toString();
    }
}
