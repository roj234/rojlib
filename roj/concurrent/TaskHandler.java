package roj.concurrent;

import roj.concurrent.task.ITask;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/11/30 23:07
 */
public interface TaskHandler {
    void pushTask(ITask task);

    void clearTasks();
}
