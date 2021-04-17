package roj.concurrent.task;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: ITask.java
 */
public interface ITask {
    boolean isCancelled();

    boolean cancel(boolean force);

    void calculate(Thread thread) throws Exception;

    boolean isDone();

    default void onJoin() {}

    default boolean continueExecuting() {
        return false;
    }
}
