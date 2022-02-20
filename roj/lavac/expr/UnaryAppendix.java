package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.frame.MethodPoet;
import roj.asm.type.Type;
import roj.lavac.parser.ParseContext;
import roj.lavac.parser.Symbol;

import javax.annotation.Nonnull;

/**
 * 一元运算符
 *
 * @author Roj233
 * @since 2022/3/1 19:21
 */
public final class UnaryAppendix implements Expression {
    private final short operator;
    private final LoadExpression left;

    public UnaryAppendix(short operator, Expression left) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported operator " + operator);
            case Symbol.inc:
            case Symbol.dec:
        }
        this.operator = operator;
        this.left = (LoadExpression) left;
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        int c = operator == Symbol.inc ? 1 : -1;

        switch (left.loadType()) {
            case LoadExpression.VARIABLE: {
                Variable v = (Variable) this.left;

                MethodPoet.Variable name = v.v;

                if(noRet) {
                    tree.increment(name, c);
                } else {
                    tree.load(name)
                        .increment(name, c);
                }

                v._after_write_op(tree);
            }
            break;
            case LoadExpression.STATIC_FIELD: {
                StaticField v = (StaticField) this.left;
                v.write(tree, false); // load
                if (!noRet) tree.dup();
                tree.const1(c).add();
                v.write2(tree); // store
            }
            break;
            case LoadExpression.DYNAMIC_FIELD: {
                Field v = (Field) this.left;
                v.write2(tree);
                if (!noRet) tree.dupX1();
                tree.const1(c).add();
                v.write3(tree);
            }
            break;
            case LoadExpression.ARRAY: {
                ArrayGet v = (ArrayGet) this.left;
                v.write2(tree); // array, index
                tree.noPar(Opcodes.DUP2)
                    .arrayLoad();
                if (!noRet) tree.dupX1();
                tree.const1(c)
                    .add()
                    .arrayStore();
            }
            break;
        }
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof UnaryAppendix))
            return false;
        UnaryAppendix right = (UnaryAppendix) left;
        return right.left.isEqual(left) && right.operator == operator;
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public Type type() {
        return left.type();
    }

    @Override
    public String toString() {
        return left + Symbol.byId(operator);
    }
}
