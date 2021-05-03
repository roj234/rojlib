package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.OpCode;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.util.Helpers;

import java.util.Map;

/**
 * 操作符 - 简单操作 - 获取this - aload_0
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Std implements Expression {
    final byte type;

    public Std(int type) {
        this.type = (byte) type;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        tree.Std(type == 1 ? OpCode.THIS : OpCode.ARGUMENTS);
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Std))
            return false;

        Std std = (Std) left;
        return std.type == type;
    }

    @Override
    public KType compute(Map<String, KType> param, IObject $this) {
        return $this;
    }

    @Override
    public void mark_spec_op(ParseContext ctx, int op_type) {
        if(op_type == 2) {
            Helpers.throwAny(ctx.getLexer().err("write_to_native_variable"));
        }
    }

    @Override
    public String toString() {
        return type == 1 ? "this" : "arguments";
    }
}
