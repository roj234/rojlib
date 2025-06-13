package roj.http;

import org.jetbrains.annotations.Nullable;
import roj.WillChange;
import roj.collect.ArrayList;
import roj.math.Version;
import roj.text.URICoder;
import roj.util.DynByteBuf;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2020/12/5 15:30
 */
public class HttpHead extends Headers {
	@WillChange private String a, b, c;
	@WillChange private boolean isRequest;

	public HttpHead(boolean request, String a, String b, String c) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.isRequest = request;
	}

	public boolean isRequest() {return isRequest;}

	public int getCode() {
		if (isRequest) throw new IllegalStateException();
		return Integer.parseInt(b);
	}

	public String getCodeString() {
		if (isRequest) throw new IllegalStateException();
		return c;
	}

	public String getPath() {
		if (!isRequest) throw new IllegalStateException();
		return b;
	}

	public String getMethod() {
		if (!isRequest) throw new IllegalStateException();
		return a;
	}

	public String versionStr() { return isRequest ? c : a; }
	public Version version() { return new Version(isRequest ? c : a); }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder().append(a).append(' ').append(b).append(' ').append(c).append("\r\n");
		encode(sb);
		return sb.append("\r\n").toString();
	}

	@Nullable
	public static HttpHead parseFirst(DynByteBuf buf) {
		String a, b, c;
		boolean request = false;

		success: {
			String line = readCRLF(buf);
			if (line == null) return null;

			failed: {
				int i = line.indexOf(' ');
				if (i < 0) break failed;
				a = line.substring(0, i);
				int j = line.indexOf(' ', i+1);
				if (j < 0) break failed;
				b = line.substring(i+1, j);
				c = line.substring(j+1);
				if (a.startsWith("HTTP/")) break success;
				request = true;
				if (HttpUtil.getMethodId(a) < 0) break failed;
				if (c.startsWith("HTTP/")) break success;
			}
			throw new IllegalArgumentException("无效请求头 " + line);
		}

		return new HttpHead(request, a.substring(a.indexOf(' ') + 1), b, c);
	}
	public static HttpHead parse(DynByteBuf buf) {
		HttpHead head = parseFirst(buf);
		if (head == null || !parseHeader(head, buf)) throw new IllegalStateException("数据不全");
		return head;
	}

	public static String readCRLF(DynByteBuf buf) {
		int len = buf.rIndex+buf.readableBytes();
		int i = buf.rIndex;
		int beginI = i;
		while (i < len) {
			if (buf.get(i) == '\r') {
				if (i+1 == len) return null;

				if (buf.get(i+1) == '\n') {
					buf.rIndex = i+2;
					return buf.readAscii(beginI, i-beginI);
				}
			}
			i++;
		}
		return null;
	}

	public final List<Cookie> cookies() {
		String field = header("set-cookie");
		if (field.isEmpty()) return Collections.emptyList();

		List<Cookie> cookies = new ArrayList<>();
		complexValue(field, new BiConsumer<>() {
			Cookie cookie;

			@Override
			public void accept(String k, String v) {
				if (cookie != null && cookie.read(k, v)) return;

				try {
					if (cookie != null) cookie.clearDirty();
					cookie = new Cookie(URICoder.decodeURI(k), URICoder.decodeURI(v));
					cookies.add(cookie);
				} catch (MalformedURLException e) {
					cookie = new Cookie("invalid");
				}
			}
		}, false);

		int i = cookies.size()-1;
		if (i >= 0) cookies.get(i).clearDirty();

		return cookies;
	}
}