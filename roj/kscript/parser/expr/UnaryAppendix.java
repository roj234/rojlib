package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.OpCode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;

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
public final class UnaryAppendix implements Expression {
    private final short operator;
    private final LoadExpression left;

    public UnaryAppendix(short operator, Expression left) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported op " + operator);
            case Symbol.inc:
            case Symbol.dec:
        }
        this.operator = operator;
        this.left = (LoadExpression) left;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        final int c = operator == Symbol.inc ? 1 : -1;

        if (left instanceof Variable) {
            Variable v = (Variable) this.left;

            String name = v.name;

            if(noRet) {
                tree.Inc(name, c);
            } else {
                tree.Get(name)
                        .Inc(name, c);
            }

            v._after_write_op();
        } else {
            left.writeLoad(tree);

            tree.Std(OpCode.DUP2)
                    .Std(OpCode.GET_OBJ)
                    .Load(KInt.valueOf(c))
                    .Std(OpCode.ADD);

            if(!noRet) {
                tree.Std(OpCode.DUP).Std(OpCode.SWAP3);
            }

            tree.Std(OpCode.PUT_OBJ);
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
    public KType compute(Map<String, KType> param, IObject $this) {
        int val = operator == Symbol.inc ? 1 : -1;
        KType base;
        KType copy;

        if(left instanceof Variable) {
            Variable v = (Variable) left;
            base = left.compute(param, $this);
        } else {
            Field field = (Field) left;
            base = field.parent.compute(param, $this).asObject().get(field.name);
        }

        copy = base.copy();

        if (base.isInt()) {
            KInt i = base.asKInt();
            i.value += val;
        } else {
            KDouble i = base.asKDouble();
            i.value += val;
        }

        return copy;
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
    public byte type() {
        return (byte) (left.type() == 1 ? 1 : 0);
    }

    @Override
    public String toString() {
        return left + Symbol.byId(operator);
    }
}
