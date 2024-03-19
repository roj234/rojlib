package roj.text;

import roj.collect.MyBitSet;
import roj.collect.TrieTree;
import roj.config.Tokenizer;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO merge UTFCoder and this
 * @author Roj234
 * @since 2023/2/23 0023 18:06
 */
public class EscapeUtil {
	public static EscapeUtil getInstance() {
		return new EscapeUtil();
	}


	public static String decodeURI(CharSequence src) throws MalformedURLException {
		ByteList bb = new ByteList();
		try {
			return unescape(src, new CharList(), bb).toStringAndFree();
		} finally {
			bb._free();
		}
	}
	public static <T extends Appendable> T unescape(CharSequence src, T sb, DynByteBuf tmp) throws MalformedURLException {
		tmp.clear();

		int len = src.length();
		int i = 0;

		while (i < len) {
			char c = src.charAt(i);
			switch (c) {
				case '+': c = ' '; break;
				case '%':
					try {
						while (true) {
							if (i+1 >= len) {
								try {
									sb.append(src.charAt(i++));
								} catch (IndexOutOfBoundsException ignored) {}
								break;
							}
							if (src.charAt(i) != '%') break;

							if (src.charAt(i+1) == 'u') {
								if (tmp.wIndex() > 0) {
									UTF8MB4.CODER.decodeFixedIn(tmp, tmp.wIndex(), sb);
									tmp.clear();
								}

								if (i+6 > len) break;
								try {
									sb.append((char) Tokenizer.parseNumber(src, i + 2, i + 6, 1));
								} catch (NumberFormatException|IndexOutOfBoundsException e) {
									i++;
									break;
								}
								i += 6;
							} else {
								try {
									tmp.put((byte) Tokenizer.parseNumber(src, i + 1, i + 3, 1));
								} catch (NumberFormatException|IndexOutOfBoundsException e) {
									i++;
									break;
								}
								i += 3;
							}
						}

						if (tmp.wIndex() > 0) {
							UTF8MB4.CODER.decodeFixedIn(tmp, tmp.wIndex(), sb);
							tmp.clear();
						}

						continue;
					} catch (Exception e) {
						// not compatible with RFC 2396
						throw new MalformedURLException("无法作为UTF8解析:" + e.getMessage());
					}
			}

			try {
				sb.append(c);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
			i++;
		}
		return sb;
	}

	public static final MyBitSet URI_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!@#$&*()_+-=/?.,:;'");
	public static final MyBitSet URI_COMPONENT_SAFE = MyBitSet.from(TextUtil.digits).addAll("~!*()_-.'");

	public static String encodeURI(CharSequence src) { return encodeURI(new CharList(), src).toStringAndFree(); }
	public static String encodeURIComponent(CharSequence src) { return encodeURIComponent(new CharList(), src).toStringAndFree(); }
	public static <T extends Appendable> T encodeURI(T sb, CharSequence src) {
		ByteList bb = new ByteList();
		try {
			return escape(bb.putUTFData(src), sb, URI_SAFE);
		} finally {
			bb._free();
		}
	}
	public static <T extends Appendable> T encodeURIComponent(T sb, CharSequence src) {
		ByteList bb = new ByteList();
		try {
			return escape(bb.putUTFData(src), sb, URI_COMPONENT_SAFE);
		} finally {
			bb._free();
		}
	}
	public static <T extends Appendable> T escape(CharSequence src, T sb, MyBitSet safe) {
		try {
			for (int i = 0; i < src.length(); i++) {
				char c = src.charAt(i);
				if (safe.contains(c)) sb.append(c);
				else sb.append("%").append(Integer.toString(c, 16));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}

	public static String escapeFilePath(CharSequence src) { return escapeFilePath(new CharList(), src, FILE_PATH_INVALID).toStringAndFree(); }
	public static String escapeFileName(CharSequence src) { return escapeFilePath(new CharList(), src, FILE_NAME_INVALID).toStringAndFree(); }
	// 为了与其他解析器兼容，我会序列化+号
	private static final MyBitSet FILE_NAME_INVALID = MyBitSet.from("\\/:*?\"<>|+"), FILE_PATH_INVALID = MyBitSet.from(":*?\"<>|+");
	public static <T extends Appendable> T escapeFilePath(T sb, CharSequence src, MyBitSet invalid) {
		try {
			for (int i = 0; i < src.length(); i++) {
				char c = src.charAt(i);
				if (!invalid.contains(c)) sb.append(c);
				else sb.append("%").append(Integer.toString(c, 16));
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return sb;
	}
	public static <T extends Appendable> T unescapeFilePath(CharSequence src, T sb, DynByteBuf tmp) throws MalformedURLException {
		tmp.clear();

		int len = src.length();
		int i = 0;

		while (i < len) {
			char c = src.charAt(i);
			if (c == '%') {
				try {
					while (true) {
						if (i + 1 >= len) {
							try {
								sb.append(src.charAt(i++));
							} catch (IndexOutOfBoundsException ignored) {}
							break;
						}
						if (src.charAt(i) != '%') break;

						try {
							tmp.put((byte) Tokenizer.parseNumber(src, i + 1, i + 3, 1));
						} catch (NumberFormatException | IndexOutOfBoundsException e) {
							i++;
							break;
						}
						i += 3;
					}

					if (tmp.wIndex() > 0) {
						UTF8MB4.CODER.decodeFixedIn(tmp, tmp.wIndex(), sb);
						tmp.clear();
					}

					continue;
				} catch (Exception e) {
					// not compatible with RFC 2396
					throw new MalformedURLException("无法作为UTF8解析:" + e.getMessage());
				}
			}

			try {
				sb.append(c);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
			i++;
		}
		return sb;
	}


	public static String htmlspecial(CharSequence str) { return h(str).toStringAndFree(); }
	public static String htmlspecial_decode(CharSequence str) { return hd(str).toStringAndFree(); }
	public static String htmlspecial_decode_all(CharSequence str) { return HtmlSpecialDecodeFull(new CharList(str)).toStringAndFree(); }

	public static CharList htmlspecial(CharList sb, CharSequence str) { return (CharList) h(str).appendToAndFree(sb); }
	public static CharList htmlspecial_decode(CharList sb, CharSequence str) { return (CharList) hd(str).appendToAndFree(sb); }
	public static CharList htmlspecial_decode_all(CharList sb, CharSequence str) { return (CharList) HtmlSpecialDecodeFull(new CharList(str)).appendToAndFree(sb); }

	public static CharList htmlspecial_decode_all_inline(CharList sb) { return HtmlSpecialDecodeFull(sb); }

	private static CharList h(CharSequence s) { return new CharList(s).replaceMulti(HtmlSpecialEncode); }
	private static CharList hd(CharSequence s) { return new CharList(s).replaceMulti(HtmlSpecialDecode); }
	public static CharList HtmlSpecialDecodeFull(CharList sb) {
		sb.replaceMulti(LargeTable.Table);
		Matcher m = AmpBang.matcher(sb);

		int pos = 0;
		char[] ab = new char[2];
		while (m.find(pos)) {
			int start = m.start(0);
			int end = m.end(0);
			int id = Integer.parseInt(m.group(1));

			String s1;
			if (id <= 0xFFFF) {
				ab[0] = (char) id;
				s1 = new String(ab,0,1);
			} else {
				ab[0] = Character.highSurrogate(id);
				ab[1] = Character.lowSurrogate(id);
				s1 = new String(ab);
			}

			sb.replace(start, end, s1);
			pos = Math.max(0, end-11);
		}
		return sb;
	}

	private static final Pattern AmpBang = Pattern.compile("&#([0-9]{1,8});");
	public static final TrieTree<String> HtmlSpecialEncode = new TrieTree<>(), HtmlSpecialDecode = new TrieTree<>();
	static {
		String[] T_encode = {"&amp;", "&lt;", "&gt;", "&quot;", "&apos;"}, T_decode = {"&", "<", ">", "\"", "'"};
		for (int i = 0; i < T_encode.length; i++) {
			HtmlSpecialEncode.put(T_decode[i], T_encode[i]);
			HtmlSpecialDecode.put(T_encode[i], T_decode[i]);
		}
	}
	private static final class LargeTable {
		static final TrieTree<String> Table = new TrieTree<>();
		static {
			// Reference: https://www.degraeve.com/reference/specialcharacters.php
			// Reference: https://box3.cn
			final String TABLE =
				"&Aacute;Á\n" + "&Aring;Å\n" + "&Acirc;Â\n" + "&Atilde;Ã\n" + "&Alpha;Α\n" + "&Auml;Ä\n" +
					"&AElig;Æ\n" + "&Agrave;À\n" + "&Beta;Β\n" + "&Ccedil;Ç\n" + "&Chi;Χ\n" + "&Dagger;‡\n" + "&Delta;Δ\n" +
					"&Epsilon;Ε\n" + "&Eacute;É\n" + "&Ecirc;Ê\n" + "&ETH;Ð\n" + "&Eta;Η\n" + "&Euml;Ë\n" + "&Egrave;È\n" +
					"&Gamma;Γ\n" + "&Iacute;Í\n" + "&Iuml;Ï\n" + "&Icirc;Î\n" + "&Igrave;Ì\n" + "&Iota;Ι\n" + "&Kappa;Κ\n" +
					"&Lambda;Λ\n" + "&Mu;Μ\n" + "&Ntilde;Ñ\n" + "&Nu;Ν\n" + "&Oacute;Ó\n" + "&Oslash;Ø\n" + "&Ocirc;Ô\n" +
					"&Otilde;Õ\n" + "&Ouml;Ö\n" + "&Omicron;Ο\n" + "&Omega;Ω\n" + "&Ograve;Ò\n" + "&Phi;Φ\n" +
					"&Pi;Π\n" + "&Prime;″\n" + "&Psi;Ψ\n" + "&Rho;Ρ\n" + "&Sigma;Σ\n" + "&Theta;Θ\n" + "&THORN;Þ\n" +
					"&Tau;Τ\n" + "&Upsilon;Υ\n" + "&Uacute;Ú\n" + "&Uuml;Ü\n" + "&Ucirc;Û\n" + "&Ugrave;Ù\n" +
					"&Xi;Ξ\n" + "&Yacute;Ý\n" + "&Zeta;Ζ\n" + "&aacute;á\n" + "&aring;å\n" + "&acute;´\n" + "&acirc;â\n" +
					"&asymp;≈\n" + "&atilde;ã\n" + "&alpha;α\n" + "&alefsym;ℵ\n" + "&auml;ä\n" + "&amp;&\n" + "&aelig;æ\n" +
					"&ang;∠\n" + "&and;∧\n" + "&agrave;à\n" + "&bdquo;„\n" + "&beta;β\n" + "&bull;•\n" + "&brvbar;¦\n" +
					"&brkbar;¦\n" + "&chi;χ\n" + "&cap;∩\n" + "&crarr;↵\n" + "&ccedil;ç\n" + "&clubs;♣\n" + "&cent;¢\n" +
					"&cedil;¸\n" + "&curren;¤\n" + "&cup;∪\n" + "&copy;©\n" + "&cong;≅\n" + "&deg;°\n" + "&delta;δ\n" +
					"&dagger;†\n" + "&darr;↓\n" + "&divide;÷\n" + "&die;¨\n" + "&diams;♦\n" + "&dArr;⇓\n" + "&epsilon;ε\n" +
					"&exist;∃\n" + "&eacute;é\n" + "&equiv;≡\n" + "&ecirc;ê\n" + "&eth;ð\n" + "&eta;η\n" + "&euml;ë\n" +
					"&empty;∅\n" + "&egrave;è\n" + "&frac14;¼\n" + "&frac12;½\n" + "&frac34;¾\n" + "&frasl;⁄\n" +
					"&forall;∀\n" + "&gt;>\n" + "&gamma;γ\n" + "&ge;≥\n" + "&harr;↔\n" + "&hearts;♥\n" + "&hellip;…\n" +
					"&hibar;¯\n" + "&hArr;⇔\n" + "&iacute;í\n" + "&iquest;¿\n" + "&icirc;î\n" + "&isin;∈\n" + "&iuml;ï\n" +
					"&iexcl;¡\n" + "&image;ℑ\n" + "&infin;∞\n" + "&int;∫\n" + "&igrave;ì\n" + "&iota;ι\n" + "&kappa;κ\n" +
					"&lArr;⇐\n" + "&larr;←\n" + "&laquo;«\n" + "&lambda;λ\n" + "&lsquo;‘\n" + "&lsaquo;‹\n" + "&lceil;⌈\n" +
					"&lt;<\n" + "&ldquo;“\n" + "&le;≤\n" + "&lfloor;⌊\n" + "&lowast;∗\n" + "&loz;◊\n" + "&mdash;—\n" +
					"&middot;·\n" + "&minus;−\n" + "&micro;µ\n" + "&macr;¯\n" + "&mu;μ\n" + "&nabla;∇\n" + "&ni;∋\n" +
					"&nbsp; \n" + "&nsub;⊄\n" + "&ntilde;ñ\n" + "&ndash;–\n" + "&nu;ν\n" + "&nearr;↗\n" + "&ne;≠\n" + //  
					"&not;¬\n" + "&notin;∉\n" + "&nwarr;↖\n" + "&oplus;⊕\n" + "&oacute;ó\n" + "&ordf;ª\n" + "&ordm;º\n" +
					"&or;∨\n" + "&ocirc;ô\n" + "&oslash;ø\n" + "&otilde;õ\n" + "&otimes;⊗\n" + "&oline;‾\n" + "&ouml;ö\n" +
					"&omicron;ο\n" + "&omega;ω\n" + "&ograve;ò\n" + "&phi;φ\n" + "&pi;π\n" + "&piv;ϖ\n" + "&para;¶\n" +
					"&part;∂\n" + "&prod;∏\n" + "&prop;∝\n" + "&prime;′\n" + "&psi;ψ\n" + "&plusmn;±\n" + "&permil;‰\n" +
					"&perp;⊥\n" + "&pound;£\n" + "&quot;\"\n" + "&rho;ρ\n" + "&rArr;⇒\n" + "&rarr;→\n" + "&radic;√\n" +
					"&raquo;»\n" + "&rsquo;’\n" + "&rsaquo;›\n" + "&rceil;⌉\n" + "&rdquo;”\n" + "&reg;®\n" + "&real;ℜ\n" +
					"&rfloor;⌋\n" + "&shy;\n" + "&spades;♠\n" + "&sigma;σ\n" + "&sigmaf;ς\n" + "&sim;∼\n" + "&sbquo;‚\n" +
					"&szlig;ß\n" + "&sdot;⋅\n" + "&sup1;¹\n" + "&supe;⊇\n" + "&sup2;²\n" + "&sup3;³\n" + "&sup;⊃\n" +
					"&sum;∑\n" + "&sub;⊂\n" + "&sube;⊆\n" + "&searr;↘\n" + "&sect;§\n" + "&swarr;↙\n" + "&theta;θ\n" +
					"&thetasym;ϑ\n" + "&there4;∴\n" + "&thorn;þ\n" + "&times;×\n" + "&tau;τ\n" + "&trade;™\n" +
					"&upsilon;υ\n" + "&upsih;ϒ\n" + "&uarr;↑\n" + "&uacute;ú\n" + "&uArr;⇑\n" + "&ucirc;û\n" +
					"&uuml;ü\n" + "&uml;¨\n" + "&ugrave;ù\n" + "&weierp;℘\n" + "&xi;ξ\n" + "&yacute;ý\n" + "&yen;¥\n" +
					"&yuml;ÿ\n" + "&zeta;ζ\n" + "&apos;'\n" + "&emsp;　";
			int i = 0;
			int l;
			int[] s = new int[4];
			while (i < TABLE.length()) {
				int k = TABLE.indexOf('\n', i);
				if (k < 0) k = TABLE.length();
				l = 0;

				do {
					int j = TABLE.indexOf(';', i)+1;
					if (j > k || j == 0) break;

					s[l++] = i;
					s[l++] = j;
					i = j;
				} while (TABLE.charAt(i) == '&');

				String val = TABLE.substring(i,k);
				i = k+1;

				Table.put(TABLE, s[0], s[1], val);
				if (l > 2) Table.put(TABLE, s[2], s[3], val);
			}
		}
	}

}