package roj.net.cross.server;

import roj.net.ch.ChannelCtx;
import roj.net.ch.Pipe;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static roj.net.cross.Util.*;
import static roj.net.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2021/12/23 22:14
 */
final class PipeLogin extends Stated {
	static final PipeLogin PIPE_LOGIN = new PipeLogin();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Client W = ctx.attachment(Client.CLIENT);

		DynByteBuf rb = (DynByteBuf) msg;
		Integer user = rb.readInt();
		int pass = rb.readInt();

		PipeGroup group = server.pipes.get(user);
		if (group == null || pass == 0) {
			print(W + ": 无效的管道 " + user + "@" + pass);
			ctx.close();
			return;
		}

		Pipe pipe = group.pairRef;
		if (group.upPass == pass) {
			ChannelCtx.bite(ctx, (byte) PC_LOGON_H);
			pipe.setUp(ctx.channel());
			group.upPass = 0;
			if (DEBUG) print(W + ": " + user + " up logon");
		} else if (group.downPass == pass) {
			ChannelCtx.bite(ctx, (byte) PC_LOGON_H);
			pipe.setDown(ctx.channel());
			group.downPass = 0;
			if (DEBUG) print(W + ": " + user + " down logon");
		} else {
			print(W + ": " + user + " 密码无效");
			ctx.close();
			return;
		}

		if (group.upPass == group.downPass) {
			server.pipes.remove(user);
			group.downOwner.pending = null;

			print("管道 #" + user + " 开启");
			AtomicInteger i = server.remain;
			try {
				server.man.getLoop().register(pipe, p -> {
					i.addAndGet(2);
					PipeGroup group1 = (PipeGroup) ((Pipe) p).att;
					try {
						group1.close(-1);
					} catch (IOException ignored) {}
				});
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
