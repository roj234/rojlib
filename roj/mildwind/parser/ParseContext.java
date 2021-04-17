package roj.mildwind.parser;

import roj.config.ParseException;
import roj.mildwind.type.JsFunction;
import roj.mildwind.type.JsObject;

import javax.annotation.Nullable;

/**
 * @author Roj234
 * @since 2020/10/17 16:34
 */
public interface ParseContext {
	JSLexer lex();

	/**
	 * 解析嵌套函数
	 */
	default JsFunction inlineFunction() throws ParseException { return null; }
	default JsFunction parseLambda() throws ParseException { return null; }

	@Nullable
	default JsObject maybeConstant(String name) { return null; }
}
