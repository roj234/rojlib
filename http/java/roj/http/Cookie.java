package roj.http;

import roj.text.CharList;
import roj.text.DateFormat;
import roj.text.URICoder;

/**
 * @author Roj234
 * @since 2023/2/3 16:31
 */
public final class Cookie {
	public static final Cookie EMPTY = new Cookie("");

	public final String name;
	private String value;

	private String domain, path;
	private long expires;
	private byte flag;

	public Cookie(String name) { this(name, ""); }
	public Cookie(String name, String value) {
		this.name = name.toString();
		this.value = value;
		this.flag = 16;

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
		switch (flag&3) {
			default:
			case 0: return "Lax";
			case 1: return "Strict";
			case 2: return "None";
		}
	}

	public boolean isDirty() { return (flag&16) != 0; }
	public void clearDirty() { flag &= ~16; }

	public Cookie value(String v) { value = v; flag |= 16; return this; }
	public Cookie domain(String v) { domain = v; flag |= 16; return this; }
	public Cookie path(String v) { path = v; flag |= 16; return this; }
	/**
	 * 0: 会话
	 * -1: 删除
	 */
	public Cookie expires(long time) { expires = time; flag |= 16; return this; }
	public Cookie httpOnly(boolean b) {
		if (b) flag |= 4;
		else flag &= ~4;
		flag |= 16;
		return this;
	}
	public Cookie secure(boolean b) {
		if (b) flag |= 8;
		else flag &= ~8;
		flag |= 16;
		return this;
	}
	public Cookie sameSite(String s) {
		if (s.equalsIgnoreCase("Lax")) {
			flag = (byte) ((flag&~3)  |16);
		} else if (s.equalsIgnoreCase("Strict")) {
			flag = (byte) ((flag&~3)|1|16);
		} else if (s.equalsIgnoreCase("None")) {
			flag = (byte) ((flag&~3)|2|16);
		} else {
			throw new IllegalArgumentException(s);
		}
		return this;
	}

	public boolean read(String k, String v) {
		switch (k.toLowerCase()) {
			case "max-age": expires = System.currentTimeMillis()+Long.parseLong(v); break;
			case "expires": expires = DateFormat.parseRFC5322Datetime(v); break;
			case "domain": domain = v; break;
			case "path": path = v; break;
			case "httponly": flag |= 4; break;
			case "secure": flag |= 8; break;
			case "samesite": sameSite(v); flag &= ~16; break;
			default: return false;
		}
		return true;
	}

	public void write(CharList sb, boolean atServer) {
		sb.append(name).append('=');
		URICoder.encodeURIComponent(sb, value);
		if (!atServer) return;
		if (expires != 0) sb.append("; Max-Age=").append(expires < 0 ? "-1" : (expires-System.currentTimeMillis()) / 1000);
		if (domain != null) sb.append("; Domain=").append(domain);
		if (path != null) URICoder.encodeURI(sb.append("; Path="), path);
		if ((flag&4)!=0) sb.append("; HttpOnly");
		if ((flag&3)!=0) sb.append("; SameSite=").append(sameSite());
		if ((flag&10)!=0) sb.append("; Secure");
	}

	@Override
	public String toString() { return URICoder.encodeURIComponent(name)+"="+ URICoder.encodeURIComponent(value); }
}