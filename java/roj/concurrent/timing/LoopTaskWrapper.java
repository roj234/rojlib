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

	private static final long STATE_OFFSET = ReflectionUtils.fieldOffset(LoopTaskWrapper.class, "state");
	/**
	 * -1: 任务取消
	 *  0: '确切时间'模式
	 *  1: '自动延迟'模式 - 等待执行
	 *  2: '自动延迟'模式 - 允许执行
	 *  3: '自动延迟'模式 - 正在执行
	 *  4: '自动延迟'模式 - 超时
	 */
	private volatile int state;

	public LoopTaskWrapper(Scheduler sched, ITask task, long interval, int repeat, boolean slowTaskProof) {
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
		if (this.state == 0) {
			task.execute();
		} else if ((state&1) == 0) {
			if (u.compareAndSwapInt(this, STATE_OFFSET, state, 3)) {
				try {
					task.execute();
				} finally {
					// 任务执行时间超过一个周期，在完成之后重新添加
					if (!u.compareAndSwapInt(this, STATE_OFFSET, 3, 1) || state != 2) {
						int v;
						while (true) {
							v = u.getIntVolatile(this, STATE_OFFSET);
							if (v <= 0) break;
							if (u.compareAndSwapInt(this, STATE_OFFSET, v, 1)) {
								// re-schedule
								sched.delay(this, interval);
								break;
							}
						}
					}
				}
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
					if (!u.compareAndSwapInt(this, STATE_OFFSET, state, 4)) break;
				case 4: return -1;
				default: return 0;
				case 1:
					if (!u.compareAndSwapInt(this, STATE_OFFSET, 1, 2)) break;
				case 0: break loop;
			}
		}

		return --repeat == 0 ? 0 : interval;
	}

	@Override public boolean cancel() {
		int state = u.getAndSetInt(this, STATE_OFFSET, -1);
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