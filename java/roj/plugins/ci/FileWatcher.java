package roj.plugins.ci;

import com.sun.nio.file.ExtendedWatchEventModifier;
import roj.collect.*;
import roj.io.NIOUtil;
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
import static roj.plugins.ci.Shared.BASE;

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
		final String owner;
		final byte no;
		final MyHashSet<String> mod = new MyHashSet<>();

		X(String owner, WatchKey key, int tag) {
			this.owner = owner;
			this.key = key;
			this.no = (byte) tag;
		}

		X() {
			this.owner = "";
			this.no = 0;
		}
	}

	private final X via;
	private final XHashSet<WatchKey, X> actions;
	private final WatchService watcher;
	private final String libPath;

	private final MyHashMap<String, X[]> listeners;

	public FileWatcher() throws IOException {
		watcher = NIOUtil.syncWatchPoll("文件修改监控", this);
		XHashSet.Shape<WatchKey, X> shape = XHashSet.noCreation(X.class, "key", "_next", Hasher.identity());
		actions = shape.create();
		listeners = new MyHashMap<>();
		via = new X();
		actions.add(via);

		File lib = new File(BASE, "class");
		lib.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
		this.libPath = lib.getAbsolutePath();
	}

	@Override
	public void accept(WatchKey key) {
		X csm = actions.get(key);
		if (csm == null || csm == via) {
			for (WatchEvent<?> event : key.pollEvents()) {
				if (!event.kind().name().equals("OVERFLOW")) {
					String path = key.watchable().toString();
					String name = event.context().toString().toLowerCase();
					if (path.startsWith(libPath)) {
						if (!name.startsWith(DONT_LOAD_PREFIX) && (name.endsWith(".zip") || name.endsWith(".jar"))) {
							Shared.mappingIsClean = false;
							break;
						}
					}
				}
			}

			if (!key.reset()) Terminal.warning("[watcher reset failed]class没了？");
			return;
		}

		boolean isOverflow = false;
		x:
		for (WatchEvent<?> event : key.pollEvents()) {
			String name = event.kind().name();
			MyHashSet<String> s = csm.mod;
			switch (name) {
				case "OVERFLOW": {
					Terminal.error("[PW]更改的文件过多，已暂停自动编译 "+key.watchable());
					isOverflow = true;
					break x;
				}
				case "ENTRY_MODIFY":
				case "ENTRY_CREATE":
				case "ENTRY_DELETE": {
					String id = key.watchable().toString() + File.separatorChar + event.context();
					if (new File(id).isDirectory()) break;
					if (csm.no == ID_SRC) {
						if (!id.endsWith(".java")) break x;
					}
					synchronized (s) {
						if (name.equals("ENTRY_DELETE")) {
							s.remove(id);
						} else {
							s.add(id);
						}
					}
					break;
				}
			}
		}

		// The key is not valid (directory was deleted
		if (isOverflow || !key.reset()) {
			X[] remove = listeners.remove(csm.owner);
			if (remove != null) {
				for (X set : remove) {
					actions.remove(set);
					Shared.Task.submit(set.key::cancel);
					synchronized (set.mod) {
						set.mod.clear();
						set.mod.add(null);
					}
				}
			}
		} else {
			AutoCompile.notifyUpdate();
		}
	}

	@Override
	public Set<String> getModified(Project proj, int id) {
		if (!listeners.containsKey(proj.name)) return super.getModified(proj, id);
		return listeners.get(proj.name)[id].mod;
	}

	public void removeAll() {
		for (X key : new SimpleList<>(actions)) key.key.cancel();
		actions.clear();
		listeners.clear();
	}

	public void register(Project proj) throws IOException {
		X[] arr = listeners.get(proj.name);
		if (arr != null) {
			for (X x : arr)
				x.mod.clear();
		} else {
			arr = new X[2];
			WatchKey key = proj.resPath.toPath().register(watcher, new WatchEvent.Kind<?>[] {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE, OVERFLOW}, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[0] = new X(proj.name, key, ID_RES));
			key = proj.srcPath.toPath().register(watcher, new WatchEvent.Kind<?>[] {ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE, OVERFLOW}, ExtendedWatchEventModifier.FILE_TREE);
			actions.add(arr[1] = new X(proj.name, key, ID_SRC));

			listeners.put(proj.name, arr);
		}
	}
}