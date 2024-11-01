package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.config.data.CNull;
import roj.plugins.kscript.KCompiler;
import roj.plugins.kscript.func.Arguments;
import roj.plugins.kscript.func.KSFunction;
import roj.plugins.kscript.func.PreEval;

import java.util.List;

/**
 * 调用函数
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Invoke implements ExprNode {
    private ExprNode func;
    private List<ExprNode> args;
    private byte flag;

    public Invoke(ExprNode line, List<ExprNode> args) {
        this.func = line;
        this.args = args;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        if ((flag & 1) != 0) sb.append("new ");

        sb.append(func).append('(');
        for (ExprNode expr : args) sb.append(expr).append(',');
        if (!args.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append(')').toString();
    }

    public void setNew() {flag |= 1;}

    @NotNull
    @Override
    public ExprNode resolve() {
        func = func.resolve();

        List<ExprNode> args = this.args;
        for (int i = 0; i < args.size(); i++) {
            ExprNode cp = args.get(i).resolve();
            if(!cp.isConstant()) flag |= 1;     // unknown
            if(cp instanceof Spread) flag |= 2; // dynamic
            args.set(i, cp);
        }

        if (func.isConstant()) {
            var fn = this.func.toConstant();
            if((flag & 1) == 0) {
                var arguments = new SimpleList<CEntry>(args.size());
                for (int i = 0; i < args.size(); i++) arguments.add(args.get(i).toConstant());

                return Constant.valueOf(fn.__call(CNull.NULL, new Arguments(arguments)));
            }

            ExprNode alt = PreEval.getDedicated((KSFunction) fn);
            if (alt != null) return alt;
        }

        return this;
    }

    @Override
    public CEntry eval(CMap ctx) {
        var arguments = new SimpleList<CEntry>(args.size());
        for (int i = 0; i < args.size(); i++) {
            var node = args.get(i);
            if (!node.evalSpread(ctx, arguments))
                arguments.add(node.eval(ctx));
        }

        return this.func.eval(ctx).__call(CNull.NULL, new Arguments(arguments));
    }
    @Override
    public void compile(KCompiler tree, boolean noRet) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Invoke invoke = (Invoke) o;

        if (flag != invoke.flag) return false;
        if (!func.equals(invoke.func)) return false;
		return args.equals(invoke.args);
	}

    @Override
    public int hashCode() {
        int result = func.hashCode();
        result = 31 * result + args.hashCode();
        result = 31 * result + flag;
        return result;
    }
}
