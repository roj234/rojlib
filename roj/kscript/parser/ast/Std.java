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
import roj.asm.tree.insn.NPInsnNode;
import roj.config.word.NotStatementException;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.util.Helpers;

import java.util.Map;

/**
 * 操作符 - 简单操作 - 获取this - aload_0
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Std implements Expression {
    public static final Std STD1 = new Std(1), STD2 = new Std(2);

    final byte type;

    public Std(int type) {
        this.type = (byte) type;
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        tree.Std(type == 1 ? Opcode.THIS : Opcode.ARGUMENTS);
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Std))
            return false;

        Std std = (Std) left;
        return std.type == type;
    }

    @Override
    public KType compute(Map<String, KType> param) {
        if(type == 1) {
            KType t = param.get("<this>");
            if (t != null)
                return t;
        }
        throw new UnsupportedOperationException("No internal variable available");
    }

    @Override
    public void mark_spec_op(ParseContext ctx, int op_type) {
        if(op_type == 2) {
            Helpers.athrow(ctx.getLexer().err("write_to_native_variable"));
        }
    }

    @Override
    public void toVMCode(CompileContext ctx, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        // $this, argList
        ctx.list.add(NPInsnNode.of(type == 1 ? Opcodes.ALOAD_1 : Opcodes.ALOAD_2));
    }

    @Override
    public String toString() {
        return type == 1 ? "this" : "arguments";
    }
}
