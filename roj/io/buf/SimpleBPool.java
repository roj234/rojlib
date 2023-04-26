package roj.io.buf;

import roj.collect.BSLowHeap;
import roj.util.*;

import java.util.Comparator;

/**
 * @author Roj233
 * @since 2022/6/1 7:05
 */
public class SimpleBPool implements BPool {
	private static final Comparator<Object> CMP = (o1, o2) -> {
		int a = o1 instanceof byte[] ? ((byte[]) o1).length : (int) ((NativeMemory) o1).length();
		int b = o2 instanceof byte[] ? ((byte[]) o2).length : (int) ((NativeMemory) o2).length();

		int v = Integer.compare(a, b);
		if (v != 0) return v;

		return Integer.compare(System.identityHashCode(o1), System.identityHashCode(o2));
	};

	private final BSLowHeap<NativeMemory> direct = new BSLowHeap<>(Helpers.cast(CMP));
	private final BSLowHeap<byte[]> heap = new BSLowHeap<>(Helpers.cast(CMP));
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
	public boolean allocate(boolean bDirect, int cap, PooledBuffer cb) {
		if (cap > max) return false;
		if (cap < min) cap = min;

		if (bDirect) {
			for (int i = direct.size()-1; i >= 0; i--) {
				NativeMemory nm = direct.get(i);
				if (nm.length() >= cap) {
					cb.set(nm, nm.address(), (int) nm.length());
					return true;
				}
			}

			NativeMemory nm = new NativeMemory(cap);
			cb.set(nm, nm.address(), (int) nm.length());
		} else {
			for (int i = heap.size()-1; i >= 0; i--) {
				byte[] bb = heap.get(i);
				if (bb.length >= cap) {
					cb.set(bb,0,bb.length);
					return true;
				}
			}

			byte[] bb = ArrayCache.getDefaultCache().getByteArray(cap, false);
			cb.set(bb,0,bb.length);
		}

		return false;
	}

	@Override
	public boolean reserve(DynByteBuf buf) {
		if (buf.isDirect()) {
			NativeMemory memory = ((DirectByteList) buf).memory();

			BSLowHeap<NativeMemory> h = direct;
			if (h.size() < maxCount) {
				h.add(memory);
				return true;
			} else {
				if (h.get(h.size()-1).length() < buf.capacity()) {
					h.remove(h.size()-1).release();
					h.add(memory);
					return true;
				}
			}
		} else {
			BSLowHeap<byte[]> h = heap;
			if (h.size() < maxCount) {
				h.add(buf.array());
				return true;
			} else {
				if (h.get(h.size() - 1).length < buf.capacity()) {
					ArrayCache.getDefaultCache().putArray(h.remove(h.size()-1));
					h.add(buf.array());
					return true;
				}
			}
		}

		return false;
	}
}
