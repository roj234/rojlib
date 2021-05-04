package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.OpCode;
import roj.kscript.type.KArray;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.kscript.util.NotStatementException;

import java.util.*;

/**
 * 操作符 - 定义数组
 *
 * @author Roj233
 * @since 2020/10/15 22:47
 */
public class ArrayDefine implements Expression {
    private final List<Expression> expr;
    protected KArray array;

    public ArrayDefine(List<Expression> args) {
        this.expr = args;
        this.array = new KArray(Arrays.asList(new KType[args.size()]));

        int i = 0;
        for (ListIterator<Expression> itr = args.listIterator(); itr.hasNext(); ) {
            Expression expr = itr.next().compress();
            if (expr.type() != -1) {
                array.set(i, expr.asCst().val());
                itr.set(null);
            } else {
                itr.set(expr);
            }
            i++;
        }
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof ArrayDefine))
            return false;
        ArrayDefine define = (ArrayDefine) left;
        return arrayEq(expr, define.expr) && define.array.__int_equals__(array);
    }

    static boolean arrayEq(List<Expression> args, List<Expression> args1) {
        if (args.size() != args1.size())
            return false;

        if (args.isEmpty())
            return true;

        Iterator<Expression> itra = args.iterator();
        Iterator<Expression> itrb = args1.iterator();
        while (itra.hasNext()) {
            final Expression next = itra.next();
            final Expression next1 = itrb.next();
            if (next == null) {
                if (next1 != null)
                    return false;
            } else if (!next.isEqual(next1))
                return false;
        }
        return true;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        tree.Load(array);
        for (int i = 0; i < expr.size(); i++) {
            Expression expr = this.expr.get(i);
            if (expr != null) {
                expr.write(tree.Std(OpCode.DUP), false);
                tree
                        .Load(KInt.Intl.valueOf(i))
                        .Std(OpCode.PUT_OBJ);
            }
        }
    }

    @Override
    public KType compute(Map<String, KType> param, IObject $this) {
        final KArray v = (KArray) array.copy();
        if(!expr.isEmpty()) {
            for (int i = 0; i < expr.size(); i++) {
                Expression exp = expr.get(i);
                if (exp != null) {
                    v.set(i, exp.compute(param, $this));
                }
            }
        }
        return v;
    }

    @Override
    public String toString() {
        return "ArrayDefine{" + "expr=" + expr + ", array=" + array + '}';
    }
}
