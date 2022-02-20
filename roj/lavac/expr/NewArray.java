package roj.lavac.expr;

import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.ParseContext;

import javax.annotation.Nonnull;

/**
 * 操作符 - 新的空白定长数组
 *
 * @author Roj233
 * @since 2022/2/27 20:47
 */
public class NewArray implements Expression {
    Type type;
    Expression length;

    public NewArray(Type type, Expression length) {
        this.type = type;
        this.length = length;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (left == null || getClass() != left.getClass())
            return false;
        NewArray define = (NewArray) left;
        return type.equals(define.type) && length.isEqual(define.length);
    }

    @Nonnull
    @Override
    public Expression compress() {
        length = length.compress();
        return this;
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        length.write(tree, false);
        tree.newArray(type);
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public String toString() {
        String d = type.toString();
        return "new " + d.substring(0, d.length() - 1) + length + "]";
    }
}
