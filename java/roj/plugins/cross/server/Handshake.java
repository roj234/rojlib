package roj.plugins.cross.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.Pipe;
import roj.net.ch.handler.MSSCipher;
import roj.net.mss.MSSException;
import roj.plugins.cross.Constants;
import roj.text.CharList;
import roj.text.logging.Level;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

import static roj.plugins.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2023/11/02 03:25
 */
final class Handshake extends Constants {
	static final Handshake HANDSHAKE = new Handshake();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		ctx.removeSelf();

		DynByteBuf rb = (DynByteBuf) msg;
		byte b = rb.get();
		switch (b) {
			case PPS_PIPE_LOGIN: doPipeLogin(ctx, rb); break;
			case PCS_LOGIN: compress(ctx.channel()); doClientLogin(ctx, rb); break;
			case PHS_LOGIN: compress(ctx.channel()); doHostLogin(ctx, rb); break;
			default: throw new MSSException(33, "数据包头错误:"+b, null);
		}
	}

	// 只要连接不被服务器关闭，就是open状态，所以立即发送，无需等待。
	private void doPipeLogin(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		int id = rb.readInt();

		PipeInfo group = server.pipes.remove(id);
		if (group == null) {
			LOGGER.debug("[{}] 无效的管道 #{}", ctx.channel().remoteAddress(), id);
			ctx.close();
			return;
		}

		if (id == group.hostId) {
			group.pipe.setDown(ctx.channel());
			group.connected |= 1;
		} else {
			group.pipe.setUp(ctx.channel());
			group.connected |= 2;
		}

		if (group.connected == 3) {
			if (LOGGER.getLevel().canLog(Level.DEBUG)) {
				LOGGER.log(Level.DEBUG, "管道 {}({}) <=> {}({}) 开启", null,
					group.pipe.getDown() == null ? "<memory>" : group.pipe.getDown().getRemoteAddress(), Integer.toHexString(group.hostId),
					group.pipe.getUp().getRemoteAddress(), Integer.toHexString(group.clientId));
			}
			if (group.host_wait != null) group.host_wait.accept(group.pipe);
			else server.launch.loop().register(group.pipe, group);

			group.connected = 7;
		}
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

		System.out.println(userId.length);
		Client c = ((Client) o);
		c.digest = userId;

		ByteList b = IOUtil.getSharedByteBuf();
		b.put(PCC_LOGON)
		 .putInt(c.clientId)
		 .put(c.room.digest.length)
		 .put(c.room.digest)
		 .putVUInt((byte) c.room.motd.length)
		 .put(c.room.motd)
		 .put((c.room.portMap.length / 2))
		 .put(c.room.portMap);

		byte[] directAddr = c.room.getDirectAddrIfValid();
		if (directAddr == null) b.put(0);
		else b.put(1).put(directAddr);

		ctx.channelWrite(b);

		b.clear();

		InetSocketAddress addr = (InetSocketAddress) ctx.remoteAddress();
		b.put(PHH_CLIENT_LOGIN).putInt(c.clientId).put(userId.length).put(userId).putVUIGB(nickName).put(addr.getAddress().getAddress());

		c.room.writeAsync(b);

		ctx.channel().addLast("client", c);
		LOGGER.info("[{}] 登录成功", ctx.channel().remoteAddress());
	}

	private void doHostLogin(ChannelCtx ctx, DynByteBuf rb) throws IOException {
		String roomToken = rb.readVUIGB();
		byte[] userId = getUserId(ctx);

		int len = rb.readVUInt();
		if (len > MAX_MOTD) {
			LOGGER.warn("[{}] 登录失败: {}", ctx.channel().remoteAddress(), "MAX_MOTD");
			kickWithMessage(ctx, PS_ERROR_SYSTEM_LIMIT);
			return;
		}
		byte[] motd = rb.readBytes(len);

		len = rb.readUnsignedByte();
		if (len < 1 || len > MAX_PORTS) {
			LOGGER.warn("[{}] 登录失败: {}", ctx.channel().remoteAddress(), "MAX_PORTS");
			kickWithMessage(ctx, PS_ERROR_SYSTEM_LIMIT);
			return;
		}
		byte[] port = rb.readBytes(len << 1);

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

		ByteList b = IOUtil.getSharedByteBuf();
		b.put(PHH_LOGON)
		 .putVUIGB(room.token);
		ctx.channelWrite(b);

		CharList sb = IOUtil.getSharedCharBuf();
		for (int i = 0; i < port.length; i++) {
			sb.append(((port[i++] & 0xFF) << 8) | (port[i] & 0xFF)).append(", ");
		}
		sb.setLength(sb.length()-2);

		ctx.channel().addLast("client", room);
		LOGGER.info("[{}] 登录成功, 端口映射表: {}", room, sb);
	}

	public void localPipe(int id, byte[] key, Consumer<Pipe> cb) {
		PipeInfo group = server.pipes.remove(id);
		if (group == null) {
			LOGGER.debug("[{}] 无效的管道 #{}", "embedded", id);
			return;
		}

		assert id == group.hostId && group.connected == 0;
		group.connected |= 1;
		group.pipe = new Pipe.CipherPipe(key);
		group.host_wait = cb;
	}

	private byte[] getUserId(ChannelCtx ctx) {
		ChannelCtx ctx1 = ctx.channel().handler("tls");
		return ctx1 == null ? AEServer.localUserId : ((AEServer.ProtocolVerify) ((MSSCipher) ctx1.handler()).getEngine()).userId;
	}
}
