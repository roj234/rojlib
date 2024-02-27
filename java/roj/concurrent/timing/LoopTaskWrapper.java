package roj.concurrent.timing;

import roj.concurrent.task.ITask;
import roj.reflect.ReflectionUtils;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2024/3/6 0006 2:07
 */
public class LoopTaskWrapper implements ITask {
	protected final Scheduler sched;
	protected final ITask task;

	protected final long interval;
	protected int repeat;

	private static final long STATE_OFFSET = ReflectionUtils.fieldOffset(LoopTaskWrapper.class, "taskState");
	private volatile int taskState;
	//CANCELLED = -1, ACCURATE_TIME = 0, AUTODELAY_READY = 1, AUTODELAY_RUNNING = 2, AUTODELAY_DELAYED = 3

	public LoopTaskWrapper(Scheduler sched, ITask task, long interval, int repeat, boolean slowTaskProof) {
		if (interval <= 0) throw new IllegalArgumentException("interval <= 0");
		this.sched = sched;
		this.task = task;
		this.interval = interval;
		this.repeat = repeat;
		this.taskState = slowTaskProof ? 1 : 0;
	}

	@Override
	public void execute() throws Exception {
		switch (taskState) {
			case 0: task.execute(); break; // ACCURATE_TIMER
			case 1:
				if (u.compareAndSwapInt(this, STATE_OFFSET, 1, 2)) {
					try {
						task.execute();
					} finally {
						// 任务执行时间超过一个周期，在完成之后重新添加
						if (u.compareAndSwapInt(this, STATE_OFFSET, 3, 1)) {
							// re-schedule
							sched.delay(this, interval);
						} else {
							u.compareAndSwapInt(this, STATE_OFFSET, 2, 1);
						}
					}
				}
				break;
		}
	}

	@Override
	public boolean cancel() {
		int state = u.getAndSetInt(this, STATE_OFFSET, -1);
		return state < 0 || task.cancel();
	}

	public long getNextRun() {
		if (u.compareAndSwapInt(this, STATE_OFFSET, 2, 3) || taskState < 0) return 0;
		return --repeat == 0 ? 0 : interval;
	}
}