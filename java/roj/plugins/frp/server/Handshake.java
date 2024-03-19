package roj.plugins.frp.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.Pipe;
import roj.net.handler.MSSCipher;
import roj.net.mss.MSSException;
import roj.plugins.frp.Constants;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;

import static roj.plugins.frp.server.AEServer.server;

/**
 * @author Roj233
 * @since 2023/11/02 03:25
 */
final class Handshake extends Constants {
	static final Handshake HANDSHAKE = new Handshake();
	static final ChannelHandler Closer = new ChannelHandler() {
		@Override
		public void handlerAdded(ChannelCtx ctx) {
			try {
				ctx.channel().closeGracefully();
			} catch (IOException e) {
				IOUtil.closeSilently(ctx.channel());
			}
		}

		int time = 1000;
		@Override
		public void channelTick(ChannelCtx ctx) throws Exception {
			if (--time == 0) ctx.channel().close();
		}
	};

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ctx.removeSelf();

		DynByteBuf rb = (DynByteBuf) msg;
		byte b = rb.readByte();
		switch (b) {
			case PPS_PIPE_CLIENT: doPipeLoginClient(ctx, rb); break;
			case PPS_PIPE_HOST: doPipeLoginHost(ctx, rb); break;
			case PCS_LOGIN: compress(ctx.channel()); doClientLogin(ctx, rb); break;
			case PHS_LOGIN: compress(ctx.channel()); doHostLogin(ctx, rb); break;
			default: throw new MSSException(33, "数据包头错误:"+b, null);
		}
	}

	@Override
	public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
		LOGGER.error("处理失败", ex);
		ctx.close();
	}

	private void doPipeLoginClient(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		byte[] sessionId = IOUtil.getSharedByteBuf().put(getUserId(ctx)).putInt(rb.readInt()).toByteArray();
		int portId = rb.readUnsignedByte();

		Client client = server.session.get(sessionId);
		if (client == null) {
			pipeLoginFail(ctx, "会话ID不正确: "+TextUtil.bytes2hex(sessionId));
			return;
		}

		Host room = client.getRoom();

		if (server.pipes.size() > SERVER_MAX_PIPES) {
			pipeLoginFail(ctx, "服务器打开的管道过多");
			return;
		}
		if (client.pipes.size() > CLIENT_MAX_PIPES) {
			pipeLoginFail(ctx, "客户端打开的管道过多");
			return;
		}
		if (room.pipes.size() > HOST_MAX_PIPES) {
			pipeLoginFail(ctx, "房间打开的管道过多");
			return;
		}

		PipeInfo group = new PipeInfo();
		group.pipe = new Pipe();
		group.timeout = System.currentTimeMillis() + CLIENT_TIMEOUT;
		group.client = client;
		group.host = room;

		group.connection = ctx.channel();
		ctx.readInactive();
		ctx.channel().remove("splitter");

		sessionId = new byte[16];
		server.rnd.nextBytes(sessionId);

		room.writeAsync(IOUtil.getSharedByteBuf().put(PHH_CLIENT_REQUEST_CHANNEL).putInt(client.getClientId()).put(portId).put(sessionId));
		server.pipes.put(DynByteBuf.wrap(sessionId), group);
	}
	private void doPipeLoginHost(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		PipeInfo info = tryPipeLogin(ctx, rb);
		if (info == null) return;

		info.pipe = new Pipe();
		info.pipe.setDown(ctx.channel());
		info.pipe.setUp(info.connection);

		server.launch.loop().register(info.pipe, info);

		if (LOGGER.getLevel().canLog(Level.DEBUG)) {
			LOGGER.log(Level.DEBUG, "管道 {}({}) <=> {}({}) 开启", null,
				info.pipe.getDown().getRemoteAddress(), info.host,
				info.pipe.getUp().getRemoteAddress(), info.client);
		}
	}
	public MyChannel doPipeLoginHostLocal(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		PipeInfo info = tryPipeLogin(ctx, rb);
		if (info == null) return null;

		MyChannel up = info.connection;
		up.remove("tls");

		if (LOGGER.getLevel().canLog(Level.DEBUG)) {
			LOGGER.debug("管道 <local>({}) <=> {}({}) 开启", info.host, up.remoteAddress(), info.client);
		}

		return info.connection;
	}
	private PipeInfo tryPipeLogin(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		PipeInfo info = server.pipes.remove(rb);
		if (info == null) {
			pipeLoginFail(ctx, "会话ID不正确: "+rb.hex());
			return null;
		}

		MyChannel up = info.connection;
		up.readActive();
		up.fireChannelWrite(IOUtil.getSharedByteBuf().put(PPP_LOGON));
		up.flush();

		synchronized (info.client) {
			synchronized (info.host) {
				if (info.client.getClientId() > 0 && info.host.getClientId() == 0) {
					info.client.pipes.add(info);
					info.host.pipes.add(info);
				} else {
					ctx.close();
					return null;
				}
			}
		}

		return info;
	}
	private void pipeLoginFail(ChannelCtx ctx, String reason) throws IOException {
		LOGGER.info("[{}][PL]: {}", ctx.channel().remoteAddress(), reason);
		ctx.channelWrite(IOUtil.getSharedByteBuf().put(PPP_LOGIN_FAIL).putVUIGB(reason));
		ctx.replaceSelf(Closer);
	}

	private void doClientLogin(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		String roomToken = rb.readVUIGB();
		String nickName = rb.readVUIGB();
		byte[] userId = getUserId(ctx);

		Object o = server.clientLogin(userId, roomToken);
		if (o.getClass() == Integer.class) {
			int code = (int) o;
			LOGGER.info("[{}] 登录失败: {}", ctx.channel().remoteAddress(), ERROR_NAMES[code - 0x80]);

			kickWithMessage(ctx, code);
			return;
		}

		Client c = (Client) o;
		Host room = c.room;
		c.digest = userId;

		ByteList b = IOUtil.getSharedByteBuf();
		b.put(PCC_LOGON)
		 .putInt(c.clientId)
		 .put(room.digest)
		 .putVUInt(room.motd.length)
		 .put(room.motd)
		 .put(room.portMap.length)
		 .put(room.portMap)
		 .put(room.udpOffset);
		ctx.channelWrite(b);

		InetSocketAddress addr = (InetSocketAddress) ctx.remoteAddress();
		b.clear();
		b.put(PHH_CLIENT_LOGIN).putInt(c.clientId).put(userId).putVUIGB(nickName).put(addr.getAddress().getAddress());
		room.writeAsync(b);

		ctx.channel().addLast("client", c);
		LOGGER.info("[{}] 登录成功", ctx.channel().remoteAddress());

		byte[] sessionId = IOUtil.getSharedByteBuf().put(c.digest).putInt(c.getClientId()).toByteArray();
		server.session.put(sessionId, c);
	}
	private void doHostLogin(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		String roomToken = rb.readVUIGB();
		byte[] userId = getUserId(ctx);

		int len = rb.readVUInt();
		if (len > MAX_MOTD) {
			LOGGER.warn("[{}] 登录失败: {}", ctx.channel().remoteAddress(), "MAX_MOTD");
			kickWithMessage(ctx, PS_ERROR_STATE);
			return;
		}
		byte[] motd = rb.readBytes(len);

		len = rb.readUnsignedByte();
		if ((len&1) != 0) {
			LOGGER.warn("[{}] 登录失败: {}", ctx.channel().remoteAddress(), "PORT_SIZE");
			kickWithMessage(ctx, PS_ERROR_STATE);
			return;
		}

		byte[] port = rb.readBytes(len);
		int udpOffset = rb.readUnsignedByte();

		Object o = server.hostLogin(userId, roomToken);
		if (o.getClass() == Integer.class) {
			int code = (int) o;
			LOGGER.info("[{}] 登录失败: {}", ctx.channel().remoteAddress(), ERROR_NAMES[code - 0x80]);

			kickWithMessage(ctx, code);
			return;
		}

		Host room = (Host) o;
		room.digest = userId;

		room.motd = motd;
		room.motdString = new ByteList(motd).readGB(motd.length);
		room.portMap = port;
		room.udpOffset = udpOffset;

		ctx.channelWrite(IOUtil.getSharedByteBuf().put(PHH_LOGON));

		CharList sb = IOUtil.getSharedCharBuf();
		for (int i = 0; i < port.length; i++) {
			sb.append(((port[i++] & 0xFF) << 8) | (port[i] & 0xFF)).append(", ");
		}
		sb.setLength(sb.length()-2);

		ctx.channel().addLast("client", room);
		LOGGER.info("[{}] 登录成功, 房间 {}, 端口 [{}]", room, room.name, sb);
	}

	private static byte[] getUserId(ChannelCtx ctx) {
		ChannelCtx ctx1 = ctx.channel().handler("tls");
		return ctx1 == null ? AEServer.localUserId : ((AEServer.ProtocolVerify) ((MSSCipher) ctx1.handler()).getEngine()).userId;
	}
}