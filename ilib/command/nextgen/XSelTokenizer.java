package ilib.command.nextgen;

import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:36
 */
public class XSelTokenizer extends Tokenizer {
	private static final String[] keywords = TextUtil.split1(
	"@a,@p,@r,@e,@s," +
	"distance,x,y,z,dx,dy,dz," +
	"mode,type,name,tag,score," +
	"x_rotation,y_rotation,nbt", ',');
	public static final short FOR = 10, WHILE = 11, DO = 12, CONTINUE = 13, BREAK = 14, CASE = 15, IF = 16, ELSE = 17, GOTO = 18, FUNCTION = 19,
		RETURN = 20, THIS = 21, NEW = 22, VAR = 23, CONST = 24, SWITCH = 25, DELETE = 26, TRUE = 27, FALSE = 28, NULL = 29, UNDEFINED = 30, TRY = 31, CATCH = 32, FINALLY = 33,
		NAN = 34, INFINITY = 35, THROW = 36, DEFAULT = 37, LET = 38, ARGUMENTS = 39;

	private static final String[] operators = {
		"[", "]", "{", "}", ",", "=", ".."
	};
	public static final short left_m_bracket = 41, right_m_bracket = 42, left_l_bracket = 43, right_l_bracket = 44, comma = 45, equal = 46, range = 47;

	private static final TrieTree<Word> TOKEN_ID = new TrieTree<>();
	private static final MyBitSet TOKEN_CHAR = new MyBitSet();

	static {
		addKeywords(TOKEN_ID, 10, keywords);
		addSymbols(TOKEN_ID, TOKEN_CHAR, 41, operators);
		addWhitespace(TOKEN_CHAR);

	}

	public static String byId(short id) {
		return id <= 40 ? keywords[id-10] : operators[id-41];
	}

	{
		tokens = TOKEN_ID;
		literalEnd = TOKEN_CHAR;
	}
}
