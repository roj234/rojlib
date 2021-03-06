package roj.concurrent;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2022/1/22 13:08
 */
public class Waiters {
    private volatile Entry head;

    private static final AtomicReferenceFieldUpdater<Waiters, Entry> CASHEAD = AtomicReferenceFieldUpdater.newUpdater(Waiters.class, Entry.class, "head");
    private static final AtomicReferenceFieldUpdater<Entry, Entry>   CASNEXT = AtomicReferenceFieldUpdater.newUpdater(Entry.class, Entry.class, "next");

    public Object await(int id) {
        Entry ent = new Entry();
        ent.id = id;
        ent.owner = Thread.currentThread();

        Entry head = CASHEAD.getAndSet(this, ent);
        if (head != null) {
            head.next = ent;
        }
        LockSupport.park(this);
        return ent.payload;
    }

    public Object await(int id, int timeout) {
        Entry ent = new Entry();
        ent.id = id;
        ent.owner = Thread.currentThread();

        Entry head = CASHEAD.getAndSet(this, ent);
        if (head != null) {
            head.next = ent;
        }
        if (timeout > 0) LockSupport.parkNanos(this, timeout * 1000000L);
        else LockSupport.park(this);
        if (ent.owner != null) {
            ent.owner = null;
            return null;
        }
        return ent.payload;
    }

    public boolean signal(int id) {
        return signal(id, this);
    }

    private boolean signal(int id, Object payload) {
        Entry prev = null, ent = head;
        while (ent != null) {
            if (ent.id == id) {
                if (prev == null) {
                    // ent is head
                    do {
                        prev = head;
                    } while (prev != null && !CASHEAD.compareAndSet(this, prev, prev.next));
                } else {
                    CASNEXT.compareAndSet(prev, ent, ent.next);
                }
                ent.payload = payload;
                Thread t = ent.owner;
                ent.owner = null;
                LockSupport.unpark(t);
                return true;
            }
            prev = ent;
            ent = ent.next;
        }
        return false;
    }

    public boolean hasAny() {
        return head != null;
    }

    static final class Entry {
        int id;
        Thread owner;
        Object payload;

        volatile Entry next;
    }
}
