package roj.mapper;

import roj.archive.zip.ZipFileWriter;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.concurrent.task.AsyncTask;
import roj.mapper.util.ResWriter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper 'main' entry
 *
 * @author Roj234
 * @since 2021/5/29 18:3
 */
public class MapperMain {
	static TaskExecutor te = new TaskExecutor();

	public static void main(String[] args) throws Exception {
		long time = System.currentTimeMillis();
		if (args.length < 2) {
			throw new IllegalArgumentException(
				"Usage: Main <input> <output> [config] \n" +
					"    配置项:\n" +
					"      mappingPath => 指定映射位置\n" +
					"      libPath     => 指定库位置\n" +
					"      cachePath   => 指定缓存保存位置\n" +
					"      remapClass  => 重映射类名\n" +
					"      charset     => 文件编码\n" +
					"      reverse     => 反转映射\n" +
					"      singleThread=> 单线程\n" +
					"      flag        => flag");
		}
		File input = new File(args[0]);

		String mappingPath = "srg.srg";
		String libPath = null;
		String cachePath = null;

		boolean singleThread = false;

		Charset charset = File.separatorChar == '/' ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII;

		boolean className = false;
		boolean reverse = false;

		ConstMapper remapper = new ConstMapper();

		for (int i = 2; i < args.length; i++) {
			switch (args[i]) {
				case "singleThread":
					singleThread = true;
					System.out.println("单线程模式");
					break;
				case "mappingPath":
					mappingPath = args[++i];
					break;
				case "cachePath":
					cachePath = args[++i];
					break;
				case "libPath":
					libPath = args[++i];
					break;
				case "reverse":
					reverse = true;
					break;
				case "remapClass":
					className = true;
					break;
				case "charset":
					charset = Charset.forName(args[++i]);
					break;
				case "flag":
					remapper.flag = (byte) Integer.parseInt(args[++i]);
					break;
				default:
					throw new IllegalArgumentException("Unknown " + args[i]);
			}
		}

		te.start();
		File output = new File(args[1]);

		remapper.initEnv(new File(mappingPath), libPath == null ? null : new File(libPath), cachePath == null ? null : new File(cachePath), reverse);

		MyHashMap<String, Context> byName = new MyHashMap<>();
		Map<String, byte[]> resource = new MyHashMap<>();
		List<X> files = new SimpleList<>();

		List<Context> arr;
		if (input.isDirectory()) {
			for (File file : input.listFiles()) {
				arr = Context.fromZip(file, charset, resource);
				files.add(new X(new File(output, file.getName()), arr, new MyHashMap<>(resource)));
				for (int i = 0; i < arr.size(); i++) {
					Context ctx = arr.get(i);
					byName.put(ctx.getFileName(), ctx);
				}

				resource.clear();
			}
		} else {
			arr = Context.fromZip(input, charset, resource);
			files.add(new X(output, arr, resource));
			for (int i = 0; i < arr.size(); i++) {
				Context ctx = arr.get(i);
				byName.put(ctx.getFileName(), ctx);
			}
		}

		List<Context> all = new ArrayList<>(byName.values());
		remapper.remap(singleThread, all);
		if (className) {
			new CodeMapper(remapper).remap(singleThread, all);
		}

		for (int i = 0; i < files.size(); i++) {
			files.get(i).finish(byName);
		}
	}

	static final class X {
		ZipFileWriter zfw;
		AsyncTask<Void> task;
		MyHashSet<String> files;

		public X(File out, List<Context> arr, Map<String, byte[]> streams) throws IOException {
			zfw = new ZipFileWriter(out);
			te.pushTask(task = new AsyncTask<>(() -> {
				return new ResWriter(zfw, streams).call();
			}));
			files = new MyHashSet<>(arr.size());
			for (int i = 0; i < arr.size(); i++) {
				files.add(arr.get(i).getFileName());
			}
		}

		public void finish(MyHashMap<String, Context> byName) throws Exception {
			task.get();
			for (String name : files) {
				Context c = byName.get(name);
				zfw.writeNamed(c.getFileName(), c.getCompressedShared());
			}
			zfw.finish();
		}
	}
}