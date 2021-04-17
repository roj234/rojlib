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

import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.Opcode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.text.TextUtil;

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
public final class UnaryPrefix implements Expression {
    private final short      operator;
    private       Expression right;

    public UnaryPrefix(short operator) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported operator " + operator);
            case Symbol.logic_not:
            case Symbol.rev:
            case Symbol.inc:
            case Symbol.dec:
            case Symbol.add:
            case Symbol.sub:
        }
        this.operator = operator;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        switch (operator) {
            case Symbol.logic_not:
            case Symbol.sub:
            case Symbol.add:
            case Symbol.rev:
                if(noRet)
                    throw new NotStatementException();
                // those could only be used inside expr...

                right.write(tree, false);

                Opcode op;
                switch (operator) {
                    case Symbol.logic_not:
                        op = Opcode.NOT;
                        break;
                    case Symbol.sub:
                        op = Opcode.NEGATIVE;
                        break;
                    case Symbol.rev:
                        op = Opcode.REVERSE;
                        break;
                    case Symbol.add:
                        op = Opcode.CAST_INT;
                        break;
                    default:
                        throw OperationDone.NEVER;
                }

                tree.Std(op);
                break;
            case Symbol.inc:
            case Symbol.dec:
                if (right.getClass() == Variable.class) {
                    Variable v = (Variable) right;

                    tree.Inc(v.name, operator == Symbol.inc ? 1 : -1);

                    v._after_write_op();

                    if(!noRet) {
                        tree.Get(v.name);
                    }
                } else {
                    LoadExpression f = (LoadExpression) right;
                    f.writeLoad(tree);

                    tree.Std(Opcode.DUP2)
                            .Std(Opcode.GET_OBJ)
                            .Load(KInt.valueOf(operator == Symbol.inc ? 1 : -1))
                            .Std(Opcode.ADD);

                    if(noRet) {
                        tree.Std(Opcode.PUT_OBJ);
                    } else {
                                tree.Std(Opcode.DUP)
                                .Std(Opcode.SWAP3)
                                .Std(Opcode.PUT_OBJ);
                    }

                }
                break;
        }
    }

    @Override
    public KType compute(Map<String, KType> param) {
        KType base;
        switch (operator) {
            case Symbol.add:
                base = right.compute(param);
                return base.isInt() ? base : KInt.valueOf(base.asInt());
            case Symbol.sub:
                base = right.compute(param);

                if (base.isInt()) {
                    base.setIntValue(-base.asInt());
                } else {
                    base.setDoubleValue(-base.asDouble());
                }
                return base;
        }

        int val = operator == Symbol.inc ? 1 : -1;

        if(right instanceof Variable) {
            Variable v = (Variable) right;
            base = right.compute(param);
        } else {
            Field field = (Field) right;
            base = field.parent.compute(param).asObject().get(field.name);
        }

        if (base.isInt()) {
            base.setIntValue(base.asInt() + val);
        } else {
            base.setDoubleValue(base.asDouble() + val);
        }

        return base;
    }

    @Override
    public boolean isConstant() {
        return operator != Symbol.inc && operator != Symbol.dec && type() != -1;
    }

    @Nonnull
    @Override
    public Expression compress() {
        if(right == null)
            throw new IllegalArgumentException("Missing right");

        if (operator == Symbol.sub && right instanceof UnaryPrefix) {
            UnaryPrefix p = (UnaryPrefix) right;
            if (p.operator == Symbol.sub) { // 双重否定
                Expression expr = p.right.compress();
                return expr.isConstant() ? (expr.asCst().val().isInt() ? expr : Constant.valueOf(KInt.valueOf(expr.asCst().asInt()))) : new AsInt(expr);
            }
        }

        // inc/dec不能对常量使用, 所以不用管了
        if(!(right = right.compress()).isConstant()) return this;
        final Constant cst = right.asCst();

        switch (right.type()) {
            case 0:
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!cst.asBool());
                    case Symbol.rev: {
                        KType base = cst.val();
                        base.setIntValue(~base.asInt());
                        return right;
                    }
                    case Symbol.sub: {
                        KType base1 = cst.val();
                        base1.setIntValue(-base1.asInt());
                        return right;
                    }
                    case Symbol.add:
                        return right;
                }
                break;
            case -1:
            case 1: // support not but cant rev
            case 2:
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!cst.asBool());
                    case Symbol.rev:
                        return Constant.valueOf(~cst.asInt());
                    case Symbol.sub:
                        if (right.type() == 1) {
                            return Constant.valueOf(-cst.asDouble());
                        }
                        int isDouble = TextUtil.isNumber(cst.asString());
                        return new Constant(isDouble == 1 ? KDouble.valueOf(-cst.asDouble()) : KInt.valueOf(-cst.asInt()));
                    case Symbol.add:
                        return Constant.valueOf(cst.asInt());
                }
                break;
            case 3:
                boolean operand = cst.asBool();
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!operand);
                    case Symbol.rev:
                        return Constant.valueOf(operand ? ~1 : ~0);
                    case Symbol.sub:
                        return Constant.valueOf(operand ? -1 : -0);
                    case Symbol.add:
                        return Constant.valueOf(operand ? +1 : +0);
                }
                break;
        }

        throw OperationDone.NEVER;
    }

    @Override
    public byte type() {
        switch (operator) {
            case Symbol.inc:
            case Symbol.dec:
                return (byte) (right.type() == 1 ? 1 : 0);
            case Symbol.logic_not:
                return 3;
            case Symbol.rev:
                return 0;
            case Symbol.sub:
                switch (right.type()) {
                    case 0:
                    case 1:
                        return right.type();
                    case 2:
                        return (byte) TextUtil.isNumber(right.asCst().asString());
                    case 3:
                        return 0;
                }
        }
        return -1;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof UnaryPrefix))
            return false;
        UnaryPrefix left1 = (UnaryPrefix) left;
        return left1.right.isEqual(right) && left1.operator == operator;
    }

    @Override
    public String toString() {
        return Symbol.byId(operator) + ' ' + right;
    }

    public String setRight(Expression right) {
        if(right == null)
            return "upf: right is null";

        if(!(right instanceof Field) && !(right instanceof ArrayGet)) {
            switch (operator) {
                case Symbol.inc:
                case Symbol.dec:
                    return "unary.expecting_variable";
            }
        }

        this.right = right;

        return null;
    }
}
