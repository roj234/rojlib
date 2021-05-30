package roj.concurrent;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.WeakMyHashMap;
import roj.reflect.DirectFieldAccess;
import roj.reflect.DirectFieldAccessor;
import roj.reflect.Instanced;
import roj.reflect.PackagePrivateProxy;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/5/16 14:23
 */
public final class ThreadAllocCleaner extends Thread {
    static final ThreadAllocCleaner instance;
    static ReferenceQueue<Object> queue;
    static MyHashMap<Thread, WeakMyHashMap<ThreadLocalNG<?>, Object>> threadMap = new MyHashMap<>();
    static final int WAIT_TIMEOUT = Integer.parseInt(System.getProperty("threadLocalNG.clearIntervalMs", "100"));

    public static Map<ThreadLocalNG<?>, Object> getOrCreateMap() {
        Thread t = Thread.currentThread();
        MyHashMap.Entry<Thread, WeakMyHashMap<ThreadLocalNG<?>, Object>> entry = threadMap.getOrCreateEntry(t);
        if(entry.v == IntMap.NOT_USING) {
            entry.v = new WeakMyHashMap<>(4);
            threadMap._int_plus_size();

            acc.setInstance(t);
            new Cl(t, acc.getObject());
            acc.clearInstance();
        }
        return entry.v;
    }

    @SuppressWarnings("unchecked")
    private ThreadAllocCleaner() {
        setDaemon(true);
        setName("ThreadLocal cleaner");
        setPriority(Thread.MIN_PRIORITY);
        queue = PackagePrivateProxy.proxyIt(ReferenceQueue.class, ThreadAllocCleaner.class, "java.lang.ref", "enqueue");
        ((Instanced)queue).setInstance(this);
    }

    @Override
    public void run() {
        Collection<WeakMyHashMap<ThreadLocalNG<?>, Object>> values = threadMap.values();
        while (true) {
            for(WeakMyHashMap<?,?> map : values)
                map.removeClearedEntry();

            try {
                Thread.sleep(WAIT_TIMEOUT);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Delegation method to {@link ReferenceQueue#enqueue(Reference)}
     */
    public boolean enqueue(Reference<?> r) {
        threadMap.remove(((Cl) r).t);
        return false;
    }

    static final class Cl extends WeakReference<Object> {
        Thread t;
        public Cl(Thread thread, Object referent) {
            super(referent, queue);
            this.t = thread;
        }
    }

    static DirectFieldAccessor acc;

    static {
        try {
            acc = DirectFieldAccess.get(Thread.class, Thread.class.getDeclaredField("threadLocals"));
        } catch (NoSuchFieldException e) {
            throw new InternalError();
        }
        (instance = new ThreadAllocCleaner()).start();
    }
}
