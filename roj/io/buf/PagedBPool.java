package roj.io.buf;

import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;
import roj.util.NativeMemory;

/**
 * @author Roj233
 * @since 2022/6/1 7:05
 */
public class PagedBPool implements BPool {
	private static final int RESERVED_FOR_EXPAND = 16;
	private final NativeMemory mem = new NativeMemory();
	private long directAddr = -1;
	private byte[] heap;

	private final Page dUsed, hUsed;

	final int capacity;

	public PagedBPool(int cap) {
		capacity = cap;

		dUsed = Page.create(cap);
		hUsed = Page.create(cap);
	}

	@Override
	public String toString() { return "PagedBPool{D="+dUsed+", H="+hUsed+"}"; }

	@Override
	public boolean allocate(boolean direct, int cap, PooledBuffer pb) {
		cap += RESERVED_FOR_EXPAND;

		int off = (int) (direct ? dUsed : hUsed).alloc(cap);
		if (off < 0) return false;

		off += RESERVED_FOR_EXPAND;
		// 预留空间方便往前扩展(通常都是数据包长度不是么)
		pb.setMetadata(RESERVED_FOR_EXPAND);

		if (direct) {
			if (directAddr < 0) directAddr = mem.allocate(capacity);
			pb.set(mem, directAddr+off, cap);
		} else {
			if (heap == null) heap = new byte[capacity];
			pb.set(heap, off, cap);
		}
		return true;
	}

	@Override
	public boolean reserve(DynByteBuf buf) {
		PooledBuffer pb = (PooledBuffer) buf;
		if (buf.isDirect()) {
			DirectByteList.Slice b = (DirectByteList.Slice) buf;
			dUsed.free(buf.address() - directAddr - pb.getMetadata(), b.capacity());
		} else {
			ByteList.Slice b = (ByteList.Slice) buf;
			hUsed.free(b.arrayOffset() - pb.getMetadata(), b.capacity());
		}
		return true;
	}

	@Override
	public boolean expand(DynByteBuf buf, final int more, boolean addAtEnd) {
		PooledBuffer pb = (PooledBuffer) buf;
		// 零拷贝
		failed:
		if (buf.isDirect()) {
			long addr = buf.address();

			DirectByteList.Slice b = (DirectByteList.Slice) buf;
			if (addAtEnd) {
				if (!dUsed.allocAfter(addr - directAddr - pb.getMetadata(), b.capacity(), more)) break failed;
				b.update(addr, b.capacity()+more);
			} else {
				if (pb.getMetadata() >= more) pb.setMetadata(pb.getMetadata() - more);
				else if (!dUsed.allocBefore(addr - directAddr - pb.getMetadata(), b.capacity(), more)) break failed;
				// 为了少写点代码，就不更新metadata了

				b.update(addr-more, b.capacity()+more);
				b.rIndex += more;
				b.wIndex(b.wIndex()+more);
			}
			return true;
		} else {
			ByteList.Slice b = (ByteList.Slice) buf;
			if (addAtEnd) {
				if (!hUsed.allocAfter(b.arrayOffset() - pb.getMetadata(), b.capacity(), more)) break failed;
				b.update(b.arrayOffset(), b.capacity()+more);
			} else {
				if (pb.getMetadata() >= more) pb.setMetadata(pb.getMetadata() - more);
				else if (!hUsed.allocBefore(b.arrayOffset() - pb.getMetadata(), b.capacity(), more)) break failed;

				b.update(b.arrayOffset()-more, b.capacity()+more);
				b.wIndex(b.wIndex()+more);
			}
			return true;
		}

		return false;
	}
}
