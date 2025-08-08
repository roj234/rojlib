package roj.plugins.frp;

import roj.http.HttpHead;
import roj.http.h2.H2Connection;
import roj.http.h2.H2Exception;
import roj.http.h2.H2Stream;
import roj.io.IOUtil;
import roj.net.*;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.concurrent.locks.Lock;

/**
 * @author Roj234
 * @since 2024/9/15 0:22
 */
sealed class FrpProxy extends H2Stream implements ChannelHandler permits FrpClient.UDP, FrpHostConnection.Client, FrpServerConnection.TCP {
	private static final Logger LOGGER = Logger.getLogger("AE/Proxy");

	volatile FrpCommon man;

	MyChannel connection;
	PortMapEntry port;
	TSWriter tsWriter;

	FrpProxy(int id) {super(id);}

	public void init(MyChannel ch, PortMapEntry entry) {
		ch.addLast("proxy", this).addLast("tswriter", tsWriter = new TSWriter());
		this.connection = ch;
		this.port = entry;
	}

	@Override protected void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) throws IOException {
		this.man = (FrpCommon) man;
		connection.readActive();
		LOGGER.info("流{}已开启({})", this, head.header(":status"));
	}

	@Override
	protected void onFinish(H2Connection man) {
		this.man = (FrpCommon) man;
		LOGGER.info("流{}已关闭({})", this, -1);
		try {
			//if (connection.isTCP())
			//	onData(man, IOUtil.getSharedByteBuf().putAscii("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\n\r\n").putAscii(String.valueOf(errno)));
		} catch (Exception ignored) {}
		try {
			if (tsWriter != null) tsWriter.close();
			else IOUtil.closeSilently(connection);
		} catch (IOException ignored) {}
	}

	@Override public String toString() {
		if (man.isServer()) return "["+Net.toString(man.channel().remoteAddress())+"/#"+id+"]  "+port;
		else return "["+Net.toString(man.channel().localAddress())+"/#"+id+"]  "+Net.toString(connection.remoteAddress())+" => "+port;
	}

	// Local Data
	@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Lock lock = man.channel().channel().lock();
		lock.lock();
		try {
			boolean flowControl = man.sendData(this, (DynByteBuf) msg, false);
			// 发送端的流量控制
			if (flowControl) connection.readInactive();
		} finally {
			lock.unlock();
		}
	}
	// Local Flow
	@Override protected void onWindowUpdate(H2Connection man) throws IOException {
		if (getSendWindow() > 1400 && connection.readDisabled()) connection.readActive();
	}
	// Local Close
	@Override public final void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(MyChannel.IN_EOF)) {
			Lock lock = man.channel().channel().lock();
			lock.lock();
			try {
				man.sendData(this, ByteList.EMPTY, true);
			} finally {
				lock.unlock();
			}
		}
	}

	// Remote Data
	@Override protected String onData(H2Connection man, DynByteBuf buf) throws IOException {
		if (tsWriter != null) tsWriter.fireChannelWrite(buf);return null;
	}
	// Remote Flow
	@Override public final void channelFlushed(ChannelCtx ctx) throws IOException {
		int rcvWin = getReceiveWindow();
		if (rcvWin <= 32767) {
			Lock lock = man.channel().channel().lock();
			lock.lock();
			try {
				// 增大流量控制窗口
				man.sendWindowUpdate(this, 65536 - rcvWin);
			} finally {
				lock.unlock();
			}
		}
	}
	// Remote Close
	@Override protected void onDone(H2Connection man) throws IOException {if (tsWriter != null) tsWriter.closeGracefully();}

	// common close notify
	@Override public void channelClosed(ChannelCtx ctx) throws IOException {
		if (man == null) return;

		Lock lock = man.channel().channel().lock();
		lock.lock();
		try {
			if (man.isValid(this)) man.streamError(id, H2Exception.ERROR_OK);
		} finally {
			lock.unlock();
		}
	}
}
