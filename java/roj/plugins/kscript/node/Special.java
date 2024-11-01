package roj.plugins.kscript.node;

import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

/**
 * 特殊字段
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Special implements ExprNode {
    public static final Special THIS = new Special(1), ARGS = new Special(2);

    private final byte type;
    public Special(int type) {this.type = (byte) type;}

    @Override public String toString() {return type == 1 ? "this" : "arguments";}

    @Override public CEntry eval(CMap ctx) {
        if(type == 1) return ctx;
        throw new UnsupportedOperationException("Not available");
    }

    @Override public void compile(KCompiler tree, boolean noRet) {
        if(noRet) throw new NotStatementException();
        //tree.Std(type == 1 ? Opcode.THIS : Opcode.ARGUMENTS);
    }
}
