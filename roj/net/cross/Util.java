package roj.net.cross;

import roj.concurrent.Shutdownable;
import roj.net.ch.MyChannel;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.StandardSocketOptions;

/**
 * @author Roj233
 * @since 2021/9/12 5:25
 */
public class Util {
	public static final boolean DEBUG = true;//System.getProperty("AE.debug") != null;

	static {
		try {
			roj.misc.CpFilter.registerShutdownHook();
		} catch (Error ignored) {}
	}

	public static final int MAGIC = 0xAEAEAEAE;
	public static final int PROTOCOL_VERSION = 42;

	public static final String[] HS_ERROR_NAMES = {"人数过多", "客户端过期", "服务器过期", "系统限制", "超时", "协议错误"};
	public static final int HS_ERR_THROTTLING = 100;
	public static final int HS_ERR_VERSION_LOW = 101;
	public static final int HS_ERR_VERSION_HIGH = 102;
	public static final int HS_ERR_POLICY = 103;
	public static final int HS_ERR_TIMEOUT = 104;
	public static final int HS_ERR_PROTOCOL = 105;

	// region 控制频道数据包
	public static final int P_HEARTBEAT = 1;
	public static final int PS_LOGIN_PIPE = 1;
	/**
	 * 客户端登录
	 * {
	 * u1 id;
	 * u1 nameLen;
	 * u1 passLen;
	 * utf[nameLen] name;
	 * utf[passLen] pass;
	 * }
	 */
	public static final int PS_LOGIN_C = 2;
	/**
	 * 客户端登录完毕
	 * {
	 * u1 id;
	 * u1 infoLen;
	 * u1 motdLen;
	 * u1 portLen;
	 * u4 clientId;
	 * utf[infoLen] info;
	 * utf[motdLen] motd;
	 * u2[<N>] ports;
	 * }
	 */
	public static final int PC_LOGON_C = 2;
	/**
	 * 房主登录
	 * {
	 * u1 id;
	 * u1 nameLen;
	 * u1 passLen;
	 * u1 motdLen;
	 * u1 portLen;
	 * utf[nameLen] name;
	 * utf[passLen] pass;
	 * utf[motdLen] motd;
	 * u2[<N>] ports;
	 * }
	 */
	public static final int PS_LOGIN_H = 3;
	/**
	 * 房主登录完毕
	 * {
	 * u1 id;
	 * u1 infoLen;
	 * utf[infoLen] info;
	 * }
	 */
	public static final int PC_LOGON_H = 2;
	/**
	 * 退出登录（断开连接）
	 * {u1 id}
	 */
	public static final int P_LOGOUT = 4;
	/**
	 * 服务器告知客户端加入
	 * {
	 * u1 id;
	 * u4 ordinal;
	 * u2 port;
	 * u1 ipLen;
	 * u1[ipLen] ip;
	 * }
	 */
	public static final int PH_CLIENT_LOGIN = 3;
	/**
	 * 服务器告知客户端退出
	 * {
	 * u1 id;
	 * u4 ordinal;
	 * }
	 */
	public static final int PH_CLIENT_LOGOUT = 5;
	/**
	 * 房主踢出客户端
	 * {
	 * u1 id;
	 * u4 ordinal;
	 * }
	 */
	public static final int PS_KICK_CLIENT = 5;
	/**
	 * 客户端想开启一个数据频道，并提供加密密码第一部分
	 * {
	 * u1 id;
	 * u1 portId;
	 * u1[32] rnd1;
	 * }
	 */
	public static final int PS_REQUEST_CHANNEL = 6;
	/**
	 * A. 客户端想开启一个数据频道，并提供加密密码第一部分后，服务端创建SocketPair并发给host
	 * B. 房主同意开启，提供密码第二部分，服务端返回client
	 * {
	 * u1 id;
	 * u1 portId; (only when to host)
	 * u1[32] rnd;
	 * ORIGIN origin; (only when to host)
	 * DATA_CHANNEL data_channel_id;
	 * }
	 * <p>
	 * DATA_CHANNEL {
	 * u4 ordinal;
	 * u4 login_pass; // 之后不传
	 * }
	 */
	public static final int P_CHANNEL_RESULT = 7;
	/**
	 * 房主同意开启，并返回加密密码第二部分
	 * {
	 * u1 id;
	 * ORIGIN destination;
	 * u1[32] rnd;
	 * }
	 * <p>
	 * enum ORIGIN {
	 * -1=SERVER, 0=HOST, else = CLIENT
	 * }
	 */
	public static final int PS_CHANNEL_OPEN = 8;
	/**
	 * 房主或服务端不同意开启数据频道
	 * {
	 * u1 id;
	 * ORIGIN origin;
	 * VarIntUTF reason;
	 * }
	 */
	public static final int P_CHANNEL_OPEN_FAIL = 9;
	/**
	 * 任意一方关闭数据频道
	 * {
	 * u1 id;
	 * ORIGIN origin; (only when from server)
	 * DATA_CHANNEL.ordinal data_channel_id;
	 * }
	 */
	public static final int P_CHANNEL_CLOSE = 10;
	/**
	 * 重置管道状态
	 * {
	 * u1 id;
	 * DATA_CHANNEL.ordinal data_channel_id;
	 * }
	 */
	public static final int P_CHANNEL_RESET = 11;

	public static final int P_EMBEDDED_DATA = 12;
	/**
	 * 上次的操作失败了
	 * { u1 id; }
	 */
	public static final int P_FAIL = 13;
	/**
	 * UPnP ping
	 * from HOST/CLIENT: {
	 * u1 id;
	 * u8 secret;
	 * u2 port;
	 * u1 ipLen;
	 * u1[ipLen] ip;
	 * }
	 * from SERVER: {
	 * u1 id;
	 * u1 status;
	 * }
	 * to UPnP: { u1 id; }
	 */
	public static final int P_UPNP_PING = 14;
	/**
	 * UPnP pong
	 * from UPnP: { u1 id; u8 secret; }
	 * from HOST/CLIENT/SERVER: { (Notifies [it] to use this address)
	 * u1 id;
	 * u2 port;
	 * u1 ipLen;
	 * u1[ipLen] ip;
	 * }
	 */
	public static final int P_UPNP_PONG = 15;

	// endregion

	public static final String[] ERROR_NAMES = {"发生了异常", "登录失败", "未知数据包", "服务器关闭", "主机掉线", "系统限制", "超时", "你被T了"};
	public static final int PS_ERROR_IO = 0x20;
	public static final int PS_ERROR_AUTH = 0x21;
	public static final int PS_ERROR_UNKNOWN_PACKET = 0x22;
	public static final int PS_ERROR_SHUTDOWN = 0x23;
	public static final int PS_ERROR_MASTER_DIE = 0x24;
	public static final int PS_ERROR_SYSTEM_LIMIT = 0x25;
	public static final int PS_ERROR_TIMEOUT = 0x26;
	public static final int PS_ERROR_KICKED = 0x27;

	public static final int SERVER_TIMEOUT = 60000;
	public static final int CLIENT_TIMEOUT = 30000;

	public static PrintStream out;

	public static void print(String msg) {
		if (out == null) return;
		out.println(msg);
	}

	public static void registerShutdownHook(Shutdownable s) {
		Runtime.getRuntime().addShutdownHook(new Thread(s::shutdown));
	}

	@Deprecated
	public static void initSocketPref(Socket client) throws SocketException {
		client.setTcpNoDelay(true);
		client.setTrafficClass(0b10010000);
	}

	public static void initSocketPref(MyChannel client) throws IOException {
		client.setOption(StandardSocketOptions.TCP_NODELAY, true);
		client.setOption(StandardSocketOptions.SO_REUSEADDR, true);
		client.setOption(StandardSocketOptions.IP_TOS, 0b10010000);
	}
}
