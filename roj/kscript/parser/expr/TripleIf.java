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
package roj.kscript.parser.expr;

import roj.asm.Opcodes;
import roj.asm.tree.insn.IfInsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.CompileContext;
import roj.kscript.ast.IfNode;
import roj.kscript.ast.LabelNode;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 三元运算符 ? :
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class TripleIf implements Expression {
    Expression determine, truly, fake;

    public TripleIf(Expression determine, Expression truly, Expression fake) {
        this.determine = determine;
        this.truly = truly;
        this.fake = fake;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        LabelNode ifFalse = new LabelNode();
        LabelNode end = new LabelNode();

        determine.write(tree, false);
        truly.write(tree.If(ifFalse, IfNode.TRUE).Goto(end), noRet);
        fake.write(tree.Node(ifFalse), noRet);
        tree.node0(end);

        /**
         * if(!determine)
         *   goto :ifFalse
         *  truly
         *  goto :end
         * :ifFalse
         *  fake
         * :end
         */
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return determine.compute(param).asBool() ? truly.compute(param) : fake.compute(param);
    }

    @Nonnull
    @Override
    public Expression compress() {
        truly = truly.compress();
        fake = fake.compress();
        if ((determine = determine.compress()).type() == -1) {
            return this;
        } else {
            return determine.asCst().asBool() ? truly : fake;
        }
    }

    @Override
    public byte type() {
        byte typeA = truly.type();
        byte typeB = fake.type();
        return typeA == typeB ? typeA : -1;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof TripleIf))
            return false;
        TripleIf tripleIf = (TripleIf) left;
        return tripleIf.determine.isEqual(determine) && tripleIf.truly.isEqual(truly) && tripleIf.fake.isEqual(fake);
    }

    @Override
    public void toVMCode(CompileContext ctx, boolean noRet) {
        determine.toVMCode(ctx, false);
        ctx.list.add(NodeCache.a_asBool_0());
        IfInsnNode _if_ = new IfInsnNode(Opcodes.IFEQ, null);
        ctx.list.add(_if_);
        truly.toVMCode(ctx, noRet);
        ctx.list.add(_if_.target = new LabelInsnNode());
        fake.toVMCode(ctx, noRet);
    }

    @Override
    public String toString() {
        return determine.toString() + " ? " + truly + " : " + fake;
    }
}
