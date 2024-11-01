package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;
import roj.plugins.kscript.func.KSArray;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 定义Array[]
 * @author Roj233
 * @since 2020/10/15 22:47
 */
final class NewArray implements ExprNode {
    public static final NewArray EMPTY = new NewArray(Collections.emptyList());

    private final List<ExprNode> expr;
    private final CEntry[] constant;

    public NewArray(List<ExprNode> args) {
        this.expr = args;
        this.constant = new CEntry[args.size()];
    }

    @Override
    public String toString() {return "Array[" + "expr=" + expr + ", constant=" + Arrays.toString(constant) + ']';}

    @Override
    public @NotNull ExprNode resolve() {
        var args = expr;
        int i = 0;
        for (int j = 0; j < args.size(); j++) {
            var expr = args.get(j).resolve();
            /*if (expr.isConstant()) {
                constant[i] = expr.toConstant();
                args.set(i, null);
            } else */{
                args.set(i, expr);
            }
            i++;
        }
        return this;
    }

    @Override public boolean isConstant() {return expr.isEmpty();}
    @Override
    public CEntry toConstant() {
        if (!isConstant()) return ExprNode.super.toConstant();
        return new KSArray(SimpleList.asModifiableList(constant.clone()));
    }

    @Override
    public CEntry eval(CMap ctx) {
        var array = new SimpleList<CEntry>(expr.size());
        // todo support spread
        for (int i = 0; i < expr.size(); i++) {
            ExprNode exp = expr.get(i);
            if (exp != null && !exp.evalSpread(ctx, array)) {
                array.add(exp.eval(ctx));
            }
        }

        return new KSArray(array);
    }
    @Override
    public void compile(KCompiler tree, boolean noRet) {
        if(noRet) throw new NotStatementException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NewArray array = (NewArray) o;

        if (!expr.equals(array.expr)) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
		return Arrays.equals(constant, array.constant);
	}

    @Override
    public int hashCode() {
        int result = expr.hashCode();
        result = 31 * result + Arrays.hashCode(constant);
        return result;
    }
}
