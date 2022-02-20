package roj.lavac.expr;

import roj.asm.frame.MethodPoet;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.ParseContext;
import roj.util.Helpers;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since  2022/2/27 20:30
 */
public final class Variable implements LoadExpression {
    MethodPoet.Variable v;
    private LDC cst;
    private byte spec_op_type;

    public Variable(MethodPoet.Variable v) {
        this.v = v;
    }

    @Override
    public void mark_spec_op(ParseContext ctx, int op_type) {
        if(op_type == 1) {
            AnnVal t = ctx.getConstant(v);
            if(t != null) {
                cst = LDC.valueOf(t);
            }
        }

        if(op_type == 2 && v.constant) {
            Helpers.athrow(ctx.getLexer().err("write_final_lv"));
        }

        spec_op_type |= op_type;
    }

    @Override
    public boolean isEqual(Expression o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Variable v = (Variable) o;

        return v.v.equals(this.v);
    }

    @Override
    public boolean isConstant() {
        return cst != null;
    }

    @Override
    public LDC asCst() {
        if (cst == null) {
            throw new IllegalArgumentException("not a constant.");
        }
        return cst;
    }

    @Nonnull
    @Override
    public Expression compress() {
        return cst == null ? this : cst;
    }

    @Override
    public Type type() {
        return (Type) v.type;
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        if (cst == null) tree.load(v);
        else cst.write(tree, false);

        _after_write_op(tree);
    }

    void _after_write_op(ParseContext ctx) {
        if ((spec_op_type & 1) != 0) {
            ctx.useVariable(v);
        }
        if ((spec_op_type & 2) != 0) {
            ctx.assignVariable(v);
        }
        spec_op_type = 0;
    }

    @Override
    public void write2(ParseContext tree) {}

    @Override
    public byte loadType() {
        return VARIABLE;
    }
}
