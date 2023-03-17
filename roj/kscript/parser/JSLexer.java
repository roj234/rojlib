package roj.kscript.parser;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.word.I18n;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2020/9/27 12:31
 */
public final class JSLexer extends Tokenizer {
	private static final String[] keywords = TextUtil.split1(
		"for,while,do,continue,break,case,if,else,goto,function," +
			"return,this,new,var,const,switch,delete,true,false,null,undefined,try,catch,finally," +
			"NaN,Infinity,throw,default,let,arguments",
		',');
	public static final short FOR = 10, WHILE = 11, DO = 12, CONTINUE = 13, BREAK = 14, CASE = 15, IF = 16, ELSE = 17, GOTO = 18, FUNCTION = 19,
		RETURN = 20, THIS = 21, NEW = 22, VAR = 23, CONST = 24, SWITCH = 25, DELETE = 26, TRUE = 27, FALSE = 28, NULL = 29, UNDEFINED = 30, TRY = 31, CATCH = 32, FINALLY = 33,
		NAN = 34, INFINITY = 35, THROW = 36, DEFAULT = 37, LET = 38, ARGUMENTS = 39;

	private static final String[] operators = {
		"{", "}", "[", "]", "(", ")", "=>", ".", // 如果你想让js像java...  ->
		"++", "--", "!", "&&", "||",
		"<", ">", ">=", "<=", "===", "==", "!=",
		"~", "&", "|", "^",
		"+", "-", "*", "/", "%", "**", "<<", ">>", ">>>",
		"?", ":", ",", ";",
		"=", "+=", "-=", "*=", "/=", "%=", "&=", "^=", "|=", "<<=", ">>=", ">>>=",
		"$<", ">$", // 预处理器 (such as minecraft:xxx)
		"..." // spread
	};
	public static final short
		left_l_bracket = 41, right_l_bracket = 42, left_m_bracket = 43, right_m_bracket = 44, left_s_bracket = 45, right_s_bracket = 46, lambda = 47, dot = 48,
		inc = 49, dec = 50, logic_not = 51, logic_and = 52, logic_or = 53,
		lss = 54, gtr = 55, geq = 56, leq = 57, feq = 58, equ = 59, neq = 60,
		rev = 61, and = 62, or = 63, xor = 64,
		add = 65, sub = 66, mul = 67, div = 68, mod = 69, pow = 70, lsh = 71, rsh = 72, rsh_unsigned = 73,
		ask = 74, colon = 75, comma = 76, semicolon = 77,
		assign = 78, add_assign = 79, sub_assign = 80, mul_assign = 81, div_assign = 82, mod_assign = 83, and_assign = 84, xor_assign = 85, or_assign = 86, lsh_assign = 87, rsh_assign = 88, rsh_unsigned_assign = 89,
		preprocess_s = 90, preprocess_e = 91,
		spread = 92, rest = spread;

	private static final TrieTree<Word> TOKEN_ID = new TrieTree<>();
	private static final MyBitSet TOKEN_CHAR = new MyBitSet();
	private static final Int2IntMap priorities = new Int2IntMap();

	public static I18n translate;

	static {
		String path = System.getProperty("kscript.translate");
		try {
			translate = new I18n(path == null ? IOUtil.readUTF("META-INF/kscript.lang") : IOUtil.readUTF(new File(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		addKeywords(TOKEN_ID, 10, keywords);
		addSymbols(TOKEN_ID, TOKEN_CHAR, 41, operators);
		addWhitespace(TOKEN_CHAR);

		// 操作符优先级表
		Int2IntMap p = priorities;

		p.putInt(and, 100);
		p.putInt(or, 100);
		p.putInt(xor, 100);

		p.putInt(lsh, 99);
		p.putInt(rsh, 99);
		p.putInt(rsh_unsigned, 99);

		p.putInt(pow, 98);

		p.putInt(mul, 97);
		p.putInt(div, 97);
		p.putInt(mod, 97);

		p.putInt(add, 96);
		p.putInt(sub, 96);

		p.putInt(lss, 95);
		p.putInt(gtr, 95);
		p.putInt(geq, 95);
		p.putInt(leq, 95);
		p.putInt(feq, 95);
		p.putInt(equ, 95);
		p.putInt(neq, 95);

		p.putInt(logic_and, 94);
		p.putInt(logic_or, 94);
	}

	public static boolean isKeyword(Word w) {
		return w.type() >= 10 && w.type() <= 40;
	}
	public static boolean isSymbol(Word w) {
		return w.type() > 40 && w.type() < 100;
	}

	public static int symbolPriority(Word word) {
		int prio = priorities.getOrDefaultInt(word.type(), -1);
		if (prio == -1) throw new IllegalArgumentException(word.val() + " is not a binary operator");
		return prio;
	}

	public static int symbolOperateCount(short type) {
		switch (type) {
			case logic_not:
			case inc: case dec: case rev: return 1;
			case ask: return 3;
			default: return 2;
			case left_l_bracket: case right_l_bracket:
			case left_m_bracket: case right_m_bracket:
			case left_s_bracket: case right_s_bracket:
			case lambda: case dot:
			case colon: case comma: case semicolon: return 0;
		}
	}
	public static String byId(short id) {
		return id <= 40 ? keywords[id-10] : operators[id-41];
	}

	{
		i18n = translate;
		tokens = TOKEN_ID;
		literalEnd = TOKEN_CHAR;
	}

	public void disableSignedNumber() {
		System.out.println("DSN is not available in JSLexer:138");
	}

	public void enableSignedNumber() {

	}
}
