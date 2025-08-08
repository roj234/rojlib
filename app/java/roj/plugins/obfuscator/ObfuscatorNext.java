package roj.plugins.obfuscator;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asmx.Context;
import roj.collect.ArrayList;
import roj.concurrent.TaskPool;
import roj.crypt.CryptoFactory;
import roj.gui.Profiler;
import roj.io.IOUtil;
import roj.plugins.obfuscator.naming.ABC;
import roj.plugins.obfuscator.naming.Deobfuscate;
import roj.plugins.obfuscator.naming.NameObfuscator;
import roj.plugins.obfuscator.naming.RenameExclusion;
import roj.collect.FlagSet;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static roj.asmx.Context.runAsync;

/**
 * @author Roj234
 * @since 2025/3/18 1:03
 */
public class ObfuscatorNext {
	private List<ObfuscateTask> tasks = new ArrayList<>();
	private static final int ASYNC_THRESHOLD = 1000;

	public static void main(String[] args) throws IOException {
		System.out.println("ObfuscatorNext, welcome");
		Profiler begin = new Profiler("ObfuscatorNext").begin();

		var src = new File("example.jar");

		Profiler.startSection("LoadResource");
		ZipFileWriter zfw = /*ObfUtil.createFakeZip*/new ZipFileWriter(IOUtil.deriveOutput(src, ".obf"));
		List<Context> arr = Context.fromZip(src, zfw);

		Profiler.endStartSection("exclusion");

		var obf = new NameObfuscator();
		var excl = new RenameExclusion();
		for (int i = 0; i < arr.size(); i++) {
			String className = arr.get(i).getClassName();
			ClassNode data = arr.get(i).getData();
			if (!data.name().equals(className)) {
				System.out.println("Name conflict for "+className+", skip.");
				arr.remove(i--);
				zfw.beginEntry(new ZEntry(className+".class"));
				AsmCache.toByteArrayShared(data).writeToStream(zfw);
				zfw.closeEntry();
				continue;
			}

			excl.checkExclusion(data, -1);
		}

		Profiler.endStartSection("runTask");

		obf.rand = new Random(42L);
		obf.exclusions = excl.toRuleset();
		obf.exclusions.add("roj/reflect/ClassDefiner//__", NameObfuscator.EX_CLASS|NameObfuscator.EX_METHOD, true, true);
		obf.exclusions.add("roj/asmx/launcher", Integer.MAX_VALUE, true, true);
		System.out.println(obf.exclusions);

		obf.clazz = new Deobfuscate();
		obf.method = new Deobfuscate();
		obf.field = new Deobfuscate();

		File file = new File("example_map.txt");
		if (file.isFile()) {
			obf.m.loadMap(file, false);
			obf.hasMap = true;
		}

		//obf.m.loadLibraries(lib);

		var next = new ObfuscatorNext();
		next.addTask(obf);

		next.obfuscate(arr);

		obf.m.saveMap(file);

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
		var pool = TaskPool.common();
		var rand = CryptoFactory.WyRandom();

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
