package roj.asm.remapper.util;

import java.util.List;
import java.util.function.Consumer;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: Worker.java
 */
public final class Worker extends Thread {
    private final List<Context> files;
    private final Consumer<Context> consumer;

    public Worker(List<Context> files, Consumer<Context> consumer, String name) {
        setName(name);
        setDaemon(true);
        this.files = files;
        this.consumer = consumer;
    }

    public void run() {
        for (int i = 0; i < files.size(); i++) {
            consumer.accept(files.get(i));
        }
    }
}
