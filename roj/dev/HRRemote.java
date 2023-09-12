package roj.dev;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.osi.ServerLaunch;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 热重载,支持一对多
 * 限制:
 * 1. 正在执行的函数, 下次调用才能起效
 * 2. 不能增加/修改/删除 方法/字段以及它们的类型/参数/访问级
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
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			DynByteBuf buf = (DynByteBuf) msg;
			if (buf.readableBytes() < buf.get(buf.rIndex)) return;
			buf.skipBytes(1);

			switch (buf.readUnsignedByte()) {
				case R_OK:
					int succeed = buf.readUnsignedShort();
					int all = buf.readUnsignedShort();
					System.out.println("重载[O]: " + succeed + " / " + all);
					break;
				case R_ERR:
					succeed = buf.readUnsignedShort();
					all = buf.readUnsignedShort();
					System.out.println("重载[X]: " + succeed + " / " + all);
					break;
				case R_SHUTDOWN:
					ctx.close();
					break;
			}
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			clients.remove(this);
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
		tmp.put(0x66).putShort(modified.size());
		for (int i = 0; i < modified.size(); i++) {
			IClass clz = modified.get(i);
			tmp.putUTF(clz.name().replace('/', '.'));
			ByteList shared = Parser.toByteArrayShared(clz);
			tmp.putInt(shared.wIndex()).put(shared);
		}

		ByteList array = ByteList.wrap(tmp.toByteArray());
		for (Client client : clients) {
			try {
				client.ctx.fireChannelWrite(array);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private final ConcurrentLinkedQueue<Client> clients;

	public HRRemote(int port) throws IOException {
		clients = new ConcurrentLinkedQueue<>();

		ServerLaunch.tcp().threadMax(1).threadPrefix("热重载服务器")
					.listen(InetAddress.getLoopbackAddress(), port)
					.option(StandardSocketOptions.SO_REUSEADDR, true)
					.initializator((ctx) -> {
						Client t = new Client(ctx);
						ctx.addLast("handler", t);
						clients.add(t);
					}).launch();
	}
}
