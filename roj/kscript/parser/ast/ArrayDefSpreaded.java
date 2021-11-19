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

import roj.kscript.api.IArray;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.type.KArray;
import roj.kscript.type.KType;

import java.util.List;
import java.util.Map;

/**
 * 操作符 - 定义数组 ...
 *
 * @author Roj233
 * @since 2021/6/18 8:50
 */
public final class ArrayDefSpreaded extends ArrayDef {
    Spread sp;

    public ArrayDefSpreaded(List<Expression> args) {
        sp = (Spread) args.remove(args.size() - 1);
        __init(args);
    }

    @Override
    public boolean isEqual(Expression left) {
        return this == left || (super.isEqual(left) && sp.isEqual(((ArrayDefSpreaded)left).sp));
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) {
        super.write(tree, noRet);
        tree.Std(Opcode.SPREAD_ARRAY);
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
        }
        IArray array2 = sp.compute(param).asArray();
        for (int i = 0; i < array2.size(); i++) {
            v.add(array2.get(i));
        }
        return v;
    }

    @Override
    public String toString() {
        return "ArrayDef [Spreaded] {" + "expr=" + expr + ", array=" + array + '}';
    }
}
