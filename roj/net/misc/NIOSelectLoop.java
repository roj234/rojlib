package roj.net.misc;

import roj.net.misc.MySelector.MyKeySet;
import roj.util.FastLocalThread;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/1/24 11:38
 */
public abstract class NIOSelectLoop<T extends Selectable> implements Shutdownable {
    public static final long HALF_MS = 500_000L;

    static final class SelectThread extends FastLocalThread {
        private final NIOSelectLoop<?> owner;
        private final Selector selector;
        volatile boolean idle;

        SelectThread(NIOSelectLoop<?> owner) throws IOException {
            super(owner.group, owner.group.getName() + " #" + owner.index++);
            this.owner = owner;
            this.selector = MySelector.open();
            setDaemon(true);
        }

        @Override
        public void run() {
            Selector sel = this.selector;
            NIOSelectLoop<?> loop = this.owner;
            MyKeySet keys = (MyKeySet) sel.selectedKeys();
            while (!loop.wasShutdown()) {
                try {
                    sel.selectNow();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } catch (ClosedSelectorException e) {
                    break;
                }
                if (!sel.isOpen()) break;
                this.idle = true;
                if (sel.keys().isEmpty()) {
                    LockSupport.parkNanos(loop.idleKill * 1_000_000L);
                    if (sel.keys().isEmpty()) {
                        //System.out.println("Idle exit");
                        break;
                    }
                }
                if (keys.isEmpty() || Thread.interrupted()) {
                    synchronized (sel.keys()) {
                        for (SelectionKey key : sel.keys()) {
                            Selectable t = ((Att) key.attachment()).t;
                            try {
                                t.idleTick();
                            } catch (Throwable e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    LockSupport.parkNanos(HALF_MS);
                    continue;
                }
                this.idle = false;
                for (int i = 0; i < keys.size(); i++) {
                    SelectionKey key = keys.get(i);
                    Att att = (Att) key.attachment();
                    Selectable t = att.t;
                    if (t.isClosedOn(key)) {
                        key.cancel();
                        call(att);
                        continue;
                    }
                    try {
                        t.selected(key.readyOps());
                    } catch (Throwable e) {
                        e.printStackTrace();
                        try {
                            t.close();
                        } catch (IOException ignored) {}
                        try {
                            loop.unregister(Helpers.cast(t));
                        } catch (IOException ignored) {}
                        call(att);
                    }
                }
                keys.clear();
                synchronized (sel.keys()) {
                    for (SelectionKey key : sel.keys()) {
                        Selectable t = ((Att) key.attachment()).t;
                        try {
                            t.tick();
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            try {
                sel.close();
            } catch (IOException ignored) {}
        }

        static void call(Att att) {
            if (att.cb != null) {
                try {
                    att.cb.accept(att.t);
                } catch (Throwable ignored) {}
                att.cb = null;
            }
        }
    }

    private final ThreadGroup group;
    private final Object lock;
    private final int maxIoThreads, idleKill, threshold;
    private final Shutdownable owner;

    protected SelectThread[] tmp = new SelectThread[1];
    protected int index;
    protected boolean shutdown;

    protected NIOSelectLoop(Shutdownable owner, String prefix, int maxThreads, int idleKill, int threshold) {
        this.group = new ThreadGroup(prefix);
        this.maxIoThreads = maxThreads;
        this.idleKill = idleKill;
        this.threshold = threshold;
        this.lock = new Object();
        this.owner = owner;
    }

    public final int getIdleCount() {
        int idle = 0;
        synchronized (lock) {
            if (tmp.length < group.activeCount()) {
                tmp = new SelectThread[group.activeCount()];
            }

            int amount = group.enumerate(tmp);
            for (int i = 0; i < amount; i++) {
                SelectThread thread = tmp[i];
                if (thread.idle) idle++;
            }
        }
        return idle;
    }

    public final int getRunningCount() {
        return group.activeCount();
    }

    public boolean wasShutdown() {
        return shutdown || (owner != null && owner.wasShutdown());
    }

    public void shutdown() {
        synchronized (lock) {
            if (shutdown) return;
            shutdown = true;
            if (tmp.length < group.activeCount()) {
                tmp = new SelectThread[group.activeCount()];
            }

            while (group.activeCount() > 0) {
                group.interrupt();
                int amount = group.enumerate(tmp);
                for (int i = 0; i < amount; i++) {
                    SelectThread t = tmp[i];
                    try {
                        t.selector.close();
                    } catch (IOException ignored) {}
                }
                group.interrupt();
            }
        }
    }

    public void register(T t, Consumer<T> callback) throws Exception {
        unregister(t);

        Att att = new Att();
        att.t = t;
        att.cb = Helpers.cast(callback);

        int amount;
        SelectThread lowest = null;

        synchronized (lock) {
            if (tmp.length < group.activeCount()) {
                tmp = new SelectThread[group.activeCount()];
            }

            amount = group.enumerate(tmp);
            for (int i = 0; i < amount; i++) {
                SelectThread thread = tmp[i];
                Selector s = thread.selector;
                if (s.isOpen()) {
                    if (lowest == null || s.keys().size() < lowest.selector.keys().size()) {
                        lowest = thread;
                    }
                }
            }
        }

        if (lowest != null) {
            if (lowest.selector.keys().size() <= threshold || amount >= maxIoThreads) {
                try {
                    register1(lowest.selector, t, att);
                    LockSupport.unpark(lowest);
                    return;
                } catch (ClosedSelectorException ignored) {}
            }
        }

        SelectThread thread = new SelectThread(this);
        register1(thread.selector, t, att);
        thread.start();
        LockSupport.unpark(thread);
    }

    public abstract void unregister(T t) throws IOException;

    protected abstract void register1(Selector sel, T t, Object att) throws IOException;

    static final class Att {
        Selectable           t;
        Consumer<Selectable> cb;
    }
}
