package roj.net.http.h2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.collect.IntMap;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.net.http.Headers;
import roj.net.http.server.H2C;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.SocketException;
import java.util.function.Consumer;

import static roj.net.http.h2.H2Exception.*;

/**
 * <a href="https://www.rfc-editor.org/rfc/rfc9113">RFC9113</a>
 * @author Roj234
 * @since 2022/10/7 0007 21:38
 */
public final class H2Connection implements ChannelHandler {
	public static final Logger LOGGER = Logger.getLogger("HTTP/2");
	public static final String H2_GOAWAY = "h2:go_away";
	// HPACK Dynamic Table encType
	public static final byte FIELD_SAVE = 0, FIELD_DISCARD = 1, FIELD_DISCARD_ALWAYS = 2;

	private static final int
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

	private static final int
		FLAG_PRIORITY   = 0b100000,
		FLAG_PADDED     = 0b001000,
		FLAG_HEADER_END = 0b000100,
		FLAG_END        = 0b000001,
		FLAG_ACK        = 0b000001;
	private static final int[] KNOWN_FLAGS = new int[] {
		FLAG_PADDED|FLAG_END,
		FLAG_PRIORITY|FLAG_PADDED|FLAG_HEADER_END|FLAG_END,
		0, 0,
		FLAG_ACK, FLAG_PADDED|FLAG_HEADER_END, FLAG_ACK, 0,
		0, FLAG_HEADER_END,
		0, 0
	};

	private final H2Manager manager;
	private final H2Setting localSetting = new H2Setting(), remoteSetting = new H2Setting();
	private final HPACK hpack = new HPACK();
	private int hpackLock;

	private static final int FLAG_SERVER = 1, FLAG_SETTING_SENT = 2, FLAG_GOAWAY_SENT = 4, FLAG_GOAWAY_RECEIVED = 8;
	private byte flag;

	private final IntMap<H2Stream> streams = new IntMap<>();
	private int nextStreamId;

	private long dosTime;
	private int dosSize, dosCount, dosCount2;

	private H2Ping ping;

	private int sendWindow, receiveWindow;
	private final H2FlowControl flowControl;

	public H2Connection(H2Manager manager, boolean isServer) {this(manager, isServer, new H2FlowControlSimple());}
	public H2Connection(H2Manager manager, boolean isServer, H2FlowControl flowControl) {
		this.manager = manager;
		this.flag = (byte) (isServer ? FLAG_SERVER : 0);
		this.flowControl = flowControl;
	}

	private ChannelCtx ctx;

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		nextStreamId = 1-(flag & FLAG_SERVER);
		receiveWindow = sendWindow = 65535;
		this.ctx = ctx;

		ByteList list = IOUtil.getSharedByteBuf();
		if ((flag & FLAG_SERVER) == 0) {
			ctx.channelWrite(list.putAscii(H2C.MAGIC));
			list.clear();

			flowControl.initSetting(localSetting);
			manager.initSetting(localSetting);
			syncSettings();

			flag |= FLAG_SETTING_SENT;
		}
	}

	//region 数据包接收
	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;

		int wIdx = buf.wIndex();
		while (buf.readableBytes() > 3) {
			int len = buf.readMedium(buf.rIndex);
			if (len > localSetting.max_frame_size) {sizeError();return;}
			len += 6;
			if (buf.readableBytes() < len+3) return;

			buf.wIndex((buf.rIndex+=3) + len);
			try {
				frame(ctx, buf);
				buf.rIndex = buf.wIndex();
			} finally {
				if (buf.capacity() != 0) buf.wIndex(wIdx);
			}
		}
	}
	private void frame(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		int type = buf.readUnsignedByte();
		if (type > FRAME_BLOCKED) {error(ERROR_PROTOCOL, "Unknown.Frame.Type");return;}

		int flag = buf.readUnsignedByte();
		if ((flag & ~KNOWN_FLAGS[type]) != 0) {error(ERROR_PROTOCOL, "Unknown.Frame.Flag");return;}

		int id = buf.readInt();
		if (id < 0) {error(ERROR_PROTOCOL, "StreamId");return;}
		if (id > nextStreamId) nextStreamId = id+1;

		H2Stream c;
		switch (type) {
			case FRAME_SETTINGS -> {
				if (id != 0) error(ERROR_PROTOCOL, "StreamId");
				else if (buf.readableBytes() % 6 != 0) sizeError();

				if ((flag & FLAG_ACK) == 0) {
					packetLimit("Setting", -1);

					int prevWs = remoteSetting.initial_window_size;
					remoteSetting.read(buf, isServer());
					manager.validateRemoteSetting(remoteSetting);
					int ws = remoteSetting.initial_window_size;

					if (prevWs != ws) {
						for (H2Stream stream : streams.values()) stream.sendWindow += ws - prevWs;
					}

					ctx.channelWrite(IOUtil.getSharedByteBuf().putMedium(0).put(FRAME_SETTINGS).put(FLAG_ACK).putInt(0));

					if ((this.flag & FLAG_SETTING_SENT) == 0) {
						this.flag |= FLAG_SETTING_SENT;
						flowControl.initSetting(localSetting);
						manager.initSetting(localSetting);
						syncSettings();
					}

					hpack.setEncoderTableSize(remoteSetting.header_table_size);
				} else {
					if (buf.isReadable()) sizeError();
					hpack.setDecoderTableSize(localSetting.header_table_size);
				}
			}
			case FRAME_HEADER -> {
				c = streams.get(id);
				if (c == null) {
					if (id != 0 && isServer()) {
						// PROTOCOL_ERROR or REFUSED_STREAM determines automatic retry (Section 8.7)
						if (streams.size() >= localSetting.max_streams) {streamError(id, ERROR_REFUSED);return;}
						if (id < nextStreamId-1) {error(ERROR_PROTOCOL, "StreamId");return;}
						c = newStream(id);
					} else {invalidStream(id);return;}
				}
				if (hpackLock != 0 && hpackLock != id) {error(ERROR_PROTOCOL, "Continuous.Expected");return;}

				if ((flag & FLAG_PADDED) != 0) unpad(buf);
				if ((flag & FLAG_PRIORITY) != 0) c.priority(buf.readInt(), buf.readUnsignedByte()+1);

				var err = c.header(buf, hpack, true);
				if (err != null) error(ERROR_PROTOCOL, err);
				else {
					if ((flag & FLAG_HEADER_END) != 0) headerEnd(c);
					else hpackLock = id;
					if ((flag & FLAG_END) != 0) dataEnd(c);
				}
			}
			case FRAME_CONTINUATION -> {
				if (hpackLock != id) {error(ERROR_PROTOCOL, "Continuous.Unexpected");return;}
				c = streams.get(id);
				if (c == null) invalidStream(id);
				else {
					var err = c.header(buf, hpack, false);
					if (err != null) error(ERROR_PROTOCOL, err);
					else if ((flag & FLAG_HEADER_END) != 0) {
						headerEnd(c);
						hpackLock = 0;
					}
				}
			}
			case FRAME_DATA -> {
				c = streams.get(id);
				if (c == null) {invalidStream(id);return;}

				int len = buf.readableBytes();
				if ((receiveWindow -= len) < 0) error(ERROR_FLOW_CONTROL, "Data.Window");
				else if ((c.receiveWindow -= len) < 0) streamError(c.id, ERROR_FLOW_CONTROL);
				else {
					flowControl.dataReceived(this, c, len);

					if ((flag & FLAG_PADDED) != 0) unpad(buf);
					String err;
					try {
						err = c.data(this, buf);
					} catch (Exception e) {
						c.onError(this, e);
						return;
					}
					if (err != null) streamError(id, ERROR_PROTOCOL);
					else if ((flag & FLAG_END) != 0) dataEnd(c);
				}
			}
			case FRAME_PUSH_PROMISE -> {
				if (!localSetting.push_enable || isServer()) {error(ERROR_PROTOCOL, "Push.Enable");return;}
				c = streams.get(id);
				if (c == null) {invalidStream(id);return;}
				if (hpackLock != 0 && hpackLock != id) {error(ERROR_PROTOCOL, "Continuous.Expected");return;}

				if ((flag & FLAG_PADDED) != 0) unpad(buf);

				id = buf.readInt();
				if ((id&1) == (nextStreamId&1)) {error(ERROR_PROTOCOL,"StreamId");return;}
				if (streams.get(id) != null) {invalidStream(id);return;}
				if (id > nextStreamId) nextStreamId = id+1;
				// #section-6.6-10
				if (streams.size() >= localSetting.max_streams) {streamError(id, ERROR_REFUSED);return;}

				c = newStream(id);
				var err = c.header(buf, hpack, true);
				if (err != null) error(ERROR_PROTOCOL, err);
				else if ((flag & FLAG_HEADER_END) != 0) headerEnd(c);
				else hpackLock = id;
			}
			case FRAME_PRIORITY -> {
				if (buf.readableBytes() != 5) sizeError();
				c = streams.get(id);
				if (c == null) invalidStream(id);
				else {
					packetLimit("Priority", -1);
					c.priority(buf.readInt(), buf.readUnsignedByte()+1);
				}
			}
			case FRAME_RST_STREAM -> {
				if (buf.readableBytes() != 4) sizeError();
				else {
					c = streams.remove(id);
					if (c != null) c.RST(this, buf.readInt());
					else {
						if (id == 0) error(ERROR_PROTOCOL, "StreamId");
						packetLimit("RST", -1);
					}
					checkGoaway();
				}
			}
			case FRAME_PING -> {
				if (buf.readableBytes() != 8) sizeError();
				else if (id != 0) error(ERROR_PROTOCOL, "StreamId");
				else if ((flag & FLAG_ACK) == 0) {
					packetLimit("Ping", -1);

					ByteList ob = IOUtil.getSharedByteBuf();
					ctx.channelWrite(ob.putMedium(8).put(FRAME_PING).put(FLAG_ACK).putInt(0).put(buf));
				} else if (ping == null || buf.readLong() != (ping.sendTime ^ ping.nonce)) error(ERROR_PROTOCOL, "Ping.Ack");
				else {
					ping.recvTime = System.currentTimeMillis();
					ping.callback.accept(ping);
					ping = null;
				}
			}
			case FRAME_GOAWAY -> {
				if (buf.readableBytes() < 8) sizeError();
				else if (id != 0) error(ERROR_PROTOCOL, "StreamId");
				else {
					int remote_goaway = buf.readInt();
					int errno = buf.readInt();
					String reason = null;
					try {
						reason = buf.readUTF(buf.readableBytes());
					} catch (Exception ignored) {}

					this.flag |= FLAG_GOAWAY_SENT;

					if (nextStreamId > remote_goaway) {
						LOGGER.debug("远程关闭了连接,最后的序列号是{}, 本地的序列号是{}", remote_goaway, nextStreamId);
						for (var itr = streams.values().iterator(); itr.hasNext(); ) {
							var stream = itr.next();
							if (stream.id > remote_goaway) {
								itr.remove();
								stream.RST(this, errno);
							}
						}
					}

					if (errno != ERROR_OK) LOGGER.warn("远程发送的Goaway({}): {}", errno, reason);

					checkGoaway();
				}
			}
			case FRAME_WINDOW_UPDATE -> {
				if (buf.readableBytes() != 4) {sizeError();return;}

				int win = buf.readInt();
				if (win <= 0) {
					if (id == 0) error(ERROR_PROTOCOL, "WindowSize");
					else streamError(id, ERROR_PROTOCOL);
					return;
				}

				if (id == 0) {
					if ((win += sendWindow) < 0) error(ERROR_FLOW_CONTROL, "WindowSize");
					sendWindow = win;
				} else {
					c = streams.get(id);
					//RFC 9113 section-6.9-10
					if (c == null) return;

					if ((win += sendWindow) < 0) streamError(id, ERROR_FLOW_CONTROL);
					c.sendWindow = win;
				}
			}
			case FRAME_ALTSVC -> {
				//|         Origin-Len (16)       | Origin? (*)                 ...
				//+-------------------------------+-------------------------------+
				//|                   Alt-Svc-Field-Value (*)                   ...
			}
			case FRAME_BLOCKED -> {}
		}
	}

	private void headerEnd(H2Stream c) {
		var err = c.headerEnd(this);
		if (err != null) streamError(c.id, ERROR_PROTOCOL);
	}
	private void dataEnd(H2Stream c) throws IOException {
		try {
			c.dataEnd(this);
		} catch (Exception e) {
			c.onError(this, e);
		} finally {
			if (!isServer()) {
				streams.remove(c.id);
				c.finish(this);
				checkGoaway();
			}
		}
	}
	private void packetLimit(String info, int size) {
		if ((flag & FLAG_SERVER) != 0) {
			if (size < 0) {
				if (System.currentTimeMillis() - dosTime < 10 && ++dosCount2 > 7) {
					error(ERROR_CALM_DOWN, info);
				} else {
					dosCount2 = 0;
				}
				dosTime = System.currentTimeMillis();
			} else {
				dosSize += size;
				dosCount++;

				if ((dosSize | dosCount) < 0) {
					dosSize = size;
					dosCount = 1;
				} else if ((dosCount & 127) == 0) {
					if (dosSize / dosCount < 128) {
						error(ERROR_CALM_DOWN, info);
					}
				}
			}
		}
	}
	private void unpad(DynByteBuf buf) {
		// A receiver is not obligated to verify padding
		// but MAY treat non-zero padding as a connection error.
		int len = buf.wIndex()-buf.readUnsignedByte();
		if (len < 0) error(ERROR_PROTOCOL, "Padding");
		else buf.wIndex(len);
	}
	private H2Stream newStream(int id) {
		var stream = manager.createStream(id);
		stream.state = H2Stream.OPEN;
		stream.sendWindow = remoteSetting.initial_window_size;
		stream.receiveWindow = localSetting.initial_window_size;
		if (stream.headerSize == 0)
			stream.headerSize = localSetting.max_header_list_size < 0 ? 32767 : localSetting.max_header_list_size;
		streams.putInt(id, stream);
		return stream;
	}
	private void checkGoaway() throws IOException {
		if ((flag&(FLAG_GOAWAY_SENT|FLAG_GOAWAY_RECEIVED)) == 0) return;
		if (streams.isEmpty()) {
			if ((flag&FLAG_GOAWAY_SENT) == 0) goaway(ERROR_OK, "");
			try {
				ctx.channel().closeGracefully();
			} catch (Exception e) {
				ctx.close();
			}
		}
	}
	//endregion
	// region 错误处理
	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		if (ex instanceof H2Exception exc) {error(exc.errno, exc.getMessage());return;}

		if (ex.getClass() == IOException.class || ex instanceof SocketException) {
			LOGGER.debug(ex.getClass().getSimpleName()+": "+ex.getMessage());
			ctx.close();
			return;
		}

		LOGGER.warn("未捕获的异常", ex);
		error(ERROR_INTERNAL, ex.toString());
	}
	// connection error
	private void sizeError() {error(ERROR_FRAME_SIZE, null);}
	public void error(int errno, String reason) {
		if (errno != ERROR_INTERNAL) LOGGER.debug("协议错误({}): {}", errno, reason);

		if ((flag& FLAG_GOAWAY_SENT) == 0) goaway(errno, reason != null && LOGGER.canLog(Level.DEBUG) ? reason : "");

		try {
			ctx.channel().close();
		} catch (IOException ignored) {}
	}
	// stream level
	private void invalidStream(int id) {
		if (id == 0) error(ERROR_PROTOCOL, "StreamId");
		else streamError(id, ERROR_STREAM_CLOSED);
	}
	public void streamError(int id, int errno) {
		if (id != ERROR_INTERNAL) LOGGER.debug("流错误[{}.Stream#{}]: {}", this, id, errno);

		var c = streams.remove(id);
		if (c != null) c.finish(this);
		else packetLimit("RST", -1);

		DynByteBuf ob = IOUtil
			.getSharedByteBuf().putMedium(4)
			.put(FRAME_RST_STREAM).put(0)
			.putInt(id).putInt(errno);
		try {
			ctx.channelWrite(ob);
			ctx.flush();
		} catch (IOException ignored) {}
	}
	//endregion
	//region 数据包发送
	private static DynByteBuf withLength(DynByteBuf buf) {return buf.putMedium(0, buf.wIndex()-9);}
	public void syncSettings() throws IOException {
		var ob = IOUtil.getSharedByteBuf();
		localSetting.write(ob.putMedium(0).put(FRAME_SETTINGS).put(0).putInt(0), isServer());
		ctx.channelWrite(withLength(ob));
	}
	public boolean ping(Consumer<H2Ping> callback) throws IOException {
		if (ping != null) return false;
		ping = new H2Ping(callback);

		var ob = IOUtil.getSharedByteBuf().putMedium(8)
			.put(FRAME_PING).put(0)
			.putInt(0).putLong(ping.sendTime ^ ping.nonce);
		ctx.channelWrite(ob);
		return true;
	}
	@Nullable
	public H2Stream push(@NotNull H2Stream enclosingStream) throws IOException {
		if (!remoteSetting.push_enable || !isServer()) return null;

		H2Stream stream = newStream(nextStreamId);
		nextStreamId += 2;
		stream.state = H2Stream.PROCESSING;

		var ob = IOUtil.getSharedByteBuf().putMedium(8)
			.put(FRAME_PUSH_PROMISE).put(0)
			.putInt(enclosingStream.id).putInt(stream.id);
		ctx.channelWrite(ob);

		return stream;
	}
	//region Goaway (trigger via event)
	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(H2_GOAWAY) && (flag& FLAG_GOAWAY_SENT) == 0) goaway((int)event.getData(), "");
	}
	public void goaway(int errno, @NotNull String message) {
		flag |= FLAG_GOAWAY_SENT;
		DynByteBuf ob = IOUtil
			.getSharedByteBuf().putMedium(0)
			.put(FRAME_GOAWAY).put(0)
			.putInt(0).putInt(nextStreamId-1).putInt(errno).putUTFData(message);
		try {
			ctx.channelWrite(withLength(ob));
		} catch (IOException ignored) {}
	}
	//endregion
	//region WindowUpdate
	public H2Setting getLocalSetting() {return localSetting;}
	public int getReceiveWindow() {return receiveWindow;}
	public void sendWindowUpdate(@Nullable H2Stream stream, @Range(from = 1, to = Integer.MAX_VALUE) int increment) throws IOException {
		//noinspection all
		if (increment < 1) throw new IllegalArgumentException("increment < 1");

		int id;
		if (stream == null) {
			id = 0;
			receiveWindow += increment;
		} else {
			id = stream.id;
			stream.receiveWindow += increment;
		}

		ctx.channelWrite(IOUtil.getSharedByteBuf().putMedium(4).put(FRAME_WINDOW_UPDATE).put(0).putInt(id).putInt(increment));
	}
	//endregion
	//region HEADER/DATA
	/**
	 * 需要注意的是，伪头需要写在header里 ":method", ":path", ":scheme", ":authority"
	 * // 8.2.3. 压缩Cookie
	 * //Cookie头字段[COOKIE]使用分号（"；"）来划分cookie对（或 "碎屑"）。这个头字段包含多个值，但不使用COMMA（","）作为分隔符，从而防止在多个字段行上发送cookie对（见[HTTP]第5.2条）。这可能会大大降低压缩效率，因为对单个cookie-pairs的更新会使存储在HPACK表中的任何字段行无效。
	 * //为了实现更好的压缩效率，Cookie头字段可能被分割成单独的头字段，每个头字段有一个或多个cookie对。如果在解压缩后有多个Cookie头字段，在被传递到非HTTP/2上下文（如HTTP/1.1连接或普通HTTP服务器应用程序）之前，必须使用0x3b、0x20（ASCII字符串"；"）的两个八位数分隔符将这些字段连接成一个八位数字符串。
	 *
	 * @param header
	 * @param noBody
	 * @return
	 * @throws IOException
	 */
	public H2Stream sendHeaderClient(@NotNull Headers header, boolean noBody) throws IOException {return sendHeaderClient(header, noBody, 0, false, 0);}
	public H2Stream sendHeaderClient(@NotNull Headers header, boolean noBody, int depend, boolean exclusive, int weight) throws IOException {
		if (isServer()) throw new IllegalArgumentException("服务端请使用sendHeader()");
		H2Stream stream = newStream(nextStreamId);
		nextStreamId += 2;
		sendHeader0(stream, header, noBody, depend | (exclusive ? 0x80000000 : 0), weight);
		//详情查看客户端状态转换图
		stream.state = stream.state == H2Stream.SEND_BODY ? H2Stream.C_SEND_BODY : H2Stream.OPEN;
		return stream;
	}
	/**
	 * 发送头部
	 * @param noBody true代表没有body数据要发送
	 */
	public void sendHeader(@NotNull H2Stream stream, @NotNull Headers header, boolean noBody) throws IOException {
		if (!isServer()) throw new IllegalArgumentException("客户端请使用sendHeaderClientSide()");
		if (stream.state != H2Stream.PROCESSING) throw new IllegalArgumentException("流"+stream+"不在可以发送头部的状态");
		sendHeader0(stream, header, noBody, 0, 0);
		if (stream.state == H2Stream.CLOSED) {
			streams.remove(stream.id);
			stream.finish(this);
		}
	}
	private void sendHeader0(H2Stream stream, Headers header, boolean noBody, int depend, int weight) throws IOException {
		ByteList ob = IOUtil.getSharedByteBuf();
		ob.putMedium(0).put(FRAME_HEADER).put(0).putInt(stream.id);
		if (weight != 0) {
			ob.put(4, FLAG_PRIORITY);
			if (!streams.containsKey(depend) || weight < 0 || weight > 256) throw new IllegalArgumentException("weight error");
			ob.putInt(depend).put(weight -1);
		}

		noBody = encodeLoop(header, noBody, ob, true);
		noBody = encodeLoop(header, noBody, ob, false);

		ob.put(4, ob.get(4) | FLAG_HEADER_END | (noBody ? FLAG_END : 0));
		ctx.channelWrite(withLength(ob));
		stream.state = noBody ? H2Stream.CLOSED : H2Stream.SEND_BODY;
	}
	private boolean encodeLoop(Headers header, boolean noBody, ByteList ob, boolean pseudo) throws IOException {
		// 是否需要在这里限制header list？
		int size1 = remoteSetting.max_header_list_size;

		int size = remoteSetting.max_frame_size;
		int oldSize = ob.wIndex();
		for (var entry : header.entrySet()) {
			var name = (String) entry.getKey();

			if (name.charAt(0) == ':' != pseudo) continue;
			hpack.encode(name, entry.getValue(), ob, header.getEncType(name));
			int newSize = ob.wIndex();
			if (newSize > size) {
				ob.wIndex(oldSize);
				ctx.channelWrite(withLength(ob));

				ob.rIndex = 0;
				ob.wIndex(9 + (newSize -= oldSize));
				ob.putShortLE(3, FRAME_CONTINUATION);
				System.arraycopy(ob.list, oldSize, ob.list, 9, newSize - oldSize);

				noBody = false;
			}
			oldSize = newSize;
		}
		return noBody;
	}

	public int getImmediateWindow(H2Stream stream) {
		if (ctx.isFlushing()) return 0;
		int v = stream == null ? sendWindow : Math.min(sendWindow, stream.sendWindow);
		return Math.min(remoteSetting.max_frame_size, v);
	}
	/**
	 * 发送数据
	 * @param isLastBlock true代表这是最后的数据
	 * @return 是否因为流量控制未发送完全
	 */
	public boolean sendData(H2Stream stream, DynByteBuf data, boolean isLastBlock) throws IOException {
		if (stream.state != (isServer() ? H2Stream.SEND_BODY : H2Stream.C_SEND_BODY)) throw new IllegalArgumentException("流"+stream+"不在可以发送数据的状态");

		int window = Math.min(stream.sendWindow, this.sendWindow);
		// 两年过去，我已经忘了BLOCK帧是干啥的了，RFC里也没看到
		if (window <= 0 || ctx.isFlushing()) return true;

		DynByteBuf wt;
		boolean limitedByFlowControl;
		if (data.readableBytes() > window) {
			wt = data.slice(window);
			limitedByFlowControl = true;
		} else {
			wt = data;
			limitedByFlowControl = false;
		}
		stream.sendWindow -= data.readableBytes();
		this.sendWindow -= data.readableBytes();

		int size = remoteSetting.max_frame_size;

		ByteList ob = new ByteList();
		ob.ensureCapacity(Math.min(wt.readableBytes(), size)+9);
		ob.putMedium(0).put(FRAME_DATA).put(0).putInt(stream.id);

		while (wt.readableBytes() > size) {
			ob.put(wt, size);
			wt.rIndex += size;
			ctx.channelWrite(withLength(ob));
			ob.rIndex = 0;
			ob.wIndex(9);

			if (ctx.isFlushing()) {
				stream.sendWindow += wt.readableBytes();
				this.sendWindow += wt.readableBytes();
				data.rIndex -= wt.readableBytes();

				ob._free();
				return true;
			}
		}

		if (isLastBlock) ob.put(4, FLAG_END);
		ob.put(wt);
		wt.rIndex = wt.wIndex();
		ctx.channelWrite(withLength(ob));

		ob._free();

		if (isLastBlock) {
			if (isServer()) {
				stream.state = H2Stream.CLOSED;
				streams.remove(stream.id);
				stream.finish(this);
			} else {
				stream.state = H2Stream.OPEN;
			}
		}

		return limitedByFlowControl;
	}
	//endregion
	//endregion
	public boolean isValid(H2Stream stream) {return streams.get(stream.id) == stream;}
	public boolean isServer() {return (flag & FLAG_SERVER) != 0;}
	public ChannelCtx channel() {return ctx;}
	@Override
	public String toString() {return ctx == null ? "<disconnected>" : String.valueOf(ctx.channel().remoteAddress());}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		for (var stream : streams.values()) {
			try {
				stream.tick(this);
			} catch (Exception e) {
				stream.onError(this, e);
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		for (var stream : streams.values()) {
			try {
				stream.finish(this);
			} catch (Exception ignored) {}
		}
		streams.clear();
	}
}