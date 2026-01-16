package roj.http;

import roj.collect.BitSet;
import roj.text.CharList;
import roj.text.DateFormat;
import roj.text.URICoder;

import java.util.Locale;

/**
 * @author Roj234
 * @since 2023/2/3 16:31
 */
public final class Cookie {
	public final String name;
	private String value;

	private String domain, path;
	private long expires;
	private byte flag;

	public Cookie(String name) { this(name, ""); }
	public Cookie(String name, String value) {
		this.name = name.toString();
		this.value = value;

		if (name.startsWith("__Secure-")) flag |= 8;
		if (name.startsWith("__Host-")) {
			flag |= 8;
			path = "/";
		}
	}

	public String value() { return value; }
	public String domain() { return domain; }
	public String path() { return path; }
	public long expires() { return expires; }
	public boolean isExpired() { return expires != 0 && System.currentTimeMillis() > expires; }
	public boolean httpOnly() { return (flag&4)!=0; }
	public boolean secure() { return (flag&8)!=0; }
	public String sameSite() {
		return switch (flag & 3) {
			default -> "Lax";
			case 1 -> "Strict";
			case 2 -> "None";
		};
	}

	public Cookie value(String v) { value = v; return this; }
	public Cookie domain(String v) { domain = v; return this; }
	public Cookie path(String v) { path = v; return this; }
	/**
	 * 0: 会话
	 * -1: 删除
	 */
	public Cookie expires(long time) { expires = time; return this; }
	public Cookie httpOnly(boolean b) {
		if (b) flag |= 4;
		else flag &= ~4;
		return this;
	}
	public Cookie secure(boolean b) {
		if (b) flag |= 8;
		else flag &= ~8;
		return this;
	}
	public Cookie sameSite(String s) {
		if (s.equalsIgnoreCase("Lax")) {
			flag = (byte) ((flag&~3)  );
		} else if (s.equalsIgnoreCase("Strict")) {
			flag = (byte) ((flag&~3)|1);
		} else if (s.equalsIgnoreCase("None")) {
			flag = (byte) ((flag&~3)|2);
		} else {
			throw new IllegalArgumentException(s);
		}
		return this;
	}

	public boolean read(String k, String v) {
		switch (k.toLowerCase(Locale.ROOT)) {
			case "max-age": expires = System.currentTimeMillis()+Long.parseLong(v); break;
			case "expires": expires = DateFormat.parseRFC5322Datetime(v); break;
			case "domain": domain = v; break;
			case "path": path = v; break;
			case "httponly": flag |= 4; break;
			case "secure": flag |= 8; break;
			case "samesite": sameSite(v); break;
			default: return false;
		}
		return true;
	}


	private static final BitSet COOKIE_KEY_INVALID = BitSet.from("()<>@,;:\\\"/[]?={} \t");
	static {COOKIE_KEY_INVALID.addRange(0, 32);COOKIE_KEY_INVALID.add(127);}
	private static final BitSet COOKIE_VAL_INVALID = BitSet.from(" \t\r\n,;\\\"");
	static {COOKIE_VAL_INVALID.addRange(0, 32);COOKIE_VAL_INVALID.add(127);}

	public void write(CharList sb, boolean atServer) {
		URICoder.escapeBlacklist(sb, name, COOKIE_KEY_INVALID);
		sb.append('=');
		URICoder.escapeBlacklist(sb, value, COOKIE_VAL_INVALID);
		if (!atServer) return;
		if (expires != 0) sb.append("; Max-Age=").append(expires < 0 ? "-1" : (expires-System.currentTimeMillis()) / 1000);
		if (domain != null) sb.append("; Domain=").append(domain);
		if (path != null) URICoder.encodeURI(sb.append("; Path="), path);
		if ((flag&4)!=0) sb.append("; HttpOnly");
		if ((flag&3)!=0) sb.append("; SameSite=").append(sameSite());
		if ((flag&10)!=0) sb.append("; Secure");
	}

	@Override
	public String toString() {
		var sb = new CharList();
		write(sb, true);
		return sb.toStringAndFree();
	}
}