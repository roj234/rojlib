package roj.net.http;

import roj.collect.TrieTree;
import roj.net.http.srv.Request;
import roj.text.CharList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/2/24 0024 2:01
 */
public class HttpUtil {
	// region CORS
	public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	public static final String ACCESS_CONTROL_MAX_aGE = "Access-Control-Max-Age";
	public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
	public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

	public static boolean isCORSPreflight(Request req) {
		return req.action() == Action.OPTIONS && req.containsKey("Origin") && req.containsKey("Access-Control-Request-Method");
	}
	// endregion

	static final Pattern pa = Pattern.compile("(up.browser|up.link|mmp|symbian|smartphone|midp|wap|phone|iphone|ipad|ipod|android|xoom)", Pattern.CASE_INSENSITIVE);
	public static boolean is_wap(Request req) {
		String ua = req.get("user-agent");
		if (ua != null && pa.matcher(ua).matches()) return true;

		return req.getField("accept").contains("application/vnd.wap.xhtml+xml");
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
	static class LargeTable {
	private static final TrieTree<String> Table = new TrieTree<>();
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

	// 禁止缓存
	public static void no_cache() {
		//header("Pragma:no-cache\r\n");
		//header("Cache-Control:no-cache\r\n");
		//header("Expires:0\r\n");
	}
}