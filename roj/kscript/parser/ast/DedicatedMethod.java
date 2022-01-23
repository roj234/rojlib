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
import roj.kscript.api.Computer;
import roj.kscript.asm.KS_ASM;
import roj.kscript.type.KType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since  2021/5/27 0:49
 */
public class DedicatedMethod implements Expression {
    protected final List<Expression> args;
    protected List<KType> vals;
    protected Computer cp;

    public DedicatedMethod(List<Expression> args, Computer computer) {
        this.args = args;
        this.vals = Arrays.asList(new KType[args.size()]);
        for (int i = 0; i < args.size(); i++) {
            Expression ex = args.get(i);
            if(ex.isConstant()) {
                this.vals.set(i, ex.asCst().val());
                args.set(i, null);
            }
        }
        this.cp = computer;
    }

    @Override
    public void write(KS_ASM tree, boolean noRet) throws NotStatementException {
        throw new IllegalArgumentException("Designed to be 'computed'");
    }

    @Override
    public final KType compute(Map<String, KType> param) {
        for (int i = 0; i < args.size(); i++) {
            Expression ex = args.get(i);
            if(ex != null)
                vals.set(i, ex.compute(param));
        }
        return cp.compute(vals);
    }

    @Override
    public boolean isEqual(Expression left) {
        return left == this;
    }
}
