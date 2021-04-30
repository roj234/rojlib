/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: NextTickList.java
 */
package roj.concurrent;

import roj.math.MutableBoolean;

public final class NextTickList {
    private NextTickEntry entry;
    private final MutableBoolean empty = new MutableBoolean(true);

    public void add(Runnable fun) {
        synchronized (empty) {
            empty.set(false);
        }
        NextTickEntry prev = entry;
        (entry = new NextTickEntry(fun)).prev = prev;
    }

    public void call() {
        NextTickEntry e = entry;
        entry = null;
        while (e != null) {
            e.run.run();
            e = e.prev;
        }
        synchronized (empty) {
            empty.set(true);
        }
    }

    public boolean empty() {
        synchronized (empty) {
            return empty.get();
        }
    }

    public static final class NextTickEntry {
        Runnable run;

        public NextTickEntry(Runnable r) {
            run = r;
        }

        NextTickEntry prev;
    }
}