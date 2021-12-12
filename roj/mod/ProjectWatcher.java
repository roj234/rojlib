/*
 * This file is a part of MI
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
package roj.mod;

import com.sun.nio.file.ExtendedWatchEventModifier;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.locks.LockSupport;

/**
 * Project file change watcher
 *
 * @author Roj233
 * @since 2021/7/17 19:01
 */
public final class ProjectWatcher extends IProjectWatcher implements Runnable {
    static final class X {
        WatchKey key;
        final String owner;
        final byte x;
        MyHashSet<String> s = new MyHashSet<>();

        X(String owner, WatchKey key, int tag) {
            this.owner = owner;
            this.key = key;
            this.x = (byte) tag;
        }

        X() {
            this.owner = "";
            this.x = 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            X x = (X) o;

            return key.equals(x.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    X via;
    MyHashSet<X> actions;
    final WatchService watcher;
    final Thread t;
    Runnable cb;
    boolean pause;

    MyHashMap<String, X[]> listeners;

    public ProjectWatcher(Path libPath, Runnable callback) throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        actions = new MyHashSet<>();
        listeners = new MyHashMap<>();
        via = new X();

        libPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                         StandardWatchEventKinds.ENTRY_MODIFY);
        cb = callback;

        Thread t = this.t = new Thread(this);
        t.setName("Filesystem Watcher");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void run() {
        while (listeners != null) {
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException e) {
                System.out.println("ProjectWatcher.pause()");
                pause = true;
                // noinspection all
                Thread.interrupted();
                LockSupport.park();
                // noinspection all
                Thread.interrupted();
                pause = false;
                System.out.println("ProjectWatcher.unpause()");
                continue;
            }

            via.key = key;
            X csm = actions.find(via);
            System.out.println("ProjectWatcher.onEvent(): " + key.watchable());

            x:
            for (WatchEvent<?> event : key.pollEvents()) {
                String name = event.kind().name();
                if(csm == null) {
                    if (!name.equals("OVERFLOW")) {
                        @SuppressWarnings("unchecked")
                        String id = ((WatchEvent<Path>) event).context().toString();
                        if (id.endsWith(".zip") || id.endsWith(".jar")) cb.run();
                    }
                    break;
                }
                MyHashSet<String> s = csm.s;
                switch (name) {
                    case "OVERFLOW": {
                        CmdUtil.error("[PW]Event overflow " + key.watchable());
                        key.cancel();
                        break x;
                    }
                    case "ENTRY_MODIFY":
                    case "ENTRY_CREATE": {
                        @SuppressWarnings("unchecked")
                        String id = key.watchable().toString() + File.separatorChar + ((WatchEvent<Path>) event).context().toString();
                        if (new File(id).isDirectory()) break;
                        if (csm.x == ID_SRC) {
                            if (!id.endsWith(".java"))
                                break x;
                        }
                        s.add(id);
                        break;
                    }
                    case "ENTRY_DELETE": {
                        @SuppressWarnings("unchecked")
                        String id = key.watchable().toString() + File.separatorChar + ((WatchEvent<Path>) event).context().toString();
                        if (new File(id).isDirectory()) break;
                        if (csm.x == ID_SRC) {
                            if (!id.endsWith(".java"))
                                break x;
                        }
                        s.remove(id);
                        break;
                    }
                }
            }

            // The key is not valid (directory was deleted
            if (!key.reset()) {
                key.cancel();
                if(csm != null) {
                    X[] remove = listeners.remove(csm.owner);
                    if (remove != null) {
                        for (X set : remove) {
                            actions.remove(set);
                            set.key.cancel();
                        }
                    }
                }
            }
        }

        try {
            watcher.close();
        } catch (IOException e) {
            Helpers.athrow(e);
        }
    }

    @Override
    public MyHashSet<String> getModified(Project proj, int id) {
        if(listeners == null || !listeners.containsKey(proj.name))
            return super.getModified(proj, id);
        return listeners.get(proj.name)[id].s;
    }

    public void reset() {
        if(listeners == null)
            return;

        while (!pause) {
            t.interrupt();
            LockSupport.parkNanos(1000L);
        }

        for (X key : actions) {
            key.key.cancel();
        }
        actions.clear();
        listeners.clear();

        do {
            LockSupport.unpark(t);
        } while (pause);
    }

    @Override
    public void terminate() {
        if(listeners == null)
            return;

        while (!pause) {
            t.interrupt();
            LockSupport.parkNanos(1000L);
        }

        for (X key : actions) {
            key.key.cancel();
        }
        actions.clear();
        listeners.clear();
        listeners = null;

        while (pause) {
            LockSupport.unpark(t);
        }

    }

    public void register(Project proj) throws IOException {
        if(listeners == null)
            return;

        while (!pause) {
            t.interrupt();
            LockSupport.parkNanos(1000L);
        }

        X[] arr = listeners.get(proj.name);
        if(arr != null) {
            for (X x : arr)
                x.s.clear();
            return;
        }

        arr = new X[2];
        WatchKey key = proj.resource.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        }, ExtendedWatchEventModifier.FILE_TREE);
        actions.add(arr[0] = new X(proj.name, key, 0));
        key = proj.source.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        }, ExtendedWatchEventModifier.FILE_TREE);
        actions.add(arr[1] = new X(proj.name, key, 1));

        listeners.put(proj.name, arr);

        do {
            LockSupport.unpark(t);
            System.out.println("ProjectWatcher.asyncUnpause()");
        } while (pause);
    }
}
