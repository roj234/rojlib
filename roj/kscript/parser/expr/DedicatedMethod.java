package roj.kscript.parser.expr;

import roj.config.word.NotStatementException;
import roj.kscript.api.Computer;
import roj.kscript.ast.ASTree;
import roj.kscript.type.KType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/5/27 0:49
 */
public class DedicatedMethod implements Expression {
    protected final List<Expression> args;
    protected List<KType> vals;
    protected Computer cp;

    public DedicatedMethod(List<Expression> args, Computer computer) {
        this.args = args;
        this.vals = Arrays.asList(new KType[args.size()]);
        for (int i = 0; i < args.size(); i++) {
            Expression ex = args.get(i);
            if(ex.isConstant()) {
                this.vals.set(i, ex.asCst().val());
                args.set(i, null);
            }
        }
        this.cp = computer;
    }

    @Override
    public void write(ASTree tree, boolean noRet) throws NotStatementException {
        throw new IllegalArgumentException("Designed to be 'computed'");
    }

    @Override
    public final KType compute(Map<String, KType> param) {
        for (int i = 0; i < args.size(); i++) {
            Expression ex = args.get(i);
            if(ex != null)
                vals.set(i, ex.compute(param));
        }
        return cp.compute(vals);
    }

    @Override
    public boolean isEqual(Expression left) {
        return left == this;
    }
}
