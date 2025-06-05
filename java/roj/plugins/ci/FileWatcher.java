package roj.plugins.ci;

import com.sun.nio.file.ExtendedWatchEventModifier;
import roj.collect.*;
import roj.io.IOUtil;
import roj.plugins.ci.event.LibraryModifiedEvent;
import roj.ui.Terminal;

import java.io.File;
import java.io.IOException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;
import static roj.asmx.mapper.Mapper.DONT_LOAD_PREFIX;
import static roj.plugins.ci.FMD.BASE;

/**
 * Project file change watcher
 *
 * @author Roj233
 * @since 2021/7/17 19:01
 */
final class FileWatcher extends IFileWatcher implements Consumer<WatchKey> {
	private static final class X {
		private Object _next;

		WatchKey key;
		final Project owner;
		final byte no;
		final MyHashSet<String> mod = new MyHashSet<>();
		final MyHashSet<String> del = new MyHashSet<>();

		X(Project owner, WatchKey key, int tag) {
			this.owner = owner;
			this.key = key;
			this.no = (byte) tag;
		}

		X() {
			this.owner = null;
			this.no = 0;
		}
	}

	private final X via;
	private final XashMap<WatchKey, X> actions;
	private final WatchService watcher;
	private final String libPath;

	private final MyHashMap<String, X[]> listeners;

	public FileWatcher() throws IOException {
		watcher = IOUtil.syncWatchPoll("文件修改监控", this);
		XashMap.Builder<WatchKey, X> builder = XashMap.noCreation(X.class, "key", "_next", Hasher.identity());
		actions = builder.create();
		listeners = new MyHashMap<>();
		via = new X();
		actions.add(via);

		File lib = new File(BASE, "libs");
		lib.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
		this.libPath = lib.getAbsolutePath();
	}

	@Override
	public void accept(WatchKey key) {
		X handler = actions.get(key);
		if (handler == null || handler == via) {
			for (WatchEvent<?> event : key.pollEvents()) {
				if (!event.kind().name().equals("OVERFLOW")) {
					String path = key.watchable().toString();
					String name = event.context().toString().toLowerCase();
					if (path.startsWith(libPath)) {
						if (!name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"))) {
							FMD.EVENT_BUS.post(new LibraryModifiedEvent(null));
							break;
						}
					}
				}
			}

			if (!key.reset()) Terminal.warning("[watcher reset failed]libs没了？");
			return;
		}

		boolean isOverflow = false;
		loop:
		for (WatchEvent<?> event : key.pollEvents()) {
			String name = event.kind().name();
			MyHashSet<String> s = handler.mod;
			switch (name) {
				case "OVERFLOW": {
					Terminal.error("[PW]更改的文件过多，已暂停自动编译 "+key.watchable());
					isOverflow = true;
					break loop;
				}
				case "ENTRY_MODIFY", "ENTRY_CREATE", "ENTRY_DELETE": {
					String id = key.watchable().toString()+File.separatorChar+event.context();
					if (new File(id).isDirectory()) break;
					if (handler.no == ID_LIB) {
						FMD.EVENT_BUS.post(new LibraryModifiedEvent(handler.owner));
						break loop;
					} else {
						if (handler.no == ID_SRC && !id.endsWith(".java")) continue;

						synchronized (s) {
							s.add(id);
						}
					}
				}
			}
		}

		// The key is not valid (directory was deleted
		if (isOverflow || !key.reset()) {
			X[] remove = listeners.remove(handler.owner);
			if (remove != null) {
				for (X set : remove) {
					actions.remove(set);
					FMD.EXECUTOR.submit(set.key::cancel);
					synchronized (set.mod) {
						set.mod.clear();
						set.mod.add(null);
						set.del.clear();
					}
				}
			}
		} else {
			handler.owner.fileChanged();
		}
	}

	@Override
	public Set<String> getModified(Project proj, int id) {
		if (!listeners.containsKey(proj.name)) return super.getModified(proj, id);
		return listeners.get(proj.name)[id].mod;
	}

	public void removeAll() {
		for (X listener : new SimpleList<>(actions)) {
			var key1 = listener.key;
			if (key1 != null) key1.cancel();
		}
		actions.clear();
		listeners.clear();
	}

	public void add(Project proj) throws IOException {
		X[] arr = listeners.get(proj.name);
		if (arr != null) {
			for (X x : arr) {
				x.mod.clear();
				x.del.clear();
			}
		} else {
			arr = new X[3];
			WatchEvent.Kind<?>[] ALL_KIND = {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE, OVERFLOW};
			WatchKey key = proj.resPath.toPath().register(watcher, ALL_KIND, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[0] = new X(proj, key, ID_RES));
			key = proj.srcPath.toPath().register(watcher, ALL_KIND, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[1] = new X(proj, key, ID_SRC));
			key = proj.libPath.toPath().register(watcher, ALL_KIND, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[2] = new X(proj, key, ID_LIB));

			listeners.put(proj.name, arr);
		}
	}

	@Override
	public void remove(Project proj) {
		X[] arr = listeners.remove(proj.name);
		if (arr != null) {
			for (X x : arr)
				x.key.cancel();
		}
	}
}