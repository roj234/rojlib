package roj.net.cross.server;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;

import static roj.net.cross.Util.*;

/**
 * @author Roj233
 * @since 2021/12/21 13:28
 */
final class ClientLogin extends Stated {
	static final ClientLogin CLIENT_LOGIN = new ClientLogin();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf rb = (DynByteBuf) msg;

		int nameLen = rb.get() & 0xFF;
		int passLen = rb.get() & 0xFF;

		Client W = ctx.attachment(Client.CLIENT);

		int code = AEServer.server.login(W, false, rb.readUTF(nameLen), rb.readUTF(passLen));
		if (code != -1) {
			print(W + "登录失败: " + ERROR_NAMES[code - 0x20]);
			ChannelCtx.bite(ctx, (byte) code);

			ctx.replaceSelf(Logout.LOGOUT);
			return;
		}

		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.put((byte) PC_LOGON_C)
		   .put((byte) AEServer.server.info.length)
		   .put((byte) W.room.motd.length)
		   .put((byte) (W.room.portMap.length / 2))
		   .putInt(W.clientId)
		   .put(AEServer.server.info)
		   .put(W.room.motd)
		   .put(W.room.portMap);
		if (W.room.upnpAddress != null) {
			tmp.put(W.room.upnpAddress);
		}
		ctx.channelWrite(tmp);

		InetSocketAddress s = (InetSocketAddress) ctx.remoteAddress();
		byte[] addr = s.getAddress().getAddress();
		tmp.clear();
		tmp.put((byte) PH_CLIENT_LOGIN).putInt(W.clientId).putShort(s.getPort()).put((byte) addr.length).put(addr);
		W.room.master.sync(ByteList.wrap(tmp.toByteArray()));

		print(W + "登录成功");

		ctx.replaceSelf(ClientWork.CLIENT_WORK);
	}
}
