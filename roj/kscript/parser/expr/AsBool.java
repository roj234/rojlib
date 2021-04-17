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

import roj.config.word.NotStatementException;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.CompileContext;
import roj.kscript.ast.Opcode;
import roj.kscript.type.KBool;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/5/3 20:14
 */
public final class AsBool implements Expression {
    Expression right;

    public AsBool(Expression right) {
        this.right = right;
    }

    @Override
    public void write(ASTree tree, boolean noRet) throws NotStatementException {
        right.write(tree, false);
        tree.Std(Opcode.CAST_BOOL);
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return KBool.valueOf(right.compute(param).asBool());
    }

    @Override
    public void toVMCode(CompileContext ctx, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        ctx.list.add(NodeCache.a_asBool_0());
        ctx.list.add(NodeCache.a_asBool_1());
    }

    @Override
    public byte type() {
        return 3;
    }
}
