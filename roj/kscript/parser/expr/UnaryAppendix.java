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

import roj.kscript.ast.ASTree;
import roj.kscript.ast.Opcode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 一元运算符
 * <BR>
 * 我的实现方式有点坑... 再说
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class UnaryAppendix implements Expression {
    private final short operator;
    private final LoadExpression left;

    public UnaryAppendix(short operator, Expression left) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported operator " + operator);
            case Symbol.inc:
            case Symbol.dec:
        }
        this.operator = operator;
        this.left = (LoadExpression) left;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        final int c = operator == Symbol.inc ? 1 : -1;

        if (left instanceof Variable) {
            Variable v = (Variable) this.left;

            String name = v.name;

            if(noRet) {
                tree.Inc(name, c);
            } else {
                tree.Get(name)
                        .Inc(name, c);
            }

            v._after_write_op();
        } else {
            left.writeLoad(tree);

            tree.Std(Opcode.DUP2)
                    .Std(Opcode.GET_OBJ)
                    .Load(KInt.valueOf(c))
                    .Std(Opcode.ADD);

            if(!noRet) {
                tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
            }

            tree.Std(Opcode.PUT_OBJ);
        }
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof UnaryAppendix))
            return false;
        UnaryAppendix right = (UnaryAppendix) left;
        return right.left.isEqual(left) && right.operator == operator;
    }

    @Override
    public KType compute(Map<String, KType> param) {
        int val = operator == Symbol.inc ? 1 : -1;
        KType base;
        KType copy;

        if(left instanceof Variable) {
            Variable v = (Variable) left;
            base = left.compute(param);
        } else {
            Field field = (Field) left;
            base = field.parent.compute(param).asObject().get(field.name);
        }

        copy = base.copy();

        if (base.isInt()) {
            base.setIntValue(base.asInt() + val);
        } else {
            base.setDoubleValue(base.asDouble() + val);
        }

        return copy;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public byte type() {
        return (byte) (left.type() == 1 ? 1 : 0);
    }

    @Override
    public String toString() {
        return left + Symbol.byId(operator);
    }
}
