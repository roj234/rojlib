package roj.concurrent.task;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/12/19 23:03
 */
public interface ITaskUncancelable extends ITask {
    @Override
    default boolean isCancelled() {
        return false;
    }

    @Override
    default boolean cancel(boolean force) {
        return false;
    }
}
