package roj.net.handler;

import roj.concurrent.task.ITask;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.reflect.ReflectionUtils;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;
import roj.util.VMUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
public class Fail2Ban implements ChannelHandler, ITask {
	private static final Logger LOGGER = Logger.getLogger();
	private final ConcurrentHashMap<InetAddress, Attempt> data = new ConcurrentHashMap<>();

	final class Attempt {
		private static final long COUNT_OFFSET = ReflectionUtils.fieldOffset(Attempt.class, "count");
		private volatile int count;
		private long forgive;
		public Attempt(InetAddress address) {}

		public boolean login() {
			boolean ok = ReflectionUtils.u.getAndAddInt(this, COUNT_OFFSET, 1) < LOGIN_ATTEMPTS;
			if (!ok) forgive = System.currentTimeMillis();
			return ok;
		}
		public void success() {count = 0;}
	}

	private final int LOGIN_ATTEMPTS, LOGIN_TIMEOUT;
	private final ScheduleTask task;
	public Fail2Ban(int maxAttempts, int interval) {
		LOGIN_ATTEMPTS = maxAttempts;
		LOGIN_TIMEOUT = interval;
		task = Scheduler.getDefaultScheduler().delay(this, LOGIN_TIMEOUT);
	}

	@Override public void execute() throws Exception {
		var time = System.currentTimeMillis()-LOGIN_TIMEOUT;
		for (var itr = data.values().iterator(); itr.hasNext(); ) {
			if (itr.next().forgive < time) itr.remove();
		}
	}

	private static InetAddress getAddress(ChannelCtx ctx) throws UnknownHostException {
		var ip = ((InetSocketAddress) ctx.remoteAddress()).getAddress();
		// /64 子网
		if (ip instanceof Inet6Address v6) {
			byte[] address = v6.getAddress();
			for (int i = 8; i < 16; i++) address[i] = 0;
			ip = InetAddress.getByAddress(address);
		}
		return ip;
	}

	private final Function<InetAddress, Attempt> NEW = Attempt::new;
	@Override public void channelOpened(ChannelCtx ctx) throws IOException {
		var ip = getAddress(ctx);
		var user = data.computeIfAbsent(ip, NEW);
		if (user != null && !user.login()) {
			LOGGER.info("已阻止 {} 的连接请求", ip);

			ctx.close();
			if (VMUtil.isRoot()) {
				// TODO netsh timed block
				//Scheduler.getDefaultScheduler().delay(ctx::close, 60000);
			}
		}
	}

	@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var event = new Event("fail2ban:inspect", msg);
		ctx.postEvent(event);

		switch (event.getResult()) {
			case Event.RESULT_ACCEPT -> ctx.channelRead(msg);
			case Event.RESULT_DENY -> ctx.close();
		}
	}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		try {
			var attempt = data.get(getAddress(ctx));
			if (attempt != null) attempt.success();
		} catch (UnknownHostException ignored) {}
	}

	@Override public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals("fail2ban:inspect")) {
			var copy = (DynByteBuf)event.getData();
			LOGGER.info("{} 发送了无效的数据包: {}", ctx.remoteAddress(), copy.slice(copy.rIndex, Math.min(1024, copy.readUnsignedByte())).dump());
			event.setResult(Event.RESULT_DENY);
		}
	}
}