package roj.kscript.parser.expr;

import roj.kscript.KConstants;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.type.KObject;
import roj.kscript.type.KString;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 操作符 - 定义映射
 *
 * @author Roj233
 * @since 2020/10/15 22:47
 */
public final class ObjectDefine implements Expression {
    private final Map<String, Expression> expr;
    KObject object;

    public ObjectDefine(Map<String, Expression> args) {
        this.expr = args;
        this.object = new KObject(KConstants.OBJECT);

        for (Iterator<Map.Entry<String, Expression>> it = args.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Expression> entry = it.next();

            Expression expr = entry.getValue().compress();
            if (expr.type() != -1) {
                object.put(entry.getKey(), expr.asCst().val());
                it.remove();
            } else {
                entry.setValue(expr);
            }
        }
    }

    @Override
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        final KObject v = (KObject) object.copy();
        if(!expr.isEmpty()) {
            for (Map.Entry<String, Expression> entry : expr.entrySet()) {
                v.put(entry.getKey(), entry.getValue().compute(parameters, thisContext));
            }
        }
        return v;
    }

    @Override
    public void write(ASTree tree) {
        tree.Load(object);
        for (Map.Entry<String, Expression> entry : expr.entrySet()) {
            Expression expr = entry.getValue();
            if (expr != null) {
                expr.write(tree.Std(ASTCode.DUP).Load(KString.valueOf(entry.getKey())));
                tree.Std(ASTCode.PUT_OBJECT);
            }
        }
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public byte type() {
        return -1;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof ObjectDefine))
            return false;
        ObjectDefine define = (ObjectDefine) left;
        return mapEq(expr, define.expr) && define.object.equalsTo(object);
    }

    private static boolean mapEq(Map<String, Expression> expr, Map<String, Expression> expr1) {
        if (expr.size() != expr1.size())
            return false;

        if (expr.isEmpty())
            return true;

        Iterator<Map.Entry<String, Expression>> itra;
        Iterator<Map.Entry<String, Expression>> itrb;
        List<Map.Entry<String, Expression>> tmp = new ArrayList<>(expr.entrySet());
        tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
        itra = tmp.iterator();
        tmp = new ArrayList<>(expr1.entrySet());
        tmp.sort((o1, o2) -> Integer.compare(o1.getKey().hashCode(), o2.getKey().hashCode()));
        itrb = tmp.iterator();


        while (itra.hasNext()) {
            final Map.Entry<String, Expression> next = itra.next();
            final Map.Entry<String, Expression> next1 = itrb.next();
            if (next == null) {
                if (next1 != null)
                    return false;
            } else if (!next.getKey().equals(next1.getKey()) || !next1.getValue().isEqual(next.getValue()))
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ObjectDefine{" + "expr=" + expr + ", object=" + object + '}';
    }
}
