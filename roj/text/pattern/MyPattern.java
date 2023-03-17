package roj.text.pattern;

import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;

import java.util.List;
import java.util.Map;

import static roj.text.pattern.PatternLexer.*;

/**
 * 所有不在花括号内的均视为字面量
 * 格式:
 * {. [长度|num|1]} [长度]的任意字符
 * {. <N/F>"x" ... } 最近/最远 到x[]为止的任意字符
 * {<名称|literal> [长度|num|1]/[“允许的值” ...]} [长度]的相同标记（每次匹配相同）
 * {: "str" ...} 修饰后比较的字面量(取其一)
 * ! 加在最前,表示这个子匹配的结果输出
 * 修饰符加在类别标识之后：
 * 	A：'反混淆'修饰 适用于Solid
 * 	N：最近 适用于ANY
 * 	F：最远 适用于ANY
 * 	^：必须从一行开始匹配
 * 	$：必须匹配到一行末
 * @author Roj234
 * @since 2022/10/23 0023 4:25
 */
public abstract class MyPattern {
	public static Matcher compile(String s) throws ParseException {
		PatternLexer wr = new PatternLexer(); wr.init(s);
		List<MyPattern> list = new SimpleList<>();
		MutableInt out = new MutableInt();
		int i = 0, prevI = 0;
		outer:
		while (i < s.length()) {
			while (s.charAt(i) == '{') {
				if (prevI < i) list.add(new SolidChars(s.substring(prevI, i)));

				wr.index = i+1;
				list.add(parse(wr, out));
				wr.except(right_l_bracket);

				prevI = i = wr.index;
				if (i == s.length()) break outer;
			}
			i++;
		}

		if (prevI < i) list.add(new SolidChars(s.substring(prevI, i)));
		return new Matcher(list, out.getValue());
	}

	static MyPattern parse(PatternLexer wr, MutableInt outCount) throws ParseException {
		int flag = 0;

		Word w = wr.next();
		if (w.type() == gth) {
			flag |= OUT;
			w = wr.next();
			outCount.increment();
		}

		MyPattern p;
		// . LIT :
		switch (w.type()) {
			case dot:
				p = new Any();
				break;
			case Word.LITERAL:
				p = new Dynamic(w.val());
				break;
			case colon:
				p = null;
				break;
			default: throw wr.err("Unexpected header '"+w.val()+"'");
		}

		// alphabet modifier
		w = wr.next();
		if (w.type() == Word.LITERAL) {
			MyBitSet set = MyBitSet.from(w.val());
			// A, alpha conversation
			if (set.contains('A')) {
				if (p != null)
					throw wr.err("'A' modifier only applicable to SolidChars");
				flag |= DEOBF;
			}
			// B, begin
			if (set.contains('^')) {
				flag |= LINE_BEGIN;
			}
			// E, end
			if (set.contains('$')) {
				flag |= LINE_END;
			}
			// N, nearest
			if (set.contains('N')) {
				if (!(p instanceof Any))
					throw wr.err("'N' modifier only applicable to Any");
				//flag |= LITERALIZE;
				throw new UnsupportedOperationException("Not implement");
			}
			// F, farest
			if (set.contains('F')) {
				if (!(p instanceof Any))
					throw wr.err("'F' modifier only applicable to Any");
				//flag |= LITERALIZE;
				throw new UnsupportedOperationException("Not implement");
			}
			w = wr.next();
		}

		// length
		len:
		if (p != null) {
			if (w.type() == Word.INTEGER) {
				p.length = w.asInt();
				wr.next();
			}
		} else {
			if (w.type() != Word.STRING)
				throw wr.err("SolidChars require string");
			String v = w.val();
			if ((flag & DEOBF) != 0)
				v = deobfuscate(v).toString();

			w = wr.next();
			switch (w.type()) {
				default: wr.unexpected(w.val());break;
				case right_l_bracket:
					p = new SolidChars(v);
					break len;
				case Word.STRING: break;
			}

			List<String> list = new SimpleList<>();
			list.add(v);

			loop:
			while (wr.hasNext()) {
				v = w.val();
				if ((flag & DEOBF) != 0)
					v = deobfuscate(v).toString();
				list.add(v);

				w = wr.next();
				switch (w.type()) {
					default: wr.unexpected(w.val()); break;
					case right_l_bracket: break loop;
					case Word.STRING: break;
				}
			}
			p = new SolidCharsList(list);
		}

		wr.retractWord();
		p.flag = (byte) flag;
		return p;
	}
	static final int OUT = 1, DEOBF = 2, LINE_BEGIN = 4, LINE_END = 8;

	int length;
	byte flag;

	abstract boolean match(CharSequence s, MutableInt i, Map<String, String> ctx, CharList out);

	public static final MyBitSet CNSPEC = MyBitSet.from("Γ。，、：∶；‘’“”〝〞ˆˇ﹕︰﹔﹖﹑·¨.¸;´？！～—｜‖＂〃｀@﹫¡¿﹏﹋︴々﹟#﹩$﹠&﹪%﹡﹢×﹦‐￣¯―﹨˜﹍﹎＿-~（）〈〉‹›﹛﹜『』〖〗［］《》〔〕{}「」【】︵︷︿︹︽_︶︸﹀︺︾ˉ﹂﹄︼﹁﹃︻▲●□…→");
	static final String bj = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 `~!@#$%^&*()_+-={}|[]:\";'<>?,./";
	static final String qj;
	static {
		char[] arr = bj.toCharArray();
		for (int i = 0; i < arr.length; i++) {
			arr[i] += 65248;
		}
		qj = new String(arr);
	}

	static char deobfuscate(char c) {
		if (c == '。') return '.';
		if (CNSPEC.contains(c)) return '!';

		int i = qj.indexOf(c);
		if (i >= 0) c = bj.charAt(i);
		return Character.toLowerCase(c);
	}

	public static CharSequence deobfuscate(CharSequence seq) {
		CharList tmp = IOUtil.getSharedCharBuf().append(seq);
		for (int i = 0; i < tmp.length(); i++) {
			tmp.set(i, deobfuscate(tmp.charAt(i)));
		}
		return tmp.toString();
	}

	static int nextLine(CharSequence s, int i) {
		while (i < s.length()) {
			char c = s.charAt(i++);
			if (c == '\r') {
				if (i < s.length() && s.charAt(i) == '\n') {
					return i + 1 | (2 << 30);
				}
			} else if (c == '\n') {
				return i | (1 << 30);
			}
		}
		return -1;
	}
}
