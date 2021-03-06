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
package roj.kscript.parser.ast;

import roj.asm.Opcodes;
import roj.asm.tree.insn.IfInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.asm.tree.insn.NPInsnNode;
import roj.asm.util.InsnList;
import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KType;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 获取对象可变名称属性
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class ArrayGet implements LoadExpression {
    Expression array, index;
    boolean delete;

    public ArrayGet(Expression array, Expression index) {
        this.array = array;
        this.index = index;
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) {
        if(noRet && !delete)
            throw new NotStatementException();

        this.array.write(tree, false);
        this.index.write(tree, false);
        tree.Std(delete ? Opcode.DELETE_OBJ : Opcode.GET_OBJ);
    }

    @Nonnull
    @Override
    public Expression compress() {
        array = array.compress();
        index = index.compress();
        return this;
    }

    public boolean setDeletion() {
        return delete = true;
    }

    @Override
    public byte type() {
        return -1;
    }

    @Override
    public String toString() {
        return array.toString() + '[' + index.toString() + ']';
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof ArrayGet))
            return false;
        ArrayGet get = (ArrayGet) left;
        return get.array.isEqual(array) && get.index.isEqual(index);
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return array.compute(param).asArray().get(index.compute(param).asInt());
    }

    @Override
    public void toVMCode(CompileContext ctx, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        array.toVMCode(ctx, false);
        InsnList list = ctx.list;
        list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/api/KType", "asArray", "()Lroj/kscript/api/IArray;"));
        index.toVMCode(ctx, false);
        if(index.type() != 2) {
            if(index.type() == -1) {
                list.add(NPInsnNode.of(Opcodes.DUP));
                list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "isInt", "()Z"));
                LabelInsnNode label = new LabelInsnNode();
                list.add(new IfInsnNode(Opcodes.IFNE, label));
                list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asString", "()Ljava/lang/String;"));
                list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/api/IObject", "get", "(Ljava/lang/String;)Lroj/kscript/type/KType;"));
                list.add(label);
            }
            list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asInt", "()I"));
            list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/api/IArray", "get", "(I)Lroj/kscript/type/KType;"));
        } else {
            list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/type/KType", "asString", "()Ljava/lang/String;"));
            list.add(new InvokeInsnNode(Opcodes.INVOKEVIRTUAL, "roj/kscript/api/IObject", "get", "(Ljava/lang/String;)Lroj/kscript/type/KType;"));
        }
    }

    @Override
    public void writeLoad(KS_ASM tree) {
        this.array.write(tree, false);
        this.index.write(tree, false);
    }

    @Override
    public void assignInCompute(Map<String, KType> param, KType val) {
        KType b = index.compute(param);
        KType a = array.compute(param);

        if (a.canCastTo(Type.ARRAY) && b.isInt()) {
            a.asArray().set(b.asInt(), val);
        } else {
            a.asObject().put(b.asString(), val);
        }
    }
}
