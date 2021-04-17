package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.config.word.Word;
import roj.config.word.WordPresets;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.Keyword;
import roj.kscript.type.*;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 常量表达式 1
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant implements Expression {
    private final KType constant;

    public Constant(KType number) {
        this.constant = number;
    }

    public static Constant valueOf(int word) {
        return new Constant(KInteger.valueOf(word));
    }

    public static Constant valueOf(double word) {
        return new Constant(KDouble.valueOf(word));
    }

    public static Constant valueOf(String word) {
        return new Constant(KString.valueOf(word));
    }

    public static Constant valueOf(boolean word) {
        return new Constant(KBoolean.valueOf(word));
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
                return valueOf(KInteger.valueOf(word.val()));
            case Keyword.TRUE:
            case Keyword.FALSE:
                return valueOf(KBoolean.valueOf(word.val().equals("true")));
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
        return cst.constant.getType() == constant.getType() && cst.constant.equalsTo(constant);
    }

    public boolean asBoolean() {
        return constant.asBoolean();
    }

    public int asInteger() {
        return constant.asInteger();
    }

    public double asDouble() {
        return constant.asDouble();
    }

    public String asString() {
        return constant.asString();
    }

    @Override
    public void write(ASTree tree) {
        tree.Load(constant);
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        return constant;
    }

    @Override
    public byte type() {
        return typeOf(constant);
    }

    public static byte typeOf(KType constant) {
        switch (constant.getType()) {
            case NUMBER:
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
        return constant.toString();
    }

    public KType val() {
        return constant;
    }

}