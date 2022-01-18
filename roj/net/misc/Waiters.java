package roj.net.misc;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj233
 * @since 2022/1/22 13:08
 */
public class Waiters {
    private volatile Entry head;

    static final AtomicReferenceFieldUpdater<Waiters, Entry> CASHEAD = AtomicReferenceFieldUpdater.newUpdater(Waiters.class, Entry.class, "head");
    static final AtomicReferenceFieldUpdater<Entry, Entry>   CASNEXT = AtomicReferenceFieldUpdater.newUpdater(Entry.class, Entry.class, "next");

    public void await(int id) {
        Entry ent = new Entry();
        ent.id = id;
        ent.owner = Thread.currentThread();

        Entry head = CASHEAD.getAndSet(this, ent);
        if (head != null) {
            head.next = ent;
        }
        LockSupport.park(this);
    }

    public boolean signal(int id) {
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
                LockSupport.unpark(ent.owner);
                return true;
            }
            prev = ent;
            ent = ent.next;
        }
        return false;
    }

    static final class Entry {
        int id;
        Thread owner;

        volatile Entry next;
    }
}
