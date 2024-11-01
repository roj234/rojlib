package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;
import roj.text.CharList;

import java.util.List;

/**
 * 逗号分隔语句
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Chained implements ExprNode {
    private final List<ExprNode> par;
    public Chained(List<ExprNode> par) {this.par = par;}

    @Override
    public String toString() {
        CharList sb = new CharList();
        for (int i = 0; i < par.size(); i++) {
            sb.append(par.get(i).toString()).append(", ");
        }
        sb.setLength(sb.length() - 2);
        return sb.toStringAndFree();
    }

    @NotNull
    @Override
    public ExprNode resolve() {
        for (int i = 0; i < par.size(); i++) {
            par.set(i, par.get(i).resolve());
        }
        return par.size() == 1 ? par.get(0) : this;
    }

    @Override public byte type() {return par.get(par.size() - 1).type();}

    @Override
    public CEntry eval(CMap ctx) {
        int t;
        ExprNode last = par.get(t = par.size() - 1);
        for (int i = 0; i < t; i++) {
            par.get(i).eval(ctx);
        }
        return last.eval(ctx);
    }
    @Override
    public void compile(KCompiler tree, boolean noRet) {
        int t;
        ExprNode last = par.get(t = par.size() - 1);
        for (int i = 0; i < t; i++) {
            par.get(i).compile(tree, true);
        }
        last.compile(tree, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Chained chained = (Chained) o;

		return par.equals(chained.par);
	}

    @Override
    public int hashCode() {
        return par.hashCode();
    }
}
