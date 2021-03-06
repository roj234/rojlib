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
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.KS_ASM;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * @author Roj233
 * @since 2020/10/13 22:15
 */
public interface Expression {
    int OBJECT = -1, INT = 0, DOUBLE = 1, STRING = 2, BOOL = 3;

    /**
     * Append itself to an {@link KS_ASM}
     */
    void write(KS_ASM tree, boolean noRet) throws NotStatementException;

    /**
     * Compress constant expression
     *
     * @return this if not compressed or new {@link Expression}
     * @see #type()
     */
    @Nonnull
    default Expression compress() {
        return this;
    }

    /**
     * -1 - unknown <br>
     * 0 - int <br>
     * 1 - double <br>
     * 2 - string <br>
     * 3 - bool <br>
     */
    default byte type() {
        return -1;
    }

    default boolean isConstant() {
        return type() != -1;
    }

    default Constant asCst() {
        throw new IllegalArgumentException("This (" + toString() + ") - " + getClass().getName() + " is not a constant.");
    }

    default KType compute(Map<String, KType> param) {
        throw new UnsupportedOperationException(getClass().getName());
    }

    default boolean isEqual(Expression left) {
        return left == this;
    }

    /**
     * ??????????????????
     * @param op_type 1: var_read; 2: var_write;
     */
    default void mark_spec_op(ParseContext ctx, int op_type) {}

    default void toVMCode(CompileContext ctx, boolean noRet) {
        throw new UnsupportedOperationException(getClass() + " does not support it now");
    }
}
