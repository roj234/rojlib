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

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;

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

    MyHashMap<WatchKey, X> actions;
    final WatchService watcher;
    Thread t;
    Runnable cb;

    MyHashMap<String, X[]> registeredProjects;

    public ProjectWatcher(Path libPath, Runnable callback) throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        actions = new MyHashMap<>();
        registeredProjects = new MyHashMap<>();

        libPath.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                         StandardWatchEventKinds.ENTRY_MODIFY);
        cb = callback;

        Thread t = this.t = new Thread(this);
        t.setName("Filesystem Watcher");
        t.setDaemon(true);
        t.start();
    }

    public boolean paused() {
        synchronized (this) {
            return paused;
        }
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
                            s.clear();
                            s.add(null);
                            CmdUtil.warning("Filesystem event overflow");
                            break x;
                        }
                        case "ENTRY_MODIFY":
                        case "ENTRY_CREATE": {
                            @SuppressWarnings("unchecked")
                            String id = key.watchable().toString() + File.separatorChar + ((WatchEvent<Path>) event).context().toString();
                            switch (csm.x) {
                                case 0:
                                    if(!id.endsWith(".class"))
                                        break x;
                                    break;
                                case 2:
                                    if(!id.endsWith(".java"))
                                        break x;
                                    break;
                            }
                            s.add(id);
                            break;
                        }
                        case "ENTRY_DELETE": {
                            @SuppressWarnings("unchecked")
                            String id = key.watchable().toString() + File.separatorChar + ((WatchEvent<Path>) event).context().toString();
                            switch (csm.x) {
                                case 0:
                                    if(!id.endsWith(".class"))
                                        break x;
                                    break;
                                case 2:
                                    if(!id.endsWith(".java"))
                                        break x;
                                    break;
                            }
                            s.remove(id);
                            break;
                        }
                    }
                }

                // exit loop if the key is not valid (if the directory was deleted
                if (!key.reset()) {
                    X csm = actions.remove(key);
                    if(csm == null)
                        continue;
                    x:
                    for (Iterator<Map.Entry<String, X[]>> itr = registeredProjects.entrySet().iterator(); itr.hasNext(); ) {
                        Map.Entry<String, X[]> entry = itr.next();
                        for (X set : entry.getValue()) {
                            if (set == csm) {
                                itr.remove();
                                break x;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                synchronized (this) {
                    paused = true;
                    try {
                        wait();
                    } catch (InterruptedException ignored) {}
                    paused = false;
                }
            }
        }

        /*try {
            watcher.close();
        } catch (IOException ignored) {}*/
    }

    @Override
    public MyHashSet<String> getModified(Project proj, int id) {
        return registeredProjects.containsKey(proj.name) ? registeredProjects.get(proj.name)[id].s : super.getModified(proj, id);
    }

    public void reset() {
        synchronized (this) {
            if(!paused)
                throw new IllegalStateException("Should be paused");
            for(X x : actions.values()) {
                MyHashSet<String> s = x.s;
                s.clear();
                s.add(null);
            }
        }
    }

    public void pause(boolean paused) {
        synchronized (this) {
            if (this.paused != paused)
                t.interrupt();
        }
    }

    @Override
    public void terminate() {
        pause(true);
        for (WatchKey key : actions.keySet())
            key.cancel();
        actions.clear();
        registeredProjects.clear();
        registeredProjects = null;
        pause(false);
    }

    static final int ID_BIN = 0, ID_RES = 1, ID_SRC = 2;

    public void register(Project proj) throws IOException {
        if(registeredProjects == null)
            return;
        X[] arr = registeredProjects.get(proj.name);
        if(arr != null) {
            for (X x : arr)
                x.s.clear();
            return;
        }

        arr = new X[3];
        WatchKey key = proj.binary.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                                                             StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                                                             StandardWatchEventKinds.ENTRY_MODIFY
                                                     },
                                                     ExtendedWatchEventModifier.FILE_TREE);
        actions.put(key, arr[0] = new X(0));
        key = proj.resource.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                                                      StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                                                      StandardWatchEventKinds.ENTRY_MODIFY
                                              },
                                              ExtendedWatchEventModifier.FILE_TREE);
        actions.put(key, arr[1] = new X(1));
        key = proj.source.toPath().register(watcher, new WatchEvent.Kind<?>[]{
                                                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                                                    StandardWatchEventKinds.ENTRY_MODIFY
                                            },
                                            ExtendedWatchEventModifier.FILE_TREE);
        actions.put(key, arr[2] = new X(2));
        registeredProjects.put(proj.name, arr);
    }
}
