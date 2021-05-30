package roj.kscript.parser.expr;

import roj.config.word.NotStatementException;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.IfNode;
import roj.kscript.type.KBool;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/5/3 20:14
 */
public final class TmpAsBool implements Expression {
    Expression right;

    public TmpAsBool(Expression right) {
        this.right = right;
    }

    @Override
    public void write(ASTree tree, boolean noRet) throws NotStatementException {
        right.write(tree, false);
        tree.IfLoad(IfNode.TRUE);
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return KBool.valueOf(right.compute(param).asBool());
    }

    @Override
    public byte type() {
        return 3;
    }
}
