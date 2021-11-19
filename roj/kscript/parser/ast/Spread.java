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
import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.asm.KS_ASM;
import roj.kscript.type.KArray;
import roj.kscript.type.KType;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 扩展运算符
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/16 20:11
 */
public final class Spread implements Expression {
    Expression provider;

    public Spread(Expression provider) {
        this.provider = provider;
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) throws NotStatementException {
        provider.write(tree, false);
        //tree.Std(Opcode.SPREAD);
    }

    @Nonnull
    @Override
    public Expression compress() {
        if(provider.isConstant()) {
            return Constant.valueOf(__spread(provider.asCst().val()));
        }
        return this;
    }

    private static IArray __spread(KType val) {
        if(val.canCastTo(Type.ARRAY)) {
            return val.asArray();
        } else {
            IObject obj = val.asObject();
            int l = obj.get("length").asInt();
            KArray array = new KArray(l);
            for (int i = 0; i < l; i++) {
                array.add(obj.get(String.valueOf(i)));
            }
            return array;
        }
    }

    @Override
    public byte type() {
        return -1;
    }

    @Override
    public boolean isConstant() {
        return provider.isConstant();
    }

    @Override
    public Constant asCst() {
        return Constant.valueOf(__spread(provider.asCst().val()));
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return __spread(provider.compute(param));
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Spread))
            return false;
        Spread sp = (Spread) left;
        return sp.provider.isEqual(provider);
    }

    @Override
    public String toString() {
        return "... " + provider;
    }
}
