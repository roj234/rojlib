package roj.kscript.parser;

import roj.kscript.parser.expr.Expression;
import roj.kscript.type.KType;

import javax.annotation.Nullable;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/17 16:34
 */
public interface PContext {
    JSLexer getLexer();

    @Nullable
    default KType useVariable(String name) {
        return null;
    }

    default void assignVariable(String name, Expression right) {
    }
}
