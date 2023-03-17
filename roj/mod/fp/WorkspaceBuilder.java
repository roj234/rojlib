package roj.mod.fp;

import roj.archive.zip.ZipArchive;
import roj.collect.IntMap;
import roj.collect.TrieTree;
import roj.concurrent.task.ITask;
import roj.mapper.ConstMapper;
import roj.mod.Shared;
import roj.mod.mapping.MappingFormat;
import roj.ui.CmdUtil;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static roj.mod.Shared.BASE;
import static roj.mod.Shared.MC_BINARY;

/**
 * Abstraction
 *
 * @author Roj234
 * @since 2020/8/30 11:32
 */
public abstract class WorkspaceBuilder implements ITask {
	public abstract String getId();

	public File jsonPath;
	public IntMap<File> file = new IntMap<>();

	public MappingFormat mf;
	public Map<String, Object> mf_cfg;

	ConstMapper mapper = new ConstMapper();

	volatile int error = 114514;

	static final File destination = new File(BASE, "class/"+MC_BINARY+".jar");

	public WorkspaceBuilder() {}

	static Method addURL;
	static {
		try {
			Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
			m.setAccessible(true);
			addURL = m;
		} catch (NoSuchMethodException e) {
			CmdUtil.error("Failed to get addURL method!", e);
		}
	}
	static boolean loadClass(String clazz, File jar) {
		return loadClass(clazz, Collections.singletonList(jar));
	}
	static boolean loadClass(String clazz, List<File> jars) {
		try {
			Class.forName(clazz);
		} catch (Throwable e1) {
			try {
				for (File jar : jars) {
					addURL.invoke(WorkspaceBuilder.class.getClassLoader(), jar.toURI().toURL());
				}
				Class.forName(clazz);
			} catch (Throwable e) {
				CmdUtil.error(clazz+"加载失败", e);
				return false;
			}
		}
		return true;
	}

	@Override
	public final void execute() {
		try {
			run();
		} catch (Throwable e) {
			error = -1;
			CmdUtil.error("未处理的异常", e);
		} finally {
			synchronized (this) {
				notifyAll();
			}
		}
	}
	public abstract void run();

	Set<String> skipLib = Collections.emptySet();
	public boolean mergeLibraryHook(File file, String artifact) {
		return skipLib.contains(artifact);
	}
	public void loadLibraries(File root, Collection<String> libraries) {
		TrieTree<String> tree = new TrieTree<>();
		for (String lib : libraries) {
			tree.put(lib, lib);
		}
		loadLibraries1(root, tree);
	}
	void loadLibraries1(File root, TrieTree<String> artifacts) {}
	static String get1(TrieTree<String> artifacts, String name) {
		List<String> list = artifacts.valueMatches(name, 2);
		if (list.size() != 1) throw new IllegalStateException("Not found artifact '"+name+"'");
		return list.get(0);
	}

	public final boolean awaitSuccess() {
		synchronized (this) {
			while (error == 114514) {
				try {
					wait();
				} catch (InterruptedException ignored) {}
			}
			Shared.Task.awaitFinish();
		}
		if (error != 0) CmdUtil.warning("错误码 "+error);
		return error == 0;
	}

	static void removePackageInfo() {
		try (ZipArchive mz = new ZipArchive(destination)) {
			for (String name : mz.getEntries().keySet()) {
				if (name.endsWith("package-info.class")) {
					mz.put(name, null);
				}
			}
			mz.store();
		} catch (IOException e) {
			CmdUtil.warning("删除package-info失败", e);
		}
	}
}
