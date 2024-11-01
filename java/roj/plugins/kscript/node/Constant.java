package roj.plugins.kscript.node;

import roj.config.data.*;
import roj.plugins.kscript.KCompiler;

/**
 * 常量
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Constant implements ExprNode {
    private final CEntry c;
    public Constant(CEntry c) {this.c = c;}

    public static Constant valueOf(int word) {return new Constant(CInt.valueOf(word));}
    public static Constant valueOf(double word) {return new Constant(CDouble.valueOf(word));}
    public static Constant valueOf(String word) {return new Constant(CString.valueOf(word));}
    public static Constant valueOf(boolean word) {return new Constant(CBoolean.valueOf(word));}
    public static Constant valueOf(CEntry word) {return new Constant(word);}

    @Override public String toString() {return c.toString();}

    @Override public byte type() {return typeOf(c);}
    public static byte typeOf(CEntry c) {
        return switch (c.getType()) {
            default -> -1;
            case INTEGER -> 0;
            case DOUBLE -> 1;
            case STRING -> 2;
            case BOOL -> 3;
        };
    }

    @Override public boolean isConstant() {return true;}
    @Override public CEntry toConstant() {return c;}

    @Override public CEntry eval(CMap ctx) {return c;}
    @Override
    public void compile(KCompiler tree, boolean noRet) {
        if(noRet) throw new NotStatementException();

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Constant constant = (Constant) o;

		return c.equals(constant.c);
	}

    @Override public int hashCode() {return c.hashCode();}
}