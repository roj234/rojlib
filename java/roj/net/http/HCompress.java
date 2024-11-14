package roj.net.http;

import roj.compiler.plugins.asm.ASM;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.net.ChannelCtx;
import roj.net.Event;
import roj.net.handler.GDeflate;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Deflate/GZip
 * @author Roj233
 * @since 2022/6/2 3:29
 */
public final class HCompress extends GDeflate {
	public static final String EVENT_IN_END = "hComp:inEnd", EVENT_CLOSE_OUT = "hComp:closeOut";

	public HCompress(int buf) {this.buf = buf;}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		if (inf == null) {
			ctx.channelRead(msg);
			return;
		}
		if (inf.finished()) throw new EOFException();

		DynByteBuf in = (DynByteBuf) msg;
		DynByteBuf out = ctx.allocate(false, Math.min(in.readableBytes(), buf));
		try {
			inflateRead(ctx, in, out);
		} finally {
			BufferPool.reserve(out);
		}

		if (inf.finished()) {
			in.rIndex -= inf.getRemaining();

			Event event = new Event(EVENT_IN_END, this);
			ctx.postEvent(event);

			if (in.isReadable() && event.getResult() != Event.RESULT_DENY)
				ctx.channelRead(in);
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (def == null || def.finished()) {
			ctx.channelWrite(msg);
			return;
		}

		DynByteBuf in = (DynByteBuf) msg;
		DynByteBuf out = ctx.allocate(ASM.TARGET_JAVA_VERSION >= 11, MathUtils.clamp(in.readableBytes(), 128, buf));
		try {
			deflateWrite(ctx, in, out, 0);
		} finally {
			BufferPool.reserve(out);
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(EVENT_CLOSE_OUT)) {
			if (def != null) {
				def.finish();
				channelWrite(ctx, ByteList.EMPTY);
			}
		}
	}

	public void setDef(Deflater def) {this.def = def;}
	public void setInf(Inflater inf) {this.inf = inf;}
	public Deflater getDef() {return def;}
	public Inflater getInf() {return inf;}
}