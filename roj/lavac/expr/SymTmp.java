package roj.lavac.expr;

import roj.asm.type.Type;
import roj.lavac.parser.ParseContext;
import roj.lavac.parser.Symbol;

/**
 * 临时操作符1 - 保存运算符
 *
 * @author Roj233
 * @since 2022/3/1 19:14
 */
public final class SymTmp implements Expression {
    public short operator;

    static final SymTmp[] table;
    static {
        table = new SymTmp[Symbol.operators.length];
        for (int i = 0; i < table.length; i++) {
            table[i] = new SymTmp(i + 501);
        }
    }

    private SymTmp(int operator) {
        this.operator = (short) operator;
    }

    public static SymTmp retain(short op) {
        return table[op - 501];
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SymTmp tmp = (SymTmp) o;
        return operator == tmp.operator;
    }

    @Override
    public int hashCode() {
        return operator;
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Type type() {
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