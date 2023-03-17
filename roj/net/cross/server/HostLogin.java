package roj.net.cross.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class HostLogin extends Stated {
	static final HostLogin HOST_LOGIN = new HostLogin();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf rb = (DynByteBuf) msg;

		int nameLen = rb.get() & 0xFF;
		int passLen = rb.get() & 0xFF;
		int motdLen = rb.get() & 0xFF;
		int portLen = rb.get() & 0xFF;

		Client W = ctx.attachment(Client.CLIENT);

		if (portLen < 1 || portLen > 64) {
			print(W + "端口映射表有误");
			ctx.replaceSelf(Logout.LOGOUT);
			return;
		}

		int code = AEServer.server.login(W, true, rb.readUTF(nameLen), rb.readUTF(passLen));
		if (code != -1) {
			print(W + "登录失败: " + ERROR_NAMES[code - 0x20]);
			ChannelCtx.bite(ctx, (byte) code);
			ctx.replaceSelf(Logout.LOGOUT);
			return;
		}

		byte[] motd = new byte[motdLen];
		rb.read(motd);

		byte[] port = new byte[portLen << 1];
		rb.read(port);

		Room room = W.room;
		room.motd = motd;
		room.motdString = new String(motd, StandardCharsets.UTF_8);
		room.portMap = port;

		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.put((byte) PC_LOGON_H).put((byte) AEServer.server.info.length).put(AEServer.server.info);
		ctx.channelWrite(tmp);

		StringBuilder pb = new StringBuilder();
		pb.append(W).append("登录成功, 端口映射表: ");
		for (int i = 0; i < port.length; i++) {
			pb.append(((port[i++] & 0xFF) << 8) | (port[i] & 0xFF)).append(", ");
		}
		pb.delete(pb.length() - 2, pb.length());
		print(pb.toString());

		ctx.replaceSelf(HostWork.HOST_WORK);
	}
}
