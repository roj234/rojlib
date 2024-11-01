package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

/**
 * 三元运算符 ? :
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Trinary implements ExprNode {
    private ExprNode cond, ok, fail;

    public Trinary(ExprNode cond, ExprNode ok, ExprNode fail) {
        this.cond = cond;
        this.ok = ok;
        this.fail = fail;
    }

    @Override public String toString() {return cond + " ? " + ok + " : " + fail;}

    @NotNull
    @Override
    public ExprNode resolve() {
        ok = ok.resolve();
        fail = fail.resolve();
        if (!(cond = cond.resolve()).isConstant()) return this;
        return cond.toConstant().asBool() ? ok : fail;
    }

    @Override
    public byte type() {
        byte typeA = ok.type();
        byte typeB = fail.type();
        return typeA == typeB ? typeA : -1;
    }

    @Override public CEntry eval(CMap ctx) {return cond.eval(ctx).asBool() ? ok.eval(ctx) : fail.eval(ctx);}
    @Override public void compile(KCompiler tree, boolean noRet) {

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Trinary trinary = (Trinary) o;

        if (!cond.equals(trinary.cond)) return false;
        if (!ok.equals(trinary.ok)) return false;
        return fail.equals(trinary.fail);
    }

    @Override
    public int hashCode() {
        int result = cond.hashCode();
        result = 31 * result + ok.hashCode();
        result = 31 * result + fail.hashCode();
        return result;
    }
}
