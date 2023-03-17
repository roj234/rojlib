package roj.kscript.parser;

import roj.config.ParseException;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nullable;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/10/17 16:34
 */
public interface ParseContext {
	JSLexer getLexer();

	/**
	 * 解析嵌套函数
	 *
	 * @param type
	 */
	default KFunction parseInnerFunc(short type) throws ParseException {
		return null;
	}

	/**
	 * 使用变量
	 */
	default void useVariable(String name) {}

	/**
	 * 注册变量
	 */
	default void assignVariable(String name) {}

	@Nullable
	default KType maybeConstant(String name) {
		return null;
	}
}
