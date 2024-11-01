package roj.plugins.kscript.node;

import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;

/**
 * @author Roj234
 * @since  2020/11/1 14:14
 */
interface VarNode extends ExprNode {
    void compileLoad(KCompiler tree);
    void evalStore(CMap ctx, CEntry val);
    boolean setDeletion();
}
