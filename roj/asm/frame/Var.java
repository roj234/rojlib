/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.asm.frame;

import roj.asm.tree.insn.InsnNode;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/2 23:28java
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
            READ_ONLY_UNI_THIS = new Var();

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
        }
        throw new ArrayIndexOutOfBoundsException(type);
    }

    private Var(byte type) {
        this.type = type;
    }

    public Var() {
        this.type = VarType.UNINITIAL_THIS;
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
            return v.type == VarType.NULL || this.type == VarType.NULL;
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
        return owner == null ? (node == null ? VarType.toString(type) : " [未初始化]直到 " + node) : owner.substring(owner.lastIndexOf('/') + 1);
    }
}
