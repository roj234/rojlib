package roj.http;

import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import static roj.reflect.Unaligned.U;

/**
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC6455 - Websocket</a>
 * @author Roj234
 * @since 2022/11/11 1:30
 */
public abstract class WebSocketConnection implements ChannelHandler {
	public static final byte
		FRAME_CONTINUE = 0x0,
		FRAME_TEXT = 0x1,
		FRAME_BINARY = 0x2,
		FRAME_CLOSE = 0x8,
		FRAME_PING = 0x9,
		FRAME_PONG = 0xA;

	/**
	 * 关闭握手异常代号
	 * 代号	描述	使用场景
	 * 1000	正常关闭	会话正常完成时
	 * 1001	离开	应用离开且不期望后续连接的尝试而关闭连接时
	 * 1002	协议错误	因协议错误而关闭连接时
	 * 1003	不可接受的数据类型	非二进制或文本类型时
	 * 1007	无效数据	文本格式错误，如编码错误
	 * 1008	消息违反政策	当应用程序由于其他代号不包含的原因时
	 * 1009	消息过大	当接收的消息太大，应用程序无法处理时（帧的载荷最大为64字节）
	 * 1010	需要拓展
	 * 1011	意外情况
	 * 2 其他代号
	 * 代号	描述	使用情况
	 * 0~999	禁止
	 * 1000~2999	保留
	 * 3000~3999	需要注册	用于程序库、框架和应用程序
	 * 4000~4999	私有	应用程序自由使用
	 */
	public static final int
		ERR_OK = 1000,
		ERR_CLOSED = 1001,
		ERR_PROTOCOL = 1002,
		ERR_INVALID_FORMAT = 1003,
		ERR_INVALID_DATA = 1007,
		ERR_POLICY = 1008,
		ERR_TOO_LARGE = 1009,
		ERR_EXTENSION_REQUIRED = 1010,
		ERR_UNEXPECTED = 1011;

	public static final int RSV_COMPRESS = 0x40;

	// flag的可选位
	public static final int REMOTE_NO_CTX = 0x01, // 对等端压缩无上下文 (跨消息复用字典)
		LOCAL_NO_CTX = 0x02, // 本地压缩无上下文
		LOCAL_SIMPLE_MASK = 0x04, // 作为客户端时,跳过mask步骤
		ACCEPT_PARTIAL_MSG = 0x08, // 允许处理组合之前的分片 (此时onPacket函数的ph参数将会有0x80位,并可以通过cf字段获取)
		__SEND_COMPRESS = 0x10, // 发送需要压缩 (内部使用)
		__CONTINUOUS_SENDING = 0x20, // 正在发送分片帧 (内部使用)
		COMPRESS_AVAILABLE = 0x40, // 对等端允许压缩 (permessage-deflate)
		REMOTE_MASK = 0x80; // 标志为服务端

	private Deflater def;
	private Inflater inf;

	private final byte[] mask = new byte[4];

	// 分片接收缓冲
	private ContinuousFrame continuousFrame;
	private static final class ContinuousFrame {
		ContinuousFrame(int data) { this.data = (byte) data; }

		final byte data;
		int fragments;
		private DynByteBuf packet;
		long length;

		DynByteBuf payload() { return packet; }

		void append(ChannelCtx ctx, DynByteBuf b) {
			if (packet == null) packet = ctx.allocate(true, b.readableBytes());
			else if (packet.writableBytes()<b.readableBytes()) packet = ctx.alloc().expand(packet, b.readableBytes());

			packet.put(b);
			length += b.readableBytes();
			fragments++;
		}

		void clear() {
			if (packet != null) {
				BufferPool.reserve(packet);
				packet = null;
			}
		}
	}

	// 最大数据长度
	protected int maxData, maxDataOnce, compressSize;
	// 空置时间 ms (注意: 你不应因发送操作而将其重置, 因为其目的是检测对等端是否还存活)
	protected int idle;
	protected byte flag;
	// (自动)发送分片大小
	protected int fragmentSize;

	public int errCode;
	public String errMsg;

	public WebSocketConnection() {
		// default 128k
		maxDataOnce = 131072;
		maxData = 1048576;
		flag = (byte) REMOTE_MASK;
		fragmentSize = 4096;
	}

	public final void enableZip() {
		if (def != null) return;
		this.def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		this.inf = new Inflater(true);
		this.flag |= COMPRESS_AVAILABLE;
	}

	public void setMaxData(int maxData) {this.maxData = maxData;}
	public void setMaxDataOnce(int maxDataOnce) {this.maxDataOnce = maxDataOnce;}

	public static CharList decodeToUTF(DynByteBuf in) { return in.readUTF(in.readableBytes(), IOUtil.getSharedCharBuf()); }

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		idle++;
		if (idle == 30000) {
			send(FRAME_PING, null);
		} else if (idle == 35000) {
			close(ERR_UNEXPECTED, "timeout");
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (inf != null) {
			inf.end();
			def.end();
		}
	}

	private static final int HEADER = 0, LENGTH = 1, DATA = 2;
	private byte state;
	private char header;
	private int length;

	@Override
	@SuppressWarnings("fallthrough")
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		idle = 0;

		var rb = (DynByteBuf) msg;
		switch (state) {
			case HEADER:
				if (rb.readableBytes() < 2) return;
				this.header = rb.readChar();

				int len = header&0xFF;
				if ((len & 0x80) != (flag & REMOTE_MASK)) {
					close(ERR_PROTOCOL, "not masked properly");
					return;
				}
				len = switch (len & 0x7F) {
					case 126 -> 2; // extra 2 bytes
					case 127 -> 8; // extra 8 bytes
					default -> 0;
				};

				int head = header >>> 8;
				if ((head & 15) >= 0x8) {
					if (len != 0) {
						close(ERR_TOO_LARGE, "control frame size");
						return;
					}
					if ((head & 0x80) == 0) {
						close(ERR_PROTOCOL, "control frame fragmented");
						return;
					}
				}

				this.length = len;
				this.state = LENGTH;
			case LENGTH:
				if (rb.readableBytes() < length) return;
				len = header & 0x7F;
				switch (len) {
					case 126: len = rb.readChar(); break;
					case 127:
						long l = rb.readLong();
						if (l > Integer.MAX_VALUE - len) {
							close(ERR_TOO_LARGE, ">2G");
							return;
						}
						len = (int) l;
						break;
				}
				if ((flag & REMOTE_MASK) != 0) len += 4;

				if (len > maxDataOnce) {
					close(ERR_TOO_LARGE, null);
					return;
				}

				this.length = len;
				this.state = DATA;
			case DATA:
				if (rb.readableBytes() < length) return;
				if ((flag & REMOTE_MASK) != 0) {
					rb.readFully(mask);
					length -= 4;
				}
				break;
		}

		int wPos = rb.wIndex();
		int head = header>>>8;

		rb.wIndex(rb.rIndex+length);
		if ((flag & REMOTE_MASK) != 0) mask(rb);

		if (continuousFrame == null) {
			if ((head & 0xF) == 0) {
				close(ERR_PROTOCOL, "Unexpected continuous frame");
				return;
			} else if ((head & 0x80) == 0) {
				continuousFrame = new ContinuousFrame(head);
			}
		} else if ((head & 0xF) != 0 && (head & 0xF) < 8) {
			close(ERR_PROTOCOL, "Receive new message in continuous frame");
			return;
		} else {
			head = (head & 0x80) | (continuousFrame.data & 0x7F);
		}

		boolean compressed = (head & RSV_COMPRESS) != 0;
		if (compressed) {
			if (inf == null) {
				close(ERR_PROTOCOL, "Illegal rsv bits");
				return;
			}

			var hasNext = true;
			byte[] zi = IOUtil.getSharedByteBuf().list;

			var zo = ctx.allocate(false, 1024);

			try {
				while (hasNext) {
					int $len = Math.min(rb.readableBytes(), zi.length);
					rb.readFully(zi, 0, $len);

					pushEOS:
					if (!rb.isReadable()) {
						if ((head & 0x80) != 0) {
							// not enough space, process input data first
							if (zi.length - $len < 4) break pushEOS;

							zi[$len++] = 0;
							zi[$len++] = 0;
							zi[$len++] = -1;
							zi[$len++] = -1;

							// break on next cycle
							hasNext = false;
						} else {
							break;
						}
					}

					inf.setInput(zi, 0, $len);

					try {
						do {
							int i = inf.inflate(zo.array(), zo.arrayOffset() + zo.wIndex(), zo.capacity() - zo.wIndex());
							zo.wIndex(zo.wIndex() + i);

							if (!zo.isWritable()) {
								if (zo.capacity() << 1 > maxDataOnce) {
									close(ERR_TOO_LARGE, "decompressed data > "+zo.capacity()+" bytes");
									return;
								}
								zo = ctx.alloc().expand(zo, zo.capacity());
							}
						} while (!inf.needsInput());
					} catch (Exception e) {
						Helpers.athrow(e);
					}
				}

				// not continuous
				if ((head & 0x80) != 0 && (flag & REMOTE_NO_CTX) != 0) {
					inf.reset();
				}
			} catch (Exception e) {
				BufferPool.reserve(zo);
				throw e;
			}

			rb = zo;
		}

		try {
			if (continuousFrame != null) {
				if ((flag & ACCEPT_PARTIAL_MSG) != 0) {
					onPacket(0x80 | (continuousFrame.data&0xF), rb);
					continuousFrame.fragments++;
				} else {
					continuousFrame.append(ctx, rb);
					if (continuousFrame.length > maxData) {
						close(ERR_TOO_LARGE, null);
					}

					if ((head & 0x80) != 0) {
						try {
							onPacket(continuousFrame.data&0xF, continuousFrame.payload());
						} finally {
							continuousFrame.clear();
							continuousFrame = null;
						}
					}
				}
			} else {
				onPacket(head & 15, rb);
			}
		} finally {
			state = HEADER;
			if (compressed) BufferPool.reserve(rb);
			else if (rb.capacity() != 0) rb.wIndex(wPos);
		}
	}

	private void mask(DynByteBuf b) {
		byte[] mask = this.mask;
		int maskI = U.getInt(mask, (long) Unaligned.ARRAY_BYTE_BASE_OFFSET);

		byte[] ref = b.array();
		long addr = b._unsafeAddr()+b.rIndex;
		long len = addr+b.readableBytes();
		while (len-addr >= 4) {
			U.putInt(ref,addr,maskI^ U.getInt(ref,addr));
			addr += 4;
		}

		int i = 0;
		while (addr < len) {
			U.putByte(ref,addr,(byte)(mask[i++]^ U.getByte(ref,addr)));
			addr++;
		}
	}

	protected void onPacket(int ph, DynByteBuf in) throws IOException {
		switch (ph & 0xF) {
			case FRAME_CLOSE:
				if (in.readableBytes() < 2) {
					close(ERR_CLOSED, "closed");
					return;
				}
				if (errCode == 0) {
					errCode = in.readChar();
					errMsg = in.readUTF(in.readableBytes());
				}
				try {
					send(FRAME_CLOSE, in);
				} catch (IOException ignored) {}
				ch.close();
				break;
			case FRAME_PONG: break;
			case FRAME_PING: send(FRAME_PONG, null); break;
			case FRAME_TEXT, FRAME_BINARY: onData(ph, in); break;
			default: throw new IOException("Unsupported packet id #"+ph);
		}
	}

	protected abstract void onData(int ph, DynByteBuf in) throws IOException;

	protected int randomMask() {
		return ThreadLocalRandom.current().nextInt();
	}

	public final void close(int code, String msg) throws IOException {
		if (errCode != 0) {
			ch.close();
			return;
		}

		if (msg == null) msg = "";
		else if (msg.length() > 125) msg = msg.substring(0, 125);

		errCode = code;
		errMsg = msg;

		var data = IOUtil.getSharedByteBuf().putShort(code).putUTFData(msg);
		if (data.wIndex() > 125) data.wIndex(125);
		send(FRAME_CLOSE, data);
		ch.channel().closeGracefully();
	}

	public final void send(CharSequence data) throws IOException {
		ByteList b = new ByteList();
		send(FRAME_TEXT, b.putUTFData(data));
		b._free();
	}
	public final void send(DynByteBuf data) throws IOException { send(FRAME_BINARY, data); }
	public final void send(int opcode, DynByteBuf data) throws IOException {
		if ((flag & __CONTINUOUS_SENDING) != 0) throw new IOException("sendContinuous() not reach EOF");
		if ((opcode & RSV_COMPRESS) > (flag & RSV_COMPRESS)) throw new IOException("Invalid compress state");

		if (data == null) data = ByteList.EMPTY;
		else if (compressSize != 0 && data.readableBytes() > compressSize && (flag & RSV_COMPRESS) != 0) opcode |= RSV_COMPRESS;

		int rem = data.readableBytes();
		boolean comp = rem > 0 && (opcode & RSV_COMPRESS) != 0;
		if (fragmentSize > 0 && rem > fragmentSize) {
			// frame[0]: continuous flag + original opcode
			data.wIndex(data.rIndex + fragmentSize);
			send0(opcode, data, comp);
			rem -= fragmentSize;

			// frame[1] ... frame[count - 1]: opcode=0
			while (rem > fragmentSize) {
				data.wIndex(data.rIndex + fragmentSize);
				send0(FRAME_CONTINUE, data, comp);
				rem -= fragmentSize;
			}

			// frame[count]: not continuous
			data.wIndex(data.rIndex + rem);
			send0(0x80, data, comp);
		} else {
			send0(opcode | 0x80, data, comp);
		}
	}

	public final void sendContinuous(int opcode, DynByteBuf data, boolean endOfFrame) throws IOException {
		boolean first = (flag & __CONTINUOUS_SENDING) == 0;
		if (first) {
			if (endOfFrame) {
				send(opcode, data);
			} else {
				if ((opcode & RSV_COMPRESS) > (flag & RSV_COMPRESS)) throw new IOException("Invalid compress state");
				opcode &= ~0x80;
				if ((opcode & RSV_COMPRESS) != 0) flag |= __SEND_COMPRESS;
				flag |= __CONTINUOUS_SENDING;
			}
		} else {
			opcode = endOfFrame ? 0x80 : 0;
		}
		send0(opcode, data, (flag & __SEND_COMPRESS) != 0);

		if (endOfFrame) flag &= ~(__CONTINUOUS_SENDING | __SEND_COMPRESS);
	}

	public ChannelCtx ch;

	private void send0(int opcode, DynByteBuf data, boolean compress) throws IOException {
		int $len = data.readableBytes();

		if (compress) {
			DynByteBuf buf = null;
			if (data.hasArray()) {
				def.setInput(data.array(), data.arrayOffset() + data.rIndex, data.readableBytes());
			} else {
				buf = ch.allocate(false, $len);
				byte[] zb = buf.array();

				// input is promised to used up
				data.readFully(zb, 0, $len);
				def.setInput(zb, 0, $len);
			}

			ByteList zbh = IOUtil.getSharedByteBuf();
			byte[] zb = zbh.array();
			$len = 0;

			try {
				if ((flag & LOCAL_NO_CTX) != 0) def.finish();

				int flush = Deflater.NO_FLUSH;
				while (true) {
					int i = def.deflate(zb, $len, zb.length - $len, flush);
					$len += i;
					if (zb.length == $len) {
						byte[] zb1 = new byte[zb.length << 1];
						System.arraycopy(zb, 0, zb1, 0, $len);
						zb = zb1;
						if (zb1.length < 262144) zbh.list = zb1;
					} else if (i == 0) {
						if (flush == Deflater.NO_FLUSH) {
							flush = Deflater.SYNC_FLUSH;
						} else {
							break;
						}
					}
				}

				if ((opcode & 0x80) != 0) {
					if ((flag & LOCAL_NO_CTX) != 0) def.reset();
					$len -= 4;
				}
			} finally {
				if (buf != null) BufferPool.reserve(buf);
			}

			data = ByteList.wrap(zb, 0, $len);
		}

		var out = ch.allocate(true, $len + 10).put((byte) opcode);
		if ($len <= 125) {
			out.put((byte) $len);
		} else if ($len <= 65535) {
			out.put(126).putShort($len);
		} else {
			out.put(127).putLong($len);
		}

		if ((flag & REMOTE_MASK) == 0) {
			out.put(1, (byte) (out.get(1) | REMOTE_MASK));
			if ((flag & LOCAL_SIMPLE_MASK) != 0) out.putInt(0);
			else {
				int mask = randomMask();
				out.putInt(mask);
				byte[] bm = this.mask;
				bm[0] = (byte) (mask >>> 24);
				bm[1] = (byte) (mask >>> 16);
				bm[2] = (byte) (mask >>> 8);
				bm[3] = (byte) mask;
				mask(data);
			}
		}

		out.put(data);
		data.rIndex = data.wIndex();
		try {
			ch.channelWrite(out);
		} finally {
			BufferPool.reserve(out);
		}
	}

	@Override public void handlerAdded(ChannelCtx ctx) {ch = ctx;}
	@Override public void handlerRemoved(ChannelCtx ctx) {ch = null;}
}