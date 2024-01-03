package roj.mod;

import com.sun.nio.file.ExtendedWatchEventModifier;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.concurrent.timing.ScheduleTask;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import static java.nio.file.StandardWatchEventKinds.*;
import static roj.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.mod.Shared.BASE;
import static roj.mod.Shared.DEBUG;

/**
 * Project file change watcher
 *
 * @author Roj233
 * @since 2021/7/17 19:01
 */
final class FileWatcher extends IFileWatcher implements Runnable {
	private static final class X {
		WatchKey key;
		final String owner;
		final byte x;
		final MyHashSet<String> s = new MyHashSet<>();

		X(String owner, WatchKey key, int tag) {
			this.owner = owner;
			this.key = key;
			this.x = (byte) tag;
		}

		X() {
			this.owner = "";
			this.x = 0;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			X x = (X) o;

			return key == x.key;
		}

		@Override
		public int hashCode() {
			return key.hashCode();
		}
	}

	private final X via;
	private final MyHashSet<X> actions;
	private final WatchService watcher;
	private final Thread t;
	private volatile boolean pause;
	private final String libPath;
	private ScheduleTask reloadMapTask;

	private MyHashMap<String, X[]> listeners;

	public FileWatcher() throws IOException {
		watcher = FileSystems.getDefault().newWatchService();
		actions = new MyHashSet<>();
		listeners = new MyHashMap<>();
		via = new X();

		File lib = new File(BASE, "/class/");
		lib.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
		this.libPath = lib.getAbsolutePath();

		Thread t = this.t = new Thread(this);
		t.setName("文件修改监控");
		t.setDaemon(true);
		t.start();
	}

	@Override
	public void run() {
		while (listeners != null) {
			WatchKey key;
			try {
				key = watcher.take();
			} catch (InterruptedException e) {
				pause = true;
				do {
					LockSupport.park();
				} while (pause);
				continue;
			}

			via.key = key;
			X csm = actions.find(via);
			if (csm == via) {
				for (WatchEvent<?> event : key.pollEvents()) {
					if (!event.kind().name().equals("OVERFLOW")) {
						String path = key.watchable().toString();
						String name = event.context().toString().toLowerCase();
						if (path.startsWith(libPath)) {
							if (!name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"))) {

								if (reloadMapTask != null) reloadMapTask.cancel();
								reloadMapTask = Shared.PeriodicTask.delay(() -> {
									if (DEBUG) CLIUtil.warning("清除映射表缓存");
									Shared.mapperFwd.clear();
									ATHelper.close();
								}, 2000);

								break;
							}
						}
					}
				}

				if (!key.reset()) {
					CLIUtil.warning("[watcher reset failed]这不应当发生");
					key.cancel();
				}
				continue;
			}

			x:
			for (WatchEvent<?> event : key.pollEvents()) {
				String name = event.kind().name();
				MyHashSet<String> s = csm.s;
				switch (name) {
					case "OVERFLOW": {
						CLIUtil.error("[PW]Event overflow "+key.watchable());
						key.cancel();
						break x;
					}
					case "ENTRY_MODIFY":
					case "ENTRY_CREATE": {
						String id = key.watchable().toString()+File.separatorChar+event.context();
						if (new File(id).isDirectory()) break;
						if (csm.x == ID_SRC) {
							if (!id.endsWith(".java")) break x;
						}
						synchronized (s) { s.add(id); }
						break;
					}
				}
			}

			// The key is not valid (directory was deleted
			if (!key.reset()) {
				key.cancel();
				if (csm != null) {
					X[] remove = listeners.remove(csm.owner);
					if (remove != null) {
						for (X set : remove) {
							actions.remove(set);
							set.key.cancel();
							synchronized (set.s) {
								set.s.clear();
								set.s.add(null);
							}
						}
					}
				}
			} else {
				AutoCompile.notifyIt();
			}
		}

		try {
			watcher.close();
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	@Override
	public Set<String> getModified(Project proj, int id) {
		if (listeners == null || !listeners.containsKey(proj.name)) return super.getModified(proj, id);
		return listeners.get(proj.name)[id].s;
	}

	public void removeAll() {
		if (listeners == null) return;

		while (!pause) {
			t.interrupt();
			LockSupport.parkNanos(1000L);
		}

		for (X key : actions) {
			key.key.cancel();
		}
		actions.clear();
		listeners.clear();

		pause = false;
		LockSupport.unpark(t);
	}

	@Override
	public void terminate() {
		if (listeners == null) return;

		while (!pause) {
			t.interrupt();
			LockSupport.parkNanos(1000L);
		}

		for (X key : actions) {
			key.key.cancel();
		}
		actions.clear();
		listeners.clear();
		listeners = null;

		pause = false;
		LockSupport.unpark(t);
	}

	public void register(Project proj) throws IOException {
		if (listeners == null) return;

		while (!pause) {
			t.interrupt();
			LockSupport.parkNanos(1000L);
		}

		X[] arr = listeners.get(proj.name);
		if (arr != null) {
			for (X x : arr)
				x.s.clear();
		} else {
			arr = new X[2];
			WatchKey key = proj.resPath.toPath().register(watcher, new WatchEvent.Kind<?>[] {ENTRY_CREATE, ENTRY_MODIFY, OVERFLOW}, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[0] = new X(proj.name, key, ID_RES));
			key = proj.srcPath.toPath().register(watcher, new WatchEvent.Kind<?>[] {ENTRY_CREATE, ENTRY_MODIFY, OVERFLOW}, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[1] = new X(proj.name, key, ID_SRC));

			listeners.put(proj.name, arr);
		}

		pause = false;
		LockSupport.unpark(t);
	}
}