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

import roj.collect.MyHashSet;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.Symbol;

/**
 * 临时操作符1 - 保存运算符
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class SymTmp implements Expression {
    public short operator;

    static final MyHashSet<SymTmp> finder = new MyHashSet<>();
    static SymTmp checker = new SymTmp(0);

    private SymTmp(int operator) {
        this.operator = (short) operator;
    }

    public static SymTmp retain(short op) {
        checker.operator = op;
        SymTmp tmp = finder.intern(checker);
        if(tmp == checker) {
            checker = new SymTmp(0);
        }
        return tmp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SymTmp tmp = (SymTmp) o;
        return operator == tmp.operator;
    }

    @Override
    public int hashCode() {
        return operator;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte type() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEqual(Expression left) {
        return false;
    }

    @Override
    public String toString() {
        return "~{" + Symbol.byId(operator) + '}';
    }
}