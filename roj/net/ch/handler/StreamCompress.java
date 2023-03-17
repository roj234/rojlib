package roj.net.ch.handler;

import roj.math.MathUtils;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.Event;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.NamespaceKey;

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import static roj.util.NamespaceKey.of;

/**
 * 主要给Http用。
 *
 * @author Roj233
 * @since 2022/6/2 3:29
 */
public class StreamCompress implements ChannelHandler {
	public static final String ID = "sc";
	public static final NamespaceKey
		RESET_IN = of("sc:reset_in"), RESET_OUT = of("sc:reset_out"),
		IN_EOF = of("sc:in_eof"), OUT_EOF = of("sc:out_eof");

	private Deflater def;
	private Inflater inf;

	private int buf;

	public StreamCompress(int buf) {
		this.buf = buf;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		if (inf == null) {
			ctx.channelRead(msg);
			return;
		}
		if (inf.finished()) throw new EOFException();

		DynByteBuf in = (DynByteBuf) msg;

		int len = in.readableBytes();

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			inf.setInput(in.array(), in.arrayOffset()+in.rIndex, len);
			in.rIndex = in.wIndex();
		} else {
			tmp1 = ctx.allocate(false, Math.min(buf, len));
		}

		DynByteBuf out = ctx.allocate(false, Math.min(len, buf));
		len = 0;

		try {
			do {
				while (!inf.needsInput()) {
					int i;
					try {
						i = inf.inflate(out.array(), out.arrayOffset()+len, out.capacity()-len);
					} catch (DataFormatException e) {
						throw new ZipException(e.getMessage());
					}

					if (i == 0) break;
					if ((len += i) == out.capacity()) {
						out.rIndex = len = 0;
						out.wIndex(out.capacity());
						ctx.channelRead(out);
					}
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.read(tmp1.array(), tmp1.arrayOffset(), cnt);
				inf.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			} while (true);

			if (len > 0) {
				out.rIndex = 0;
				out.wIndex(len);
				ctx.channelRead(out);
			}
		} finally {
			if (tmp1 != null) ctx.reserve(tmp1);
			ctx.reserve(out);
		}

		if (inf.finished()) {
			in.rIndex -= inf.getRemaining();
			ctx.postEvent(new Event(IN_EOF, this));
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (def == null || def.finished()) {
			ctx.channelWrite(msg);
			return;
		}

		DynByteBuf in = (DynByteBuf) msg;

		DynByteBuf out = ctx.allocate(false, MathUtils.clamp(in.readableBytes(), 128, buf));

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			def.setInput(in.array(), in.arrayOffset() + in.rIndex, in.readableBytes());
			in.skipBytes(in.readableBytes());
		} else {
			tmp1 = ctx.allocate(false, Math.min(buf, in.readableBytes()));
		}

		try {
			int v = 0;
			do {
				while (true) {
					int i = def.deflate(out.array(), out.arrayOffset() + v, out.capacity() - v);

					if ((v += i) == out.capacity()) {
						out.rIndex = v = 0;
						out.wIndex(out.capacity());
						ctx.channelWrite(out);
					} else if (i == 0) break;
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.read(tmp1.array(), tmp1.arrayOffset(), cnt);
				def.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			} while (true);

			if (v > 0) {
				out.rIndex = 0;
				out.wIndex(v);
				ctx.channelWrite(out);
			}
		} finally {
			if (tmp1 != null) ctx.reserve(tmp1);
			ctx.reserve(out);
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (def != null) def.end();
		if (inf != null) inf.end();
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		NamespaceKey id = event.id;

		if (!id.getNamespace().equals("sc")) return;
		switch (id.getPath()) {
			case "reset_in":
				inf.reset();
				break;
			case "reset_out":
				def.reset();
				break;
			case "out_eof":
				if (def != null) {
					def.finish();
					channelWrite(ctx, ByteList.EMPTY);
				}
				break;
		}
	}

	public void setBufferSize(int buf) {
		this.buf = buf;
	}
	public void setDef(Deflater def) {
		this.def = def;
	}
	public void setInf(Inflater inf) {
		this.inf = inf;
	}
	public Deflater getDef() {
		return def;
	}
	public Inflater getInf() {
		return inf;
	}
}
