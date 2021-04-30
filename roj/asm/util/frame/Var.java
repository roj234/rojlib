package roj.asm.util.frame;

import roj.asm.struct.insn.InsnNode;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Var.java.java
 */
public final class Var {
    public byte type;
    public String owner = null;
    public InsnNode node = null;

    public static final Var TOP = new Var(VarType.TOP),
            INT = new Var(VarType.INT),
            FLOAT = new Var(VarType.FLOAT),
            DOUBLE = new Var(VarType.DOUBLE),
            LONG = new Var(VarType.LONG),
            NULL = new Var(VarType.NULL),
            UNINITIAL_THIS = new Var(VarType.UNINITIAL_THIS);

    public static Var std(int type) {
        switch (type) {
            case 0:
                return TOP;
            case 1:
                return INT;
            case 2:
                return FLOAT;
            case 3:
                return DOUBLE;
            case 4:
                return LONG;
            case 5:
                return NULL;
            case 6:
                return UNINITIAL_THIS;
        }
        throw new ArrayIndexOutOfBoundsException(type);
    }

    public Var(byte type) {
        this.type = type;
    }

    public Var(String owner) {
        this.type = VarType.REFERENCE;
        this.owner = owner;
    }

    public Var(InsnNode node) {
        this.type = VarType.UNINITIAL;
        this.node = node;
    }

    public boolean eq(Var v) {
        if(this == v)
            return true;
        if(v == null)
            return false;
        if (v.type != this.type) {
            return false;
        } else {
            if (this.owner != null) {
                return this.owner.equals(v.owner);
            } else {
                if (v.owner != null)
                    return false;
                if (this.node != null && v.node != null) {
                    return InsnNode.validate(this.node) == InsnNode.validate(v.node);
                } else {
                    return v.node == this.node;
                }
            }
        }
    }

    public String toString() {
        return owner == null ? (node == null ? VarType.toString(type) : " [未初始化]直到 " + node) : owner;
    }
}
