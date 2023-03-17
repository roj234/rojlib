package roj.mod;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.ui.CmdUtil;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

import static roj.mod.Shared.DEBUG;

/**
 * @author solo6975
 * @since 2022/1/24 20:14
 */
final class AutoCompile extends Thread {
	static int Debounce;

	private static AutoCompile inst;
	private static boolean prevEnable;

	static void setEnabled(boolean enabled) {
		if (enabled && inst == null) {
			inst = new AutoCompile();
		}
		if (inst == null) return;
		inst.enable = enabled;
		LockSupport.unpark(inst);
	}

	public static void notifyIt() {
		if (inst != null && inst.enable) LockSupport.unpark(inst);
	}

	private AutoCompile() {
		setName("自动编译");
		setDaemon(true);
		start();
		tmp1 = new SimpleList<>(100);
		tmp2 = new SimpleList<>(100);
	}

	private final SimpleList<String> tmp1, tmp2;
	private boolean enable, selfTrigger;

	static void beforeCompile() {
		if (inst == null) return;
		if (!inst.selfTrigger) {
			if (prevEnable = inst.enable) {
				inst.enable = false;
				LockSupport.unpark(inst);
			}
		}
	}

	static void afterCompile(int v) {
		if (inst == null) return;
		if (!inst.selfTrigger) {
			inst.enable = prevEnable;
		}
	}

	@Override
	public void run() {
		while (Shared.project == null) {
			if (DEBUG) System.out.println("[AC] 等待项目初始化");
			LockSupport.park();
		}

		while (true) {
			LockSupport.park();
			if (!enable) {
				if (DEBUG) System.out.println("[AC] 关闭");
			} else {
				Project p = Shared.project;
				List<Project> dependencies = p.getAllDependencies();
				if (dependencies.isEmpty()) {
					checkAndCompile(p);
				} else {
					dependencies.add(p);
					for (int i = dependencies.size() - 1; i >= 0; i--) {
						if (checkAndCompile(dependencies.get(i))) {
							break;
						}
					}
				}
			}
		}
	}

	private boolean checkAndCompile(Project p) {
		MyHashSet<String> set = Shared.watcher.getModified(p, IFileWatcher.ID_SRC);
		if (set.contains(null) || set.isEmpty()) {
			if (set.contains(null)) if (DEBUG) System.out.println("[AC] 未注册监听器");
			return false;
		} else {
			if (Debounce > 0) {
				tmp1.clear();
				tmp1.addAll(set);

				long lastTime = System.currentTimeMillis() + Debounce;
				do {
					if (!enable) return true;
					LockSupport.parkUntil(lastTime);
				} while (System.currentTimeMillis() < lastTime);

				set = Shared.watcher.getModified(p, IFileWatcher.ID_SRC);
				tmp2.clear();
				tmp2.addAll(set);
				if (!tmp1.equals(tmp2)) {
					LockSupport.unpark(this);
					return true;
				}
			}

			MyHashMap<String, Object> ojbk = new MyHashMap<>(4);
			ojbk.put("zl", "");
			ojbk.put("forcezl", "");
			try {
				selfTrigger = true;
				FMDMain.build(ojbk);
				if (DEBUG) CmdUtil.success("[AC] Done");
			} catch (Throwable e) {
				CmdUtil.error("自动编译出错", e);
				enable = false;
			}
			selfTrigger = false;
		}
		return true;
	}
}
