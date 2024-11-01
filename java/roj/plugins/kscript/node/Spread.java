package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

import java.util.List;
import java.util.Map;

/**
 * 一元前缀运算
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Spread implements UnaryPreNode {
    ExprNode right;
    Spread() {}

    @Override public String toString() {return "... "+right;}
    @NotNull
    @Override
    public ExprNode resolve() {right = right.resolve();return this;}

    //@Override public boolean isConstant() {return right.isConstant();}

    @Override
    public void compile(KCompiler tree, boolean noRet) {throw new IllegalStateException("SpreadNode");}

    public boolean evalSpread(CMap ctx, List<CEntry> array) {
        var entry = right.eval(ctx).asList();
        array.addAll(entry.raw());
        return true;
    }

    public boolean evalSpread(CMap ctx, Map<String, CEntry> object) {
        var entry = right.eval(ctx).asMap();
        object.putAll(entry.raw());
        return true;
    }

    public String setRight(ExprNode right) {
        if(right == null) return "unary.missing_operand";
        this.right = right;
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Spread pre = (Spread) o;
		return right.equals(pre.right);
	}

    @Override
    public int hashCode() {
		return right.hashCode()+1;
    }
}
