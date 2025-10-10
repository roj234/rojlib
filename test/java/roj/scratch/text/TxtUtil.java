package roj.scratch.text;

import roj.collect.BitSet;

/**
 * @author Roj234
 * @since 2025/09/14 04:00
 */
public class TxtUtil {
	// regexp: ([a-z])\1
	// 就这样

	public static final BitSet 标点 = BitSet.from("Γ。，、：∶；‘’“”〝〞ˆˇ﹕︰﹔﹖﹑·¨.¸;´？！～—｜‖＂〃｀@﹫¡¿﹏﹋︴々﹟#﹩$﹠&﹪%﹡﹢×﹦‐￣¯―﹨˜﹍﹎＿-~（）〈〉‹›﹛﹜『』〖〗［］《》〔〕{}「」【】︵︷︿︹︽_︶︸﹀︺︾ˉ﹂﹄︼﹁﹃︻▲●□…→");
	public static final String 半角 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 `~!@#$%^&*()_+-={}|[]:\";'<>?,./";
	public static final String 全角;
	static {
		char[] arr = 半角.toCharArray();
		for (int i = 0; i < arr.length; i++) arr[i] += 65248;
		全角 = new String(arr);
	}
	public static char normalize(char c) {
		if (c == '。') return '.';
		if (标点.contains(c)) return '!';

		int i = 全角.indexOf(c);
		if (i >= 0) c = 半角.charAt(i);
		return Character.toLowerCase(c);
	}
}
