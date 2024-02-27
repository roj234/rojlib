package roj.plugins.ci;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.ServerLaunch;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.util.List;

/**
 * 热重载,支持一对多
 * 限制: 正在执行的函数, 下次调用才能起效
 *
 * @author Roj233
 * @since 2022/2/21 15:09
 */
public final class HRServer {
	public static final int DEFAULT_PORT = 4485;

	final class Client implements ChannelHandler {
		final MyChannel ctx;

		Client(MyChannel ctx) {this.ctx = ctx;}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) {
			DynByteBuf buf = (DynByteBuf) msg;
			if (buf.readableBytes() >= 2 && buf.readableBytes() >= buf.readShort(buf.rIndex)+2) {
				System.out.println(ctx.remoteAddress()+": "+buf.readUTF());
			}
		}

		@Override
		public void channelClosed(ChannelCtx ctx) {
			synchronized (HRServer.this) {clients.remove(this);}
			System.out.println(ctx.remoteAddress()+" 下线了");
		}

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
		tmp.put(1).putShort(modified.size());

		for (int i = 0; i < modified.size(); i++) {
			IClass clz = modified.get(i);
			ByteList data = Parser.toByteArrayShared(clz);

			try {
				send(tmp.putUTF(clz.name().replace('/', '.')).putInt(data.readableBytes()).put(data));
			} catch (Exception ignored) {
				// concurrent modification
			}
			tmp.clear();
		}
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

	private final SimpleList<Client> clients = new SimpleList<>();

	public HRServer(int port) throws IOException {
		ServerLaunch.tcp("热重载服务器")
					.bind2(InetAddress.getLoopbackAddress(), port)
					.option(StandardSocketOptions.SO_REUSEADDR, true)
					.initializator(ctx -> {
						System.out.println(ctx.remoteAddress()+" 上线了");
						Client t = new Client(ctx);
						ctx.addLast("handler", t);
						synchronized (this) {clients.add(t);}
					}).launch();
	}
}