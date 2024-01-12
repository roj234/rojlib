package roj.compiler;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.config.word.I18n;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
		"default,throws,record,const,var,as,instanceof", ',');
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
		DEFAULT = 59, THROWS = 60, RECORD = 61, CONST = 62, VAR = 63, AS = 64, INSTANCEOF = 65;

	public static final String[] operators = {
		// Syntax
		"{", "}",
		"[", "]",
		"(", ")",
		"->", "=>", "?", ":",
		".", ",", ";",
		// Misc
		"?.", "@", "...", "::",
		// Unary
		"++", "--", "~", "!",
		// Number Binary (sorted to match java opcode order)
		"+", "-", "*", "/", "%", "**",
		"<<", ">>", ">>>",
		"&", "|", "^",
		// Boolean Binary
		"==", "!=", "<", ">=", ">", "<=",
		"&&", "||",
		// Assign
		"=",
		"+=", "-=", "*=", "/=", "%=", "**=",
		"<<=", ">>=", ">>>=", "&=", "^=", "|=",
	};
	public static final short
		left_l_br = 100, right_l_br = 101,
		left_m_br = 102, right_m_br = 103,
		left_s_br = 104, right_s_br = 105,
		lambda = 106, mapkv = 107, ask = 108, colon = 109,
		dot = 110, comma = 111, semicolon = 112,

		optional_chaining = 113, at = 114, varargs = 115, method_referent = 116,

		inc = 117, dec = 118, rev = 119, logic_not = 120,

		add = 121, sub = 122, mul = 123, div = 124, mod = 125, pow = 126,
		lsh = 127, rsh = 128, rsh_unsigned = 129,
		and = 130, or = 131, xor = 132,

		equ = 133, neq = 134, lss = 135, geq = 136, gtr = 137, leq = 138,
		logic_and = 139, logic_or = 140,

		assign = 141,
		add_assign = 142, sub_assign = 143, mul_assign = 144, div_assign = 145, mod_assign = 146, pow_assign = 147,
		lsh_assign = 148, rsh_assign = 149, rsh_unsigned_assign = 150, and_assign = 151, xor_assign = 152, or_assign = 153;
	public static final String[] operators_alt = {
		// Syntax
		"{", "}",
		"【", "】",
		"（", "）",
		};

	public static final int CAT_METHOD = 1, CAT_TYPE = 2, CAT_MODIFIER = 4, CAT_HEADER = 8, CAT_TYPE_TYPE = 16;

	private static final TrieTree<Word> TOKEN_ID = new TrieTree<>();
	private static final MyBitSet TOKEN_CHAR = new MyBitSet();
	private static final Int2IntMap priorities = new Int2IntMap();
	private static final Int2IntMap DSN_C2C = new Int2IntMap();
	static { fcSetDefault(DSN_C2C, PARSE_NUMBER|JAVA_COMMENT); }

	public static I18n translate;
	static {
		String path = System.getProperty("kscript.translate");
		try {
			translate = new I18n(path == null ? IOUtil.getTextResource("META-INF/kscript.lang") : IOUtil.readUTF(new File(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		addKeywords(TOKEN_ID, 10, keywords);
		addSymbols(TOKEN_ID, TOKEN_CHAR, 100, operators);
		addWhitespace(TOKEN_CHAR);
		alias(TOKEN_CHAR, "。", dot);
		alias(TOKEN_CHAR, "，", comma);
		alias(TOKEN_CHAR, "：", colon);
		alias(TOKEN_CHAR, "；", semicolon);
		alias(TOKEN_CHAR, "？", ask);
		alias(TOKEN_CHAR, "（", left_s_br);
		alias(TOKEN_CHAR, "）", right_s_br);
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
		p.putInt(equ, 95);
		p.putInt(neq, 95);

		p.putInt(logic_and, 94);
		p.putInt(logic_or, 94);
	}

	private static void alias(MyBitSet noLiterals, String kw, short begin) {
		TOKEN_ID.put(kw, new Word().init(begin, 0, kw));
		if (noLiterals != null) noLiterals.add(kw.charAt(0));
	}

	public static short byName(String token) { return TOKEN_ID.get(token).type(); }
	public static String byId(short id) { return id < 100 ? keywords[id - 10] : operators[id - 100]; }
	public static boolean isKeyword(Word w) {return w.type() > 9 && w.type() < 100;}

	public static int category(int id) {
		return switch (id) {
			// 防止泛型boom
			case VAR, rsh, rsh_unsigned -> CAT_METHOD;
			case TRUE, FALSE, NULL, VOID, INT, LONG, DOUBLE, FLOAT, SHORT, BYTE, CHAR, BOOLEAN -> CAT_TYPE;
			case PUBLIC, PROTECTED, PRIVATE, STATIC, FINAL, ABSTRACT, STRICTFP, NATIVE, VOLATILE, TRANSIENT, SYNCHRONIZED, DEFAULT -> CAT_MODIFIER;
			case PACKAGE, IMPORT, AS -> CAT_HEADER;
			case CLASS, INTERFACE, ENUM, RECORD, IMPLEMENTS, EXTENDS, AT_INTERFACE -> CAT_TYPE_TYPE;
			default -> 0;
		};
	}

	public static int binaryOperatorPriority(short op) { return priorities.getOrDefaultInt(op, -1); }

	public long env = -1;

	{
		i18n = translate;
		literalEnd = TOKEN_CHAR;
		tokens = TOKEN_ID;
		firstChar = DSN_C2C;
		// TODO 文本块
		// String html = """
		//              <html>
		//                  <body>
		//                      <p>Hello, world</p>
		//                  </body>
		//              </html>
		//              """;
	}

	@Override
	protected boolean isValidCombo(int off, Word word) {
		int category = category(word.type());
		if (category != 0 && (env & category) == 0) {
			System.out.println("ignore word "+word);
			return false;
		}
		return super.isValidCombo(off, word);
	}

	@Override
	protected Word readDigit(boolean sign) throws ParseException { return digitReader(sign, DIGIT_DFL|DIGIT_HBO); }

	// TODO
	private boolean isBlock;
	private List<Word> words = new SimpleList<>();
	public void startBlock() {

	}
	public void endBlock(boolean reset) {

	}
}