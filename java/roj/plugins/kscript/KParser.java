package roj.plugins.kscript;

import roj.config.ParseException;
import roj.plugins.kscript.func.KSFunction;
import roj.plugins.kscript.token.KSLexer;

import java.util.List;

/**
 * @author Roj234
 * @since  2020/10/17 16:34
 */
public interface KParser {
    KSLexer lexer();
    default void report(String error) throws ParseException {throw lexer().err(error);};

    /**
     * 解析嵌套函数
     * @param type
     */
    default KSFunction parseFunction(short type) throws ParseException {
        return null;
    }

    default KSFunction parseLambda(List<String> args) throws ParseException {
        return null;
    }
}
