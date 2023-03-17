package roj.text.pattern;

import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.word.Tokenizer;
import roj.config.word.Word;

/**
 * @author Roj234
 * @since 2022/10/23 0023 4:25
 */
final class PatternLexer extends Tokenizer {
	PatternLexer() {
		tokens = TOKEN_MAP;
		literalEnd = MY_SPECIAL;
	}

	public static final String[] operators = {"{", "}", "!", ".", ":"};
	public static final short left_l_bracket = 50, right_l_bracket = 51, gth = 52, dot = 53, colon = 54;

	private static final TrieTree<Word> TOKEN_MAP;
	private static final MyBitSet MY_SPECIAL;

	static {
		TOKEN_MAP = new TrieTree<>();
		MY_SPECIAL = new MyBitSet();
		addSymbols(TOKEN_MAP, MY_SPECIAL, 50, operators);
		addWhitespace(MY_SPECIAL);
	}
}
