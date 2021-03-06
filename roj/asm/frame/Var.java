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
import roj.asm.type.ParamHelper;

/**
 * @author Roj234
 * @since 2021/6/2 23:28
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
            case VarType.TOP:
                return TOP;
            case VarType.INT:
                return INT;
            case VarType.FLOAT:
                return FLOAT;
            case VarType.DOUBLE:
                return DOUBLE;
            case VarType.LONG:
                return LONG;
            case VarType.NULL:
                return NULL;
        }
        throw new ArrayIndexOutOfBoundsException(type);
    }

    public Var(byte type) {
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
            return false;//!VarType.isPrimitive(v.type) & !VarType.isPrimitive(type);
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
        if (owner == null) {
            return node == null ? VarType.toString(type) : " [????????????]?????? " + node;
        } else {
            return owner.endsWith(";") ? ParamHelper.parseField(owner).toString() : owner.substring(owner.lastIndexOf('/') + 1);
        }
    }
}
