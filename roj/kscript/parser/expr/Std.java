package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.type.KType;

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
    public void write(ASTree tree) {
        ASTCode code;

        switch (type) {/*
            case 0:
                code = ASTCode.LOAD_CONTEXT;
                break;*/
            case 1:
                code = ASTCode.LOAD_THIS;
                break;
            default:
                throw OperationDone.NEVER;
        }
        tree.Std(code);
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
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        switch (type) {/*
            case 0:
                return "上下文";*/
            case 1:
                return thisContext;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        switch (type) {/*
            case 0:
                return "上下文";*/
            case 1:
                return "this";
        }
        return "<ERROR>";
    }
}
