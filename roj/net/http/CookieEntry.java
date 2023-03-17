package roj.net.http;

import roj.net.URIUtil;
import roj.text.ACalendar;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/2/3 0003 16:31
 */
public class CookieEntry {
	public String name, value, domain, path;
	public long expires;
	public boolean httpOnly, secure;
	public String sameSite;

	public int read(List<Map.Entry<String, String>> list, int off) {
		Map.Entry<String, String> entry = list.get(off++);
		name = entry.getKey();
		value = entry.getValue();

		while (off < list.size()) {
			if (mayRead(list.get(off))) off++;
			else break;
		}
		return off;
	}
	private boolean mayRead(Map.Entry<String, String> entry) {
		switch (entry.getKey()) {
			case "Max-Age": expires = System.currentTimeMillis()+Long.parseLong(entry.getValue()); break;
			case "Expires": expires = ACalendar.parseRFCDate(entry.getValue()); break;
			case "Domain": domain = entry.getValue(); break;
			case "Path": path = entry.getValue(); break;
			case "HttpOnly": httpOnly = true; break;
			case "Secure": secure = true; break;
			case "SameSite": sameSite = entry.getValue(); break;
			default: return false;
		}
		return true;
	}

	public void write(Appendable sb) {
		try {
			sb.append(name).append('=').append(value);
			if (expires != 0) sb.append("; Max-Age=").append(Long.toString(expires-System.currentTimeMillis()));
			if (domain != null) sb.append("; Domain=").append(domain);
			if (path != null) sb.append("; Path=").append(URIUtil.encodeURIComponent(path));
			if (httpOnly) sb.append("; HttpOnly");
			if (secure) sb.append("; Secure");
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	public static String encodeHeader(List<CookieEntry> list) {
		if (list.isEmpty()) return "";

		StringBuilder sb = new StringBuilder();
		int i = 0;
		while (true) {
			list.get(i++).write(sb);
			if (i == list.size()) break;
			sb.append("; ");
		}
		return sb.toString();
	}
}
