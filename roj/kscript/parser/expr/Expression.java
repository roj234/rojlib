package roj.kscript.parser.expr;

import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTree;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * @author Roj233
 * @since 2020/10/13 22:15
 */
public interface Expression {
    int OBJECT = -1, INT = 0, DOUBLE = 1, STRING = 2, BOOL = 3;

    default Expression requireWrite() {
        return this;
    }

    /**
     * Append itself to an {@link ASTree}
     */
    void write(ASTree tree);

    /**
     * Compress constant expression
     *
     * @return this if not compressed or new {@link Expression}
     * @see #type()
     */
    @Nonnull
    default Expression compress() {
        return this;
    }

    /**
     * -1 - unknown <br>
     * 0 - int <br>
     * 1 - double <br>
     * 2 - string <br>
     * 3 - bool <br>
     */
    default byte type() {
        return -1;
    }

    default boolean isConstant() {
        return type() != -1;
    }

    default Constant asCst() {
        throw new IllegalArgumentException("This (" + toString() + ") - " + getClass().getName() + " is not a constant.");
    }

    default KType compute(Map<String, KType> parameters, IGettable thisContext) {
        throw new UnsupportedOperationException();
    }

    default boolean isEqual(Expression left) {
        return left == this;
    }
}
