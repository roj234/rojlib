package roj.compiler.macro;

import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2024/1/22 0022 12:03
 */
public class MacroTokenizer extends Tokenizer {
	private static final short MACRO_MARKER = 10, PARAM = 11, DEFINE = 12, UNDEFINE = 13, IF = 14, ELSE = 15, ENDIF = 16;
	private static final MyBitSet P_LEND = new MyBitSet();
	private static final TrieTree<Word> P_TID = new TrieTree<>();

	static {
		addSymbols(P_TID, null, PARAM, TextUtil.split1("/*MACRO*/ #PARAM #DEFINE #UNDEFINE #IF #ELSE #ENDIF #FOREACH #CALL", ' '));
		addWhitespace(P_LEND);
	}
}