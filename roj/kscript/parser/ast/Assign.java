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

import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Opcode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KDouble;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符-赋值
 *
 * @author Roj233
 * @since 2020/10/15 13:01
 */
public final class Assign implements Expression {
    LoadExpression left;
    Expression right;

    public Assign(LoadExpression left, Expression right) {
        this.left = left;
        this.right = right;
    }

    @Nonnull
    @Override
    public Expression compress() {
        left = (LoadExpression) left.compress();
        right = right.compress();
        return this;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void write(KS_ASM tree, boolean noRet) {
        boolean var = left instanceof Variable;
        boolean opDone = false;

        if (right instanceof Binary) {
            Binary bin = (Binary) right;

            Expression al = null, ar = null;
            if (bin.left.isEqual(left)) {
                al = bin.left;
                ar = bin.right;
            } else if(bin.right.isEqual(left)) {
                al = bin.right;
                ar = bin.left;
            }

            if(al != null) {
                switch (bin.operator) {
                    case Symbol.logic_or:
                    case Symbol.logic_and:
                        break;
                    case Symbol.add:
                    case Symbol.dec:
                        // i = i + 1 or i += 1; but not i++
                        if (ar.isConstant() && ar.type() == INT || ar.type() == DOUBLE) {
                            double i = ar.asCst().asDouble();

                            double count = bin.operator == Symbol.add ? i : -i;

                            if (var) {
                                if(((int) count) != count)
                                    break;
                                Variable v = (Variable) this.left;
                                tree.Inc(v.name, (int) count);
                                v._after_write_op();
                                if(!noRet) {
                                    tree.Get(v.name);
                                }
                            } else {
                                left.writeLoad(tree);

                                tree.Std(Opcode.DUP2)
                                        .Std(Opcode.GET_OBJ)
                                        .Load(KDouble.valueOf(count))
                                        .Std(bin.operator == Symbol.add ? Opcode.ADD : Opcode.SUB);
                                if(!noRet) {
                                    tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
                                }
                                tree.Std(Opcode.PUT_OBJ);
                            }

                            opDone = true;
                            break;
                        }
                    default:
                        // etc. k = k * 3;
                        if (!var) {
                            left.writeLoad(tree);

                            ar.write(tree.Std(Opcode.DUP2)
                                    .Std(Opcode.GET_OBJ), false);
                            bin.writeOperator(tree);

                            if(!noRet) {
                                tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
                            }

                            tree.Std(Opcode.PUT_OBJ);

                            opDone = true;
                        }
                    break;
                }
            }
        }

        if (!opDone) {
            if (var) {
                right.write(tree, false);
                Variable v = (Variable) this.left;
                tree.Set(v.name);
                v._after_write_op();
                if(!noRet)
                    tree.Get(v.name);
            } else {
                // parent name value
                left.writeLoad(tree);
                right.write(tree, false);
                if(!noRet) {
                    tree.Std(Opcode.DUP).Std(Opcode.SWAP3);
                }
                tree.Std(Opcode.PUT_OBJ);
            }
        }
    }

    @Override
    public byte type() {
        return left.type();
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Assign))
            return false;
        Assign assign = (Assign) left;
        return assign.left.isEqual(left) && assign.right.isEqual(right);
    }

    @Override
    public KType compute(Map<String, KType> param) {
        final KType result = right.compute(param);
        if(left instanceof Variable) {
            Variable v = (Variable) left;
            param.put(v.name, result);
        } else {
            left.assignInCompute(param, result);
        }
        return result;
    }

    @Override
    public String toString() {
        return left.toString() + '=' + right.toString();
    }
}
