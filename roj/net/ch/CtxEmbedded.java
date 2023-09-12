package roj.net.ch;

import roj.util.NamespaceKey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.Selector;

/**
 * @author Roj233
 * @since 2022/8/25 23:10
 */
public class CtxEmbedded extends MyChannel {
	public static final NamespaceKey EMBEDDED_CLOSE = NamespaceKey.of("embedded:close");
	private static final SocketAddress address = new SocketAddress() {
		@Override
		public String toString() {
			return "embedded";
		}
	};

	MyChannel owner;
	InetSocketAddress remote, local;

	public void setRemote(SocketAddress remote) { this.remote = (InetSocketAddress) remote; }
	public void setLocal(SocketAddress local) { this.local = (InetSocketAddress) local; }
	public MyChannel getOwner() { return owner; }

	public void clonePipelines() {
		pipelineHead = pipelineTail = null;

		ChannelCtx p = owner.pipelineHead;
		while (p != null) {
			addLast(p.name, p.handler);
			p = p.next;
		}
	}

	public CtxEmbedded() {}
	public CtxEmbedded(MyChannel sender) { this.owner = sender; }

	@Override
	public SocketAddress localAddress() {
		return local == null ? address : local;
	}

	@Override
	public SocketAddress remoteAddress() {
		return remote == null ? address : remote;
	}

	// region Empty embedded overrides

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException { throw new IOException("Channel is embedded"); }

	@Override
	public void readInactive() {}
	@Override
	public void readActive() {}

	@Override
	public <T> MyChannel setOption(SocketOption<T> k, T v) throws IOException { return this; }
	@Override
	public <T> T getOption(SocketOption<T> k) throws IOException { throw new IOException("Channel is embedded"); }

	@Override
	protected boolean connect0(InetSocketAddress na) throws IOException { throw new IOException("Channel is embedded"); }
	@Override
	protected SocketAddress finishConnect0() throws IOException { throw new IOException("Channel is embedded"); }
	@Override
	protected void closeGracefully0() throws IOException { close(); }
	@Override
	protected void disconnect0() throws IOException { throw new IOException("Channel is embedded"); }

	public void flush() throws IOException { if (owner != null) owner.flush(); }

	@Override
	protected void read() throws IOException {}

	// endregion

	@Override
	public boolean isOpen() { return state < CLOSED && (owner == null || owner.isOpen()); }

	protected void write(Object o) throws IOException {
		owner.fireChannelWrite(o);
	}

	@Override
	protected void closeHandler() throws IOException {
		if (owner != null) owner.postEvent(new Event(EMBEDDED_CLOSE, this));
		super.closeHandler();
	}
}