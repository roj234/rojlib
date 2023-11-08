package roj.net.cross.server;

import roj.collect.IntMap;
import roj.config.data.CMapping;
import roj.config.serial.CVisitor;
import roj.config.serial.ToEntry;
import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.Event;
import roj.net.ch.MyChannel;
import roj.net.ch.handler.MSSCipher;
import roj.net.ch.handler.Timeout;
import roj.net.cross.Constants;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;

import static roj.net.cross.server.AEServer.server;

/**
 * @author Roj234
 * @since 2023/11/2 0002 5:31
 */
public abstract class Connection extends Constants {
	ChannelCtx handler;
	byte[] digest;

	// 统计数据
	public final long creation;
	public long lastPacket;

	Connection() { creation = System.currentTimeMillis(); }

	public abstract int getClientId();
	public abstract Host getRoom();

	@Override
	public final void handlerAdded(ChannelCtx ctx) { handler = ctx; }

	@Override
	public final void onEvent(ChannelCtx ctx, Event event) throws IOException {
		NamespaceKey id = event.id;
		if (id == Timeout.WRITE_TIMEOUT) {
			LOGGER.info("[{}] WRITE_TIMEOUT", this);
			ctx.close();
		} else if (id == Timeout.READ_TIMEOUT) {
			LOGGER.info("[{}] READ_TIMEOUT", this);
			kickWithMessage(ctx, PS_ERROR_TIMEOUT);
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws IOException {
		super.channelTick(ctx);

		Pinger task = pingTask;
		if (task != null && task.state != 0) {
			DynByteBuf b = IOUtil.getSharedByteBuf();
			b.put(P_S_PING).put(task.state);
			try {
				ctx.channelWrite(b);
			} finally {
				pingTask = null;
			}
		}
	}

	@Override
	public abstract void channelClosed(ChannelCtx ctx) throws IOException;

	public void kickWithMessage(int message) { kickWithMessage(handler, message); }

	final IntMap<PipeInfo> pipes = new IntMap<>();
	public final PipeInfo getPipe(int pipeId) { return pipes.get(pipeId); }

	final void sendHeartbeat(ChannelCtx ctx) throws IOException {
		ByteList b = IOUtil.getSharedByteBuf();
		ctx.channelWrite(b.put(P___HEARTBEAT).putLong(System.currentTimeMillis()));
	}

	// region 直连地址
	private byte[] directAddr;
	private long directAddrValid = -1;

	private long pingTimer;
	private Pinger pingTask;

	public final byte[] getDirectAddrIfValid() { return directAddrValid == 0 ? directAddr : null; }
	final Pinger ping(byte[] ip, char port) {
		long time = System.currentTimeMillis();
		if (pingTask != null || time - pingTimer < 10000) return null;
		pingTimer = time;

		Pinger task = new Pinger(directAddrValid = server.rnd.nextLong());
		try {
			MyChannel ctx = MyChannel.openTCP()
				.addLast("cipher", new MSSCipher())
				.addLast("timeout", new Timeout(2000, 100))
				.addLast("pinger", task);
			ctx.connect(new InetSocketAddress(InetAddress.getByAddress(ip), port));
			server.launch.loop().register(ctx, null, SelectionKey.OP_CONNECT);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return pingTask = task;
	}
	final void pong(DynByteBuf rb) {
		directAddrValid = directAddrValid != -1 && rb.readLong() == directAddrValid ? 0 : -1;
	}
	// endregion

	public void serialize(CVisitor v) {}
	@Deprecated
	public CMapping serialize() {
		ToEntry entry = new ToEntry();
		serialize(entry);
		return entry.get().asMap();
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		String s = handler.remoteAddress().toString();
		return sb.append(s.startsWith("/") ? s.substring(1) : s).toStringAndFree();
	}
}
