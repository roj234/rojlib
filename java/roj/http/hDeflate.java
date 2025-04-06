package roj.http;

import roj.compiler.plugins.asm.ASM;
import roj.crypt.CRC32s;
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
public final class hDeflate extends GDeflate {
	public static final String ANCHOR = "http:deflate";
	public static final String IN_EOF = "hdef:inEof", OUT_FINISH = "hdef:closeOut";

	private byte flag;
	private int inCrc, outCrc;

	public hDeflate(int buf) {this.buf = buf;}
	public static ChannelCtx inflate(ChannelCtx ctx, Inflater inf, boolean crc) {
		hDeflate hd;

		var exist = ctx.channel().handler(ANCHOR);
		if (exist == null) {
			if (inf == null) return ctx;

			ctx.channel().addAfter(ctx, ANCHOR, hd = new hDeflate(1024));
			exist = ctx.channel().handler(ANCHOR);
		} else {
			hd = (hDeflate) exist.handler();
		}

		var prevInf = hd.getInf();
		if (prevInf != null && prevInf != inf) prevInf.end();

		hd.setInf(inf);
		if (inf != null) hd.setInCrc(crc);
		return exist;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		if (inf == null) {ctx.channelRead(msg);return;}
		if (inf.finished()) throw new EOFException();

		var in = (DynByteBuf) msg;
		var out = ctx.allocate(false, MathUtils.clamp(in.readableBytes(), 128, buf));
		try {
			inflateRead(ctx, in, out);
		} finally {
			BufferPool.reserve(out);
		}
		in.rIndex -= inf.getRemaining();

		if (inf.finished()) {
			Event event = new Event(IN_EOF, this);
			ctx.postEvent(event);

			if (in.isReadable() && event.getResult() != Event.RESULT_DENY)
				ctx.prev().handler().channelRead(ctx, in);
		}
	}

	@Override
	protected void readPacket(ChannelCtx ctx, DynByteBuf out) throws IOException {
		if ((flag&1) != 0) inCrc = CRC32s.update(inCrc, out);
		super.readPacket(ctx, out);
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		if (def == null || def.finished()) {ctx.channelWrite(msg);return;}

		var in = (DynByteBuf) msg;
		if ((flag&2) != 0) outCrc = CRC32s.update(outCrc, in);
		DynByteBuf out = ctx.allocate(ASM.TARGET_JAVA_VERSION >= 11, MathUtils.clamp(in.readableBytes(), 128, buf));
		try {
			deflateWrite(ctx, in, out, 0);
		} finally {
			BufferPool.reserve(out);
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(OUT_FINISH)) {
			if (def != null) {
				def.finish();
				channelWrite(ctx, ByteList.EMPTY);
			}
		}
	}

	public void setInf(Inflater inf) {this.inf = inf;}
	public Inflater getInf() {return inf;}
	public void setInCrc(boolean doCrc) {
		if (doCrc) flag |= 1; else flag &= 2;
		inCrc = CRC32s.INIT_CRC;
	}
	public int getInCrc() {return CRC32s.retVal(inCrc);}

	public void setDef(Deflater def) {this.def = def;}
	public Deflater getDef() {return def;}
	public void setOutCrc(boolean doCrc) {
		if (doCrc) flag |= 2; else flag &= 1;
		outCrc = CRC32s.INIT_CRC;
	}
	public int getOutCrc() {return CRC32s.retVal(outCrc);}
}