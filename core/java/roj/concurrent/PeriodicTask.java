package roj.concurrent;

import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;

import java.lang.invoke.VarHandle;

/**
 * @author Roj234
 * @since 2024/3/6 2:07
 */
@FastVarHandle
public class PeriodicTask implements Runnable, Cancellable {
	protected TimerTask handle;
	protected final Runnable task;

	protected final long period;
	protected int repeat;

	private static final VarHandle STATE = Handles.lookup().findVarHandle(PeriodicTask.class, "state", int.class);
	/**
	 * -1: 任务取消
	 *  0: '固定频率'模式
	 *  1: '固定延迟'模式 - 等待中
	 *  2: '固定延迟'模式 - 已提交
	 *  3: '固定延迟'模式 - 正在执行
	 *  4: '固定延迟'模式 - 执行超时
	 */
	private volatile int state;

	public PeriodicTask(Runnable task, long period, int repeat, boolean fixedDelay) {
		if (period <= 0) throw new IllegalArgumentException("Non-positive period.");
		this.task = task;
		this.period = period;
		this.repeat = repeat <= 0 ? -1 : repeat;
		this.state = fixedDelay ? 1 : 0;
	}
	protected void setHandle(TimerTask handle) {this.handle = handle;}

	@Override
	public void run() {
		var state = this.state;
		if (state == 0) {
			task.run();
		} else if ((state&1) == 0) {
			STATE.compareAndSet(this, 2, 3);
			try {
				task.run();
			} finally {
				while (true) {
					state = this.state;
					if (state < 0 || STATE.compareAndSet(this, state, 1)) {
						// 任务执行时间超过一个周期，在完成之后重新添加
						if (state == 4) handle.reschedule(period);
						break;
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
				case 2, 3: if (!STATE.compareAndSet(this, state, 4)) break;
				case 4: default: return -1;
				case 1: if (!STATE.compareAndSet(this, 1, 2)) break;
				case 0: break loop;
			}
		}

		return repeat > 0 && --repeat == 0 ? 0 : period;
	}

	@Override public boolean cancel(boolean mayInterruptIfRunning) {
		int state = (int) STATE.getAndSet(this, -1);
		return state >= 0 || task instanceof Cancellable cancellable && cancellable.cancel(mayInterruptIfRunning);
	}
	@Override public boolean isCancelled() {return (state & 1) != 0;}

	@Override public String toString() {
		return "PeriodicTask{"+switch (state) {
			case 0 -> "FixedRate";
			case 1 -> "FixedDelay/Pending";
			case 2 -> "FixedDelay/Ready";
			case 3 -> "FixedDelay/Executing";
			case 4 -> "FixedDelay/Delayed";
			default -> "Cancelled";
		}+", period="+period+", repeat="+(repeat<0?"inf":repeat)+", task="+task+'}';
	}
}