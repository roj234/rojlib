package roj.concurrent;

import roj.reflect.ReflectionUtils;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/3/6 2:07
 */
public class LoopTask implements Task {
	protected final Scheduler sched;
	protected final Task task;

	protected final long interval;
	protected int repeat;

	private static final long STATE_OFFSET = ReflectionUtils.fieldOffset(LoopTask.class, "state");
	/**
	 * -1: 任务取消
	 *  0: '确切时间'模式
	 *  1: '自动延迟'模式 - 等待执行
	 *  2: '自动延迟'模式 - 允许执行
	 *  3: '自动延迟'模式 - 正在执行
	 *  4: '自动延迟'模式 - 超时
	 */
	private volatile int state;

	public LoopTask(Scheduler sched, Task task, long interval, int repeat, boolean slowTaskProof) {
		if (interval <= 0) throw new IllegalArgumentException("interval <= 0");
		this.sched = sched;
		this.task = task;
		this.interval = interval;
		this.repeat = repeat;
		this.state = slowTaskProof ? 1 : 0;
	}

	@Override
	public void execute() throws Exception {
		var state = this.state;
		if (state == 0) {
			task.execute();
		} else if ((state&1) == 0) {
			// state 3只是防止重复执行(以及给toString看的，可以和4合并)
			U.compareAndSwapInt(this, STATE_OFFSET, 2, 3);
			try {
				task.execute();
			} finally {
				// 任务执行时间超过一个周期，在完成之后重新添加
				if (U.getAndSetInt(this, STATE_OFFSET, 1) == 4)
					sched.delay(this, interval);
			}
		}
	}

	/**
	 * 返回0代表不执行第二次
	 * 返回-1代表这次也不执行
	 */
	@SuppressWarnings("fallthrough")
	public long getNextRun() {
		loop: for(;;) {
			var state = this.state;
			switch (state) {
				case 2, 3:
					if (!U.compareAndSwapInt(this, STATE_OFFSET, state, 4)) break;
				case 4:
				default: return -1;
				case 1:
					if (!U.compareAndSwapInt(this, STATE_OFFSET, 1, 2)) break;
				case 0: break loop;
			}
		}

		return --repeat == 0 ? 0 : interval;
	}

	@Override public boolean cancel() {
		int state = U.getAndSetInt(this, STATE_OFFSET, -1);
		return state < 0 | task.cancel();
	}
	@Override public boolean isCancelled() {return (state & 1) != 0;}

	@Override public String toString() {
		return "LoopTask{interval=" + interval + ", repeat=" + repeat + ", [" + switch (state) {
			case 0 -> "FixedInterval";
			case 1 -> "FixedDelay - Pending";
			case 2 -> "FixedDelay - Ready";
			case 3 -> "FixedDelay - Executing";
			case 4 -> "FixedDelay - Delayed";
			default -> "Cancelled";
		} + "], task="+task+'}';
	}
}