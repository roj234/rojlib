/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.net.misc;

import roj.collect.SimpleList;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Pipe network IO threads
 * @author Roj233
 * @since 2021/12/24 23:27
 */
class MyKeySet extends SimpleList<SelectionKey> implements Set<SelectionKey> {
    MyKeySet() {
        super(100);
    }
}
interface SetSet {
    void setSelectedSet(Selector selector, Set<SelectionKey> selectedSet);
    void setPublicSelectedSet(Selector selector, Set<SelectionKey> totalSet);
}
public final class PipeIOThread extends Thread {
    private static final ThreadGroup PIPE_IO = new ThreadGroup("Pipe IO");
    private static PipeIOThread[] tmp = new PipeIOThread[0];
    private static int index;
    private static final int MAX_IO_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int IDLE_KILL_TIMEOUT = 50000;

    public static synchronized int getIdleCount() {
        if (tmp.length < PIPE_IO.activeCount()) {
            tmp = new PipeIOThread[PIPE_IO.activeCount()];
        }

        int idle = 0;
        int amount = PIPE_IO.enumerate(tmp);
        for (int i = 0; i < amount; i++) {
            PipeIOThread thread = tmp[i];
            if (thread.idle) idle++;
        }
        return idle;
    }

    public static int getRunningCount() {
        return PIPE_IO.activeCount();
    }

    public static synchronized void syncWaitShutdown() {
        if (tmp.length < PIPE_IO.activeCount()) {
            tmp = new PipeIOThread[PIPE_IO.activeCount()];
        }

        while (PIPE_IO.activeCount() > 0) {
            PIPE_IO.interrupt();
            int amount = PIPE_IO.enumerate(tmp);
            for (int i = 0; i < amount; i++) {
                PipeIOThread thread = tmp[i];
                try {
                    thread.selector.close();
                } catch (IOException ignored) {}
            }
            LockSupport.parkNanos(500);
        }
    }

    public static synchronized void syncRegister(Shutdownable server, Pipe pair, Consumer<Pipe> callback) throws Exception {
        if (tmp.length < PIPE_IO.activeCount()) {
            tmp = new PipeIOThread[PIPE_IO.activeCount()];
        }

        FileDescriptorChannel fdcA = new FileDescriptorChannel(pair.getUpstreamFD());
        FileDescriptorChannel fdcB = new FileDescriptorChannel(pair.getDownstreamFD());

        Att att = new Att();
        att.pair = pair;
        att.callback = callback;

        int amount = PIPE_IO.enumerate(tmp);
        Selector lowest = null;
        for (int i = 0; i < amount; i++) {
            PipeIOThread thread = tmp[i];
            Selector s = thread.selector;
            if (s.isOpen() && s.keys().size() < 100) {
                if (lowest == null || s.keys().size() < lowest.keys().size()) {
                    lowest = s;
                }
            }
        }
        if (lowest != null) {
            try {
                pair.upKey = fdcA.register(lowest, SelectionKey.OP_READ, att);
                pair.downKey = fdcB.register(lowest, SelectionKey.OP_READ, att);
                return;
            } catch (ClosedChannelException | ClosedSelectorException ignored) {}
        }

        if (amount < MAX_IO_THREADS) {
            PipeIOThread thread = new PipeIOThread(server, index++);
            try {
                pair.upKey = fdcA.register(thread.selector, SelectionKey.OP_READ, att);
                pair.downKey = fdcB.register(thread.selector, SelectionKey.OP_READ, att);
            } catch (ClosedChannelException e) {
                throw new IllegalStateException("Should not happen: closed channel", e);
            }
            thread.start();
        } else {
            lowest = null;
            for (int i = 0; i < amount; i++) {
                PipeIOThread thread = tmp[i];
                Selector s = thread.selector;
                if (s.isOpen()) {
                    if (lowest == null || s.keys().size() < lowest.keys().size()) {
                        lowest = s;
                    }
                }
            }
            try {
                pair.upKey = fdcA.register(lowest, SelectionKey.OP_READ, att);
                pair.downKey = fdcB.register(lowest, SelectionKey.OP_READ, att);
            } catch (ClosedChannelException | ClosedSelectorException ignored) {}
        }
    }

    static SetSet setter;

    private final Shutdownable server;
    private final Selector selector;
    volatile boolean idle;

    public PipeIOThread(Shutdownable server, int index) {
        super(PIPE_IO, "Pipe IO #" + index);
        this.server = server;
        Selector t;
        try {
            t = Selector.open();
        } catch (IOException e) {
            Helpers.athrow(e);
            t = null;
        }
        if (setter == null) {
            synchronized (PipeIOThread.class) {
                if (setter == null)
                    setter = DirectAccessor.builder(SetSet.class).unchecked()
                                           .access(t.getClass(), "selectedKeys", null, "setSelectedSet")
                                           .access(t.getClass(), "publicSelectedKeys", null, "setPublicSelectedSet")
                                           .build();
            }
        }
        MyKeySet set = new MyKeySet();
        setter.setSelectedSet(t, set);
        setter.setPublicSelectedSet(t, set);
        this.selector = t;
        setDaemon(true);
    }

    @Override
    public void run() {
        int idle = 0;
        while (!server.wasShutdown() && idle < IDLE_KILL_TIMEOUT) {
            try {
                selector.selectNow();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            } catch (ClosedSelectorException e) {
                break;
            }
            if (selector.selectedKeys().isEmpty() || Thread.interrupted()) {
                LockSupport.parkNanos(20);
                idle++;
                this.idle = true;
                continue;
            }
            this.idle = false;
            idle = 0;
            MyKeySet keys = (MyKeySet) selector.selectedKeys();
            for (int i = 0; i < keys.size(); i++) {
                SelectionKey key = keys.get(i);
                Att att = (Att) key.attachment();
                Pipe pair = att.pair;
                if (pair.isEof()) {
                    key.cancel();
                    pair.upKey = pair.downKey = null;
                    if (att.callback != null) {
                        try {
                            att.callback.accept(pair);
                        } catch (Throwable ignored) {}
                        att.callback = null;
                    }
                    continue;
                }
                try {
                    pair.transfer();
                } catch (Throwable e) {
                    e.printStackTrace();
                    try {
                        pair.release();
                    } catch (IOException ignored) {}
                    key.cancel();
                    pair.upKey = pair.downKey = null;
                    if (att.callback != null) {
                        try {
                            att.callback.accept(pair);
                        } catch (Throwable ignored) {}
                        att.callback = null;
                    }
                }
            }
            keys.clear();
            try {
                for (SelectionKey key : selector.keys()) {
                    ((Att) key.attachment()).pair.idleTime++;
                }
            } catch (Throwable ignored) {}
            LockSupport.parkNanos(1000);
        }
        try {
            selector.close();
        } catch (IOException ignored) {}
    }

    static final class Att {
        Pipe           pair;
        Consumer<Pipe> callback;
    }
}
