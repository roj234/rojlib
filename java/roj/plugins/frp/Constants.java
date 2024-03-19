package roj.plugins.frp;

import roj.concurrent.PacketBuffer;
import roj.concurrent.Shutdownable;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.handler.Compress;
import roj.text.logging.Level;
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
	public static final String PROTOCOL_VERSION = "2.2.0 (2024-04-06)";
	public static final Level LOG_LEVEL = Level.ALL;

	public static final int PIPE_IDLE_MAX = 600000;

	public static final int SERVER_TIMEOUT = 90000;
	public static final int CLIENT_TIMEOUT = 30000;

	public static final int MAX_PORTS = 16;
	public static final int MAX_MOTD = 32767;
	public static final int MAX_PACKET = 8192;

	public static final int DIGEST_LENGTH = 32;

	public static final int CLIENT_MAX_PIPES = 16;
	public static final int HOST_MAX_PIPES = 512;
	public static final int SERVER_MAX_PIPES = 1000;

	public static final String[] ERROR_NAMES = {
		"参数无效", "房间不存在/房间已存在", "证书指纹不在白名单中", "未知数据包", "服务器关闭", "房间关闭", "权限不足", "超时", "你被踢出"
	};
	public static final int PS_ERROR_STATE = 0x80;
	public static final int PS_ERROR_ROOM_EXISTENCE = 0x81;
	public static final int PS_ERROR_AUTH = 0x82;
	public static final int PS_ERROR_UNKNOWN_PACKET = 0x83;
	public static final int PS_ERROR_SHUTDOWN = 0x84;
	public static final int PS_ERROR_ROOM_CLOSE = 0x85;
	public static final int PS_ERROR_PERMISSION = 0x86;
	public static final int PS_ERROR_TIMEOUT = 0x87;
	public static final int PS_ERROR_KICKED = 0x88;

	// region 数据包ID和结构
	// region Handshake
	/**
	 * 收到之后重置MSS状态，与Host重新协商
	 * u4 clientId;
	 * u1 pipeId;
	 */
	public static final int PPS_PIPE_CLIENT = 1;
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
	 * u1 udpOffset
	 */
	public static final int PHS_LOGIN = 3;
	/**
	 * 收到之后重置MSS状态，与Host重新协商
	 * opaque ? session_id
	 */
	public static final int PPS_PIPE_HOST = 4;
	// endregion

	// logon reply
	/**
	 * u4 clientId;
	 * u1[digest_len] roomUserId; // SHA-1 digest of user certificate
	 * vi_gbk roomMotd;
	 * u1 portTableSize;
	 * u2[portTableSize] portTable;
	 * u1 udpOffset
	 */
	public static final int PCC_LOGON = 1;
	/**
	 * [no payload]
	 */
	public static final int PHH_LOGON = 1;

	/**
	 * 管道开启 (证书匹配Host，以此保证端到端安全)
	 * [no payload] => Server ChangeCipherSpec
	 * u1 portId => Host
	 */
	public static final int PPP_LOGON = 1;
	/**
	 * 管道拒绝开启 (证书匹配Server)
	 * vi_gbk reason;
	 */
	public static final int PPP_LOGIN_FAIL = 2;

	// common (host & client)
	// both
	/**
	 * u8 server_timestamp;
	 */
	public static final int P___HEARTBEAT = 1;
	public static final int P___LOGOUT = 2;
	public static final int P___OBSOLETED_3 = 3;
	// receives

	// host
	// receives
	/**
	 * 服务器转发到Host
	 * u4 clientId
	 * u1 portId;
	 * u1[16] sessionId;
	 */
	public static final int PHH_CLIENT_REQUEST_CHANNEL = 5;
	/**
	 * 服务器告知客户端加入
	 * u4 ordinal;
	 * u1 digest_len;
	 * u1[digest_len] userId;
	 * vi_gbk name;
	 *
	 * opaque[packet length] ip;
	 */
	public static final int PHH_CLIENT_LOGIN = 6;
	/**
	 * 服务器告知客户端退出
	 * u4 ordinal;
	 */
	public static final int PHH_CLIENT_LOGOUT = 7;
	// sends
	/**
	 * 不同意开启
	 * u1[16] sessionId;
	 * vi_gbk reason;
	 */
	public static final int PHS_CHANNEL_DENY = 4;
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
	public static final int PHS_OPERATION = 5;
	/**
	 * vi_gbk new_motd;
	 */
	public static final int PHS_UPDATE_MOTD = 6;
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
		LOGGER = Logger.getLogger(getClass().getSimpleName());
		LOGGER.setLevel(LOG_LEVEL);
	}

	private final PacketBuffer packets = new PacketBuffer(5);
	public final void writeAsync(DynByteBuf rb) {
		if (rb.readableBytes() > MAX_PACKET) throw new IllegalArgumentException("Packet too big ("+rb.readableBytes()+" > "+MAX_PACKET+")");
		packets.offer(rb);
	}
	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		if (!packets.isEmpty()) {
			ByteList buf = IOUtil.getSharedByteBuf();
			while (true) {
				DynByteBuf take = packets.take(buf);
				if (take == null) break;
				ctx.channelWrite(take);
				buf.clear();
			}
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
			LOGGER.warn("[{}] 警告: {}", this, ERROR_NAMES[b]);
		} else {
			rb.rIndex = 0;
			LOGGER.warn("[{}] 未知的数据包: {}", this, rb.dump());
			kickWithMessage(ctx, PS_ERROR_UNKNOWN_PACKET);
		}
		rb.rIndex = rb.wIndex();
	}
}