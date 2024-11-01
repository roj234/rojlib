package roj.plugins.kscript.token;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.compiler.plugins.annotations.AutoIncrement;
import roj.config.I18n;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.io.IOUtil;
import roj.plugins.kscript.KCompiler;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;

import static roj.config.Word.LITERAL;

/**
 * JavaScript Lexer : string => tokens
 *
 * @author Roj234
 * @since  2020/10/3 19:20
 */
public class KSLexer extends Tokenizer {
    public static final String[] keywords = TextUtil.split1("for,while,do,continue,break,case,if,else,goto,function," +
        "return,this,new,var,const,switch,delete,true,false,null,\1UNDEF0,try,catch,finally," +
        "NaN,Infinity,throw,default,let,arguments", ',');
    public static final short FOR = 10, WHILE = 11, DO = 12, CONTINUE = 13, BREAK = 14, CASE = 15,
        IF = 16, ELSE = 17, GOTO = 18,
        FUNCTION = 19, RETURN = 20, THIS = 21, NEW = 22,
        VAR = 23, CONST = 24,
        SWITCH = 25,
        DELETE = 26,
        TRUE = 27, FALSE = 28, NULL = 29, UNDEFINED = 30, TRY = 31, CATCH = 32, FINALLY = 33,
        NAN = 34, INFINITY = 35, THROW = 36, DEFAULT = 37, LET = 38, ARGUMENTS = 39;

    public static final String[] operators = {
        "{", "}",
        "[", "]",
        "(", ")",
        "=>", "?", ":",
		".", ",", ";",
		// Unary
		"...",
        "++", "--", "~",  "!",
		// Number Binary (sorted to match java opcode order)
		"+", "-", "*", "/", "%", "**",
		"<<", ">>", ">>>",
		// Number Binary with boolean support
		"&", "|", "^",
		// Boolean Binary with boolean support
		"&&", "||",
		// nullish_consolidating (是这个名字吗？)
		"??",
		// Boolean Binary without boolean support
		"===", "!==", "==", "!=", "<", ">=", ">", "<=",
		//Assign
		"=",
		"+=", "-=", "*=", "/=", "%=", "**=",
		"<<=", ">>=", ">>>=",
		"&=", "|=", "^=",
    };
	@AutoIncrement(50)
    public static final short
        lBrace = 50, rBrace = 51,
        lBracket = 52, rBracket = 53,
        lParen = 54, rParen = 55,
        lambda = 56, ask = 57, colon = 58,
		dot = 59, comma = 60, semicolon = 61,

		spread = 62, rest = spread,
        inc = 63, dec = 64, rev = 65, logic_not = 66,

		add = 67, sub = 68, mul = 69, div = 70, mod = 71, pow = 72,
		lsh = 73, rsh = 74, rsh_unsigned = 75,

		and = 76, or = 77, xor = 78,

		logic_and = 79, logic_or = 80,
		nullish_consolidating = 81,

		feq = 82, fne = 83, equ = 84, neq = 85,
        lss = 86, geq = 87, gtr = 88, leq = 89,
        assign = 90,
		binary_assign_base_offset = 91, binary_assign_count = 12,
		binary_assign_delta = add-binary_assign_base_offset;

	public KSLexer() {}

    public static final TrieTree<Word> KS_TOKEN = new TrieTree<>();
    private static final MyBitSet KS_LEND = new MyBitSet();
    private static final Int2IntMap KS_C2C = new Int2IntMap();

    public static I18n i18n;
    static {
        String path = System.getProperty("roj.lavac.i18n");
        try {
            i18n = new I18n(path == null ? IOUtil.getTextResource("roj/compiler/kscript.lang") : IOUtil.readUTF(new File(path)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        addKeywords(KS_TOKEN, 10, keywords);
        addSymbols(KS_TOKEN, KS_LEND, 50, operators);

        // 文本块的支持
        KS_C2C.putAll(SIGNED_NUMBER_C2C);
        KS_C2C.remove('"');

		KS_TOKEN.put("'", new Word().init(0, ST_LITERAL_STRING, "'"));
		KS_TOKEN.put("\"", new Word().init(0, ST_STRING, "\""));
        KS_TOKEN.put("“", new Word().init(0, ST_STRING, "”"));
        KS_TOKEN.put("//", new Word().init(0, ST_SINGLE_LINE_COMMENT, "//"));
        KS_TOKEN.put("/*", new Word().init(0, ST_MULTI_LINE_COMMENT, "*/"));

        addWhitespace(KS_LEND);
        KS_LEND.remove('$');
        KS_LEND.add('"');

        alias("。", dot, KS_LEND);
        alias("，", comma, KS_LEND);
        alias("：", colon, KS_LEND);
        alias("；", semicolon, KS_LEND);
        alias("？", ask, KS_LEND);
        alias("（", lParen, KS_LEND);
        alias("）", rParen, KS_LEND);
        alias("【", lBracket, KS_LEND);
        alias("】", rBracket, KS_LEND);
        alias("《", lss, KS_LEND);
        alias("》", gtr, KS_LEND);
    }

    { literalEnd = KS_LEND; tokens = KS_TOKEN; }

    @Override
    public final Tokenizer init(CharSequence seq) {
        super.init(seq);
        LN = 1;
        lh = null;
        return this;
    }

    private static void alias(String kw, short begin, MyBitSet noLiterals) {
        KS_TOKEN.put(kw, new Word().init(begin, 0, kw));
        if (noLiterals != null) noLiterals.add(kw.charAt(0));
    }

    public static String repr(short id) { return id < 50 ? keywords[id - 10] : operators[id - 50]; }

	KCompiler lh;

    public final void setLineHandler(KCompiler lh) {
        this.lh = lh;
        lh.handleLineNumber(LN);
    }

	@Override
	@SuppressWarnings("fallthrough")
	public final Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i);
			switch (KS_C2C.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i))) {
						prevIndex = index = i;
						return digitReader(true, DIGIT_DFL|DIGIT_HBO);
					}
					// fall to literal(symbol)
				default:
					prevIndex = index = i;
					Word w = readSymbol();
					if (w == COMMENT_RETRY_HINT) {i = index;continue;}
					return w;
				case C_NUMBER:
					prevIndex = index = i;
					return digitReader(false, DIGIT_DFL|DIGIT_HBO);
				case C_WHITESPACE: i++;
			}
		}

		index = i;
		return eof();
	}

	@Override
	protected Word onNumberFlow(CharList str, short from, short to) {
		//LocalContext.get().report(Kind.ERROR, "lexer.number.overflow");
		return null;
	}

	@Override
	protected void afterWord() {
		int line = LN;
		super.afterWord();
		if (line != LN) {
			if (lh != null) lh.handleLineNumber(LN);
		}
	}

	@Override
	protected String i18n(String msg) { return msg; }

	public final Word except(short type) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		String s = type == LITERAL ? "标识符" : repr(type);
		throw err("unexpected_2:"+w.val()+':'+s);
	}

	public Word current() { return wd; }

	public boolean nextIf(short type) throws ParseException {
		if (next().type() == type) return true;
		retractWord(); return false;
	}

	public Word optionalNext(short type) throws ParseException {
		if (next().type() == type) next();
		return wd;
	}
}
