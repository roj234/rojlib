package roj.concurrent;

import roj.concurrent.pool.TaskPool;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/1/1 18:37
 */
public final class SharedThreads {
    public static final TaskPool CPU_POOL = new TaskPool(2, Runtime.getRuntime().availableProcessors(), 32, 30000, "cpu_worker");
    public static final TaskPool IO_POOL = new TaskPool(0, Runtime.getRuntime().availableProcessors() * 2, 32, 30000, "io_worker");
}
