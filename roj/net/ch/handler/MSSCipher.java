package roj.net.ch.handler;

import roj.io.IOUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.Event;
import roj.net.mss.MSSEngine;
import roj.net.mss.MSSEngineClient;
import roj.net.mss.MSSException;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.IOException;

import static roj.util.ByteList.EMPTY;

/**
 * @author Roj233
 * @since 2022/5/17 13:11
 */
public class MSSCipher extends PacketMerger {
	public static final NamespaceKey MSS_NOTIFY = NamespaceKey.of("mss:close_notify");
	private final MSSEngine engine;

	public MSSCipher(MSSEngine engine) {
		super();
		this.engine = engine;
	}

	public MSSCipher() {
		super();
		this.engine = new MSSEngineClient();
	}

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		handShake(engine, ctx, EMPTY);
		if (engine.isHandshakeDone()) {
			ctx.channelOpened();
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (!ctx.isOutputOpen()) return;

		DynByteBuf data = (DynByteBuf) msg;

		int req;
		DynByteBuf out = ctx.allocate(false, 1024);
		try {
			do {
				req = engine.wrap(data, out);
				if (req >= 0) {
					ctx.channelWrite(out);
				} else {
					ctx.reserve(out);
					out = ctx.allocate(false, -req);
				}
			} while (data.isReadable());
		} finally {
			ctx.reserve(out);
		}
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf data = (DynByteBuf) msg;

		if (!engine.isHandshakeDone()) {
			handShake(engine, ctx, data);
			if (engine.isHandshakeDone()) {
				ctx.channelOpened();
				if (!data.isReadable()) return;
			} else {
				return;
			}
		}

		int req;
		DynByteBuf out = ctx.allocate(false, 1024);
		try {
			do {
				req = engine.unwrap(data, out);
				if (!out.isReadable()) return;

				if (req < 0) {
					int cap = out.capacity()-req;
					ctx.reserve(out);
					out = ctx.allocate(false, cap);
				} else {
					mergedRead(ctx, out);
					out.clear();
					if (!data.isReadable()) return;
				}
			} while (true);
		} finally {
			ctx.reserve(out);
		}
	}

	private static void handShake(MSSEngine engine, ChannelCtx ctx, DynByteBuf recv) throws IOException {
		int req;

		DynByteBuf out = ctx.allocate(true, 1024);
		try {
			do {
				try {
					req = engine.handshake(out, recv);
				} catch (MSSException e) {
					if (!engine.isClientMode()) {
						ctx.channelWrite(IOUtil.getSharedByteBuf().putAscii("" +
							"HTTP/1.1 400 Bad Request\r\n" +
							"Connection: close\r\n" +
							"\r\n" +
							"<h1>This is a MSS protocol server</h1>"));
					}
					throw e;
				}
				switch (req) {
					case MSSEngine.HS_OK:
						if (out.wIndex() > 0) {
							ctx.channelWrite(out);
							out.clear();
						}
						if (engine.isHandshakeDone()) return;
						break;
					case MSSEngine.HS_BUFFER_UNDERFLOW: return;
					case MSSEngine.HS_BUFFER_OVERFLOW:
						ctx.reserve(out);
						out = ctx.allocate(true, engine.getBufferSize());
				}
			} while (true);
		} finally {
			ctx.reserve(out);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		super.channelClosed(ctx);

		try {
			channelWrite(ctx, EMPTY);
		} catch (IOException ignored) {}
		engine.close(null);
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(MSS_NOTIFY)) {
			if (engine.isClosed()) {
				event.setResult(Event.RESULT_DENY);
			} else {
				event.setResult(Event.RESULT_ACCEPT);
				engine.close(String.valueOf(event.getData()));
				channelWrite(ctx, EMPTY);
			}
		}
	}
}
