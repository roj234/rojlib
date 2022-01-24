package roj.mod;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.ui.CmdUtil;

import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

/**
 * @author solo6975
 * @since 2022/1/24 20:14
 */
final class AutoCompile extends Thread {
    static AutoCompile inst;
    public static void setEnabled(boolean enabled) {
        if (enabled && inst == null) {
            inst = new AutoCompile();
        }
        inst.enable = enabled;
        LockSupport.unpark(inst);
    }

    private AutoCompile() {
        setName("Auto compiler");
        setDaemon(true);
        start();
        tmp1 = new SimpleList<>(100);
        tmp2 = new SimpleList<>(100);
    }

    private final SimpleList<String> tmp1, tmp2;
    private boolean enable;

    @Override
    public void run() {
        while (true) {
            if (!enable) {
                LockSupport.park(this);
            } else {
                MyHashSet<String> set = Shared.watcher.getModified(Shared.currentProject, IProjectWatcher.ID_SRC);
                if (set.contains(null)) {
                    enable = false;
                } else {
                    tmp1.clear();
                    tmp1.addAll(set);
                    LockSupport.parkNanos(200_000_000L);
                    set = Shared.watcher.getModified(Shared.currentProject, IProjectWatcher.ID_SRC);
                    tmp2.clear();
                    tmp2.addAll(set);
                    if (tmp1.equals(tmp2)) {
                        MyHashMap<String, Object> ojbk = new MyHashMap<>(4);
                        ojbk.put("zl", "");
                        try {
                            FMDMain.build(ojbk);
                        } catch (IOException | InterruptedException e) {
                            CmdUtil.error("自动编译出错", e);
                        }
                    }
                }
            }
        }
    }
}
