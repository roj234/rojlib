package roj.mod;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.ui.CmdUtil;

import java.util.concurrent.locks.LockSupport;

import static roj.mod.Shared.DEBUG;

/**
 * @author solo6975
 * @since 2022/1/24 20:14
 */
final class AutoCompile extends Thread {
    static int Debounce;

    private static AutoCompile inst;
    private static long lastTime;
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
        if (inst != null && inst.enable)
            LockSupport.unpark(inst);
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
            if (inst.enable = prevEnable) {
                LockSupport.unpark(inst);
            }
        }
    }

    @Override
    public void run() {
        while (Shared.project == null) {
            if (DEBUG) System.out.println("[AC] 等待项目初始化");
            LockSupport.park();
        }
        ext:
        while (true) {
            LockSupport.park();
            if (!enable) {
                if (DEBUG) System.out.println("[AC] 关闭");
            } else {
                Project p = Shared.project;
                MyHashSet<String> set = Shared.watcher.getModified(p, IFileWatcher.ID_SRC);
                if (set.contains(null) || set.isEmpty()) {
                    if (set.contains(null))
                        if (DEBUG) System.out.println("[AC] 未注册监听器");
                } else {
                    if (Debounce > 0) {
                        tmp1.clear(); tmp1.addAll(set);

                        LockSupport.parkNanos(Debounce * 1_000_000L);
                        while (System.currentTimeMillis() < lastTime) {
                            if (!enable || p != Shared.project) continue ext;
                            LockSupport.parkUntil(lastTime);
                        }

                        set = Shared.watcher.getModified(Shared.project, IFileWatcher.ID_SRC);
                        tmp2.clear(); tmp2.addAll(set);
                    }
                    if (tmp1.equals(tmp2)) {
                        MyHashMap<String, Object> ojbk = new MyHashMap<>(4);
                        ojbk.put("zl", "");
                        try {
                            selfTrigger = true;
                            FMDMain.build(ojbk);
                            if (DEBUG) CmdUtil.success("[AC] Done");
                        } catch (Throwable e) {
                            CmdUtil.error("自动编译出错", e);
                        }
                        selfTrigger = false;
                        lastTime = System.currentTimeMillis() + Debounce;
                    }
                }
            }
        }
    }
}
