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

import roj.config.word.NotStatementException;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KArray;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;

import java.util.*;

/**
 * 操作符 - 定义数组
 *
 * @author Roj233
 * @since 2020/10/15 22:47
 */
public class ArrayDef implements Expression {
    public static final ArrayDef EMPTY = new ArrayDef(Collections.emptyList());

    List<Expression> expr;
    KArray array;

    public ArrayDef(List<Expression> args) {
        __init(args);
    }

    ArrayDef() {}

    void __init(List<Expression> args) {
        final List<Expression> expr;
        this.expr = args;
        this.array = new KArray(Arrays.asList(new KType[args.size()]));

        int i = 0;
        for (int j = 0; j < args.size(); j++) {
            Expression arg = args.get(j).compress();
            if (arg.isConstant()) {
                array.set(i, arg.asCst().val());
                args.set(i, null);
            } else {
                args.set(i, arg);
            }
            i++;
        }
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (left == null || getClass() != left.getClass())
            return false;
        ArrayDef define = (ArrayDef) left;
        return arrayEq(expr, define.expr) && define.array.__int_equals__(array);
    }

    static boolean arrayEq(List<Expression> args, List<Expression> args1) {
        if (args.size() != args1.size())
            return false;

        if (args.isEmpty())
            return true;

        Iterator<Expression> itra = args.iterator();
        Iterator<Expression> itrb = args1.iterator();
        while (itra.hasNext()) {
            final Expression next = itra.next();
            final Expression next1 = itrb.next();
            if (next == null) {
                if (next1 != null)
                    return false;
            } else if (!next.isEqual(next1))
                return false;
        }
        return true;
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        tree.Load(array);
        for (int i = 0; i < expr.size(); i++) {
            Expression expr = this.expr.get(i);
            if (expr != null) {
                expr.write(tree.Std(Opcode.DUP), false);
                tree
                        .Load(KInt.valueOf(i))
                        .Std(Opcode.PUT_OBJ);
            }
        }
    }

    @Override
    public KType compute(Map<String, KType> param) {
        final KArray v = (KArray) array.copy();
        if(!expr.isEmpty()) {
            for (int i = 0; i < expr.size(); i++) {
                Expression exp = expr.get(i);
                if (exp != null) {
                    v.set(i, exp.compute(param));
                }
            }
            // todo support spread
        }
        return v;
    }

    @Override
    public String toString() {
        return "ArrayDef{" + "expr=" + expr + ", array=" + array + '}';
    }
}
