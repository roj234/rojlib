package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.Keyword;
import roj.kscript.type.*;
import roj.kscript.util.NotStatementException;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 常量表达式 1
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant implements Expression {
    private final KType c;

    public Constant(KType number) {
        this.c = number;
    }

    public static Constant valueOf(int word) {
        return new Constant(KInt.valueOf(word));
    }

    public static Constant valueOf(double word) {
        return new Constant(KDouble.valueOf(word));
    }

    public static Constant valueOf(String word) {
        return new Constant(KString.valueOf(word));
    }

    public static Constant valueOf(boolean word) {
        return new Constant(KBool.valueOf(word));
    }

    public static Constant valueOf(KType word) {
        return new Constant(word);
    }

    public static Constant valueOf(Word word) {
        switch (word.type()) {
            case Keyword.NULL:
                return valueOf(KNull.NULL);
            case Keyword.UNDEFINED:
                return valueOf(KUndefined.UNDEFINED);
            case WordPresets.CHARACTER:
            case WordPresets.STRING:
                return valueOf(KString.valueOf(word.val()));
            case WordPresets.DECIMAL_D:
            case WordPresets.DECIMAL_F:
                return valueOf(KDouble.valueOf(word.val()));
            case WordPresets.INTEGER:
                return valueOf(KInt.valueOf(word.val()));
            case Keyword.TRUE:
            case Keyword.FALSE:
                return valueOf(word.val().equals("true") ? KBool.TRUE : KBool.FALSE);
            case Keyword.NAN:
                return valueOf(KDouble.valueOf(Double.NaN));
            case Keyword.INFINITY:
                return valueOf(KDouble.valueOf(Double.POSITIVE_INFINITY));
            default:
                throw OperationDone.NEVER;
        }
    }

    @Override
    public Constant asCst() {
        return this;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Constant))
            return false;
        Constant cst = (Constant) left;
        return cst.c.getType() == c.getType() && cst.c.equalsTo(c);
    }

    public boolean asBool() {
        return c.asBool();
    }

    public int asInt() {
        return c.asInt();
    }

    public double asDouble() {
        return c.asDouble();
    }

    public String asString() {
        return c.asString();
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        tree.Load(c);
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public KType compute(Map<String, KType> param, IObject $this) {
        return c;
    }

    @Override
    public byte type() {
        return typeOf(c);
    }

    public static byte typeOf(KType constant) {
        switch (constant.getType()) {
            case INT:
                return 0;
            case DOUBLE:
                return 1;
            case BOOL:
                return 3;
            case STRING:
                return 2;
            case NULL:
            case UNDEFINED:
                return -1;
        }
        throw new IllegalArgumentException("Unknown type of " + constant);
    }

    @Override
    public String toString() {
        return c.toString();
    }

    public KType val() {
        return c;
    }

}