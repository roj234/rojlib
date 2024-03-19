package roj.minecraft.server.network;

import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * @author Roj234
 * @since 2024/3/19 0019 22:21
 */
public class Compress implements ChannelHandler {
	private final int thr;
	private final Inflater inf = new Inflater();
	private final Deflater def = new Deflater();

	public Compress(int thr) {this.thr = thr;}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (buf.isReadable()) {
			int uncompLen = buf.readVarInt();
			if (uncompLen == 0) {
				ctx.channelRead(buf);
			} else {
				if (uncompLen < thr) throw new IOException("Badly compressed packet - size of "+uncompLen+" is below server threshold of "+thr);
				if (uncompLen > 8388608) throw new IOException("Badly compressed packet - size of"+uncompLen+" is larger than protocol maximum of 8388608");

				ByteList out = (ByteList) ctx.allocate(false, uncompLen);
				ByteList tmp = null;

				try {
					if (buf instanceof ByteList b) {
						inf.setInput(b.array(), b.relativeArrayOffset(), b.readableBytes());
						b.rIndex = b.wIndex();
					} else {
						tmp = (ByteList) ctx.allocate(false, Math.min(buf.readableBytes(), 1024));
					}

					int i = 0;
					while (true) {
						int len = inf.inflate(out.array(), out.arrayOffset()+i, uncompLen-i);
						if (len == 0) {
							if (i == uncompLen) break;

							tmp.clear();
							int count = Math.min(tmp.capacity(), buf.readableBytes());
							tmp.put(buf, count);
							buf.rIndex += count;

							inf.setInput(tmp.array(), tmp.arrayOffset(), count);
						}
						i += len;
					}
					inf.reset();

					out.wIndex(uncompLen);
					ctx.channelRead(out);
				} catch (DataFormatException e) {
					throw new IOException(e);
				} finally {
					BufferPool.reserve(out);
					if (tmp != null) BufferPool.reserve(tmp);
				}
			}
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		if (buf.readableBytes() < thr) {
			if (!buf.isReadable()) ctx.channelWrite(buf);
			else {
				DynByteBuf out = ctx.alloc().expandBefore(buf, 1);
				try {
					out.put(0, 0);
					ctx.channelWrite(buf);
				} finally {
					if (out != buf) BufferPool.reserve(out);
				}
			}
		} else {
			ByteList out = new ByteList();
			out.putVarInt(buf.readableBytes());

			def.reset();
			ByteList tmp = null;
			if (buf instanceof ByteList b) {
				def.setInput(b.array(), b.relativeArrayOffset(), b.readableBytes());
				def.finish();
				b.rIndex = b.wIndex();
			} else {
				tmp = (ByteList) ctx.allocate(false, Math.min(buf.readableBytes(), 1024));
			}

			try {
				while (true) {
					while (true) {
						out.ensureWritable(1024);
						int len = def.deflate(out.array(), out.wIndex(), out.unsafeWritableBytes());
						if (len == 0 && out.isWritable()) break;
						out.wIndex(out.wIndex()+len);
					}

					if (tmp == null || !buf.isReadable()) break;

					tmp.clear();
					int count = Math.min(tmp.capacity(), buf.readableBytes());
					tmp.put(buf, count);
					buf.rIndex += count;

					def.setInput(tmp.array(), tmp.arrayOffset(), count);
					if (!buf.isReadable()) def.finish();
				}
				ctx.channelWrite(out);
			} finally {
				out._free();
				if (tmp != null) BufferPool.reserve(tmp);
			}
		}
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		inf.end();
		def.end();
	}
}