package roj.plugins.web.flow;

import roj.collect.RingBuffer;
import roj.collect.ToLongMap;
import roj.http.server.ContentWriter;
import roj.http.server.ResponseHeader;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.net.util.SpeedLimiter;
import roj.reflect.Handles;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/11/26 16:44
 */
class FlowController extends SpeedLimiter implements ChannelHandler {
	private final FlowControl owner;

	public FlowController(FlowControl owner, FlowControl.LimitGroup group) {
		super(group);
		this.owner = owner;
		this.pendingConnections = RingBuffer.lazy(group.maxConnections);
	}
	private FlowControl.LimitGroup getLimitGroup() {return (FlowControl.LimitGroup) setting;}

	long timestamp;

	RingBuffer<MyChannel> pendingConnections;
	private final ToLongMap<MyChannel> channel = new ToLongMap<>();

	private volatile int lock;
	private static final VarHandle LOCK = Handles.lookup().findVarHandle(FlowController.class, "lock", int.class);

	public int limit(int pendingBytes) {
		if (setting.maxTokens == 0) return pendingBytes;

		if (!LOCK.compareAndSet(this, 0, 1)) return 0;
		try {
			return super.limit(pendingBytes);
		} finally {
			lock = 0;
		}
	}

	@Override
	public void handlerAdded(ChannelCtx ctx) {ctx.prev(ResponseHeader.class).limitSpeed(this);}

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
		// is address limiter
		if (getLimitGroup().name == null) {
			ResponseHeader prev = Objects.requireNonNull(ctx.prev(ResponseHeader.class));
			var request = prev.request();
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
		if (channel.size() >= getLimitGroup().maxConcurrentConnections) {
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
		// websocket
		if(ctx.prev(ContentWriter.class) == null) ctx.removeSelf();
	}
}
