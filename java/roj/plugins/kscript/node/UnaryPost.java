package roj.plugins.kscript.node;

import roj.config.data.*;
import roj.plugins.kscript.KCompiler;
import roj.plugins.kscript.token.KSLexer;

/**
 * 一元后缀运算符
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class UnaryPost implements ExprNode {
    private final short operator;
    private VarNode left;

    public UnaryPost(short operator, VarNode left) {
        this.operator = operator;
        this.left = left;
    }

    @Override public String toString() {return left + KSLexer.repr(operator);}
    @Override public byte type() {return (byte) (left.type() == 0 ? 0 : 1);}
    @Override public ExprNode resolve() {left = (VarNode) left.resolve();return this;}

    @Override
    public CEntry eval(CMap ctx) {
        var val = left.eval(ctx);
        int inc = operator == KSLexer.inc ? 1 : -1;

        var node = left;
        var newVal = val.getType() == Type.INTEGER ? CInt.valueOf(val.asInt() + inc) : CDouble.valueOf(val.asDouble() + inc);
        node.evalStore(ctx, newVal);
        return val;
    }

    @Override
    public void compile(KCompiler tree, boolean noRet) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnaryPost post = (UnaryPost) o;

        if (operator != post.operator) return false;
		return left.equals(post.left);
	}

    @Override
    public int hashCode() {
        int result = operator;
        result = 31 * result + left.hashCode();
        return result;
    }
}
