package roj.mapper.obf.nodename;

import roj.text.CharList;

import java.util.Random;

/**
 * Confusing chars
 *
 * @author Roj233
 * @since 2021/7/18 19:29
 */
public final class CharMix extends SimpleNamer {
	public int min, max;
	public char[] chars;

	public CharMix() {}

	public CharMix(String seq, int min, int max) {
		chars = seq.toCharArray();
		this.min = min;
		this.max = max;
	}

	public static CharMix newIII(int min, int max) {
		CharMix mix = new CharMix();
		mix.min = min;
		mix.max = max;
		mix.chars = new char[] {'1', 'I', 'i', 'l'};
		return mix;
	}

	public static CharMix newDelim(int min, int max) {
		CharMix mix = new CharMix();
		mix.min = min;
		mix.max = max;
		mix.chars = "ˉ-—一﹍﹎＿_".toCharArray();
		return mix;
	}

	public static CharMix 中华文化博大精深(int min, int max) {
		CharMix mix = new CharMix();
		mix.min = min;
		mix.max = max;
		// hard:";[)"
		// generic:":<>."
		mix.chars = "Γ。，、：；‘’“”〝〞ˆˇ﹕︰﹔﹖﹑·¨¸´？！～—｜‖＂〃｀@﹫¡¿﹏﹋︴々﹟#﹩$﹠&﹪%﹡﹢×﹦‐￣¯―﹨˜﹍﹎＿-~（）〈〉‹›﹛﹜『』〖〗［］《》〔〕{}「」【】︵︷︿︹︽_︶︸﹀︺︾ˉ﹂﹄︼﹁﹃︻▲●□…→".toCharArray();
		return mix;
	}

	@Override
	protected boolean generateName(Random rnd, CharList sb, int target) {
		int len = rnd.nextInt(max - min + 1) + min;
		while (len-- > 0) sb.append(chars[rnd.nextInt(chars.length)]);
		return true;
	}
}
