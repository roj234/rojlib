package roj.io;

import roj.collect.RingBuffer;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

/**
 * @author Roj233
 * @since 2022/3/14 1:50
 */
public final class PooledBuf {
    private final RingBuffer<Holder> bbHolders = new RingBuffer<>(10);
    private static final class Holder extends ByteList {}

    public ByteList retain() {
        ByteList b = bbHolders.isEmpty() ? new Holder() : bbHolders.removeFirst();
        b.clear();
        return b;
    }

    public boolean release(ByteList b) {
        if (!(b instanceof Holder)) return false;
        if (!bbHolders.contains(b)) {
            b.clear();
            bbHolders.addLast((Holder) b);
            return true;
        }
        return false;
    }

    private static final FastThreadLocal<PooledBuf> INST = FastThreadLocal.withInitial(PooledBuf::new);

    public static PooledBuf alloc() {
        return INST.get();
    }
}
