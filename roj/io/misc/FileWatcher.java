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
package roj.io.misc;

import roj.collect.MyHashMap;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Consumer;

/**
 * File Change Watcher
 *
 * @author Roj233
 * @since 2021/7/17 19:01
 */
public class FileWatcher extends Thread {
    boolean runnable;
    MyHashMap<WatchKey, Consumer<Path>> actions;
    final WatchService watcher;

    public FileWatcher() throws IOException {
        setName("Filesystem Watcher");
        setDaemon(true);
        this.watcher = FileSystems.getDefault().newWatchService();
        runnable = true;
        actions = new MyHashMap<>();
    }

    public void close() {
        runnable = false;
        interrupt();
    }

    @Override
    public void run() {
        while (runnable) {
            try {
                WatchKey key = watcher.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    final WatchEvent<Path> path1 = (WatchEvent<Path>) event;

                    Consumer<Path> csm = actions.get(key);
                    Path file = path1.context();
                    //FMDMain.runAtMainThread(() -> {
                        csm.accept(file);
                    //});
                }

                // exit loop if the key is not valid (if the directory was deleted
                if (!key.reset()) {
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }

        try {
            watcher.close();
        } catch (IOException ignored) {}
    }

    public void register(Path path, Consumer<Path> action) throws IOException {
        WatchKey key = path.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY);
        actions.put(key, action);
    }

    public void register(Path path, Consumer<Path> action, WatchEvent.Kind<?> ... kinds) throws IOException {
        WatchKey key = path.register(watcher, kinds);
        actions.put(key, action);
    }
}
