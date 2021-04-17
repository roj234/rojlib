package roj.kscript.parser.expr;

import roj.kscript.ast.ASTree;
import roj.kscript.parser.Symbol;

import javax.annotation.Nonnull;

/**
 * 临时操作符1 - 保存运算符
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class SymboL implements Expression {
    public final short operator;

    public SymboL(short operator) {
        this.operator = operator;
    }

    @Override
    public void write(ASTree tree) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public Expression compress() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte type() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEqual(Expression left) {
        return false;
    }

    @Override
    public String toString() {
        return "~{" + Symbol.byId(operator) + '}';
    }
}