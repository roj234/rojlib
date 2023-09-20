package roj.lavac.parser;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.mildwind.parser.JSLexer;
import roj.text.TextUtil;

/**
 * Java词法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public final class JavaLexer extends Tokenizer {
	public static final String[] keywords = TextUtil.split1("for,while,do,continue,break,case,if,else,goto,return,switch," +
		"this,new,true,false,null," +
		"void,int,long,double,float,short,byte,char,boolean," +
		"try,catch,finally,throw," +
		"public,protected,private,static,final,abstract," +
		"strictfp,native,volatile,transient,synchronized," +
		"class,interface,@interface,enum," +
		"implements,extends,super," +
		"package,import," +
		"default,throws,record,const,var,as", ',');
	public static final short
		FOR = 10, WHILE = 11, DO = 12, CONTINUE = 13, BREAK = 14, CASE = 15, IF = 16, ELSE = 17, GOTO = 18, RETURN = 19, SWITCH = 20,
		THIS = 21, NEW = 22,
		TRUE = 23, FALSE = 24, NULL = 25,
		VOID = 26, INT = 27, LONG = 28, DOUBLE = 29, FLOAT = 30, SHORT = 31, BYTE = 32, CHAR = 33, BOOLEAN = 34,
		TRY = 35, CATCH = 36, FINALLY = 37, THROW = 38,
		PUBLIC = 39, PROTECTED = 40, PRIVATE = 41,
		STATIC = 42, FINAL = 43, ABSTRACT = 44,
		STRICTFP = 45, NATIVE = 46, VOLATILE = 47, TRANSIENT = 48, SYNCHRONIZED = 49,
		CLASS = 50, INTERFACE = 51, AT_INTERFACE = 52, ENUM = 53,
		IMPLEMENTS = 54, EXTENDS = 55, SUPER = 56,
		PACKAGE = 57, IMPORT = 58,
		DEFAULT = 59, THROWS = 60, RECORD = 61, CONST = 62, VAR = 63, AS = 64;

	public static final String[] operators = {
		"{", "}",
		"[", "]",
		"(", ")",
		"->", ".", // java.lambda
		"++", "--",
		"!", "&&", "||",
		"<", ">", ">=", "<=",
		"===", "==", "!=",
		"~", "&", "|", "^",
		"+", "-", "*", "/", "%", "**",
		"<<", ">>", ">>>",
		"?", ":", ",", ";",
		"=", "+=", "-=", "*=", "/=", "%=",
		"&=", "^=", "|=", "<<=", ">>=", ">>>=",
		"$<", ">$", "@", "...", "::"
	};
	public static final short
		left_l_bracket = 101, right_l_bracket = 102,
		left_m_bracket = 103, right_m_bracket = 104,
		left_s_bracket = 105, right_s_bracket = 106,
		lambda = 107, dot = 108,
		inc = 109, dec = 110,
		logic_not = 111, logic_and = 112, logic_or = 113,
		lss = 114, gtr = 115, geq = 116, leq = 117,
	/*feq = 118,*/ equ = 119, neq = 120,
		rev = 121, and = 122, or = 123, xor = 124,
		add = 125, sub = 126, mul = 127, div = 128, mod = 129, pow = 130,
		lsh = 131, rsh = 132, rsh_unsigned = 133,
		ask = 134, colon = 135, comma = 136, semicolon = 137,
		assign = 138, add_assign = 139, sub_assign = 140, mul_assign = 141, div_assign = 142, mod_assign = 143,
		and_assign = 144, xor_assign = 145, or_assign = 146, lsh_assign = 147, rsh_assign = 148, rsh_unsigned_assign = 149,
		preprocess_s = 150, preprocess_e = 151, at = 152, varargs = 153, method_referent = 154;
	public static final short pow_assign = 999, optional_chaining = 998, spread = 997;

	public static final int CAT_METHOD = 1, CAT_TYPE = 2, CAT_MODIFIER = 4, CAT_HEADER = 8, CAT_TYPE_TYPE = 16, CAT_GENERIC_EXT = 32;

	private static final TrieTree<Word> TOKEN_ID = new TrieTree<>();
	private static final MyBitSet TOKEN_CHAR = new MyBitSet();
	private static final Int2IntMap priorities = new Int2IntMap();

	static {
		addKeywords(TOKEN_ID, 10, keywords);
		addSymbols(TOKEN_ID, TOKEN_CHAR, 101, operators);
		addWhitespace(TOKEN_CHAR);
		TOKEN_CHAR.remove('$');

		// 操作符优先级
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
		//p1.putInt(feq, 95);
		p.putInt(equ, 95);
		p.putInt(neq, 95);

		p.putInt(logic_and, 94);
		p.putInt(logic_or, 94);
	}

	public static short byName(String token) { return TOKEN_ID.get(token).type(); }
	public static String byId(short id) {
		return id <= 100 ? keywords[id - 10] : operators[id - 101];
	}
	public static boolean isKeyword(Word w) {return w.type() > 9 && w.type() <= 100;}
	public static boolean isSymbol(Word w) {return w.type() > 100 && w.type() <= 200;}

	public static int category(int id) {
		switch (id) {
			case FOR:
			case WHILE:
			case DO:
			case CONTINUE:
			case BREAK:
			case CASE:
			case IF:
			case ELSE:
			case GOTO:
			case SWITCH:
			case RETURN:
			case TRY:
			case CATCH:
			case FINALLY:
			case VAR:
			// 防止泛型boom
			case rsh: // >>
			case lsh: // <<
			case rsh_unsigned: // >>>
				return CAT_METHOD;
			case TRUE:
			case FALSE:
			case NULL:
			case VOID:
			case INT:
			case LONG:
			case DOUBLE:
			case FLOAT:
			case SHORT:
			case BYTE:
			case CHAR:
			case BOOLEAN:
				return CAT_TYPE;
			case PUBLIC:
			case PROTECTED:
			case PRIVATE:
			case STATIC:
			case FINAL:
			case ABSTRACT:
			case STRICTFP:
			case NATIVE:
			case VOLATILE:
			case TRANSIENT:
			case SYNCHRONIZED:
			case DEFAULT:
				return CAT_MODIFIER;
			case PACKAGE:
			case IMPORT:
			case AS:
				return CAT_HEADER;
			case CLASS:
			case INTERFACE:
			case ENUM:
			case RECORD:
			case IMPLEMENTS:
			case EXTENDS:
			case AT_INTERFACE:
				return CAT_TYPE_TYPE;
			default:
			case left_l_bracket:
			case right_l_bracket:
			case left_m_bracket:
			case right_m_bracket:
			case left_s_bracket:
			case right_s_bracket:
			case lambda:
			case colon:
			case comma:
			case semicolon:
				return 0;
				//return CAT_GENERIC_EXT;
		}
	}

	public static int symbolPriority(Word word) {
		int prio = priorities.get(word.type());
		if (prio == -1) throw new IllegalArgumentException(word.val() + " is not a binary operator");
		return prio;
	}

	public static int symbolOperateCount(short type) {
		switch (type) {
			case logic_not:
			case inc:
			case dec:
			case rev:
				return 1;
			case ask:
				return 3;
			default:
				return 2;
			case left_l_bracket:
			case right_l_bracket:
			case left_m_bracket:
			case right_m_bracket:
			case left_s_bracket:
			case right_s_bracket:
			case lambda:
			case dot:
			case colon:
			case comma:
			case semicolon:
				return 0;
		}
	}

	public long env;

	public static boolean isBinaryOperator(short type) {
		return false;
	}

	{
		i18n = JSLexer.translate;
		literalEnd = TOKEN_CHAR;
		tokens = TOKEN_ID;
	}

	@Override
	protected boolean isValidCombo(int off, Word word) {
		int category = category(word.type());
		if (category != 0 && (env & category) == 0) return false;
		return super.isValidCombo(off, word);
	}

	@Override
	protected Word readDigit(boolean sign) throws ParseException {
		return digitReader(sign, DIGIT_DFL|DIGIT_HBO);
	}

	public void disableKind(int operator) {
		env &= ~(1L << operator);
	}

	public void enableKind(int operator) {
		env |= 1L << operator;
	}
}
