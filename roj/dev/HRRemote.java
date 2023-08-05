package roj.dev;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.ServerLaunch;
import roj.net.ch.handler.Compress;
import roj.net.ch.handler.VarintSplitter;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 热重载,支持一对多
 * 限制: 正在执行的函数, 下次调用才能起效
 *
 * @author Roj233
 * @since 2022/2/21 15:09
 */
public class HRRemote {
	public static final byte R_OK = 0, R_ERR = 1, R_SHUTDOWN = 2;
	public static final int DEFAULT_PORT = 4485;

	final class Client implements ChannelHandler {
		final MyChannel ctx;

		Client(MyChannel ctx) {this.ctx = ctx;}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) {
			DynByteBuf buf = (DynByteBuf) msg;
			System.out.println(ctx.remoteAddress()+": "+buf.readUTF());
		}

		@Override
		public void channelClosed(ChannelCtx ctx) { clients.remove(this); }

		@Override
		public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
			if (ex instanceof IOException) {
				ctx.close();
			} else {
				ctx.exceptionCaught(ex);
			}
		}
	}

	public void sendChanges(List<? extends IClass> modified) {
		if (modified.isEmpty()) return;
		if (modified.size() > 9999) throw new IllegalArgumentException("Too many classes modified");

		ByteList tmp = IOUtil.getSharedByteBuf();
		for (int i = 0; i < modified.size(); i++) {
			IClass clz = modified.get(i);
			ByteList data = Parser.toByteArrayShared(clz);

			tmp.clear();
			send(tmp.put(0).put(data));
		}
		tmp.clear();
		send(tmp.put(1));
	}

	private void send(DynByteBuf tmp) {
		for (Client client : clients) {
			try {
				tmp.rIndex = 0;
				client.ctx.fireChannelWrite(tmp);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private final ConcurrentLinkedQueue<Client> clients;

	public HRRemote(int port) throws IOException {
		clients = new ConcurrentLinkedQueue<>();

		ServerLaunch.tcp("热重载服务器")
					.listen2(InetAddress.getLoopbackAddress(), port)
					.option(StandardSocketOptions.SO_REUSEADDR, true)
					.initializator((ctx) -> {
						Client t = new Client(ctx);
						ctx.addLast("Length", VarintSplitter.twoMbVLUI())
						   .addLast("Compress", new Compress())
						   .addLast("handler", t);
						clients.add(t);
					}).launch();
	}
}
