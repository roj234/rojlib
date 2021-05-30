package roj.kscript.parser.expr;

import roj.config.word.NotStatementException;
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
    private byte spec_op_type;
    private ParseContext ctx;

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
        if(op_type == 1) {
            KType t = ctx.maybeConstant(name);
            if(t != null) {
                cst = Constant.valueOf(t);
            }
        }

        spec_op_type |= op_type;
        this.ctx = ctx;
    }

    @Override
    public boolean isEqual(Expression o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Variable v = (Variable) o;

        return v.name.equals(name);
    }

    @Override
    public int hashCode() {
        int result = cst != null ? cst.hashCode() : 0;
        result = 31 * result + (int) spec_op_type;
        result = 31 * result + (ctx != null ? ctx.hashCode() : 0);
        return result;
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
    public KType compute(Map<String, KType> param) {
        return param.getOrDefault(name, KUndefined.UNDEFINED);
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        if (cst == null) tree.Get(name);
        else cst.write(tree, false);

        _after_write_op();
    }

    void _after_write_op() {
        if ((spec_op_type & 1) != 0) {
            ctx.useVariable(name);
        }
        if ((spec_op_type & 2) != 0) {
            ctx.assignVariable(name);
        }
        spec_op_type = 0;
    }

    @Override
    public void writeObj(ASTree tree) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeKey(ASTree tree) {
        throw new UnsupportedOperationException();
    }
}
