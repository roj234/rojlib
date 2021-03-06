package roj.net.misc;

import roj.net.misc.MySelector.MyKeySet;
import roj.util.FastLocalThread;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/1/24 11:38
 */
public abstract class NIOSelectLoop<T extends Selectable> implements Shutdownable {
    public static final BiConsumer<String, Throwable> PRINT_HANDLER = (reason, error) -> error.printStackTrace();

    static final class SelectThread extends FastLocalThread {
        private final NIOSelectLoop<?> owner;
        private Selector selector;

        boolean idle, wakeupLock;
        long time;

        SelectThread(NIOSelectLoop<?> owner) throws IOException {
            this.owner = owner;
            this.selector = MySelector.open();
            setName(owner.prefix + " #" + owner.index++);
            setDaemon(true);
        }

        void refresh() {
            Selector old = this.selector;
            if (old.isOpen()) {
                Selector sel;
                try {
                    sel = MySelector.open();
                } catch (IOException e) {
                    owner.exception.accept("R_OPEN", e);
                    return;
                }

                for (SelectionKey oKey : old.keys()) {
                    Att att = (Att) oKey.attachment();
                    try {
                        if (oKey.isValid() && oKey.channel().keyFor(sel) == null) {
                            int ops = oKey.interestOps();
                            oKey.cancel();
                            SelectionKey nKey = oKey.channel().register(sel, ops, att);
                            owner.onRefresh(att.t, oKey, nKey);
                        }
                    } catch (Exception e) {
                        owner.exception.accept("R_REGISTER", e);
                        call(att);
                    }
                }

                this.selector = sel;

                try {
                    old.close();
                } catch (Throwable e) {
                    owner.exception.accept("R_CLOSE", e);
                }
            }
        }

        @Override
        public void run() {
            Selector sel = this.selector;
            NIOSelectLoop<?> loop = this.owner;
            MyKeySet keys = (MyKeySet) sel.selectedKeys();
            time = System.currentTimeMillis();
            int elapsed;

            mainLoop:
            while (!loop.wasShutdown()) {
                try {
                    sel.select(1);
                } catch (IOException e) {
                    owner.exception.accept("S_SELECT", e);
                    break;
                } catch (ClosedSelectorException e) {
                    break;
                }
                if (!sel.isOpen()) break;

                while (wakeupLock) LockSupport.park();

                this.idle = true;

                if (Thread.interrupted()) break;
                if (sel.keys().isEmpty()) {
                    time = System.currentTimeMillis();
                    while (System.currentTimeMillis() < time + loop.idleKill) {
                        if (!sel.keys().isEmpty()) break;
                        LockSupport.parkNanos(loop.idleKill * 1_000_000L);
                    }
                    if (sel.keys().isEmpty()) {
                        if (Thread.interrupted() || owner.getRunningCount() > owner.minThreads) break;
                    }
                    time = System.currentTimeMillis();
                }

                if (keys.isEmpty()) {
                    elapsed = (int) (System.currentTimeMillis() - time);
                    time = System.currentTimeMillis();
                    if (elapsed == 0) continue;

                    synchronized (sel.keys()) {
                        for (SelectionKey key : sel.keys()) {
                            Selectable t = ((Att) key.attachment()).t;
                            try {
                                t.tick(elapsed);
                            } catch (Throwable e) {
                                if (e instanceof ThreadDeath) break mainLoop;
                                owner.exception.accept("T_IDLE_TICK", e);
                            }
                        }
                    }
                    continue;
                }

                this.idle = false;
                for (int i = 0; i < keys.size(); i++) {
                    SelectionKey key = keys.get(i);
                    Att att = (Att) key.attachment();
                    Selectable t = att.t;
                    boolean closedOn;
                    try {
                        closedOn = t.isClosedOn(key);
                    } catch (Throwable e) {
                        if (e instanceof ThreadDeath) break mainLoop;
                        loop.exception.accept("T_IS_CLOSED_ON", e);
                        closedOn = true;
                    }

                    if (closedOn || !key.isValid()) {
                        call(att);
                        continue;
                    }

                    try {
                        t.selected(key.readyOps());
                    } catch (Throwable e) {
                        if (e instanceof ThreadDeath) break mainLoop;
                        loop.exception.accept("T_SELECTED", e);
                        call(att);
                    }
                }
                keys.clear();

                elapsed = (int) (System.currentTimeMillis() - time);
                time = System.currentTimeMillis();
                if (elapsed == 0) continue;

                if (!sel.isOpen()) break;
                synchronized (sel.keys()) {
                    for (SelectionKey key : sel.keys()) {
                        Selectable t = ((Att) key.attachment()).t;
                        try {
                            t.tick(elapsed);
                        } catch (Throwable e) {
                            if (e instanceof ThreadDeath) break mainLoop;
                            loop.exception.accept("T_TICK", e);
                        }
                    }
                }
            }
            try {
                sel.close();
            } catch (IOException e) {
                loop.exception.accept("S_CLOSE", e);
            }
            synchronized (loop.lock) {
                SelectThread[] t = loop.threads;
                for (int i = 0; i < t.length; i++) {
                    if (t[i] == this) {
                        int len = t.length - i - 1;
                        if (len > 0) System.arraycopy(t, i + 1, t, i, len);
                        t[t.length - 1] = null;
                        break;
                    }
                }
            }
        }

        private void call(Att att) {
            try {
                att.t.close();
            } catch (Throwable e1) {
                owner.exception.accept("T_CLOSE", e1);
            }
            try {
                if (!((Object) att.cb instanceof ShutdownCallback))
                    owner.unregister(Helpers.cast(att.t));
            } catch (Throwable e1) {
                owner.exception.accept("T_UNREGISTER", e1);
            }
            synchronized (att) {
                if (att.cb != null) {
                    try {
                        att.cb.accept(att.t);
                    } catch (Throwable ignored) {}
                    att.cb = null;
                }
            }
        }
    }

    private Shutdownable owner;
    private boolean shutdown;

    public final String prefix;
    private final int minThreads, maxThreads, idleKill, threshold;
    private BiConsumer<String, Throwable> exception = PRINT_HANDLER;

    private final Object   lock;
    private SelectThread[] threads;

    int index;

    /**
     * @param owner ???????????????
     * @param prefix ??????????????????
     * @param minThreads ????????????
     * @param maxThreads ????????????
     * @param idleKill ???????????????????????????
     * @param threshold ???????????????????????????????????????????????????????????????
     */
    protected NIOSelectLoop(Shutdownable owner, String prefix, int minThreads, int maxThreads, int idleKill, int threshold) {
        if (threshold < 0) throw new IllegalArgumentException("threshold < 0");
        if (minThreads < 0) throw new IllegalArgumentException("minThreads < 0");
        if (maxThreads < 1) throw new IllegalArgumentException("maxThreads < 1");
        if (idleKill < 1000) throw new IllegalArgumentException("idleKill < 1000");
        if (threshold > 1024) throw new IllegalArgumentException("threshold > 1024");

        this.prefix = prefix;
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.idleKill = idleKill;
        this.threshold = threshold;
        this.lock = new Object();
        this.owner = owner;

        Thread[] t = this.threads = new SelectThread[Math.max(minThreads, 2)];
        for (int i = 0; i < minThreads; i++) {
            try {
                (t[i] = new SelectThread(this)).start();
            } catch (IOException e) {
                throw new IllegalStateException("Unable initialize thread", e);
            }
        }
    }

    public final void setExceptionHandler(BiConsumer<String, Throwable> exception) {
        this.exception = exception;
    }

    public final BiConsumer<String, Throwable> setExceptionHandler() {
        return exception;
    }

    public final void setOwner(Shutdownable s) {
        if (owner != null) throw new IllegalArgumentException("Already has a owner");
        owner = s;
    }

    public final int getStartedCount() {
        return index;
    }

    public final int getIdleCount() {
        int idle = 0;
        SelectThread[] t = this.threads;
        for (SelectThread thread : t) {
            if (thread == null) break;
            if (thread.idle) idle++;
        }
        return idle;
    }

    @Deprecated
    public final void checkInfiniteLoop(int timeout) {
        long latest = System.currentTimeMillis() - timeout;
        SelectThread[] t = this.threads;
        for (SelectThread thread : t) {
            if (thread == null) break;
            if (thread.time < latest) {
                thread.stop();
            }
        }
    }

    public final int getRunningCount() {
        SelectThread[] t = this.threads;
        int i = 0;
        for (; i < t.length; i++) {
            if (t[i] == null) break;
        }
        return i;
    }

    public boolean wasShutdown() {
        if (shutdown) return true;
        if (owner != null && owner.wasShutdown()) {
            shutdown();
            return true;
        }
        return false;
    }

    public void shutdown() {
        synchronized (lock) {
            if (shutdown) return;
            shutdown = true;

            SelectThread[] t = this.threads;
            for (SelectThread thread : t) {
                if (thread == null) break;

                thread.interrupt();
                try {
                    thread.selector.close();
                } catch (IOException ignored) {}
                thread.interrupt();
            }
        }
    }

    public final void register(T t, Consumer<T> callback) throws Exception {
        unregister(t);

        Att att = new Att();
        att.t = t;
        att.cb = Helpers.cast(callback);

        int i = 0;
        SelectThread lowest = null;

        SelectThread[] ts = this.threads;
        for (; i < ts.length; i++) {
            SelectThread st = ts[i];
            if (st == null) break;

            Selector s = st.selector;
            if (s.isOpen()) {
                if (lowest == null || s.keys().size() < lowest.selector.keys().size()) {
                    lowest = st;
                }
            }
        }

        if (lowest != null) {
            if (lowest.selector.keys().size() <= threshold || i >= maxThreads) {
                try {
                    lowest.wakeupLock = true;
                    lowest.selector.wakeup();

                    register1(lowest.selector, t, att);

                    lowest.wakeupLock = false;
                    LockSupport.unpark(lowest);
                    return;
                } catch (ClosedSelectorException ignored) {}
            }
        }

        SelectThread thread;
        synchronized (lock) {
            if (shutdown) return;

            if (i == ts.length) {
                ts = new SelectThread[Math.min(i + 10, maxThreads)];
                System.arraycopy(threads, 0, ts, 0, i);
                threads = ts;
            }
            thread = ts[i] = new SelectThread(this);
        }

        register1(thread.selector, t, att);
        thread.start();
    }

    public final void registerListener(Listener l) throws IOException {
        FileDescriptorChannel fdc = new FileDescriptorChannel(l.fd);

        SelectThread thread;
        SelectThread[] ts = this.threads;
        if ((thread = ts[0]) == null) {
            synchronized (lock) {
                if (ts[0] != null) {
                    thread = ts[0];
                }
            }
        }

        Att att = new Att();
        att.cb = Helpers.cast(new ShutdownCallback(this));
        att.t = l;

        if (thread != null) {
            thread.wakeupLock = true;
            thread.selector.wakeup();

            l.key = fdc.register(thread.selector, SelectionKey.OP_READ, att);

            thread.wakeupLock = false;
            LockSupport.unpark(thread);
        } else {
            synchronized (lock) {
                thread = ts[0] = new SelectThread(this);
                thread.setDaemon(false);

                l.key = fdc.register(thread.selector, SelectionKey.OP_READ, att);

                thread.start();
            }
        }
    }

    public abstract void unregister(T t) throws IOException;

    protected abstract void register1(Selector sel, T t, Object att) throws IOException;

    protected void onRefresh(Selectable t, SelectionKey from, SelectionKey to) {}

    static final class Att {
        Selectable           t;
        Consumer<Selectable> cb;
    }

    public static final class ShutdownCallback implements Consumer<Object> {
        private final NIOSelectLoop<?> loop;

        ShutdownCallback(NIOSelectLoop<?> loop) {
            this.loop = loop;
        }

        @Override
        public void accept(Object x) {
            loop.shutdown();
        }
    }
}
