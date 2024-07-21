package roj.plugins.ci;

import roj.collect.MyHashMap;
import roj.concurrent.timing.ScheduleTask;
import roj.ui.Terminal;

import static roj.plugins.ci.Shared.*;

/**
 * @author solo6975
 * @since 2022/1/24 20:14
 */
final class AutoCompile {
	private static volatile boolean idle;
	private static boolean cancelled;
	private static int delay;
	private static ScheduleTask task;

	static void setEnabled(int debounce) {
		assert task == null;
		idle = true;
		delay = debounce;
	}

	static void notifyUpdate() {
		if (!idle) return;
		if (task != null) task.cancel();
		task = PeriodicTask.delay(AutoCompile::check, delay);
	}

	static void beforeCompile() {
		if (idle && task != null && !task.isExpired()) {
			task.cancel();
			cancelled = true;
		}
	}
	static void afterCompile(int ok) {
		if (cancelled) {
			cancelled = false;
			if (ok > 0) Task.submit(AutoCompile::check);
		}
	}

	private static void check() {
		Project p = project;
		if (p == null) return;
		idle = false;
		try {
			block:
			if (!isDirty(p)) {
				for (Project p1 : p.getAllDependencies()) {
					if (isDirty(p1)) break block;
				}
				return;
			}

			java.util.Map<String, Object> args = new MyHashMap<>();
			args.put("zl", "");
			FMDMain.build(args);
		} catch (Throwable e) {
			Terminal.error("自动编译出错", e);
		} finally {
			idle = true;
		}
	}

	private static boolean isDirty(Project p) {
		var modified = Shared.watcher.getModified(p, IFileWatcher.ID_SRC);
		if (modified.contains(null) || modified.isEmpty()) {
			if (modified.contains(null)) if (DEBUG) System.out.println("[AC] 未注册监听器");
			return false;
		} else {
			return true;
		}
	}
}