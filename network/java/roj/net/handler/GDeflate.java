package roj.net.handler;

import roj.compiler.plugins.asm.ASM;
import roj.io.BufferPool;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

/**
 * @author Roj234
 * @since 2022/6/1 23:20
 */
public abstract class GDeflate implements ChannelHandler {
	protected Deflater def;
	protected Inflater inf;
	protected int buf;

	protected final void inflateRead(ChannelCtx ctx, DynByteBuf in, DynByteBuf out) throws IOException {
		int v = in.readableBytes();

		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			inf.setInput(in.array(), in.arrayOffset()+in.rIndex, v);
			in.rIndex = in.wIndex();
		} else {
			if (ASM.TARGET_JAVA_VERSION >= 11) {
				inf.setInput(in.nioBuffer());
				in.rIndex = in.wIndex();
			} else {
				tmp1 = ctx.allocate(false, Math.min(buf, v));
			}
		}

		v = out.compact().wIndex();
		try {
			do {
				while (!inf.needsInput()) {
					int i;
					try {
						if (ASM.TARGET_JAVA_VERSION >= 11 && out.isDirect()) {
							i = inf.inflate(out.nioBuffer().limit(out.capacity()).position(v));
						} else {
							i = inf.inflate(out.array(), out.arrayOffset()+v, out.capacity()-v);
						}
					} catch (DataFormatException e) {
						throw new ZipException(e.getMessage());
					}

					if (i == 0) break;
					if ((v += i) == out.capacity()) {
						out.wIndex(out.capacity());
						readPacket(ctx, out);
						v = out.compact().wIndex();
					}
				}

				if (!in.isReadable()) break;

				int cnt = Math.min(in.readableBytes(), tmp1.capacity());
				in.readFully(tmp1.array(), tmp1.arrayOffset(), cnt);
				inf.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
			} while (true);

			if (v > 0) {
				out.wIndex(v);
				readPacket(ctx, out);
			}
		} finally {
			if (tmp1 != null) BufferPool.reserve(tmp1);
		}
	}
	protected void readPacket(ChannelCtx ctx, DynByteBuf out) throws IOException {ctx.channelRead(out);}

	protected final void deflateWrite(ChannelCtx ctx, DynByteBuf in, DynByteBuf out, int doFlush) throws IOException {
		ByteBuffer __j11_ib = null, __j11_ob = null;
		DynByteBuf tmp1 = null;
		if (in.hasArray()) {
			def.setInput(in.array(), in.arrayOffset()+in.rIndex, in.readableBytes());
			in.rIndex = in.wIndex();
		} else {
			if (ASM.TARGET_JAVA_VERSION >= 11) {
				def.setInput(__j11_ib = BufferPool.mallocShell(in));
				in.rIndex = in.wIndex();
			} else {
				tmp1 = ctx.allocate(false, Math.min(buf, in.readableBytes()));
			}
		}

		try {
			if (ASM.TARGET_JAVA_VERSION >= 11 && out.isDirect()) {
				__j11_ob = BufferPool.mallocShell(out);
			}

			int v = out.wIndex();
			while (true) {
				int flush = doFlush == 1 && !in.isReadable() ? Deflater.SYNC_FLUSH : Deflater.NO_FLUSH;
				while (true) {
					int i;
					if (ASM.TARGET_JAVA_VERSION >= 11 && out.isDirect()) {
						i = def.deflate(__j11_ob.limit(out.capacity()).position(v), flush);
					} else {
						i = def.deflate(out.array(), out.arrayOffset() + v, out.capacity() - v, flush);
					}

					if ((v += i) == out.capacity()) {
						out.rIndex = 0;
						out.wIndex(v);
						v = writePacket(ctx, out, true);
					} else if (i == 0) break;
				}

				if (!in.isReadable()) {
					if (doFlush == 2) {
						def.finish();
						doFlush = 1;
					} else {
						break;
					}
				} else {
					int cnt = Math.min(in.readableBytes(), tmp1.capacity());
					in.readFully(tmp1.array(), tmp1.arrayOffset(), cnt);
					def.setInput(tmp1.array(), tmp1.arrayOffset(), cnt);
				}
			}

			if (v > 0) {
				out.rIndex = 0;
				out.wIndex(v);
				writePacket(ctx, out, false);
			}
		} finally {
			if (tmp1 != null) BufferPool.reserve(tmp1);
			if (__j11_ib != null) BufferPool.mfreeShell(in, __j11_ib);
			if (__j11_ob != null) BufferPool.mfreeShell(out, __j11_ob);
		}
	}
	protected int writePacket(ChannelCtx ctx, DynByteBuf out, boolean isFull) throws IOException {ctx.channelWrite(out);return 0;}

	@Override
	public void handlerRemoved(ChannelCtx ctx) {
		if (def != null) def.end();
		if (inf != null) inf.end();
	}
}