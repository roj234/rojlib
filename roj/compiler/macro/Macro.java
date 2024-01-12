package roj.compiler.macro;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2024/1/22 0022 12:04
 */
public class Macro {
	//private static final Pattern MACRO_HEADER = Pattern.compile("^\\s+/\\*ROJLIB_MACRO\\*/");
	private static final Pattern MACRO_REGEXP = Pattern.compile("^\\s+#(PARAM|DEFINE|UNDEF|IF|ELSE|ENDIF|LOOP)(.+$)", Pattern.MULTILINE);
	public static boolean applyMacro(CharList sb, Map<String, Word> param) throws ParseException {
		if (!checkMacro(sb)) return false;
		CharList out = new CharList();
		MyHashMap<String, Word> variable = new MyHashMap<>();
		MacroTokenizer wr = new MacroTokenizer();

		Matcher m = MACRO_REGEXP.matcher(sb);
		int i = 0;
		while (m.find(i)) {
			out.append(sb, i, m.start());

			wr.init(m.group(2));
			switch (m.group(1)) {
				// #PARAM name [type]
				case "PARAM":
					String val = wr.next().val();
					Word type = wr.next(); // maybe EOF
					variable.put(val, param.get(val));
				break;
				// #DEFINE name value
				case "DEFINE":
					variable.put(wr.next().val(), wr.next());
				break;
				// #UNDEF name
				case "UNDEF":
					variable.remove(wr.next().val());
				break;
				// a. #IF <boolean condition>
				// b. #IF DEFINED name

				case "IF":
				case "ELSE":
				case "ENDIF":
				case "LOOP":
			}
			i = m.end();
		}
		return true;
	}

	public static boolean checkMacro(CharList sb) {
		char[] list = sb.list;
		for (int i = 0; i < sb.length(); i++) {
			if (!Tokenizer.WHITESPACE.contains(list[i])) {
				return TextUtil.regionMatches(sb, i, "/*MACRO*/", 0);
			}
		}
		sb.clear();
		return false;
	}

}