package roj.net.ch;

import roj.concurrent.Shutdownable;
import roj.concurrent.TaskHandler;
import roj.concurrent.task.ITask;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Set;

/**
 * @author Roj233
 */
public class SelectorLoopExec extends SelectorLoop {
	final class Poller2 extends Poller {
		Poller2() throws IOException {}

		final void onSelected(SelectionKey key, SelectorLoop.Att att, Selectable t) {
			handler.pushTask(((Att2)att).getSelectTask());
		}

		final ITask _task(Att2 a) {
			return () -> super.onSelected(a.sk, a, a.s);
		}

		final void onTick(Set<SelectionKey> keys) {
			for (SelectionKey key : keys) handler.pushTask((ITask) key.attachment());
		}
	}

	TaskHandler handler;

	/**
	 * @param owner 关闭监听器
	 * @param prefix 线程名字前缀
	 * @param maxThreads 最大线程
	 * @param idleKill 选择器空置多久终止
	 */
	public SelectorLoopExec(Shutdownable owner, String prefix, int maxThreads, int idleKill, TaskHandler reqHandler) {
		super(owner, prefix, 1, 0, maxThreads, idleKill, 1000, true);
		this.handler = reqHandler;
	}

	final Att createAttachment() { return new Att2(); }
	final Poller createThread() throws IOException { return this.new Poller2(); }

	static final class Att2 extends Att implements ITask {
		private SelectionKey sk;
		private Poller2 t;
		private ITask st;

		final void setThread(Poller t) { this.t = (Poller2) t; }
		public final void execute() { t.accept(sk); }
		final ITask getSelectTask() {
			if (st == null) st = t._task(this);
			return st;
		}
	}
}
