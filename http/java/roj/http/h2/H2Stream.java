package roj.http.h2;

import roj.http.HttpHead;
import roj.http.HttpUtil;
import roj.io.IOUtil;
import roj.reflect.Unsafe;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.util.Locale;
import java.util.Set;

import static roj.http.h2.H2Connection.LOGGER;
import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2022/10/7 23:47
 */
public abstract class H2Stream {
	public final int id;
	protected H2Stream(int id) {this.id = id;}

	protected static final byte OPEN = 0, HEAD_V = 1, HEAD_R = 2, DATA = 3, DATA_FIN = 4, HEAD_T = 5, CLOSED = 6, ERRORED = 7;
	byte state, outState;

	protected final byte getOutState() {return outState;}
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
		RR = Unsafe.fieldOffset(HttpHead.class, "isRequest"),
		AA = Unsafe.fieldOffset(HttpHead.class, "a"),
		BB = Unsafe.fieldOffset(HttpHead.class, "b"),
		CC = Unsafe.fieldOffset(HttpHead.class, "c");
	protected HttpHead _header = new HttpHead(false,null,null,null);

	final String header(DynByteBuf buf, HPACK coder, boolean first) throws IOException {
		switch (state) {
			default: return "StreamState";
			case HEAD_V, HEAD_R/*, HEAD_T*/: break;
			case OPEN, DATA_FIN:
				if (!first) return "StreamState";
				state++;
			break;
		}

		while (buf.isReadable()) {
			// 很不幸的是，我这里貌似无法处理Never Indexed,暂时不知怎么传下去
			var field = coder.decode(buf);
			if (field == null) continue;

			String key = field.k.toString();
			if ((headerSize -= field.len()) < 0) return "StreamSize";//throw new roj.http.server.IllegalRequestException(431);

			if (key.startsWith(":")) {
				if (state != HEAD_V || _header.containsKey(key) || !PSEUDO_HEADERS.contains(key)) return "PseudoHeader.Invalid";
			} else if (state == HEAD_V) {
				state = HEAD_R;
			}

			// key不得包含具有'连接特定语义'([HTTP] 7.6.1节)的字段
			// 唯一的例外是TE，不过，它只能为 'TE: trailer'
			try {
				_header.add(key, field.v.toString());
			} catch (IllegalArgumentException e) {
				return "Header.ValueError";
			}
		}

		return null;
	}
	final String _headerEnd(H2Connection man) throws IOException {
		var t = headerEnd(man);
		if (state < DATA) state = DATA;
		//else if (state == HEAD_T) state = CLOSED;
		return t;
	}
	protected String headerEnd(H2Connection man) throws IOException {
		var hh = this._header;

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

			U.putReference(hh, AA, method);
			U.putReference(hh, BB, path);
			U.putReference(hh, CC, scheme.toUpperCase(Locale.ROOT)+"/2");
			U.putBoolean(hh, RR, true);
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

			U.putReference(hh, AA, "HTTP/2");
			U.putReference(hh, BB, status);
			U.putReference(hh, CC, HttpUtil.getCodeDescription(intStatus));
			U.putBoolean(hh, RR, false);
		}

		return null;
	}

	final String data(H2Connection man, DynByteBuf buf) throws IOException {
		if (state != DATA) return "StreamState";

		var h = _header;
		if (h != null) {
			_header = null;
			onHeaderDone(man, h, true);
		}
		return onData(man, buf);
	}
	final void dataEnd(H2Connection man) throws IOException {
		if (state >= DATA_FIN) return;
		state = DATA_FIN;

		var h = _header;
		if (h != null) {
			_header = null;
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

	public final String _getState() {
		return switch (state) {
			case OPEN -> "OPEN";
			case HEAD_V -> "HEAD_V";
			case HEAD_R -> "HEAD_R";
			case DATA -> "RECV_BODY";
			case DATA_FIN -> "PROCESSING";
			case HEAD_T -> "PROCESSING";
			case CLOSED -> "CLOSED";
			case ERRORED -> "ERRORED";
			default -> "State "+ state;
		};
	}
	protected void tick(H2Connection man) throws IOException {}
	protected abstract void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) throws IOException;
	protected abstract String onData(H2Connection man, DynByteBuf buf) throws IOException;
	protected abstract void onDone(H2Connection man) throws IOException;
	protected void onError(H2Connection man, Throwable ex) {
		state = ERRORED;

		if (ex.getClass() == IOException.class || ex instanceof SocketException) {
			LOGGER.debug("[HS] {}: {}", ex.getClass().getSimpleName(), ex.getMessage());
			IOUtil.closeSilently(man.channel().channel());
			return;
		}

		man.streamErrorCaught(id, ex);
	}
	/**
	 * 连接被RST_STREAM或GOAWAY关闭.
	 * 这个方法至多调用一次
	 */
	protected void onRST(H2Connection man, int errno) {
		LOGGER.debug("流[{}/#{}]收到RST: {}", man, id, errno);
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