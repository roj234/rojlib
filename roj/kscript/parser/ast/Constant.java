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

import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.kscript.asm.KS_ASM;
import roj.kscript.parser.Keyword;
import roj.kscript.type.*;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 常量表达式 1
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant implements Expression {
    private final KType c;

    public Constant(KType number) {
        this.c = number;
    }

    public static Constant valueOf(int word) {
        return new Constant(KInt.valueOf(word));
    }

    public static Constant valueOf(double word) {
        return new Constant(KDouble.valueOf(word));
    }

    public static Constant valueOf(String word) {
        return new Constant(KString.valueOf(word));
    }

    public static Constant valueOf(boolean word) {
        return new Constant(KBool.valueOf(word));
    }

    public static Constant valueOf(KType word) {
        return new Constant(word);
    }

    public static Constant valueOf(Word word) {
        switch (word.type()) {
            case Keyword.NULL:
                return valueOf(KNull.NULL);
            case Keyword.UNDEFINED:
                return valueOf(KUndefined.UNDEFINED);
            case WordPresets.CHARACTER:
            case WordPresets.STRING:
                return valueOf(KString.valueOf(word.val()));
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return valueOf(KDouble.valueOf(word.number().asDouble()));
            case WordPresets.INTEGER:
                return valueOf(KInt.valueOf(word.number().asInt()));
            case Keyword.TRUE:
            case Keyword.FALSE:
                return valueOf(word.val().equals("true") ? KBool.TRUE : KBool.FALSE);
            case Keyword.NAN:
                return valueOf(KDouble.valueOf(Double.NaN));
            case Keyword.INFINITY:
                return valueOf(KDouble.valueOf(Double.POSITIVE_INFINITY));
            default:
                throw OperationDone.NEVER;
        }
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public Constant asCst() {
        return this;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Constant))
            return false;
        Constant cst = (Constant) left;
        return cst.c.getType() == c.getType() && cst.c.equalsTo(c);
    }

    public boolean asBool() {
        return c.asBool();
    }

    public int asInt() {
        return c.asInt();
    }

    public double asDouble() {
        return c.asDouble();
    }

    public String asString() {
        return c.asString();
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        tree.Load(c);
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return c;
    }

    @Override
    public byte type() {
        return typeOf(c);
    }

    public static byte typeOf(KType constant) {
        switch (constant.getType()) {
            case INT:
                return 0;
            case DOUBLE:
                return 1;
            case BOOL:
                return 3;
            case STRING:
                return 2;
            case NULL:
            case UNDEFINED:
                return -1;
        }
        throw new IllegalArgumentException("Unknown type of " + constant);
    }

    @Override
    public String toString() {
        return c.toString();
    }

    public KType val() {
        return c;
    }

}