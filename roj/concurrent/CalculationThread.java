package roj.concurrent;

import roj.concurrent.task.ITask;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: CalculationThread.java
 */
public class CalculationThread extends Thread {
    public final ITask task;

    public CalculationThread(ITask task) {
        setName("CalculationThread - " + task.hashCode());
        setDaemon(true);
        this.task = task;
    }

    @Override
    public void run() {
        Thread.yield();
        try {
            task.calculate(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
