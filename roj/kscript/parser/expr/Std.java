package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
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
    //final byte type;

    public Std(int type) {
        //this.type = (byte) type;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        // type is useless currently.
        tree.Std(ASTCode.LOAD_THIS);
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Std))
            return false;

        return true;
        //Std std = (Std) left;
        //return std.type == type;
    }

    @Override
    public KType compute(Map<String, KType> parameters, IObject thisContext) {
        return thisContext;
    }

    @Override
    public void mark_spec_op(ParseContext ctx, int op_type) {
        if(op_type != 0) {
            Helpers.throwAny(ctx.getLexer().err("write_to_this"));
        }
    }

    @Override
    public String toString() {
        return "this";
    }
}
