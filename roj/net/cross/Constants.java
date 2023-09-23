package roj.net.cross;

import roj.collect.RingBuffer;
import roj.concurrent.Shutdownable;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.handler.Compress;
import roj.text.Template;
import roj.text.logging.Level;
import roj.text.logging.LogContext;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.StandardSocketOptions;

/**
 * @author Roj233
 * @since 2021/12/21 13:18
 */
public abstract class Constants implements ChannelHandler {
	public static final String PROTOCOL_VERSION = "2.0.3";
	public static final Level LOG_LEVEL = Level.INFO;

	// 开启的管道超过PIPE_IDLE_MIN个时,自动关闭超过PIPE_IDLE_MAX时间没有数据的管道
	public static final int PIPE_IDLE_MIN = 2;
	public static final int PIPE_IDLE_MAX = 600000;

	public static final int SERVER_TIMEOUT = 60000;
	public static final int CLIENT_TIMEOUT = 30000;

	public static final int MAX_PORTS = 16;
	public static final int MAX_MOTD = 32767;
	public static final int MAX_PACKET = 8192;

	public static final int CLIENT_MAX_PIPES = 16;
	public static final int HOST_MAX_PIPES = 512;
	public static final int SERVER_MAX_PIPES = 1000;

	public static final String[] ERROR_NAMES = {
		"发生了异常", "登录失败", "未知数据包", "服务器关闭", "房间关闭", "系统限制", "超时", "你被踢了"
	};
	public static final int PS_ERROR_IO = 0x80;
	public static final int PS_ERROR_AUTH = 0x81;
	public static final int PS_ERROR_UNKNOWN_PACKET = 0x82;
	public static final int PS_ERROR_SHUTDOWN = 0x83;
	public static final int PS_ERROR_ROOM_CLOSE = 0x84;
	public static final int PS_ERROR_SYSTEM_LIMIT = 0x85;
	public static final int PS_ERROR_TIMEOUT = 0x86;
	public static final int PS_ERROR_KICKED = 0x87;

	// region 数据包ID和结构
	// region Handshake
	/**
	 * u8 token;
	 */
	public static final int PPS_PIPE_LOGIN = 1;
	/**
	 * vi_gbk roomToken;
	 * vi_gbk userToken;
	 */
	public static final int PCS_LOGIN = 2;
	/**
	 * empty vi_gbk roomToken;
	 * vi_gbk userToken;
	 * empty vi_gbk motd;
	 * u1 portLen
	 * u2[portLen] portTable
	 */
	public static final int PHS_LOGIN = 3;
	// endregion

	// logon reply
	/**
	 * u4 clientId;
	 * u1 digest_len;
	 * u1[digest_len] roomUserId; // SHA-1 digest of user certificate
	 * vi_gbk roomMotd;
	 * u1 portTableSize;
	 * u2[portTableSize] portTable;
	 * bool hasDirectAddr;
	 * u1[...] notImplemented_DirectAddr;
	 */
	public static final int PCC_LOGON = 1;
	/**
	 * vi_gbk roomToken;
	 */
	public static final int PHH_LOGON = 1;

	// common (host & client)
	// both
	/**
	 * u8 server_timestamp;
	 */
	public static final int P___HEARTBEAT = 1;
	public static final int P___LOGOUT = 2;

	/**
	 * EmbeddedMyChat使用
	 * opaque[packet length] data
	 */
	public static final int P___CHAT_DATA = 3;
	/**
	 * 无用途.
	 */
	public static final int P___NOTHING = 4;
	// receives
	/**
	 * 关闭管道（服务器主动关闭时发送，Client/Host只要调close就行）
	 * u4 pipe_key;
	 * vi_gbk reason;
	 */
	public static final int P___CHANNEL_CLOSED = 5;
	// sends
	/**
	 * 让服务端去ping
	 * u1 ipLen;
	 * opaque[ipLen] ip;
	 * u2 port;
	 * 回包
	 * u1 state
	 */
	public static final int P_S_PING = 5;
	/**
	 * 上述地址通过MSS协议连接后会发送一些随机的数据用于验证地址
	 * opaque[packet length] verify
	 */
	public static final int P_S_PONG = 6;

	// client
	// receives
	/**
	 * 同意开启
	 * u4 session_id;
	 * u1[32] shared_secret;
	 * u4 pipe_key;
	 */
	public static final int PCC_CHANNEL_ALLOW = 6;
	/**
	 * 不同意开启
	 * u4 session_id;
	 * vi_gbk reason;
	 */
	public static final int PCC_CHANNEL_DENY = 7;
	// sends
	/**
	 * 客户端想开启一个数据频道
	 * u4 session_id; // 同时开启多个频道时不用等待
	 * u1 portId;
	 * u1[32] shared_secret;
	 */
	public static final int PCS_REQUEST_CHANNEL = 7;

	// host
	// receives
	/**
	 * 服务器转发到Host
	 * u1 portId;
	 * u1[32] shared_secret;
	 * u4 fromId;
	 * u4 pipe_key;
	 */
	public static final int PHH_CLIENT_REQUEST_CHANNEL = 6;
	/**
	 * 服务器告知客户端加入
	 * u4 ordinal;
	 * u1 digest_len;
	 * u1[digest_len] userId;
	 * vi_gbk name;
	 *
	 * opaque[packet length] ip;
	 */
	public static final int PHH_CLIENT_LOGIN = 7;
	/**
	 * 服务器告知客户端退出
	 * u4 ordinal;
	 */
	public static final int PHH_CLIENT_LOGOUT = 8;
	// sends
	/**
	 * 同意开启
	 * u4 pipeId;
	 * u1[32] shared_secret_part2;
	 */
	public static final int PHS_CHANNEL_ALLOW = 7;
	/**
	 * 不同意开启
	 * u4 pipeId;
	 * vi_gbk reason;
	 */
	public static final int PHS_CHANNEL_DENY = 8;
	/**
	 * operation[packet length]
	 *
	 * struct operation {
	 *     OperationId id
	 *     u4 userId
	 * }
	 * enum OperationId {
	 *     KICK(0)
	 *     BAN(1)
	 *     UNBAN(2)
	 * }
	 */
	public static final int PHS_OPERATION = 9;
	/**
	 * vi_gbk new_motd;
	 */
	public static final int PHS_UPDATE_MOTD = 10;
	// endregion

	public static void registerShutdownHook(Shutdownable s) {
		Runtime.getRuntime().addShutdownHook(new Thread(s::shutdown));
	}

	public static void initSocketPref(MyChannel client) throws IOException {
		client.setOption(StandardSocketOptions.TCP_NODELAY, true);
		client.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		client.setOption(StandardSocketOptions.IP_TOS, 0b10010000);
	}

	public static void compress(MyChannel ch) {
		ChannelCtx timeout = ch.handler("timeout");
		if (timeout != null)
			ch.addBefore(timeout, "compress", new Compress(99999, 200, 1024, -1));
	}

	protected final Logger LOGGER;
	public Constants() {
		LogContext ctx = new LogContext();
		ctx.name(getClass().getSimpleName()).setPrefix(Template.compile("[${0}][${NAME}/${LEVEL}]: "));
		LOGGER = Logger.getLogger(ctx);
		LOGGER.setLevel(LOG_LEVEL);
	}

	private final RingBuffer<byte[]> packets = new RingBuffer<>(10, 100);
	public final void writeAsync(DynByteBuf rb) {
		if (rb.readableBytes() > MAX_PACKET) throw new IllegalArgumentException("Packet too big");
		synchronized (packets) { packets.ringAddLast(rb.toByteArray()); }
	}
	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (!packets.isEmpty()) {
			byte[] data;
			synchronized (packets) { data = packets.removeFirst(); }
			ctx.channelWrite(new ByteList(data));
		}
	}

	protected final void kickWithMessage(ChannelCtx ctx, int code) {
		ByteList b = IOUtil.getSharedByteBuf().put(code);
		try {
			ctx.channelWrite(b);
			ctx.channel().closeGracefully();
		} catch (IOException e) {
			LOGGER.warn("[{}]: ", e, this);
		}
	}

	protected final void unknownPacket(ChannelCtx ctx, DynByteBuf rb) {
		int b = rb.getU(rb.rIndex-1) - 0x80;
		if (!rb.isReadable() && b >= 0 && b < ERROR_NAMES.length) {
			LOGGER.warn("[{}] 报告了一个错误: {}", this, ERROR_NAMES[b]);
		} else {
			rb.rIndex = 0;
			LOGGER.warn("[{}] 收到了未知的数据包: {}", this, rb.dump());
			kickWithMessage(ctx, PS_ERROR_UNKNOWN_PACKET);
		}
		rb.rIndex = rb.wIndex();
	}
}
