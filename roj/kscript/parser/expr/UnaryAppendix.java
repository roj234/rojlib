package roj.kscript.parser.expr;

import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.Marks;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInteger;
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
                throw new IllegalArgumentException("Unsupported operator " + operator);
            case Symbol.inc:
            case Symbol.dec:
        }
        this.operator = operator;
        this.left = (LoadExpression) left.requireWrite().compress();
    }

    @Override
    public void write(ASTree tree) {
        final boolean var = left instanceof Variable;

        final int count = operator == Symbol.inc ? 1 : -1;

        if (var) {
            String name = ((Variable) left).name;
            tree.Section(Marks.START)
                    .Get(name)
                    .Section(Marks.END)
                    .Inc(name, count).Section(Marks.NEXT);
            // get - inc - pop
            // => inc
        } else {
            left.writeLoad(tree);

            // get - dup - load - numeric - put - pop
            // => get - load - numeric - put
            tree.Std(ASTCode.DUP2)
                    .Std(ASTCode.GET_OBJECT)

                    .Section(Marks.START)
                    .Std(ASTCode.DUP)
                    .Section(Marks.END)

                    .Load(KInteger.valueOf(count))
                    .Std(operator == Symbol.inc ? ASTCode.ADD : ASTCode.SUB)
                    .Std(ASTCode.PUT_OBJECT)
                    .Section(Marks.NEXT);
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
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        int val = operator == Symbol.inc ? 1 : -1;
        KType base;
        KType copy;

        if(left instanceof Variable) {
            Variable v = (Variable) left;
            base = left.compute(parameters, thisContext);
        } else {
            Field field = (Field) left;
            base = field.parent.compute(parameters, thisContext).asObject().get(field.name);
        }

        copy = base.copy();

        if (base.isInteger()) {
            KInteger i = base.asKInteger();
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
