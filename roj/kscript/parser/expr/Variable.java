package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/30 19:14
 */
public final class Variable extends Field {
    private Constant cst;

    public Variable(String name) {
        super(new Expression() {
            @Override
            public void write(ASTree tree, boolean noRet) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                return "$$";
            }
        }, name);
    }

    @Override
    public void mark_spec_op(ParseContext ctx, int op_type) {
        if(op_type == 0) {
            cst = Constant.valueOf(ctx.useVariable(name));
        } else {
            ctx.assignVariable(name);
        }
    }

    @Override
    public boolean setDeletion() {
        return false;
    }

    @Override
    public boolean isConstant() {
        return cst != null;
    }

    @Override
    public Constant asCst() {
        return cst == null ? super.asCst() : cst;
    }

    @Override
    public byte type() {
        return cst == null ? -1 : cst.type();
    }

    @Nonnull
    @Override
    public Expression compress() {
        return cst == null ? this : cst;
    }

    public String getName() {
        return name;
    }

    @Override
    public KType compute(Map<String, KType> parameters, IObject thisContext) {
        return parameters.getOrDefault(name, KUndefined.UNDEFINED);
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if (cst == null) tree.Get(name);
        else cst.write(tree, false);
    }

    @Override
    public void writeLoad(ASTree tree) {
        throw new UnsupportedOperationException();
    }
}
