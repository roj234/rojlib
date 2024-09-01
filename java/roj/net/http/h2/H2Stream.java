package roj.net.http.h2;

import roj.io.IOUtil;
import roj.net.http.HttpHead;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.reflect.ReflectionUtils;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

import static roj.net.http.h2.H2Connection.LOGGER;
import static roj.net.http.h2.H2Exception.ERROR_INTERNAL;
import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:47
 */
public abstract class H2Stream {
	public final int id;
	protected H2Stream(int id) {this.id = id;}

	protected static final byte C_SEND_BODY = 0, OPEN = 1, HEAD_V = 2, HEAD_R = 3, DATA = 4, HEAD_T = 5, PROCESSING = 6, SEND_BODY = 7, CLOSED = 8, ERRORED = 9;
	byte state;
	protected final byte getState() {return state;}
	protected static final byte FLAG_GOAWAY = 1, FLAG_HEADER_SENT = -128;
	protected byte flag;

	int sendWindow, receiveWindow;
	public final int getReceiveWindow() {return receiveWindow;}
	public final int getSendWindow() {return sendWindow;}

	protected int headerSize;
	protected int dependency;
	protected byte priority;
	protected void priority(int id, int weight) {
		this.dependency = id;
		this.priority = (byte) weight;
	}

	private static final Set<String> PSEUDO_HEADERS = Set.of(":method", ":path", ":scheme", ":authority", ":status");
	private static final long
		RR = ReflectionUtils.fieldOffset(HttpHead.class, "isRequest"),
		AA = ReflectionUtils.fieldOffset(HttpHead.class, "a"),
		BB = ReflectionUtils.fieldOffset(HttpHead.class, "b"),
		CC = ReflectionUtils.fieldOffset(HttpHead.class, "c");
	private HttpHead hh = new HttpHead(false,null,null,null);

	final String header(DynByteBuf buf, HPACK coder, boolean first) throws IOException {
		switch (state) {
			default: return "StreamState";
			case HEAD_V, HEAD_R, HEAD_T: break;
			case OPEN, DATA:
				if (!first) return "StreamState";
				state++;
			break;
		}

		while (buf.isReadable()) {
			// 很不幸的是，我这里貌似无法处理Never Indexed,暂时不知怎么传下去
			var field = coder.decode(buf);
			if (field == null) continue;

			String key = field.k.toString();
			if ((headerSize -= field.len()) < 0) throw new IllegalRequestException(431);

			if (key.startsWith(":")) {
				if (state != HEAD_V || hh.containsKey(key) || !PSEUDO_HEADERS.contains(key)) return "PseudoHeader.Invalid";
			} else if (state == HEAD_V) {
				state = HEAD_R;
			}

			// key不得包含具有'连接特定语义'([HTTP] 7.6.1节)的字段
			// 唯一的例外是TE，不过，它只能为 'TE: trailer'
			try {
				hh.add(key, field.v.toString());
			} catch (IllegalArgumentException e) {
				return "Header.ValueError";
			}
		}

		return null;
	}
	final String headerEnd(H2Connection man) {
		var hh = this.hh;

		var authority = hh.remove(":authority");
		// 请注意，CONNECT或asterisk-form OPTIONS请求的目标不包含authority；见[HTTP]第7.1和7.2节。
		if (authority != null) {
			String host = hh.putIfAbsent("host", authority);
			boolean eq = false;
			try {
				// https://www.rfc-editor.org/rfc/rfc3986#section-6.2.3
				// 需要注意的是，爪洼的URL和URI貌似都不符合其中的“Scheme-Based Normalization”
				eq = host == null || host.equals(authority) || new URI(host).getAuthority().equals(new URI(authority).getAuthority());
			} catch (Exception ignored) {}
			if (!eq) return "Header.Authority";
		}

		if (man.isServer()) {
			String method = hh.remove(":method"), path = hh.remove(":path"), scheme = hh.remove(":scheme");
			if (method == null || path == null || scheme == null) return "Header.Missing";
			for (String name : PSEUDO_HEADERS) {
				if (hh.remove(name) != null) return "Header.Context";
			}

			u.putObject(hh, AA, method);
			u.putObject(hh, BB, path);
			u.putObject(hh, CC, scheme.toUpperCase(Locale.ROOT)+"/2");
			u.putBoolean(hh, RR, true);
		} else {
			String status = hh.remove(":status");
			if (status == null) return "Header.Missing";
			for (String name : PSEUDO_HEADERS) {
				if (hh.remove(name) != null) return "Header.Context";
			}

			int intStatus;
			try {
				intStatus = Integer.parseInt(status);
			} catch (NumberFormatException e) {
				return "Header.StatusError";
			}

			u.putObject(hh, AA, "HTTP/2");
			u.putObject(hh, BB, status);
			u.putObject(hh, CC, HttpUtil.getDescription(intStatus));
			u.putBoolean(hh, RR, false);
		}

		if (state < DATA) state = DATA;
		return null;
	}

	final String data(H2Connection man, DynByteBuf buf) throws IOException {
		if (state != DATA) return "StreamState";
		if (!buf.isReadable()) return null;

		var h = hh;
		if (h != null) {
			hh = null;
			onHeaderDone(man, h, true);
		}
		return onData(man, buf);
	}
	final void dataEnd(H2Connection man) throws IOException {
		if (state >= PROCESSING) return;
		state = PROCESSING;

		var h = hh;
		if (h != null) {
			hh = null;
			onHeaderDone(man, h, false);
		}
		onDone(man);
	}
	final void finish(H2Connection man) {
		if (state < CLOSED) state = CLOSED;
		onFinish(man);
	}
	final void RST(H2Connection man, int errno) {
		state = ERRORED;
		onRST(man, errno);
	}

	protected final boolean isSuccessfullyFinished() {return state == CLOSED;}
	public final String _getState() {
		return switch (state) {
			case C_SEND_BODY -> "C_SEND_BODY";
			case OPEN -> "OPEN";
			case HEAD_V -> "HEAD_V";
			case HEAD_R -> "HEAD_R";
			case DATA -> "DATA";
			case HEAD_T -> "HEAD_T";
			case PROCESSING -> "PROCESSING";
			case SEND_BODY -> "SEND_BODY";
			case CLOSED -> "CLOSED";
			case ERRORED -> "ERRORED";
			default -> "State "+state;
		};
	}
	protected void tick(H2Connection man) throws IOException {}
	protected abstract void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) throws IOException;
	protected abstract String onData(H2Connection man, DynByteBuf buf) throws IOException;
	protected abstract void onDone(H2Connection man) throws IOException;
	protected void onError(H2Connection man, Exception ex) {
		state = ERRORED;

		if (ex.getClass() == IOException.class || ex instanceof SocketException) {
			LOGGER.debug("[HS] {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
			IOUtil.closeSilently(man.channel().channel());
			return;
		}

		man.streamError(id, ERROR_INTERNAL);
		LOGGER.warn("未捕获的异常[{}.Stream#{}]", ex, man, id);
	}
	/**
	 * 连接被RST_STREAM或GOAWAY关闭.
	 * 这个方法至多调用一次
	 */
	protected void onRST(H2Connection man, int errno) {
		LOGGER.debug("对等端的RST[{}.Stream#{}]: {}", man, id, errno);
		onFinish(man);
	}
	/**
	 * 连接被关闭.
	 * 可以使用{@link #isSuccessfullyFinished()}判断是正常关闭还是异常关闭
	 * 这个方法至多调用一次
	 */
	protected void onFinish(H2Connection man) {}

	/**
	 *
	 * @param man
	 */
	protected void onWindowUpdate(H2Connection man) throws IOException {}
}