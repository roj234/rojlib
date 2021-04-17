package roj.kscript.parser.expr;

import roj.kscript.ast.ASTree;

import javax.annotation.Nonnull;

/**
 * 临时操作符2 - 保存表达式
 *
 * @author Roj233
 * @since 2020/10/16 11:29
 */
public class Delegation implements Expression {
    private Expression delegate;

    public void setValue(Expression delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(ASTree tree) {

    }

    @Nonnull
    @Override
    public Expression compress() {
        return delegate.compress();
    }

    @Override
    public byte type() {
        return delegate.type();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
