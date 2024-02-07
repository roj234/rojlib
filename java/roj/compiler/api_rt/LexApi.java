package roj.compiler.api_rt;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:29
 */
public interface LexApi {
	LexApi rename(int token, String name);
	LexApi alias(int token, String alias);
	LexApi literalAlias(String alias, String literal);
}