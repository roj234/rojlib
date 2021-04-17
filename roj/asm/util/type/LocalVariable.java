package roj.asm.util.type;

import roj.asm.struct.insn.InsnNode;
import roj.asm.util.IType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/10 17:53
 */
public final class LocalVariable {
    public LocalVariable(int slot, String name, IType type, InsnNode start, InsnNode end) {
        this.slot = slot;
        this.name = name;
        this.type = type;
        this.start = start;
        this.end = end;
    }

    public String name;
    public IType type;
    public InsnNode start, end;
    public int slot;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariable that = (LocalVariable) o;
        return slot == that.slot && start == that.start && end == that.end;
    }

    @Override
    public int hashCode() {
        return slot * 31 + start.hashCode();
    }

    public String toString() {
        return String.valueOf(slot) + '\t' + type + '\t' + name + '\t' + start + '\t' + end;
    }
}
