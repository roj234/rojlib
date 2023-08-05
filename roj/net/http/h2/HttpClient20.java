package roj.net.http.h2;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.ch.handler.Timeout;
import roj.net.http.HttpHead;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Identifier;

import javax.net.ssl.SSLException;
import java.io.IOException;

/**
 * <a href="https://www.rfc-editor.org/rfc/rfc9113">RFC9113</a>
 * @author Roj234
 * @since 2022/10/7 0007 21:38
 */
public class HttpClient20 implements ChannelHandler {
	public static final Identifier H2_GOAWAY = Identifier.of("h2:go_away");

	private static final boolean PROTOCOL_IGNORE = true;
	public static final String MAGIC = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n";
	boolean protocolIgnore;

	public static final int
		FRAME_DATA = 0,
	    FRAME_HEADER = 1,
		FRAME_PRIORITY = 2,
		FRAME_RST_STREAM = 3,
		FRAME_SETTINGS = 4,
	    FRAME_PUSH_PROMISE = 5,
		FRAME_PING = 6,
		FRAME_GOAWAY = 7,
		FRAME_WINDOW_UPDATE = 8,
		FRAME_CONTINUATION = 9,
		FRAME_ALTSVC = 10,
		FRAME_BLOCKED = 11;
	static final MyBitSet AVAILABLE_FRAMES = MyBitSet.fromRange(0,10);
	MyBitSet availableFrames = AVAILABLE_FRAMES;

	public static final int
		FLAG_PRIORITY   = 0b00100000,
		FLAG_PADDED     = 0b00001000,
		FLAG_HEADER_END = 0b00000100,
		FLAG_END        = 0b00000001,
		FLAG_ACK        = 0b00000001;
	static final int[] LEGAL_FLAGS = new int[] {
		FLAG_PADDED|FLAG_END,
		FLAG_PRIORITY|FLAG_PADDED|FLAG_HEADER_END|FLAG_END,
		0, 0,
		FLAG_ACK, FLAG_PADDED|FLAG_HEADER_END, FLAG_ACK, 0,
		0, FLAG_HEADER_END,
		0, 0
	};
	int[] legalFlags = LEGAL_FLAGS;

	public static final int
		ERROR_OK = 0,
		ERROR_PROTOCOL = 1,
		ERROR_INTERNAL = 2,
		ERROR_FLOW_CONTROL = 3,
		ERROR_INITIAL_TIMEOUT = 4,
		ERROR_STREAM_CLOSED = 5,
		ERROR_FRAME_SIZE = 6,
		ERROR_REFUSED = 7,
		ERROR_CANCEL = 8,
		ERROR_COMPRESS = 9,
		ERROR_CONNECT = 10,
		ERROR_CALM_DOWN = 11,
		ERROR_INSECURITY = 12,
		ERROR_HTTP1_1_REQUIRED = 13;

	H2Setting setting = new H2Setting();
	HPACK coder = new HPACK();
	H2Ping ping;
	long last_ping;

	byte vendor_flag;
	static final int VF_SERVER = 1, VF_PARALLEL = 2;

	static final int WAITING_MAGIC = 0;

	final IntMap<H2Stream> streams = new IntMap<>();
	int streamId;

	int preClose, errCode;

	int sendWin;
	int rcvWin, deltaWin;
	long winUpdate;

	// Other frames (from any stream) MUST NOT occur between the HEADERS frame and any CONTINUATION frames that might follow.
	boolean continuous;
	private int state;

	public HttpClient20() {}

	ChannelCtx ctx;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		this.ctx = ctx;
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		state = WAITING_MAGIC;
		streamId = 1-(vendor_flag&VF_SERVER);
		rcvWin = sendWin = 65535;

		ByteList list = IOUtil.getSharedByteBuf();
		ctx.channelWrite(list.putAscii(MAGIC));

		list.clear();
		setting.write(list.putMedium(0).put((byte) FRAME_SETTINGS).put((byte) 0).putInt(0), (vendor_flag & VF_SERVER) != 0);
		ctx.channelWrite(withLength(list));
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		check_magic:
		if (state == WAITING_MAGIC) {
			if (buf.readableBytes() < MAGIC.length()) return;
			try {
				if (MAGIC.equals(buf.readUTF(MAGIC.length()))) {
					state = 1;
					ctx.channelOpened();
					break check_magic;
				}
			} catch (Exception ignored) {}
			preClose = -1;
			throw new H2Error(ERROR_PROTOCOL, "magic error");
		}

		int wIdx = buf.wIndex();
		while (buf.readableBytes() > 3) {
			int len = buf.readMedium();
			if (len > setting.max_frame_size) {
				error(ERROR_FRAME_SIZE);
			}
			if (buf.readableBytes() < len + 6) {
				buf.rIndex -= 3;
				buf.compact();
				return;
			}

			buf.wIndex(buf.rIndex + len);
			try {
				onFrame(ctx, buf);
			} finally {
				buf.rIndex = buf.wIndex();
				buf.wIndex(wIdx);
			}
		}
	}

	private void onFrame(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		int type = buf.readUnsignedByte();
		if (!availableFrames.contains(type)) {
			ignore("Illegal frame type");
			return;
		}

		int flag = buf.readUnsignedByte();
		if ((flag & ~LEGAL_FLAGS[type]) != 0) {
			ignore("Illegal flag combination");
			flag &= LEGAL_FLAGS[type];
		}

		int id = buf.readInt();
		if (id < 0) {
			ignore("Rsv bit is SET");
			id &= Integer.MAX_VALUE;
		}
		// 不是我这里打开的，所以
		if (id > streamId) streamId = id + 1;

		if (continuous && type != FRAME_CONTINUATION) {
			error(ERROR_PROTOCOL, "Continuous");
			return;
		}

		switch (type) {
			default:
				customPacket(type, flag, id, buf);
				break;
			case FRAME_DATA:
				if (id == 0) error(ERROR_PROTOCOL, "Data");

				H2Stream c = streams.get(id);
				if (c == null) {
					error(ERROR_PROTOCOL, "Data");
					break;
				}

				deltaWin += buf.readableBytes();
				rcvWin -= buf.readableBytes();
				if (deltaWin > 1024 && (System.currentTimeMillis()-winUpdate > 50 || rcvWin < 4096)) {
					ByteList list = IOUtil.getSharedByteBuf();
					list.putMedium(4).put((byte) FRAME_WINDOW_UPDATE).put((byte) 0).putInt(0).putInt(deltaWin);
					ctx.channelWrite(list);

					winUpdate = System.currentTimeMillis();
					deltaWin = 0;
				}

				if ((flag & FLAG_PADDED) != 0) unpad(buf);
				c.data(buf);
				if ((flag & FLAG_END) != 0) c.close_input();
				break;
			case FRAME_HEADER:
				if ((id&1) == 0) error(ERROR_PROTOCOL, "Server sends request");

				if ((vendor_flag & VF_SERVER) != 0) {
					if (streams.size() >= setting.r_max_streams) {
						streamError(id, ERROR_PROTOCOL);
						// PROTOCOL_ERROR or REFUSED_STREAM determines automatic retry (Section 8.7)
					}
					c = newStream(id);
				} else {
					c = streams.get(id);
					if (c == null) {
						error(ERROR_PROTOCOL, "Header2");
						break;
					}
				}

				// too large: HTTP 431 (Request Header Fields Too Large) status code [RFC6585].

				if (c.state >= H2Stream.PRE_CLOSE || !c.more_header()) {
					error(ERROR_PROTOCOL, "Header3");
				} else if (c.state == H2Stream.RESERVED) {
					c.close_input();
				}

				if ((flag & FLAG_PADDED) != 0) unpad(buf);
				if ((flag & FLAG_PRIORITY) != 0) {
					int before = buf.readInt();
					boolean exclusive = before < 0;

					c.remote_priority(exclusive, before & Integer.MAX_VALUE, buf.readUnsignedByte());
				}

				c.header_data(buf);
				if ((flag & FLAG_HEADER_END) != 0) c.header_end();
				else continuous = true;
				if ((flag & FLAG_END) != 0) c.close_input();
				break;
			case FRAME_PRIORITY:
				if (id == 0) error(ERROR_PROTOCOL, "Priority");
				else if (buf.readableBytes() != 5) error(ERROR_FRAME_SIZE, "Priority");

				packetLimit("Priority");
				// todo: stream creation?

				int before = buf.readInt();
				boolean exclusive = before < 0;

				c = newStream(id);
				c.remote_priority(exclusive, before & Integer.MAX_VALUE, buf.readUnsignedByte());
				break;
			case FRAME_RST_STREAM:
				if (id == 0) error(ERROR_PROTOCOL, "Rst_Stream");
				else if (buf.readableBytes() != 4) error(ERROR_FRAME_SIZE, "Rst_Stream");

				c = streams.remove(id);
				if (c != null) c.remote_error(buf.readInt());
				//else error(ERROR_STREAM_CLOSED, "Rst_Stream");
				break;
			case FRAME_SETTINGS:
				if (id != 0) error(ERROR_PROTOCOL, "Setting");
				else if (buf.readableBytes() % 6 != 0) error(ERROR_FRAME_SIZE, "Setting");

				int wsPrev = setting.r_init_window_size;
				setting.read(buf, (vendor_flag & VF_SERVER) != 0);
				int streamWindow = setting.r_init_window_size;

				for (H2Stream stream : streams.values()) {
					stream.changeWindow(streamWindow - wsPrev);
				}

				if ((flag & FLAG_ACK) == 0) {
					packetLimit("Setting");

					ByteList list = IOUtil.getSharedByteBuf();
					setting.write(list.putMedium(0).put((byte) FRAME_SETTINGS).put((byte) FLAG_ACK).putInt(0), (vendor_flag & VF_SERVER) != 0);

					ctx.channelWrite(withLength(list));
				}
				break;
			case FRAME_PUSH_PROMISE:
				if (!setting.push_enable || (vendor_flag&VF_SERVER)!=0) {
					error(ERROR_PROTOCOL, "Push");
					break;
				} else if (id == 0) {
					error(ERROR_PROTOCOL, "Push");
					break;
				}

				c = streams.get(id);
				if (c == null) {
					error(ERROR_STREAM_CLOSED, "Push2");
					break;
				}

				if ((flag & FLAG_PADDED) != 0) unpad(buf);

				id = buf.readInt();
				if ((vendor_flag&VF_PARALLEL) == 0) {
					streamError(id, ERROR_INTERNAL);
					break;
				}
				H2Stream c1 = newStream(id);
				c1.state = H2Stream.RESERVED;

				// 承诺的请求必须是安全的（见[HTTP]第9.2.1节）和可缓存的（见[HTTP]第9.2.3节）。承诺的请求不能包括任何内容或预告片部分。客户端如果收到一个不可缓存的、不知道是否安全的、或表明存在请求内容的承诺请求，必须用PROTOCOL_ERROR类型的流错误（第5.4.2节）重置承诺流。
				// todo 给id还是reserved？？
				c.header_data(buf);
				if ((flag & FLAG_HEADER_END) != 0) c.header_end();
				else continuous = true;

				break;
			case FRAME_PING:
				if (id != 0) {
					error(ERROR_PROTOCOL, "Ping");
					break;
				} else if (buf.readableBytes() != 8) {
					error(ERROR_FRAME_SIZE, "Ping");
					break;
				}

				if ((flag & FLAG_ACK) == 0) {
					packetLimit("Ping");

					ByteList list = IOUtil.getSharedByteBuf();
					ctx.channelWrite(list.putMedium(8).put((byte) FRAME_PING).put((byte) FLAG_ACK).putInt(0).put(buf));
				} else {
					if (ping == null) {
						ignore("Unmatched ACK ping");
						break;
					}

					if (buf.readLong() != ping.sendTime) ignore("Unmatched ACK ping");
					ping.recvTime = System.currentTimeMillis();
					ping.state = true;
					ping = null;
				}
				break;
			case FRAME_GOAWAY:
				preClose = id;
				error(buf.readInt());
				break;
			case FRAME_WINDOW_UPDATE:
				// send data when remain < 512 or window >= 512
				//    anti small increment attack
				if (buf.readableBytes() != 4) {
					error(ERROR_FRAME_SIZE, "Window_Update");
					break;
				}

				int incr = buf.readInt();
				if (incr == 0) {
					if (id == 0) error(ERROR_PROTOCOL, "Window_Update2");
					else streamError(id, ERROR_PROTOCOL);
					break;
				} else if (incr < 0) {
					ignore("WU RSV bit is SET");
					incr &= Integer.MAX_VALUE;
				}

				if (id == 0) {
					if (rcvWin + incr < 0) {
						error(ERROR_FLOW_CONTROL, "Window_Update2");
						break;
					}
					rcvWin += incr;
				} else {
					c = streams.get(id);
					if (c == null) break;

					if (c.rcvWindow + incr < 0) {
						streamError(id, ERROR_FLOW_CONTROL);
					}
					c.rcvWindow += incr;
				}

				break;
			case FRAME_CONTINUATION:
				if (id == 0 || !continuous) {
					error(ERROR_PROTOCOL, "Continuation");
					break;
				}

				c = streams.get(id);
				if (c == null || !c.more_header()) {
					error(ERROR_PROTOCOL, "Continuation");
					break;
				}

				c.header_data(buf);
				if ((flag & FLAG_HEADER_END) != 0) {
					continuous = false;
					c.header_end();
				}
				break;
			case FRAME_ALTSVC:
				//|         Origin-Len (16)       | Origin? (*)                 ...
				//+-------------------------------+-------------------------------+
				//|                   Alt-Svc-Field-Value (*)                   ...
			case FRAME_BLOCKED:
				break;
		}
	}

	private void packetLimit(String Ping) {
		if ((vendor_flag & VF_SERVER) != 0) {
			if (System.currentTimeMillis() - last_ping < 100) {
				error(ERROR_CALM_DOWN, Ping);
			}
			last_ping = System.currentTimeMillis();
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		for (H2Stream stream : streams.values()) {
			stream.tick();
		}
		if (!continuous) {

		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id == Timeout.READ_TIMEOUT && state == WAITING_MAGIC) {
			error(ERROR_INITIAL_TIMEOUT);
		}
	}

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		if (ex instanceof SSLException) {
			System.out.println("SSLException: " + ex.getMessage());
			ctx.close();
			return;
		}

		if (ex instanceof H2Error) {
			H2Error h2e = (H2Error) ex;
			error(h2e.errCode);
		} else if (ex instanceof OperationDone) {
			// do nothing
		} else {
			error(ERROR_INTERNAL, "Uncaught exception");
			ctx.exceptionCaught(ex);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (errCode == -1 && ctx.isOutputOpen()) {
			DynByteBuf list = IOUtil.getSharedByteBuf().putMedium(0)
									.put((byte) FRAME_GOAWAY).put((byte) 0)
									.putInt(streamId).putInt(Integer.MAX_VALUE);
			try {
				ctx.channelWrite(withLength(list));
			} catch (IOException ignored) {}
		}
	}

	private void unpad(DynByteBuf buf) {
		// A receiver is not obligated to verify padding
		// but MAY treat non-zero padding as a connection error.
		int len = buf.wIndex()-buf.readUnsignedByte();
		if (len < 0) error(ERROR_PROTOCOL, "Padding");
		else buf.wIndex(len);
	}

	protected void customPacket(int type, int flag, int id, DynByteBuf buf) {
		error(ERROR_PROTOCOL);
	}

	public H2Ping ping() {
		if (this.ping != null) return this.ping;

		H2Ping ping = this.ping = new H2Ping();

		DynByteBuf list = IOUtil.getSharedByteBuf().putMedium(8)
								.put((byte) FRAME_PING).put((byte) 0)
								.putInt(0).putLong(ping.sendTime = System.currentTimeMillis());
		try {
			ctx.channelWrite(list);
		} catch (IOException ignored) {}

		return ping;
	}

	public void streamError(int id, int error) {
		H2Stream c = streams.remove(id);
		if (c != null) c.local_error(error);
		DynByteBuf list = IOUtil.getSharedByteBuf().putMedium(4)
								.put((byte) FRAME_RST_STREAM).put((byte) 0)
								.putInt(id).putInt(error);
		try {
			ctx.channelWrite(list);
		} catch (IOException ignored) {}
	}

	// connection level error
	public void error(int id) {
		error(id, null);
	}
	public void error(int id, String reason) {
		if (errCode == -1) {
			errCode = id;
			if (id != 0) {
				try {
					ctx.postEvent(new Event(H2_GOAWAY, reason == null ? Integer.toHexString(id) : reason));
				} catch (IOException ignored) {}
			}
		}
		if (preClose == 0) {
			preClose = streamId;
			DynByteBuf list = IOUtil.getSharedByteBuf().putMedium(0)
									.put((byte) FRAME_GOAWAY).put((byte) 0)
									.putInt(streamId).putInt(id);
			try {
				ctx.channelWrite(withLength(list));
			} catch (IOException ignored) {}
		}

		try {
			ctx.flush();
			ctx.close();
		} catch (IOException ignored) {}
	}
	public int getErrCode() {
		return errCode;
	}

	private void ignore(String err) throws IOException {
		if (protocolIgnore) return;

		if (PROTOCOL_IGNORE) protocolIgnore = true;
		else throw new IOException(err);
	}

	public static DynByteBuf withLength(DynByteBuf buf) {
		return buf.putMedium(0, buf.wIndex()-6);
	}

	public void enableH2Parallelism() {
		vendor_flag |= VF_PARALLEL;
	}

	public H2Stream newStream(int id) {
		H2Stream stream = streams.get(id);
		// todo is exist?
		if (stream != null) return stream;

		streams.putInt(streamId, stream = new H2Stream());
		stream.id = streamId;
		streamId += 2;
		return stream;
	}

	public HttpHead response() {
		if ((vendor_flag & VF_PARALLEL) != 0)
			throw new IllegalStateException("Parallel mode");
		return streams.get(1).header;
	}

	public void waitFor() throws InterruptedException {
		if ((vendor_flag & VF_PARALLEL) != 0)
			throw new IllegalStateException("Parallel mode");

		// todo notify when request finish
	}
}
