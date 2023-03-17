package roj.io.buf;

import roj.collect.MyBitSet;
import roj.math.MathUtils;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;
import roj.util.NativeMemory;

/**
 * @author Roj233
 * @since 2022/6/1 7:05
 */
public class BitmapBPool implements BPool {
	private final NativeMemory mem = new NativeMemory();
	private long directAddr = -1;
	private byte[] heap;

	private final BitMap dUsed, hUsed;

	final class BitMap extends MyBitSet {
		int min, max;

		BitMap() {
			min = 0;
			max = piece;
		}

		int allocate(int size) {
			//already aligned
			size /= chunk;

			if (max-min < size) return -1;

			int i = min;

			while (true) {
				i = nextFalse(i);
				if (i+size > max) break;
				if (!allFalse(i, i+size)) {
					i++;
					continue;
				}

				addRange(i, i+size);
				if (i == min) min = nextFalse(i+size);
				if (i+size == max) max = prevFalse(i);
				return i;
			}

			return -1;
		}

		void release(int off, int size) {
			size /= chunk;

			removeRange(off, off+size);
			if (min > off) min = off;
			if (max < off+size) max = off+size;
		}

		boolean insertBefore(int off, int size, int extraSize) {
			if (off < extraSize) return false;

			int cStart = (off - extraSize) / chunk;
			int cEnd = off / chunk;
			if (cStart == cEnd) return true;

			if (cStart < min || cEnd > max) return false;

			if (!allFalse(cStart,cEnd)) return false;
			addRange(cStart, cEnd);

			if (min == cStart) min = nextFalse((off+size) / chunk);
			if (max == cEnd) max = prevFalse(cStart);
			return true;
		}

		boolean insertAfter(int off, int size, int extraSize) {
			int cStart = (off+size+chunk-1) / chunk;
			int cEnd = (off+size+extraSize+chunk-1) / chunk;
			if (cStart == cEnd) return true;

			if (cStart < min || cEnd > max) return false;

			if (!allFalse(cStart,cEnd)) return false;
			addRange(cStart, cEnd);

			if (min == cStart) min = nextFalse(cEnd);
			if (max == cEnd) max = prevFalse(off / chunk);
			return true;
		}

		@Override
		public String toString() {
			return "[" + min + ", " + max + "]@" + TextUtil.scaledNumber(size() * chunk) + "B in " + super.toString();
		}
	}

	final int capacity, chunk, piece;

	public BitmapBPool(int cap, int chunk) {
		if (MathUtils.getMin2PowerOf(chunk) != chunk) throw new IllegalArgumentException("Chunk must be PowerOfTwo");
		if ((cap & chunk) != 0) cap += chunk;

		this.capacity = cap;
		this.chunk = chunk;
		this.piece = cap / chunk;

		dUsed = new BitMap();
		hUsed = new BitMap();
	}

	@Override
	public String toString() { return "BitmapPool{" + "D={" + dUsed + "}, H={" + hUsed + "}, ChunkCount="+piece+", MaxSize="+capacity+'}'; }

	@Override
	public boolean allocate(boolean direct, int cap, PooledBuffer cb) {
		if (cap > capacity >> 1) return false;

		cap = (cap+chunk-1)& -chunk;

		int off = (direct ? dUsed : hUsed).allocate(cap);
		if (off < 0) return false;

		if (direct) {
			if (directAddr < 0) directAddr = mem.allocate(capacity);
			cb.set(mem, directAddr + off*chunk, cap);
		} else {
			if (heap == null) heap = new byte[capacity];
			cb.set(heap, off*chunk, cap);
		}
		return true;
	}

	@Override
	public boolean reserve(DynByteBuf buf) {
		if (buf.isDirect()) {
			DirectByteList.Slice b = (DirectByteList.Slice) buf;
			dUsed.release((int) ((buf.address() - directAddr) / chunk), b.capacity());
		} else {
			ByteList.Slice b = (ByteList.Slice) buf;
			hUsed.release(b.arrayOffset() / chunk, b.capacity());
		}
		return true;
	}

	@Override
	public boolean expand(DynByteBuf buf, int more, boolean addAtEnd) {
		// 零拷贝
		expandInPlace:
		if (buf.isDirect()) {
			long addr = buf.address();
			if (directAddr >= 0 && addr >= directAddr && addr < directAddr+mem.length()) {
				DirectByteList.Slice b = (DirectByteList.Slice) buf;
				if (addAtEnd) {
					if (!dUsed.insertAfter((int) (addr - directAddr), b.capacity(), more)) break expandInPlace;
					b.update(addr, b.capacity()+more);
				} else {
					if (!dUsed.insertBefore((int) (addr - directAddr), b.capacity(), more)) break expandInPlace;
					b.update(addr-more, b.capacity()+more);
					b.rIndex += more;
					b.wIndex(b.wIndex()+more);
				}
				return true;
			}
		} else if (buf.hasArray()) {
			if (buf.array() == heap) {
				ByteList.Slice b = (ByteList.Slice) buf;
				if (addAtEnd) {
					if (!dUsed.insertAfter(b.arrayOffset(), b.capacity(), more)) break expandInPlace;
					b.update(b.arrayOffset(), b.capacity()+more);
				} else {
					if (!dUsed.insertBefore(b.arrayOffset(), b.capacity(), more)) break expandInPlace;
					b.update(b.arrayOffset()-more, b.capacity()+more);
					b.wIndex(b.wIndex()+more);
				}
				return true;
			}
		}

		return false;
	}
}
