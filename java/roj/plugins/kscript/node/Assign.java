package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

/**
 * 赋值
 * @author Roj233
 * @since 2020/10/15 13:01
 */
final class Assign implements ExprNode {
    VarNode left;
    ExprNode right;

    public Assign(VarNode left, ExprNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {return left.toString() + '=' + right;}

    @NotNull
    @Override
    public ExprNode resolve() {
        left = (VarNode) left.resolve();
        right = right.resolve();
        return this;
    }

    @Override
    public byte type() {return right.type();}

    @Override
    public void compile(KCompiler tree, boolean noRet) {}

    @Override
    public CEntry eval(CMap ctx) {
        CEntry result = right.eval(ctx);
        left.evalStore(ctx, result);
        return result;
    }
}
