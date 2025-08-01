package roj.net;

import roj.collect.ArrayList;
import roj.io.buf.BufferPool;
import roj.util.DynByteBuf;
import roj.util.TypedKey;

import java.io.IOException;
import java.net.SocketAddress;

/**
 * @author Roj234
 * @since 2022/11/10 16:36
 */
public final class ChannelCtx {
	MyChannel root;

	public final String name;
	ChannelHandler handler;

	ChannelCtx prev, next;

	ChannelCtx(MyChannel root, String name) {
		this.root = root;
		this.name = name;
	}

	public MyChannel channel() { return root; }

	public SocketAddress remoteAddress() { return root.remoteAddress(); }
	public SocketAddress localAddress() { return root.localAddress(); }

	public boolean isOpen() { return root.isOpen(); }
	public boolean isInputOpen() { return root.isInputOpen(); }
	public boolean isOutputOpen() { return root.isOutputOpen(); }

	public void channelOpened() throws IOException {
		if (next != null) next.handler.channelOpened(next);
	}

	public void readActive() { root.readActive(); }
	public void readInactive() { root.readInactive(); }

	public <T> T attachment(TypedKey<T> key) {
		return root.attachment(key);
	}
	public <T> T attachment(TypedKey<T> key, T val) {
		return root.attachment(key, val);
	}

	public void channelWrite(Object data) throws IOException {
		if (prev != null) prev.handler.channelWrite(prev, data);
		else {
			if (!root.lock.isHeldByCurrentThread()) throw new IllegalStateException("Call MyChannel#fireChannelWrite or lock()");
			root.write(data);
		}
	}

	public void pauseAndFlush() {root.pauseAndFlush();}
	public void flush() throws IOException {root.flush();}
	public boolean isFlushing() {return root.isFlushing();}

	public void channelRead(Object data) throws IOException {
		if (next != null) next.handler.channelRead(next, data);
		else throw new IllegalStateException("No next handler");
	}

	public Event postEvent(String id) throws IOException {
		Event event = new Event(id);
		postEvent(event);
		return event;
	}
	public void postEvent(Event event) throws IOException { root.postEvent(event); }

	public void exceptionCaught(Throwable ex) throws Exception {
		if (next != null) next.handler.exceptionCaught(next, ex);
		else {
			Throwable ex1 = ex;
			while (ex != null) {
				ArrayList<StackTraceElement> list = ArrayList.asModifiableList(ex.getStackTrace());
				for (int i = 0; i < list.size(); i++) {
					StackTraceElement el = list.get(i);
					if (el.getMethodName().startsWith("channel")) {
						if (el.getClassName().startsWith("roj.net.Channel")) {
							list.remove(i--);
						}
					}
				}

				ex.setStackTrace(list.toArray(new StackTraceElement[0]));
				ex = ex.getCause();
			}
			ex1.printStackTrace();

			root.close();
		}
	}

	public void close() throws IOException { root.close(); }

	public BufferPool alloc() { return root.alloc(); }
	public DynByteBuf allocate(boolean direct, int capacity) {
		if (capacity < 0) throw new IllegalArgumentException(String.valueOf(capacity));
		return root.alloc().allocate(direct, capacity);
	}

	public ChannelHandler handler() { return handler; }

	public void replaceSelf(ChannelHandler pipe) {
		handler.handlerRemoved(this);
		this.handler = pipe;
		pipe.handlerAdded(this);
	}

	public void removeSelf() {
		root.remove(this);
	}

	public void dispose() throws IOException {
		var h = handler;
		try {
			if (h != null) h.channelClosed(this);
		} finally {
			root.remove(this);
		}
	}

	public ChannelCtx prev() { return prev; }
	public ChannelCtx next() { return next; }
	@SuppressWarnings("unchecked")
	public <T> T prev(Class<T> type) {
		var ctx = prev;
		while (true) {
			if (ctx == null) return null;
			if (type.isInstance(ctx.handler)) return (T) ctx.handler;
			ctx = ctx.prev;
		}
	}
	@SuppressWarnings("unchecked")
	public <T> T next(Class<T> type) {
		var ctx = next;
		while (true) {
			if (ctx == null) return null;
			if (type.isInstance(ctx.handler)) return (T) ctx.handler;
			ctx = ctx.next;
		}
	}


	@Override
	public String toString() { return name + "=" + handler; }
}