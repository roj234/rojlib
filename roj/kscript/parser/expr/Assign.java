package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.OpCode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KInt;
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

    public Assign(Expression left, Expression right) {
        this.left = (LoadExpression) left;
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
    public void write(ASTree tree, boolean noRet) {
        boolean var = left instanceof Variable;
        boolean optimized = false;

        if (right instanceof Binary) {
            Binary bin = (Binary) right;

            if (bin.left.isEqual(left)) {
                switch (bin.operator) {
                    case Symbol.logic_or:
                    case Symbol.logic_and:
                        break;
                    case Symbol.add:
                    case Symbol.dec: {
                        // i = i + 1 or i += 1; but not i++
                        final Expression right = bin.right;
                        if (right.type() == INT) {
                            int i = right.asCst().asInt();

                            final int count = bin.operator == Symbol.add ? i : -i;

                            if (var) {
                                Variable v = (Variable) this.left;
                                tree.Inc(v.name, count);
                                v._after_write_op();
                            } else {
                                left.writeLoad(tree);

                                /*if(SHOULD_SWAP_OP && !noRet) {
                                    // CURRENT: obj key
                                    tree.Std(OpCode.DUP2)
                                            // CURRENT: obj key obj key
                                            .Std(OpCode.GET_OBJECT)
                                            // CURRENT: obj key val
                                            .Load(KInt.valueOf(count))
                                            // CURRENT: obj key val int
                                            .Std(bin.operator == Symbol.add ? OpCode.ADD : OpCode.SUB)
                                            // CURRENT: obj key val
                                            .Std(OpCode.PUT_OBJECT);

                                    // CURRENT: obj key

                                    return;
                                } else {*/
                                    tree.Std(OpCode.DUP2)
                                            .Std(OpCode.GET_OBJ)
                                            .Load(KInt.valueOf(count))
                                            .Std(bin.operator == Symbol.add ? OpCode.ADD : OpCode.SUB)
                                            .Std(OpCode.PUT_OBJ);
                                //}
                            }

                            optimized = true;
                        }
                        break;
                    }
                    default: {
                        // etc. k = k * 3;
                        if (!var) {
                            left.writeLoad(tree);
                            bin.right.write(tree.Std(OpCode.DUP2).Std(OpCode.GET_OBJ), false);
                            bin.writeOperator(tree);
                            tree.Std(OpCode.PUT_OBJ);

                            optimized = true;
                        }
                    }
                    break;
                }
            }
        }

        if (!optimized) {
            if (var) {
                right.write(tree, false);
                Variable v = (Variable) this.left;
                tree.Set(v.name);
                v._after_write_op();
            } else {
                // parent name value
                left.writeLoad(tree);
                right.write(tree, false);
                tree.Std(OpCode.PUT_OBJ);
            }
        }

        // load
        if(!noRet)
            left.write(tree, false);
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
    public KType compute(Map<String, KType> param, IObject $this) {
        final KType result = right.compute(param, $this);
        if(left instanceof Variable) {
            Variable v = (Variable) left;
            param.put(v.name, result);
        } else {
            Field field = (Field) left;
            field.parent.compute(param, $this).asObject().put(field.name, result);
        }
        return result;
    }

    @Override
    public String toString() {
        return left.toString() + '=' + right.toString();
    }
}
