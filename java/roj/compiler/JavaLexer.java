package roj.compiler;

import roj.asm.tree.attr.LineNumberTable;
import roj.collect.Int2IntMap;
import roj.collect.IntBiMap;
import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.doc.Javadoc;
import roj.compiler.plugins.annotations.AutoIncrement;
import roj.config.I18n;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static roj.config.Word.EOF;
import static roj.config.Word.LITERAL;

/**
 * Java词法分析器
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public final class JavaLexer extends Tokenizer {
	public static final short CHARACTER = 9;

	public static final String[] keywords = TextUtil.split1(
		// 类结构
		// 这些除外:
		// class => 常量
		// extends, super => 泛型
		"package,module,package-restricted,import,as," +
		"interface,@interface,enum,record,__struct,class," +
		"sealed,non-sealed," +
		"permits,implements,extends,super," +
		// MethodOnly
		"default,throws," +

		// 权限
		"public,protected,private,static,final," +
		// FieldOnly
		"volatile,transient," +
		// MethodOnly
		"strictfp,abstract,native,synchronized," +
		"_async,const,\1MOD1," +

		// 类型
		"void,boolean,byte,char,short,int,long,float,double," +
		// 表达式
		"this,true,false,null,new,instanceof,\1EXP1," +

		// 方法内
		"for,while,do," +
		"continue,break,goto,return,throw," +
		"if,switch,try,assert," +
		"\1STM1,\1STM2,yield,with,_await,\1STM0,"+
		// statement rest
		"else,case,defer,catch,finally," +

		// 模块 with: #74
		"requires,exports,opens,uses,provides,transitive,to", ',');
	@AutoIncrement(10)
	public static final short
		PACKAGE = 10, MODULE = 11, PACKAGE_RESTRICTED = 12, IMPORT = 13, AS = 14,
		INTERFACE = 15, AT_INTERFACE = 16, ENUM = 17, RECORD = 18, STRUCT = 19, CLASS = 20,
		SEALED = 21, NON_SEALED = 22,
		PERMITS = 23, IMPLEMENTS = 24, EXTENDS = 25, SUPER = 26,
		DEFAULT = 27, THROWS = 28,

		PUBLIC = 29, PROTECTED = 30, PRIVATE = 31, STATIC = 32, FINAL = 33,
		VOLATILE = 34, TRANSIENT = 35,
		STRICTFP = 36, ABSTRACT = 37, NATIVE = 38, SYNCHRONIZED = 39,
		ASYNC = 40, CONST = 41, _1MOD1 = 42,

		VOID = 43, BOOLEAN = 44, BYTE = 45, CHAR = 46, SHORT = 47, INT = 48, LONG = 49, FLOAT = 50, DOUBLE = 51,
		THIS = 52, TRUE = 53, FALSE = 54, NULL = 55, NEW = 56, INSTANCEOF = 57, _1EXP1 = 58,

		FOR = 59, WHILE = 60, DO = 61,
		CONTINUE = 62, BREAK = 63, GOTO = 64, RETURN = 65, THROW = 66,
		IF = 67, SWITCH = 68, TRY = 69, ASSERT = 70,
		_1STM1 = 71, _1STM2 = 72, YIELD = 73, WITH = 74, AWAIT = 75, _1STM0 = 76,
		ELSE = 77, CASE = 78, DEFER = 79, CATCH = 80, FINALLY = 81,

		REQUIRES = 82, EXPORTS = 83, OPENS = 84, USES = 85, PROVIDES = 86, TRANSITIVE = 87, TO = 88;

	public static final String[] operators = {
		// Syntax
		"{", "}",
		"[", "]",
		"(", ")",
		"->", ">>>>>>>>>>>", "?", ":",
		".", ",", ";",
		// Misc
		"?.", "@", "...", "::",
		// Unary
		"++", "--", "~", "!",
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
		"==", "!=", "<", ">=", ">", "<=",
		// Assign
		"=",
		"+=", "-=", "*=", "/=", "%=", "**=",
		"<<=", ">>=", ">>>=", "&=", "^=", "|=",
		"<<<"
	};
	@AutoIncrement(100)
	public static final short
		lBrace = 100, rBrace = 101,
		lBracket = 102, rBracket = 103,
		lParen = 104, rParen = 105,
		lambda = 106, UNUSED = 107, ask = 108, colon = 109,
		dot = 110, comma = 111, semicolon = 112,

		optional_chaining = 113, at = 114, varargs = 115, method_referent = 116,

		inc = 117, dec = 118, rev = 119, logic_not = 120,

		add = 121, sub = 122, mul = 123, div = 124, mod = 125, pow = 126,
		lsh = 127, rsh = 128, rsh_unsigned = 129,
		and = 130, or = 131, xor = 132,
		logic_and = 133, logic_or = 134,
		nullish_consolidating = 135,
		equ = 136, neq = 137, lss = 138, geq = 139, gtr = 140, leq = 141,

		assign = 142,
		add_assign = 143, sub_assign = 144, mul_assign = 145, div_assign = 146, mod_assign = 147, pow_assign = 148,
		lsh_assign = 149, rsh_assign = 150, rsh_unsigned_assign = 151, and_assign = 152, xor_assign = 153, or_assign = 154,
		direct_assign = 155;

	public static final TrieTree<Word> JAVA_TOKEN = new TrieTree<>();
	private static final MyBitSet JAVA_LEND = new MyBitSet();
	private static final Int2IntMap JAVA_C2C = new Int2IntMap();

	public static I18n translate;
	static {
		String path = System.getProperty("roj.lavac.i18n");
		try {
			translate = new I18n(path == null ? IOUtil.getTextResource("roj/compiler/kscript.lang") : IOUtil.readUTF(new File(path)));
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

		alias("...finally", FINALLY, null);
		alias("...switch", SWITCH, null);
		alias("...for", FOR, null);

		alias("。", dot, JAVA_LEND);
		alias("，", comma, JAVA_LEND);
		alias("：", colon, JAVA_LEND);
		alias("；", semicolon, JAVA_LEND);
		alias("？", ask, JAVA_LEND);
		alias("（", lParen, JAVA_LEND);
		alias("）", rParen, JAVA_LEND);
		alias("【", lBracket, JAVA_LEND);
		alias("】", rBracket, JAVA_LEND);
		alias("《", lss, JAVA_LEND);
		alias("》", gtr, JAVA_LEND);
	}

	{ literalEnd = JAVA_LEND; tokens = JAVA_TOKEN; }

	@Override
	public final Tokenizer init(CharSequence seq) {
		super.init(seq);
		LN = 1;
		return this;
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

	public static final int STATE_CLASS = 0, STATE_MODULE = 1, STATE_EXPR = 2, STATE_TYPE = 3;
	public int state = STATE_CLASS;

	@Override
	protected Word newWord() {
		return new Word() {
			@Override
			public short type() {
				short id = super.type();
				return id <= CHARACTER || (id >= 100 && id <= 200) || switch (state) {
					case STATE_CLASS -> id <= JavaLexer.DOUBLE;
					case STATE_EXPR -> id >= VOID && id <= FINALLY || id == CLASS || id == FINAL || id == CONST || id == DEFAULT || id == SUPER || id > 999;
					case STATE_TYPE -> id >= VOID && id <= FINALLY || id == SUPER || id == EXTENDS;
					case STATE_MODULE -> id >= EXPORTS && id <= TO || id == WITH || id == STATIC;
					default -> false;
				} ? id : LITERAL;
			}
		};
	}

	@Override
	protected boolean isValidToken(int off, Word w) {
		if (!super.isValidToken(off, w)) return false;
		int id = w.type();
		return state != STATE_TYPE || (id != rsh && id != rsh_unsigned);
	}

	public int skipBrace() throws ParseException {
		//assert => for normal call, but not lambda compiler (index == 0)
		//if (wd.type() != lBrace) throw new IllegalStateException("not lBrace");
		int pos = index;

		int L = 1;

		while (true) {
			Word w = next();
			switch (w.type()) {
				case lBrace: L++; break;
				case rBrace: if (--L == 0) return pos; break;
				case EOF: throw err("braceMismatch");
			}
		}
	}
	public void init(int pos, int ln, int lnIndex) throws ParseException {
		next();
		index = pos;
		LN = ln;
		LNIndex = lnIndex;
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
					Word w = readSymbol();
					if (w == COMMENT_RETRY_HINT) {i = index;continue;}
					return w;
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
		LocalContext.get().report(Kind.ERROR, "lexer.number.overflow");
	}

	public List<Javadoc> javadocCollector;

	@Override
	protected Word onSpecialToken(Word w) throws ParseException {
		if (w.type() == 2) {
			if (javadocCollector == null) multiLineComment(null, "*/");
			else readJavadoc();
			return null;
		} else {
			return readStringBlock();
		}
	}
	private void readJavadoc() {
		var doc = new Javadoc();
		var in = input;
		int i = index;
		var line = found;
		do {
			line.clear();
			i = TextUtil.gAppendToNextCRLF(in, i, line);
			for (int j = 0; j < line.length(); j++) {
				char c = line.charAt(j);
				if (c != ' ' && c != '\t') {
					isBlockTag:
					if (c == '@' && !WHITESPACE.contains(line.charAt(++j))) {
						int k = j;
						while (k < line.length()) {
							if (WHITESPACE.contains(line.charAt(k))) {
								doc.visitBlockTag(line.substring(j, k));
								line.delete(0, k+1);
								break isBlockTag;
							}
							k++;
						}
						doc.visitText(line.substring(j));
						line.clear();
					}
					// not block
					break;
				}
			}

			doc.visitText(line);

			while (true) {
				char c = in.charAt(i);
				if (c == ' ' || c == '\t') i++;
				else {
					if (c == '*') i++;
					break;
				}
			}
		} while (in.charAt(i) != '/');

		doc.visitEnd();
		index = i+1;
	}
	private Word readStringBlock() throws ParseException {
		CharSequence in = input;
		int i = index;
		CharList v = found; v.clear();

		char c = in.charAt(i++);
		if (c != '\n' && (c != '\r' || in.charAt(i++) != '\n'))
			throw err("lexer.stringBlock.noCRLF", i);

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
		if (indent == 0) throw err("lexer.stringBlock.noIndent", i);

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

				throw err("lexer.stringBlock.indentChange", lend);
			}

			int j = line.trimLast().indexOf("\"\"\"", indent);
			if (j >= 0) {
				line.setLength(j);
				if (line.trimLast().length() > indent) v.append(line, indent, line.length());
				line._free();

				index = i+j+3;
				return formClip(Word.STRING, removeSlashes(v));
			}

			i = lend;

			if (line.length() > indent) v.append(line, indent, line.length());
			v.append('\n');
			line.clear();
		}
	}

	private IntBiMap<MethodWriter> stack = new IntBiMap<>();
	private MethodWriter labelGen;
	private LineNumberTable lines;

	public void setCw(MethodWriter cw) {
		if (!stack.containsValue(cw)) {
			stack.putByValue(stack.size(), cw);
			if (lines != null) lines.add(cw.label(), LN);
		} else {
			int id = stack.getInt(cw);
			while (++id < stack.size()) stack.remove(id);
		}
		labelGen = cw;
	}

	public void setLines(LineNumberTable _new) {
		assert lines == null;
		lines = _new;
		stack.clear();
	}
	public void getLines(MethodWriter cw) {
		assert labelGen == cw;

		if (labelGen.bci() == lines.lastBci()) lines.list.pop();
		if (!lines.isEmpty()) cw.lines = lines;

		lines = null;
		labelGen = null;
		stack.clear();
	}

	@Override
	protected void afterWord() {
		int line = LN;
		super.afterWord();
		if (line != LN) {
			if (lines != null && (labelGen.bci() != lines.lastBci())) {
				lines.add(labelGen.label(), LN);
			}
		}
	}

	@Override
	protected String i18n(String msg) { return translate.translate(msg); }

	public final Word except(short type) throws ParseException {
		Word w = next();
		if (w.type() == type) return w;
		String s = type == LITERAL ? "标识符" : byId(type);
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

	public int setState(int state1) {
		int prev = state;
		state = state1;
		return prev;
	}
}