package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 * @since 2020/10/13 22:15
 */
public interface ExprNode {
    int OBJECT = -1, INT = 0, DOUBLE = 1, STRING = 2, BOOL = 3;

    @NotNull
    default ExprNode resolve() {return this;}

    /**
     * -1 - unknown/object <br>
     * 0 - int <br>
     * 1 - double <br>
     * 2 - string <br>
     * 3 - bool <br>
     */
    default byte type() {return -1;}

    default boolean isConstant() {return false;}
    default CEntry toConstant() {return null;}

    default CEntry eval(CMap ctx) {throw new UnsupportedOperationException(getClass().getName());}
    void compile(KCompiler tree, boolean noRet) throws NotStatementException;

    default boolean evalSpread(CMap ctx, Map<String, CEntry> object) {return false;}
    default boolean evalSpread(CMap ctx, List<CEntry> array) {return false;}
}
