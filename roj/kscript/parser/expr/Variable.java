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
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/30 19:14
 */
public final class Variable extends Field {
    private static final Expression fakeParent = new Expression() {
        @Override
        public void write(ASTree tree, boolean noRet) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void toVMCode(CompileContext ctx, boolean noRet) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return "$$";
        }
    };

    private              Constant     cst;
    private              byte         spec_op_type;
    private              ParseContext ctx;

    public Variable(String name) {
        super(fakeParent, name);
    }

    @Override
    public void mark_spec_op(ParseContext ctx, int op_type) {
        if(op_type == 1) {
            KType t = ctx.maybeConstant(name);
            if(t != null) {
                cst = Constant.valueOf(t);
            }
        }

        spec_op_type |= op_type;
        this.ctx = ctx;
    }

    @Override
    public boolean isEqual(Expression o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Variable v = (Variable) o;

        return v.name.equals(name);
    }

    @Override
    public int hashCode() {
        int result = cst != null ? cst.hashCode() : 0;
        result = 31 * result + (int) spec_op_type;
        result = 31 * result + (ctx != null ? ctx.hashCode() : 0);
        return result;
    }

    @Override
    public boolean setDeletion() {
        return false;
    }

    @Override
    public boolean isConstant() {
        return cst != null;
    }

    @Override
    public Constant asCst() {
        return cst == null ? super.asCst() : cst;
    }

    @Override
    public byte type() {
        return cst == null ? -1 : cst.type();
    }

    @Nonnull
    @Override
    public Expression compress() {
        return cst == null ? this : cst;
    }

    public String getName() {
        return name;
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return param.getOrDefault(name, KUndefined.UNDEFINED);
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        if (cst == null) tree.Get(name);
        else cst.write(tree, false);

        _after_write_op();
    }

    @Override
    public void toVMCode(CompileContext ctx, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        ctx.loadVar(name, spec_op_type);
    }

    void _after_write_op() {
        if ((spec_op_type & 1) != 0) {
            ctx.useVariable(name);
        }
        if ((spec_op_type & 2) != 0) {
            ctx.assignVariable(name);
        }
        spec_op_type = 0;
    }
}
