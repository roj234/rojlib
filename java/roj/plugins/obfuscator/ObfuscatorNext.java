package roj.plugins.obfuscator;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.ClassNode;
import roj.asm.Parser;
import roj.asm.util.Context;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.crypt.MT19937;
import roj.gui.Profiler;
import roj.io.IOUtil;
import roj.plugins.obfuscator.naming.ABC;
import roj.plugins.obfuscator.naming.NameObfuscator;
import roj.plugins.obfuscator.naming.RenameExclusion;
import roj.util.PermissionSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static roj.asm.util.Context.runAsync;

/**
 * @author Roj234
 * @since 2025/3/18 0018 1:03
 */
public class ObfuscatorNext {
	private List<ObfuscateTask> tasks = new SimpleList<>();
	private static final int ASYNC_THRESHOLD = 1000;

	public static void main(String[] args) throws IOException {
		System.out.println("ObfuscatorNext, welcome");
		Profiler begin = new Profiler("ObfuscatorNext").begin();

		var src = new File("../example.jar");

		Profiler.startSection("LoadResource");
		ZipFileWriter zfw = ObfUtil.createFakeZip(IOUtil.deriveOutput(src, ".obf"));
		List<Context> arr = Context.fromZip(src, zfw);

		Profiler.endStartSection("exclusion");
		var set = new PermissionSet();
		set.add("roj/reflect/ClassDefiner//__", NameObfuscator.EX_CLASS|NameObfuscator.EX_METHOD, true, true);

		var obf = new NameObfuscator();
		var excl = new RenameExclusion();
		for (int i = 0; i < arr.size(); i++) {
			String className = arr.get(i).getClassName();
			ClassNode data = arr.get(i).getData();
			if (!data.name().equals(className)) {
				System.out.println("Name conflict for "+className+", skip.");
				arr.remove(i--);
				zfw.beginEntry(new ZEntry(className+".class"));
				Parser.toByteArrayShared(data).writeToStream(zfw);
				zfw.closeEntry();
				continue;
			}

			excl.checkExclusion(data, -1);
		}

		Profiler.endStartSection("runTask");

		obf.rand = new Random(42L);
		obf.exclusions = excl.toRuleset();
		obf.exclusions.addAll(set);
		obf.clazz = new ABC();
		obf.method = new ABC();
		obf.field = new ABC();

		obf.exclusions.add("roj/asmx/launcher", Integer.MAX_VALUE, true, true);
		obf.exclusions.add("roj/gui/impl", Integer.MAX_VALUE, true, true);
		obf.exclusions.add("roj/plugin", Integer.MAX_VALUE, true, true);
		obf.exclusions.add("roj/collect/SimpleList", 99, true, true);
		System.out.println(obf.exclusions);

		//obf.m.loadLibraries(lib);

		var next = new ObfuscatorNext();
		next.addTask(obf);

		next.obfuscate(arr);

		Profiler.endStartSection("IO: output");

		for (int i = 0; i < arr.size(); i++) {
			Context ctx = arr.get(i);
			zfw.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
		}

		zfw.close();
		Profiler.endSection();
		begin.end().popup();
	}

	public void addTask(ObfuscateTask task) {
		tasks.add(task);
	}

	public void obfuscate(List<Context> ctxs) {
		var pool = TaskPool.Common();
		var rand = new MT19937();

		List<List<Context>> contextTasks = new ArrayList<>((ctxs.size()-1)/ASYNC_THRESHOLD + 1);

		int i = 0;
		while (i < ctxs.size()) {
			int len = Math.min(ctxs.size()-i, ASYNC_THRESHOLD);
			contextTasks.add(ctxs.subList(i, i+len));
			i += len;
		}

		for (ObfuscateTask task : tasks) {
			if (task.isMulti()) {
				task.forEach(ctxs, rand, pool);
			} else {
				runAsync(ctx -> task.apply(ctx, rand), contextTasks, pool);
			}
		}
	}
}
