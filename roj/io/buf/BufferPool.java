package roj.io.buf;

import roj.concurrent.FastThreadLocal;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj233
 * @since 2022/6/1 7:06
 */
public class BufferPool {
	private static final boolean PREFER_DIRECT_BUFFER = true;
	private static final FastThreadLocal<BufferPool> DEFAULT = FastThreadLocal.withInitial(() -> {
		BitmapBPool bp1 = new BitmapBPool(524288, 512);
		SimpleBPool bp2 = new SimpleBPool(512, 524288, 16);
		return new BufferPool(bp1,bp2);
	});
	public static BufferPool localPool() {
		return DEFAULT.get();
	}

	private final BPool[] pools;
	private final ReentrantLock lock = new ReentrantLock(true);

	private final LeakDetector ldt = LeakDetector.create();

	public BufferPool(BPool... pools) {
		this.pools = pools;
	}

	public final DynByteBuf buffer(int cap) {
		DynByteBuf buf;
		lock.lock();
		try {
			for (BPool pool : pools) {
				buf = pool.allocate(PREFER_DIRECT_BUFFER, cap);
				if (buf == null) buf = pool.allocate(!PREFER_DIRECT_BUFFER, cap);
				if (buf != null) {
					if (ldt != null) ldt.track(buf);
					return buf;
				}
			}
		} finally {
			lock.unlock();
		}
		throw new IllegalArgumentException("无法申请"+cap+"大小的"+"缓冲区");
	}
	public final DynByteBuf buffer(boolean direct, int cap) {
		DynByteBuf buf;
		lock.lock();
		try {
			for (BPool pool : pools) {
				if ((buf = pool.allocate(direct, cap)) != null) {
					if (ldt != null) ldt.track(buf);
					return buf;
				}
			}
		} finally {
			lock.unlock();
		}
		throw new IllegalArgumentException("无法申请"+cap+"大小的"+(direct?"direct":"heap")+"缓冲区");
	}
	public final void reserve(DynByteBuf buf) {
		if (buf == null) throw new NullPointerException("buffer");
		for (BPool pool : pools) {
			if (pool.isPooled(buf)) {
				lock.lock();
				try {
					if (ldt != null) ldt.untrack(buf);
					pool.reserve(buf);
					return;
				} finally {
					lock.unlock();
				}
			}
		}
		throwUnpooled(buf);
	}

	public final boolean isPooled(DynByteBuf buf) {
		if (buf == null) throw new NullPointerException("buffer");

		for (BPool pool : pools) {
			if (pool.isPooled(buf)) return true;
		}
		return false;
	}

	public final DynByteBuf expand(DynByteBuf buf, int more) {
		return expand(buf, more, true, true);
	}
	public final DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd) {
		return expand(buf, more, addAtEnd, true);
	}
	public final DynByteBuf expand(DynByteBuf buf, int more, boolean addAtEnd, boolean reserveOld) {
		if (buf == null) throw new NullPointerException("buffer");

		for (BPool pool : pools) {
			if (pool.isPooled(buf)) {
				lock.lock();
				try {
					if (pool.expand(buf, more, addAtEnd)) return buf;

					DynByteBuf newBuf = buffer(buf.isDirect(), buf.capacity()+more);
					if (!addAtEnd) newBuf.wIndex(more);
					newBuf.put(buf);
					if (reserveOld) pool.reserve(buf);
					return newBuf;
				} finally {
					lock.unlock();
				}
			}
		}

		throwUnpooled(buf);
		return Helpers.nonnull();
	}

	@Override
	public String toString() {
		return "BufferPool{" + Arrays.toString(pools) + '}';
	}

	private static void throwUnpooled(DynByteBuf buf) {
		throw new RuntimeException("非池内或已释放的缓冲区: " + buf.getClass().getName() + "@" + System.identityHashCode(buf));
	}
}