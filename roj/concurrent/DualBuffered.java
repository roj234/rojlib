package roj.concurrent;

import roj.util.Helpers;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2022/3/14 13:14
 */
public abstract class DualBuffered<W, R> {
    private static final AtomicIntegerFieldUpdater<DualBuffered<?, ?>> I =
            AtomicIntegerFieldUpdater.newUpdater(Helpers.cast(DualBuffered.class), "i");

    private volatile int i;
    protected W w;
    protected R r;

    public DualBuffered() {}

    public DualBuffered(W w, R r) {
        this.w = w;
        this.r = r;
    }

    public final W forWrite() {
        while (true) {
            int i = this.i;
            if (I.compareAndSet(this, i & ~1, i | 1)) break;

            // 有此标记意为write lock有人抢了
            // 而不是普通的CAS失败
            // 于是等一下吧
            if ((i & 1) != 0) LockSupport.parkNanos(100);
        }

        return w;
    }

    public final void writeFinish() {
        int i = this.i;
        while (!I.compareAndSet(this, i | 1, (i & ~1) | 2)) {
            if ((i & 1) == 0) throw new IllegalStateException();
            i = this.i;
        }

        tryFlush();
    }

    public final R forRead() {
        tryFlush();
        I.addAndGet(this, 4);
        return r;
    }

    public final void readFinish() {
        int i;
        do {
            i = this.i;
            if (i < 4) throw new IllegalStateException();
        } while (!I.compareAndSet(this, i, i - 4));

        tryFlush();
    }

    private void tryFlush() {
        // 若CAS成功, 则write占用标志被设定, 且因为有bit1, i != 2成立
        // 故可保证安全性
        if (i == 2 && I.compareAndSet(this, 2, 1)) {
            try {
                move();
            } catch (Throwable e) {
                // 操作失败
                I.set(this, 2);
                throw e;
            }
            // 操作完成, 清除bit2
            I.set(this, 0);
        }
    }

    /**
     * move W to R
     */
    protected abstract void move();
}
