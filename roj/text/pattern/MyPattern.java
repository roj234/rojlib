package roj.text.pattern;

import roj.collect.MyBitSet;
import roj.io.IOUtil;
import roj.text.CharList;

public abstract class MyPattern {
	// ([a-z])\1
	// 就这样

	public static final MyBitSet 标点 = MyBitSet.from("Γ。，、：∶；‘’“”〝〞ˆˇ﹕︰﹔﹖﹑·¨.¸;´？！～—｜‖＂〃｀@﹫¡¿﹏﹋︴々﹟#﹩$﹠&﹪%﹡﹢×﹦‐￣¯―﹨˜﹍﹎＿-~（）〈〉‹›﹛﹜『』〖〗［］《》〔〕{}「」【】︵︷︿︹︽_︶︸﹀︺︾ˉ﹂﹄︼﹁﹃︻▲●□…→");
	public static final String bj = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 `~!@#$%^&*()_+-={}|[]:\";'<>?,./";
	public static final String qj;
	static {
		char[] arr = bj.toCharArray();
		for (int i = 0; i < arr.length; i++) {
			arr[i] += 65248;
		}
		qj = new String(arr);
	}

	public static char deobfuscate(char c) {
		if (c == '。') return '.';
		if (标点.contains(c)) return '!';

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
}
