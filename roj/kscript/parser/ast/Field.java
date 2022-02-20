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
import roj.asm.cst.CstUTF;
import roj.asm.tree.insn.LdcInsnNode;
import roj.asm.tree.insn.NPInsnNode;
import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KString;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public class Field implements LoadExpression {
    Expression parent;
    final String name;
    boolean delete;

    public Field(Expression parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Nonnull
    @Override
    public Expression compress() {
        parent = parent.compress();
        return this;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Field))
            return false;
        Field field = (Field) left;
        return field.parent.isEqual(parent) && field.name.equals(name);
    }

    @Override
    public void toVMCode(CompileContext ctx, boolean noRet) {
        if(noRet && !delete)
            throw new NotStatementException();

        parent.toVMCode(ctx, false);
        ctx.list.add(new LdcInsnNode(Opcodes.LDC, new CstUTF("name")));
        if(delete) {
            ctx.list.add(NodeCache.a_field_0());
            ctx.list.add(noRet ? NPInsnNode.of(Opcodes.POP) : NodeCache.a_asBool_1());
        } else {
            ctx.list.add(NodeCache.a_field_1());
        }
    }

    public boolean setDeletion() {
        return delete = true;
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) {
        if(noRet && !delete)
            throw new NotStatementException();

        parent.write(tree, false);
        tree.Load(KString.valueOf(name)).Std(delete ? Opcode.DELETE_OBJ : Opcode.GET_OBJ);
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return parent.compute(param).asObject().get(name);
    }

    @Override
    public String toString() {
        return String.valueOf(parent) + '.' + name;
    }

    @Override
    public void writeLoad(KS_ASM tree) {
        parent.write(tree, false);
        tree.Load(KString.valueOf(name));
    }

    @Override
    public void assignInCompute(Map<String, KType> param, KType val) {
        parent.compute(param).asObject().put(name, val);
    }
}
