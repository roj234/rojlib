package roj.kscript.parser.expr;

import roj.kscript.ast.ASTree;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/1 14:14
 */
public interface LoadExpression extends Expression {
    void writeLoad(ASTree tree);
}
