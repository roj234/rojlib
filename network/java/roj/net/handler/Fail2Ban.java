package roj.net.handler;

import roj.concurrent.TimerTask;
import roj.concurrent.Timer;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.Event;
import roj.reflect.Unaligned;
import roj.text.logging.Logger;
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
public class Fail2Ban implements ChannelHandler, Runnable {
	private static final Logger LOGGER = Logger.getLogger();
	private final ConcurrentHashMap<InetAddress, Attempt> data = new ConcurrentHashMap<>();

	final class Attempt {
		private static final long COUNT_OFFSET = Unaligned.fieldOffset(Attempt.class, "count");
		private volatile int count;
		private long forgive;
		public Attempt(InetAddress address) {}

		public boolean login() {
			boolean ok = Unaligned.U.getAndAddInt(this, COUNT_OFFSET, 1) < maxFail;
			if (!ok) forgive = System.currentTimeMillis();
			return ok;
		}
		public void success() {count = 0;}
	}

	private final int maxFail, purgeInterval;
	private final TimerTask purgeTask;
	public Fail2Ban(int maxAttempts, int interval) {
		maxFail = maxAttempts;
		purgeInterval = interval;
		purgeTask = Timer.getDefault().delay(this, purgeInterval);
	}

	@Override public void run() {
		var time = System.currentTimeMillis()-purgeInterval;
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
			LOGGER.info("{} 被阻止", ip);

			ctx.close();
			if (VMUtil.isRoot()) {
				// TODO netsh timed block
				//Scheduler.getDefaultScheduler().delay(ctx::close, purgeInterval);
			}
		}
	}

	@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var event = new Event("fail2ban:inspect", msg);
		ctx.postEvent(event);

		if (event.getResult() == Event.RESULT_ACCEPT) {
			ctx.channelRead(msg);
		} else {
			ctx.close();
		}
	}

	// don't call handlerRemoved
	@Override public void channelClosed(ChannelCtx ctx) throws IOException {}
	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		try {
			var ip = getAddress(ctx);
			var attempt = data.get(ip);
			if (attempt != null) {
				attempt.success();
				LOGGER.info("{} 被放行", ip);
			}
		} catch (UnknownHostException ignored) {}
	}

	@Override public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals("fail2ban:inspect")) event.setResult(Event.RESULT_DENY);
		if (event.id.equals("fail2ban:ban")) {
			var ip = getAddress(ctx);
			var user = data.computeIfAbsent(ip, NEW);
			user.count = maxFail;
			user.forgive = System.currentTimeMillis();
			LOGGER.info("{} 被代码封禁", ip);
		}
	}
}