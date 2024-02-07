package roj.compiler;

import roj.asm.tree.attr.LineNumberTable;
import roj.asm.visitor.CodeWriter;
import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.compiler.context.CompileContext;
import roj.compiler.diagnostic.Kind;
import roj.config.ParseException;
import roj.config.word.I18n;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;

import static roj.config.word.Word.CHARACTER;
import static roj.config.word.Word.LITERAL;

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
		"default,throws,record,const,var,as,instanceof," +
		"assert,yield,_with" +
		"__sub,__end_sub,__struct,__ignore_error,__NOIMPL", ',');
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
		DEFAULT = 59, THROWS = 60, RECORD = 61, CONST = 62, VAR = 63, AS = 64, INSTANCEOF = 65,
		ASSERT = 66, YIELD = 67, WITH = 68;

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
		lBrace = 100, rBrace = 101,
		lBracket = 102, rBracket = 103,
		lParen = 104, rParen = 105,
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

	public static final int CAT_METHOD = 1, CAT_TYPE = 2, CAT_MODIFIER = 4, CAT_HEADER = 8, CAT_TYPE_TYPE = 16;

	private static final TrieTree<Word> JAVA_TOKEN = new TrieTree<>();
	private static final MyBitSet JAVA_LEND = new MyBitSet();
	private static final Int2IntMap JAVA_C2C = new Int2IntMap();
	private static final Int2IntMap priorities = new Int2IntMap();

	public static I18n translate;
	static {
		String path = System.getProperty("kscript.translate");
		try {
			translate = new I18n(path == null ? IOUtil.getTextResource("META-INF/kscript.lang") : IOUtil.readUTF(new File(path)));
		} catch (IOException e) {
			e.printStackTrace();
		}

		addKeywords(JAVA_TOKEN, 10, keywords);
		addSymbols(JAVA_TOKEN, JAVA_LEND, 100, operators);

		// 文本块的支持
		JAVA_C2C.putAll(SIGNED_NUMBER_C2C);
		JAVA_C2C.remove('"');
		JAVA_TOKEN.put("\"", new Word().init(0, ST_STRING, "\""));
		JAVA_TOKEN.put("//", new Word().init(0, ST_SINGLE_LINE_COMMENT, "//"));
		JAVA_TOKEN.put("/*", new Word().init(0, ST_MULTI_LINE_COMMENT, "*/"));
		JAVA_TOKEN.put("\"\"\"", new Word().init(1, -1, "\"\"\""));
		JAVA_TOKEN.put("/**", new Word().init(2, -1, "*/"));

		addWhitespace(JAVA_LEND);
		JAVA_LEND.remove('$');

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

		alias("sr-finally", FINALLY, null);

		alias("。", dot, JAVA_LEND);
		alias("，", comma, JAVA_LEND);
		alias("：", colon, JAVA_LEND);
		alias("；", semicolon, JAVA_LEND);
		alias("？", ask, JAVA_LEND);
		alias("（", lParen, JAVA_LEND);
		alias("）", rParen, JAVA_LEND);
		alias("《", lss, JAVA_LEND);
		alias("》", gtr, JAVA_LEND);

		alias("的", dot, null);
		alias("新", NEW, null);
		alias("整数", INT, null);
		alias("数组", FINALLY, null);
		literalAlias("系统","System");
		literalAlias("输出流","out");
		literalAlias("打印","println");
		literalAlias("长度","length");
	}

	private static void literalAlias(String from, String to) {
		JAVA_TOKEN.put(from, new Word().init(Word.LITERAL, 0, to));
	}
	private static void alias(String kw, short begin, MyBitSet noLiterals) {
		JAVA_TOKEN.put(kw, new Word().init(begin, 0, kw));
		if (noLiterals != null) noLiterals.add(kw.charAt(0));
	}

	public static short byName(String token) { return JAVA_TOKEN.get(token).type(); }
	public static String byId(short id) { return id < 100 ? keywords[id - 10] : operators[id - 100]; }

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

	{ literalEnd = JAVA_LEND; tokens = JAVA_TOKEN; }

	@Override
	public final Tokenizer init(CharSequence seq) {
		super.init(seq);
		LN = 1;
		return this;
	}

	@Override
	@SuppressWarnings("fallthrough")
	public final Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;

		while (i < in.length()) {
			char c = in.charAt(i);
			switch (JAVA_C2C.getOrDefaultInt(c, 0)) {
				case C_MAY__NUMBER_SIGN:
					if (i+1 < in.length() && NUMBER.contains(in.charAt(i))) {
						prevIndex = index = i;
						return digitReader(true, DIGIT_DFL|DIGIT_HBO);
					}
					// fall to literal(symbol)
				default:
					prevIndex = index = i;
					return readSymbol();
				case C_NUMBER:
					prevIndex = index = i;
					return digitReader(false, DIGIT_DFL|DIGIT_HBO);
				case C_STRING:
					prevIndex = i;
					index = i+1;

					CharList list = readSlashString('\'', true);
					if (list.length() != 1) throw err("未结束的字符常量");
					return formClip(CHARACTER, list);
				case C_WHITESPACE: i++;
			}
		}

		index = i;
		return eof();
	}

	@Override
	protected void onNumberFlow(CharSequence str, short from, short to) {
		CompileContext.get().report(Kind.ERROR, "lexer.number.overflow");
	}

	@Override
	protected Word onSpecialToken(Word w) throws ParseException {
		if (w.type() == -2) {
			throw err("javadoc暂未支持");
		}

		CharSequence in = input;
		int i = index;
		CharList v = found; v.clear();

		char c = in.charAt(i++);
		if (c != '\n' && (c != '\r' || in.charAt(i++) != '\n'))
			throw err("期待换行", i);

		// 然后算初始的Indent
		int indent = i;
		while (true) {
			if (indent == in.length()) {
				indent = i;
				break;
			}

			c = in.charAt(indent);
			if (c != ' ' && c != '\t') break;
			indent++;
		}

		indent -= i;
		if (indent == 0) throw err("缩进不得为零", i);

		CharList line = new CharList();
		while (true) {
			int lend = TextUtil.gAppendToNextCRLF(in, i, line);

			identSucc: {
				fail:
				if (line.length() >= indent) {
					for (int j = 0; j < indent; j++) {
						char c1 = line.charAt(j);
						if (c1 != ' ' && c1 != '\t') break fail;
					}
					break identSucc;
				}

				throw err("文本块的最小缩进必须在第一行确定,我故意的", lend);
			}

			int j = line.trimLast().indexOf("\"\"\"", indent);
			if (j >= 0) {
				line.setLength(j);
				if (line.trimLast().length() > indent) v.append(line, indent, line.length());
				line._free();

				index = i+j+3;
				return formClip(Word.STRING, v);
			}

			i = lend;

			if (line.length() > indent) v.append(line, indent, line.length());
			v.append('\n');
			line.clear();
		}
	}

	@Override
	protected boolean isValidToken(int off, Word w) {
		int category = category(w.type());
		if (category != 0 && (env & category) == 0) {
			System.out.println("ignore word "+ w);
			return false;
		}
		return super.isValidToken(off, w);
	}

	CodeWriter labelGen;
	LineNumberTable table;

	@Override
	protected void afterWord() {
		int line = LN;
		super.afterWord();
		if (line != LN) {
			if (table != null) table.list.add(new LineNumberTable.Item(labelGen.label(), LN));
			System.out.println("line changed on "+index+"|"+wd);
		}
	}

	@Override
	protected String i18n(String msg) { return translate.translate(msg); }

	public final Word except(short type) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		String s = type == LITERAL ? "标识符" : byId(type);
		throw err("未预料的: " + w.val() + ", 期待: " + s);
	}

	public Word current() { return wd; }
}