package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.ParseContext;

/**
 * 强制类型转换
 * @author Roj234
 * @since  2022/2/24 19:48
 */
public class Cast implements Expression {
    Type type;
    Expression right;

    public Cast(Type type, Expression right) {
        this.type = type;
        this.right = right;
    }

    @Override
    public void write(ParseContext tree, boolean noRet) throws NotStatementException {
        right.write(tree, false);
        tree.cast(type);
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public boolean isEqual(Expression o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Cast cast = (Cast) o;

        if (!type.equals(cast.type)) return false;
        return right.isEqual(cast.right);
    }
}
