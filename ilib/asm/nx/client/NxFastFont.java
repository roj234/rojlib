package ilib.asm.nx.client;

import roj.asm.nixim.*;
import roj.collect.Int2IntMap;
import roj.opengl.text.TextRenderer;
import roj.text.TextUtil;

import net.minecraft.client.gui.FontRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast Font Renderer
 *
 * @author Roj234
 * @since 2022/4/6 21:40
 */
@Nixim("/")
class NxFastFont extends FontRenderer {
	@Copy(targetIsFinal = true, staticInitializer = "init1")
	@Dynamic("_nixim_not_custom_font")
	private static Int2IntMap ID_MAP;

	private static void init1() {
		String MAGIC =
			"ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./012345" +
			"6789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000Çüéâäà" +
			"åçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄" +
			"▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000";
		Int2IntMap idMap = ID_MAP = new Int2IntMap(MAGIC.length());
		for (int i = 0; i < MAGIC.length(); i++) {
			idMap.putInt(MAGIC.charAt(i), i);
		}
	}

	@Shadow
	boolean unicodeFlag;

	NxFastFont() {
		super(null, null, null, false);
	}

	@Inject
	@Dynamic("_nixim_not_custom_font")
	public int getCharWidth(char c) {
		if (c == 160) {
			return 4;
		} else if (c == 167) {
			return -1;
		} else if (c == ' ') {
			return 4;
		} else {
			if (c > 0 && !unicodeFlag && ID_MAP.containsKey(c)) {
				return this.charWidth[ID_MAP.getOrDefaultInt(c, 0)];
			} else if (this.glyphWidth[c] != 0) {
				int j = this.glyphWidth[c] & 255;
				return ((j & 15) + 1 - (j >>> 4)) / 2 + 1;
			} else {
				return 0;
			}
		}
	}

	@Inject
	public List<String> listFormattedStringToWidth(String str, int wrapWidth) {
		List<String> list = new ArrayList<>();
		wrapFormattedStringToWidth__IL(str, wrapWidth, list);
		return list;
	}

	@Copy
	void wrapFormattedStringToWidth__IL(String str, int wrapWidth, List<String> list) {
		while (true) {
			int i = this.sizeStringToWidth(str, wrapWidth);
			if (str.length() <= i) {
				list.add(str);
				break;
			} else {
				String s = str.substring(0, i);
				list.add(s);
				char delim = str.charAt(i);
				boolean isLinkBreak = delim == ' ' || delim == '\n';
				str = getFormatFromString(s) + str.substring(i + (isLinkBreak ? 1 : 0));
			}
		}
	}

	@Inject
	String wrapFormattedStringToWidth(String str, int wrapWidth) {
		StringBuilder sb = new StringBuilder();

		while (true) {
			int i = this.sizeStringToWidth(str, wrapWidth);
			if (str.length() <= i) {
				return sb.append(str).toString();
			} else {
				sb.append(str, 0, i);
				char delim = str.charAt(i);
				boolean isLinkBreak = delim == ' ' || delim == '\n';
				str = getFormatFromStringInternal(str, 0, i).append(str, i + (isLinkBreak ? 1 : 0), str.length()).toString();
			}
		}
	}

	@Shadow
	private int sizeStringToWidth(String str, int wrapWidth) {
		return 0;
	}

	@Copy
	public static StringBuilder getFormatFromStringInternal(CharSequence text, int i, int end) {
		StringBuilder s = new StringBuilder();
		end--;

		while ((i = TextUtil.gIndexOf(text, '\u00a7', i)) != -1) {
			if (i < end) {
				char c0 = text.charAt(i + 1);
				if (TextRenderer.COLOR_CODE_TEXT.contains(c0)) {
					if (c0 <= 'f') {
						s.delete(0, s.length());
						s.append("§").append(c0);
					} else {
						s.append("§").append(c0);
					}
				}
			} else {
				break;
			}
		}

		return s;
	}
}
