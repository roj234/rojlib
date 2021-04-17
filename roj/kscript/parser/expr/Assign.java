package roj.kscript.parser.expr;

import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.Marks;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KInteger;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * 操作符-赋值
 *
 * @author Roj233
 * @since 2020/10/15 13:01
 */
public final class Assign implements Expression {
    final LoadExpression left;
    final Expression right;

    public Assign(Expression left, Expression right) {
        this.left = (LoadExpression) left.requireWrite().compress();
        this.right = right.compress();
    }

    @Override
    public void write(ASTree tree) {
        final boolean var = left instanceof Variable;
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
                            int i = right.asCst().asInteger();

                            final int count = bin.operator == Symbol.add ? i : -i;

                            if (var) {
                                String name = ((Variable) left).name;
                                tree.Inc(name, count);
                            } else {
                                left.writeLoad(tree);

                                tree.Std(ASTCode.DUP2)
                                        .Std(ASTCode.GET_OBJECT)
                                        .Load(KInteger.valueOf(count))
                                        .Std(bin.operator == Symbol.add ? ASTCode.ADD : ASTCode.SUB)
                                        .Std(ASTCode.PUT_OBJECT);
                            }

                            optimized = true;
                        }
                        break;
                    }
                    default: {
                        // etc. k = k * 3;
                        if (!var) {
                            left.writeLoad(tree);
                            bin.right.write(tree.Std(ASTCode.DUP2).Std(ASTCode.GET_OBJECT));
                            bin.writeOperator(tree);
                            tree.Std(ASTCode.PUT_OBJECT);

                            optimized = true;
                        }
                    }
                    break;
                }
            }
        }

        if (!optimized) {
            if (var) {
                String name = ((Variable) left).name;
                right.write(tree);
                tree.Set(name);
            } else {
                // parent name value
                left.writeLoad(tree);
                right.write(tree);
                tree.Std(ASTCode.PUT_OBJECT);
            }
        }

        // load
        left.write(tree.Section(Marks.START));
        tree.Section(Marks.NEXT);
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
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        final KType result = right.compute(parameters, thisContext);
        if(left instanceof Variable) {
            Variable v = (Variable) left;
            parameters.put(v.name, result);
        } else {
            Field field = (Field) left;
            field.parent.compute(parameters, thisContext).asObject().put(field.name, result);
        }
        return result;
    }

    @Override
    public String toString() {
        return left.toString() + '=' + right.toString();
    }
}
