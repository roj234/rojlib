package roj.plugins.http.flow;

import roj.collect.RingBuffer;
import roj.collect.ToLongMap;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.net.http.server.ResponseHeader;
import roj.net.http.server.ResponseWriter;
import roj.net.util.SpeedLimiter;
import roj.reflect.ReflectionUtils;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/11/26 0026 16:44
 */
public class FlowController extends SpeedLimiter implements ChannelHandler {
	private final FlowControl owner;
	String group;
	volatile int limitAfter;
	int timeout;
	int maxConcurrentConnections, maxConnections;

	public FlowController(FlowControl owner, String group, int limitAfter, int maxConcurrentConnections, int maxConnections, int timeout, int bestMTU, int maxLatency) {
		super(bestMTU, maxLatency);
		this.owner = owner;
		this.group = group;
		this.limitAfter = limitAfter;
		this.timeout = timeout;
		this.maxConcurrentConnections = maxConcurrentConnections;
		this.maxConnections = maxConnections;
		pendingConnections = RingBuffer.lazy(maxConnections);
	}

	long timestamp;

	RingBuffer<MyChannel> pendingConnections;
	private final ToLongMap<MyChannel> channel = new ToLongMap<>();

	private volatile int lock;
	private static final long LOCK_OFFSET = ReflectionUtils.fieldOffset(FlowController.class, "lock");
	private static final long limitAfter_OFFSET = ReflectionUtils.fieldOffset(FlowController.class, "limitAfter");

	public int limit(int pendingBytes) {
		if (bps == 0) return pendingBytes;
		if (!ReflectionUtils.u.compareAndSwapInt(this, LOCK_OFFSET, 0, 1)) return 0;
		try {
			return super.limit(pendingBytes);
		} finally {
			lock = 0;
		}
	}

	public boolean isTimeout() {
		return channel.isEmpty() && System.currentTimeMillis() - timestamp > timeout;
	}

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		var rh = ctx.prev(ResponseHeader.class);
		rh.setStreamLimit(limitAfter < 0 ? this : null);
	}
	@Override
	public synchronized void handlerRemoved(ChannelCtx ctx) {
		channel.remove(ctx.channel());
		timestamp = System.currentTimeMillis();

		var first = pendingConnections.pollFirst();
		if (first != null && first.isOpen()) {
			channel.putLong(first, 0L);
			first.readActive();
		}
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		var request = ctx.prev(ResponseHeader.class).request();
		if (group == null) {
			var pipe = owner.loginCheck(this, request);
			if (pipe == null) ctx.removeSelf();
			else {
				if (pipe != this) ctx.replaceSelf(pipe);
				pipe.checkNow(ctx);
			}
		}
	}

	private synchronized void checkNow(ChannelCtx ctx) {
		var ch = ctx.channel();
		if (channel.size() >= maxConcurrentConnections) {
			ch.readInactive();

			var first = pendingConnections.ringAddLast(ch);
			try {
				if (first != null) first.close();
			} catch (IOException ignored) {}
		} else {
			channel.putLong(ch, 0L);
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws Exception {
		var writer = ctx.prev(ResponseWriter.class);
		if(writer == null) {
			ctx.removeSelf();
			return;
		}

		if(limitAfter < 0) {
			writer.setStreamLimit(this);
			return;
		}

		var entry = (ToLongMap.Entry<MyChannel>)channel.getEntry(ctx.channel());
		if (entry == null) return;

		var delta = writer.getSendBytes() - entry.v;
		entry.v = writer.getSendBytes();

		if(limitAfter < 0 || ReflectionUtils.u.getAndAddInt(this, limitAfter_OFFSET, (int) -delta) < delta) {
			writer.setStreamLimit(this);
		}
	}
}
