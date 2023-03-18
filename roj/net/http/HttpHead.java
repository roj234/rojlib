package roj.net.http;

import roj.math.Version;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * @author Roj234
 * @since 2020/12/5 15:30
 */
public class HttpHead extends Headers {
	private final String a, b, c;
	public final boolean isRequest;

	public HttpHead(boolean request, String a, String b, String c) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.isRequest = request;
	}

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
				if (Action.valueOf(a) < 0) break failed;
				if (c.startsWith("HTTP/")) break success;
			}
			throw new IllegalArgumentException("无效请求头 " + line);
		}

		return new HttpHead(request, a.substring(a.indexOf(' ') + 1), b, c);
	}
	public static HttpHead parse(DynByteBuf buf) {
		HttpHead head = parseFirst(buf);
		if (head == null || !head.parseHead(buf)) throw new IllegalStateException("数据不全");
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

	public long getContentLengthLong() {
		return Long.parseLong(getOrDefault("content-length", "-1"));
	}
	public String getContentEncoding() {
		return getOrDefault("content-encoding", "identity").toLowerCase(Locale.ROOT);
	}
}
