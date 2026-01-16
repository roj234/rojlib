package roj.http;

import org.jetbrains.annotations.Nullable;
import roj.annotation.MayMutate;
import roj.collect.ArrayList;
import roj.text.Tokenizer;
import roj.text.URICoder;
import roj.util.ArtifactVersion;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2020/12/5 15:30
 */
public class HttpHead extends Headers {
	@MayMutate
	private String a, b, c;
	@MayMutate
	private boolean isRequest;

	public HttpHead(boolean request, String a, String b, String c) {
		this.a = a;
		this.b = b;
		this.c = c;
		this.isRequest = request;
	}

	public boolean isRequest() {return isRequest;}

	public int statusCode() {
		if (isRequest) throw new IllegalStateException();
		return Integer.parseInt(b);
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
	public ArtifactVersion version() { return new ArtifactVersion(isRequest ? c : a); }

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
			if (buf.getByte(i) == '\r') {
				if (i+1 == len) return null;

				if (buf.getByte(i+1) == '\n') {
					buf.rIndex = i+2;
					return buf.getAscii(beginI, i-beginI);
				}
			}
			i++;
		}
		return null;
	}

	public final List<Cookie> cookies() {
		String field = get("set-cookie");
		if (field == null) return Collections.emptyList();

		List<Cookie> cookies = new ArrayList<>();
		BiConsumer<String, String> callback = new BiConsumer<>() {
			Cookie cookie;

			@Override
			public void accept(String k, String v) {
				try {
					if (v.startsWith("\"")) v = Tokenizer.unescape(v.substring(1, v.length()-1));
					if (cookie == null) {
						cookie = new Cookie(URICoder.decodeURI(k), URICoder.decodeURI(v));
						cookies.add(cookie);
					} else {
						cookie.read(k, v);
					}
				} catch (Throwable e) {
					Helpers.athrow(e);
				}
			}
		};

		HttpUtil.parseParameters(field, callback);
		for (String s : getRest("set-cookie")) HttpUtil.parseParameters(s, callback);

		return cookies;
	}
}