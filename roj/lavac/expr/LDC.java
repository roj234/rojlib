package roj.lavac.expr;

import roj.asm.tree.anno.*;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.lavac.AnnValNull;
import roj.lavac.parser.Keyword;
import roj.lavac.parser.ParseContext;

import javax.annotation.Nonnull;

import static roj.asm.tree.anno.AnnVal.*;

/**
 * 操作符 - 常量
 *
 * @author Roj233
 * @since 2022/2/27 19:03
 */
public final class LDC implements Expression {
    static final AnnValInt
            TRUE = new AnnValInt(AnnVal.BOOLEAN, 1), 
            FALSE = new AnnValInt(AnnVal.BOOLEAN, 0);

    private final AnnVal c;

    public LDC(AnnVal x) {
        this.c = x;
    }

    public static LDC valueOf(int word) {
        return new LDC(new AnnValInt(AnnVal.INT, word));
    }

    public static LDC valueOf(double word) {
        return new LDC(new AnnValDouble(word));
    }

    public static LDC valueOf(float word) {
        return new LDC(new AnnValFloat(word));
    }

    public static LDC valueOf(String word) {
        return new LDC(new AnnValString(word));
    }

    public static LDC valueOf(boolean word) {
        return new LDC(word ? TRUE : FALSE);
    }

    public static LDC valueOf(AnnVal word) {
        return new LDC(word);
    }

    public static LDC valueOf(Word word) {
        switch (word.type()) {
            case Keyword.NULL:
                return valueOf(AnnValNull.NULL);
            case WordPresets.CHARACTER:
            case WordPresets.STRING:
                return valueOf(word.val());
            case WordPresets.DECIMAL_D:
                return valueOf(word.number().asDouble());
            case WordPresets.DECIMAL_F:
                return valueOf((float) word.number().asDouble());
            case WordPresets.INTEGER:
                return valueOf(word.number().asInt());
            case Keyword.TRUE:
            case Keyword.FALSE:
                return valueOf(word.val().equals("true") ? TRUE : FALSE);
            default:
                throw OperationDone.NEVER;
        }
    }

    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public LDC asCst() {
        return this;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof LDC))
            return false;
        LDC cst = (LDC) left;
        return cst.c.equals(c);
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        switch (c.type()) {
            case BYTE:
            case CHAR:
            case SHORT:
            case BOOLEAN:
            case INT:
                tree.const1(((AnnValInt) c).value);
                break;
            case DOUBLE:
                tree.const1(((AnnValDouble) c).value);
                break;
            case FLOAT:
                tree.const1(((AnnValFloat) c).value);
                break;
            case LONG:
                tree.const1(((AnnValLong) c).value);
                break;
            case STRING:
                tree.const1(((AnnValString) c).value);
                break;
            case CLASS:
                tree.constClass(ParamHelper.getField(((AnnValClass) c).value));
                break;
            case ENUM:
            case ANNOTATION:
            case ARRAY:
                throw new IllegalStateException("Should not reach here");
        }
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public Type type() {
        return typeOf(c);
    }

    public static Type typeOf(AnnVal c) {
        switch (c.type()) {
            case STRING:
                return new Type("java/lang/String");
            case CLASS:
                return ((AnnValClass) c).value;
            case ENUM:
            case ANNOTATION:
            case ARRAY:
                throw new IllegalStateException("Should not reach here");
            default:
                return Type.std(c.type());
        }
    }

    @Override
    public String toString() {
        return c.toString();
    }

    public AnnVal val() {
        return c;
    }
}