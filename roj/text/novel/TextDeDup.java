package roj.text.novel;

import roj.collect.CharMap;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.concurrent.Promise;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.config.ConfigMaster;
import roj.config.serial.*;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.BsDiff;
import roj.util.Helpers;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:39
 */
public class TextDeDup {
	public static class Diff implements ITask {
		File left, right;
		int diff;
		@Optional
		int size;

		public Diff() {}

		@Override
		public void execute() throws Exception {
			if (size != 0) return;

			BsDiff d = new BsDiff();
			byte[] data = read(left);
			System.out.print(".");
			d.setLeft(data);
			System.out.print(".");
			byte[] data2 = read(right);
			System.out.print(".");
			diff = d.getDiffLength(data2, Integer.MAX_VALUE);
			size = Math.min(data.length, data2.length);
		}

		private static byte[] read(File file) throws IOException {
			CharList sb = IOUtil.getSharedCharBuf();
			try (TextReader in = TextReader.auto(file)) {
				sb.readFully(in, true);
				byte[] out = new byte[sb.length()*2];
				u.copyMemory(sb.list, Unsafe.ARRAY_CHAR_BASE_OFFSET, out, Unsafe.ARRAY_BYTE_BASE_OFFSET, out.length);
				return out;
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Diff diff = (Diff) o;

			if (!left.equals(diff.left)) return false;
			return right.equals(diff.right);
		}

		@Override
		public int hashCode() {
			int result = left.hashCode();
			result = 31 * result + right.hashCode();
			return result;
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("TextDeDuplicate <mode> <path> <list>\n" +
				"mode: find | apply\n" +
				"path: novel path\n" +
				"list: a yml/json file to store duplicate file");
			return;
		}

		basePath = new File(args[1]);
		pathLen = basePath.getAbsolutePath().length();

		sf = Serializers.newSerializerFactory();
		sf.register(File.class, new Object() {
			public String ser(File f) { return f.getAbsolutePath().substring(pathLen); }
			public File des(String s) { return new File(basePath, s); }
		});

		replacement = new CharMap<>();
		replacement.put('\r', "");
		replacement.put('\n', "");
		replacement.put('\t', "");
		replacement.put(' ', "");
		replacement.put('　', "");
		asyn2.setRejectPolicy(TaskPool::executePolicy);

		if (args[0].equals("find")) {
			cfg = new ToYaml().to(new FileOutputStream(args[2]));
			cfg.valueList();

			ConcurrentHashMap<File, byte[]> fileCache = new ConcurrentHashMap<>();
			IntMap<List<File>> files = new IntMap<>();

			bar.setName("Load");
			bar.setUnit("File");

			List<File> allFiles = IOUtil.findAllFiles(basePath, file -> file.getName().endsWith(".txt"));
			bar.addMax(allFiles.size());
			for (int i = 0; i < allFiles.size(); i++) {
				File file = allFiles.get(i);
				asyn2.pushTask(() -> {
					char[] data = ArrayCache.getCharArray(SIZE_BUFFER, false);
					CharList sb = new CharList(data);
					try (TextReader in = TextReader.auto(file)) {
						int lenBefore = in.read(data);
						sb.setLength(lenBefore);
						sb.replaceMulti(replacement);
						int lenAfter = sb.length();

						long fileSize = (long) ((double) lenAfter / lenBefore * file.length());
						synchronized (files) {
							files.computeIfAbsentIntS((int) (fileSize / BOUND), SimpleList::new).add(file);
						}

						if (lenAfter > COMPARE_BUFFER) lenAfter = COMPARE_BUFFER;

						byte[] out = new byte[lenAfter * 2];
						u.copyMemory(sb.list, Unsafe.ARRAY_CHAR_BASE_OFFSET, out, Unsafe.ARRAY_BYTE_BASE_OFFSET, out.length);
						fileCache.put(file, out);

						bar.addCurrent(1);
					} catch (Exception e) {
						System.out.println("error is " + file.getName());
						e.printStackTrace();
					}
					ArrayCache.putArray(data);
				});
			}
			asyn2.awaitFinish();
			bar.end("loaded");

			bar.reset();
			bar.setName("Compare");
			bar.setUnit("C");
			diff(files, fileCache);

			cfg.finish();
			bar.end("end");
		} else if (args[0].equals("apply")) {
			CAdapter<List<Diff>> adapter = sf.listOf(Diff.class);
			List<Diff> diffs = ConfigMaster.adapt(adapter, new File(args[2]));
			for (int i = 0; i < diffs.size();) {
				Diff d = diffs.get(i);
				if (!d.left.isFile() || !d.right.isFile()) {
					diffs.remove(i);
					continue;
				}
				if (d.left.getAbsolutePath().compareTo(d.right.getAbsolutePath()) > 0) {
					File right = d.right;
					d.right = d.left;
					d.left = right;
				}

				asyn2.pushTask(d);
				i++;
			}

			asyn2.awaitFinish();
			diffs.sort((o1, o2) -> Integer.compare(o1.diff, o2.diff));
			for (int i = diffs.size()-1; i > 0; i--) {
				if (diffs.get(i).equals(diffs.get(i-1))) diffs.remove(i);
			}
			System.out.println("count:" + diffs.size());
			ConfigMaster.write(diffs, args[2], "yml", adapter);

			for (int i = 0; i < diffs.size(); i++) {
				Diff d = diffs.get(i);

				List<String> args2 = new SimpleList<>();
				String cmd = "D:\\Desktop\\nv\\WinMerge\\WinMergeU.exe /e /t Text /xq /u /fl /enableexitcode /al /ignorecodepage /cp 54936";
				Tokenizer l = Tokenizer.arguments().init(cmd);
				while (l.hasNext()) {
					Word w = l.next();
					if (w.type() == Word.EOF) break;
					args2.add(w.val());
				}
				args2.add(d.left.getAbsolutePath());
				args2.add(d.right.getAbsolutePath());

				bar.update((double) i / diffs.size(), 1);

				System.out.println("left file:"+d.left);
				System.out.println("right file:"+d.right);
				new ProcessBuilder().command("D:\\Everything\\Everything.exe", "-s", "<"+Tokenizer.addSlashes(IOUtil.noExtName(d.left.getName())) + ">|<" + Tokenizer.addSlashes(IOUtil.noExtName(d.right.getName()))+'>').start();
				int exit = new ProcessBuilder().command(args2).start().waitFor();

				if (exit == 0) CLIUtil.userInput("请按任意键继续");

				System.out.println("exit code:"+exit);
				System.out.println();
			}
		} else if (args[0].equals("conv")) {
			NovelServer.load();
			NovelServer.convert(new File(args[1]),new File("D:\\Desktop\\novel\\tst_out"),new File("D:\\Desktop\\star.csv"),0);
		} else {
			System.out.println("unknown command, see help");
		}
	}

	static final int BOUND = 500000, SIZE_BUFFER = 10000, COMPARE_BUFFER = 8192;
	static final double DIFF_MAX = 1/3d;

	private static File basePath;
	private static int pathLen;
	private static TaskPool asyn2 = TaskPool.MaxSize(9999, "TDD worker");

	static EasyProgressBar bar = new EasyProgressBar("进度");

	private static CharMap<String> replacement;
	private static ToSomeString cfg;
	private static SerializerFactory sf;
	private static int limit;

	public static void diff(IntMap<List<File>> fileMap, ConcurrentHashMap<File, byte[]> fileCache) {
		SimpleList<IntMap.Entry<List<File>>> entries = new SimpleList<>(fileMap.selfEntrySet());
		entries.sort((o1, o2) -> Integer.compare(o1.getIntKey(), o2.getIntKey()));
		SimpleList<List<File>> files = Helpers.cast(entries);
		for (int i = 0; i < entries.size(); i++) files.set(i, entries.get(i).getValue());
		fileMap.clear();

		for (int i = files.size()-1; i > 0; i--) {
			if (files.get(i).size() < 50) files.get(i-1).addAll(files.remove(i));
		}
		MyBitSet finishedBlock = new MyBitSet(files.size());

		long maxMem = 0;
		for (byte[] value : fileCache.values())
			maxMem += 128 + value.length;
		AtomicLong curMem = new AtomicLong(maxMem);
		long finalMaxMem = maxMem;

		TaskExecutor asyn1 = new TaskExecutor();
		asyn1.start();

		for (int i = files.size()-1; i >= 0; i--) {
			List<File> prev = i==0?Collections.emptyList():files.get(i-1);
			List<File> self = files.get(i);

			int cmpCount = self.size() * prev.size() + (self.size() - 1) * self.size() / 2;
			bar.addMax(cmpCount);

			int finalI = i;
			asyn1.pushTask(() -> {
				System.out.println("================ Compare Status ================");
				System.out.println("|  Block: "+finalI+"");
				System.out.println("|  Compare: "+self.size()+"("+cmpCount+")");
				System.out.println("|  Memory:"+TextUtil.scaledNumber(curMem.get())+"B/"+TextUtil.scaledNumber(finalMaxMem)+"B");
				System.out.println("================================================");

				List<Promise<Void>> tasks = new SimpleList<>();

				for (int j = 0; j < self.size(); j++) {
					File me = self.get(j);

					byte[] dataA = fileCache.get(me);
					BsDiff diff = new BsDiff();
					diff.setLeft(dataA);

					int finalJ = j;
					tasks.add(Promise.async(asyn2, (x) -> {
						List<Diff> sorter = new SimpleList<>();

						for (int k = 0; k < prev.size(); k++) {
							File tc = prev.get(k);

							byte[] dataB = fileCache.get(tc);
							int maxHeadDiff = (int) (Math.min(dataA.length, dataB.length) * DIFF_MAX);

							int byteDiff = diff.getDiffLength(dataB, maxHeadDiff);
							if (byteDiff >= 0) {
								Diff diff1 = new Diff();
								diff1.left = me;
								diff1.right = tc;
								diff1.diff = byteDiff;
								sorter.add(diff1);
							}
						}

						for (int k = finalJ+1; k < self.size(); k++) {
							File tc = self.get(k);

							byte[] dataB = fileCache.get(tc);
							int maxHeadDiff = (int) (Math.min(dataA.length, dataB.length) * DIFF_MAX);

							int byteDiff = diff.getDiffLength(dataB, maxHeadDiff);
							if (byteDiff >= 0) {
								Diff diff1 = new Diff();
								diff1.left = me;
								diff1.right = tc;
								diff1.diff = byteDiff;
								sorter.add(diff1);
							}
						}

						if (sorter.size() > 0) {
							CAdapter<Diff> adapter = sf.adapter(Diff.class);
							synchronized (cfg) {
								for (int k = 0; k < sorter.size(); k++) {
									adapter.write(cfg, sorter.get(k));
								}
							}
						}

						int count = prev.size() + self.size() - finalJ - 1;
						bar.addCurrent(count);

						x.resolve(null);
					}));
				}

				Promise.all(null, tasks).thenR(() -> {
					synchronized (finishedBlock) {
						finishedBlock.add(finalI);
						long mem = 0;
						for (int j = 0; j < files.size(); j++) {
							if (finishedBlock.allTrue(j, j+2)) {
								List<File> value = files.get(j);
								if (value != null) {
									for (File file : value) {
										mem += fileCache.remove(file).length + 128;
									}
									files.set(j, null);
								}
							}
						}
						curMem.addAndGet(-mem);
					}
				});
			});
		}

		try {
			asyn1.waitFor();
		} catch (InterruptedException ignored) {}
		asyn2.awaitFinish();
		asyn2.shutdown();
	}
}
