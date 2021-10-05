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
import roj.collect.HashBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.ui.CmdUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

/**
 * Project file change watcher
 *
 * @author Roj233
 * @since 2021/7/17 19:01
 */
public final class ProjectWatcher extends IProjectWatcher implements Runnable {
    static final class X {
        MyHashSet<String> s = new MyHashSet<>();
        byte x;

        public X(int tag) {
            this.x = (byte) tag;
        }
    }

    HashBiMap<WatchKey, X> actions;
    final WatchService watcher;
    Thread t;
    Runnable cb;

    MyHashMap<String, X[]> registeredProjects;

    public ProjectWatcher(Path libPath, Runnable callback) throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        actions = new HashBiMap<>();
        registeredProjects = new MyHashMap<>();

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
        while (registeredProjects != null) {
            try {
                WatchKey key = watcher.take();

                x:
                for (WatchEvent<?> event : key.pollEvents()) {
                    X csm = actions.get(key);
                    if(csm == null) {
                        cb.run();
                        break;
                    }
                    MyHashSet<String> s = csm.s;
                    String name = event.kind().name();
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

                // exit loop if the key is not valid (if the directory was deleted
                if (!key.reset()) {
                    key.cancel();
                    X csm = actions.remove(key);
                    if(csm == null)
                        continue;
                    x:
                    for (Iterator<Map.Entry<String, X[]>> itr = registeredProjects.entrySet().iterator(); itr.hasNext(); ) {
                        Map.Entry<String, X[]> entry = itr.next();
                        for (X set : entry.getValue()) {
                            if (set == csm) {
                                for (X set1 : entry.getValue()) {
                                    if(set1 != csm)
                                        actions.removeByValue(set1).cancel();
                                }
                                itr.remove();
                                break x;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        try {
            watcher.close();
        } catch (IOException ignored) {}
    }

    @Override
    public MyHashSet<String> getModified(Project proj, int id) {
        if(registeredProjects == null || !registeredProjects.containsKey(proj.name))
            return super.getModified(proj, id);
        return registeredProjects.get(proj.name)[id].s;
    }

    public void reset() {
        if(registeredProjects == null)
            return;
        synchronized (this) {
            t.interrupt();
            LockSupport.parkNanos(100);
            for (WatchKey key : actions.keySet()) {
                if(key != null)
                    key.cancel();
            }
            actions.clear();
            registeredProjects.clear();
        }
    }

    @Override
    public void terminate() {
        if(registeredProjects == null)
            return;
        reset();
        registeredProjects = null;
    }

    public void register(Project proj) throws IOException {
        if(registeredProjects == null)
            return;
        X[] arr = registeredProjects.get(proj.name);
        if(arr != null) {
            for (X x : arr)
                x.s.clear();
            return;
        }

        arr = new X[2];
        WatchKey key = proj.resource.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                                                      StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                                                      StandardWatchEventKinds.ENTRY_MODIFY
                                              },
                                              ExtendedWatchEventModifier.FILE_TREE);
        actions.put(key, arr[0] = new X(0));
        key = proj.source.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                                                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                                                    StandardWatchEventKinds.ENTRY_MODIFY
                                            },
                                            ExtendedWatchEventModifier.FILE_TREE);
        actions.put(key, arr[1] = new X(1));
        registeredProjects.put(proj.name, arr);
        if(registeredProjects.size() == 1) {
            synchronized (this) {
                notify();
            }
        }
    }
}
