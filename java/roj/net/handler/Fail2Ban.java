package roj.net.handler;

import roj.concurrent.task.ITask;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
public class Fail2Ban implements ChannelHandler {
	private static final Logger LOGGER = Logger.getLogger("F2B4J");
	private final ConcurrentHashMap<InetAddress, LoginAttempt> data = new ConcurrentHashMap<>();
	private final int LOGIN_ATTEMPTS = 5, LOGIN_TIMEOUT = 900000;
	final class LoginAttempt implements ITask {
		private ScheduleTask task;
		private int count;
		private long time;
		private final InetAddress address;
		public LoginAttempt(InetAddress address) { this.address = address; }
		@Override
		public void execute() throws Exception { data.remove(address); }

		public void increment() {
			count++;

			long time1 = System.currentTimeMillis();
			if (time1 - time > 1000) {
				if (task != null) task.cancel();
				task = Scheduler.getDefaultScheduler().delay(this, LOGIN_TIMEOUT);

				time = time1;
			}
		}

		public int getFailType() {
			if (count < LOGIN_ATTEMPTS) return 0;
			if (count == LOGIN_ATTEMPTS) return 1;
			return 2;
		}
	}

	public Fail2Ban() {}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		InetAddress ipAddr = ((InetSocketAddress) ctx.remoteAddress()).getAddress();
		LoginAttempt attempt = data.get(ipAddr);
		if (attempt != null && attempt.getFailType() != 0) {
			// TODO either netsh block or JVM drop packet
			LOGGER.info("已阻止 {} 的连接请求", ipAddr);
			ctx.channel().readInactive();
			Scheduler.getDefaultScheduler().delay(ctx::close, 60000);
		}
	}

	private final Function<InetAddress, LoginAttempt> aNew = LoginAttempt::new;
	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		InetAddress ipAddr = ((InetSocketAddress) ctx.remoteAddress()).getAddress();
		data.computeIfAbsent(ipAddr, aNew).increment();
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf in = (DynByteBuf) msg;
		DynByteBuf copy = in.slice(0, in.readableBytes());
		if (copy.getU(copy.rIndex) != 0x40) {
			LOGGER.info("{} 发送了无效的数据包: {}", ctx.remoteAddress(), copy.dump());
			ctx.close();
			return;
		}

		ctx.removeSelf();
		ctx.channelRead(msg);
	}
}