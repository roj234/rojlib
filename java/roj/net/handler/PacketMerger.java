package roj.net.handler;

import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/5/17 15:57
 */
public abstract class PacketMerger implements ChannelHandler {
	protected DynByteBuf merged;

	protected final void mergedRead(ChannelCtx ctx, DynByteBuf pkt) throws IOException {
		DynByteBuf m = merged;
		if (m != null) {
			int more = pkt.readableBytes()-m.writableBytes();
			if (more > 0) m = ctx.alloc().expand(m, more);
			m.put(pkt);
			pkt.rIndex = pkt.wIndex();
			pkt = m;
			merged = null;
		}

		try {
			while (pkt.isReadable()) {
				int pos = pkt.rIndex;
				ctx.channelRead(pkt);
				if (pos == pkt.rIndex) break;
			}

			if (pkt.isReadable()) {
				if (m != null) {
					merged = m.compact();
					m = null;
				} else {
					merged = ctx.allocate(pkt.isDirect(), pkt.readableBytes()).put(pkt);
					pkt.rIndex = pkt.wIndex();
				}
			}
		} finally {
			if (m != null) BufferPool.reserve(m);
		}
	}

	public DynByteBuf getAs(boolean direct) {
		if (merged == null || !merged.isReadable()) return ByteList.EMPTY;

		DynByteBuf copy = direct ? DynByteBuf.allocateDirect(merged.readableBytes()) : DynByteBuf.allocate(merged.readableBytes());
		return copy.put(merged);
	}

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		if (merged != null) {
			BufferPool.reserve(merged);
			merged = null;
		}
	}
}