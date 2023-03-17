package roj.io.buf;

import roj.collect.BSLowHeap;
import roj.collect.IdentitySet;
import roj.util.ByteList;
import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.util.Comparator;

/**
 * @author Roj233
 * @since 2022/6/1 7:05
 */
public class SimpleBPool implements BPool {
	private static final Comparator<DynByteBuf> CMP = (o1, o2) -> {
		int v = Integer.compare(o2.capacity(), o1.capacity());
		if (v != 0) return v;
		return Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
	};

	private final BSLowHeap<DynByteBuf> direct = new BSLowHeap<>(CMP);
	private final BSLowHeap<DynByteBuf> heap = new BSLowHeap<>(CMP);
	private final IdentitySet<DynByteBuf> using = new IdentitySet<>();
	protected int min, max, maxCount;

	public SimpleBPool(int min, int max, int maxCount) {
		this.min = min;
		this.max = max;
		this.maxCount = maxCount;
	}

	@Override
	public String toString() {
		return "SimpleBPool{[" + min + "," + max + "], " + "D/H/Max=" + direct.size() + "/" + heap.size() + "/" + maxCount + '}';
	}

	@Override
	public DynByteBuf allocate(boolean direct, int cap) {
		BSLowHeap<DynByteBuf> h = direct ? this.direct : this.heap;

		if (cap < min) cap = min;

		if (!h.isEmpty() && cap < max) {
			if (h.get(0).capacity() >= cap) {
				for (int i = 1; i < h.size(); i++) {
					if (h.get(i).capacity() < cap) {
						DynByteBuf bb = h.remove(i-1);
						bb.clear();
						using.add(bb);
						return bb;
					}
				}
			}

			DynByteBuf bb = h.remove(h.size()-1);
			bb.clear();
			bb.ensureCapacity(cap);
			using.add(bb);
			return bb;
		}

		int cap1 = Math.max(cap, max);
		DynByteBuf bb = direct ? DirectByteList.allocateDirect(cap, cap1) : ByteList.allocate(cap, cap1);
		using.add(bb);
		return bb;
	}

	@Override
	public void reserve(DynByteBuf buf) {
		if (!using.remove(buf)) throw new IllegalStateException("should not reach here");
		if (buf.capacity() > max) return;

		BSLowHeap<DynByteBuf> h = buf.isDirect() ? direct : heap;
		if (h.size() < maxCount) {
			h.add(buf);
		} else {
			if (h.get(h.size() - 1).capacity() < buf.capacity()) {
				DynByteBuf buf1 = h.remove(h.size()-1);
				h.add(buf);
				buf = buf1;
			}
		}

		if (buf.isDirect()) {
			((DirectByteList) buf)._free();
		}
	}

	@Override
	public boolean isPooled(DynByteBuf buf) {
		return using.contains(buf);
	}

	@Override
	public boolean expand(DynByteBuf buf, int more, boolean addAtEnd) {
		if (addAtEnd) {
			if (buf.capacity()+more < buf.maxCapacity()) {
				buf.ensureCapacity(buf.capacity()+more);
				return true;
			}
		}
		return false;
	}
}
