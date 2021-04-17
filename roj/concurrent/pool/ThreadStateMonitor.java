package roj.concurrent.pool;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/24 22:56
 */
public interface ThreadStateMonitor {
    boolean threadDeath(TaskExecutor executor);

    boolean working();
}
