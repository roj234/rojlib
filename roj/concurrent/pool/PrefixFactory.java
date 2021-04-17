package roj.concurrent.pool;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/17 23:08
 */
public class PrefixFactory implements TaskPool.MyThreadFactory {
    final String prefix;
    final int stop;
    final AtomicInteger ai = new AtomicInteger(1);

    public PrefixFactory(String prefix, int stop) {
        this.prefix = prefix;
        this.stop = stop;
    }

    @Override
    public TaskExecutor get(TaskPool pool) {
        return new TaskExecutor(pool, prefix + '-' + ai.getAndIncrement(), stop);
    }
}
