package roj.text.novel;

import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.config.ConfigMaster;
import roj.config.serial.*;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.StreamReader;
import roj.text.TextUtil;
import roj.ui.EasyProgressBar;
import roj.ui.UIUtil;
import roj.util.BsDiff;
import roj.util.NativeMemory;
import sun.misc.Unsafe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import static roj.reflect.FieldAccessor.u;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:39
 */
public class TextDeDup {
	public static class Diff implements ITask {
		File left, right;
		int diff;
		@Optional
		float difference;

		public Diff() {}

		@Override
		public void execute() throws Exception {
			if (difference != 0) return;
			System.out.print("compute diff.");
			BsDiff diff1 = new BsDiff();
			byte[] data = read(left);
			System.out.print(".");
			diff1.initSuffix(data);
			System.out.print(".");
			byte[] data2 = read(right);
			System.out.print(".");
			diff = diff1.bscompare(data, data2, Integer.MAX_VALUE);
			System.out.println(".");
			difference = (float) diff / Math.min(data.length, data2.length);
		}

		private static byte[] read(File file) throws IOException {
			CharList sb = IOUtil.getSharedCharBuf();
			try (StreamReader in = StreamReader.auto(file)) {
				sb.readFully(in, true);
				sb.replaceMulti(replacement);
				byte[] out = new byte[sb.length()*2];
				u.copyMemory(sb.list, Unsafe.ARRAY_CHAR_BASE_OFFSET, out, Unsafe.ARRAY_BYTE_BASE_OFFSET, out.length);
				return out;
			}
		}
	}

	static int[] ref;
	static NativeMemory block = new NativeMemory(true);
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("TextDeDuplicate <mode> <path> <list>\n" +
				"mode: find | apply\n" +
				"path: novel path\n" +
				"list: a yml/json file to store duplicate file");
			return;
		}

		basePath = new File(args[1]);
		pathLen = basePath.getAbsolutePath().length()+1;

		sf = SerializerFactoryFactory.create();
		sf.register(File.class, new Object() {
			public String ser(File f) { return f.getAbsolutePath().substring(pathLen); }
			public File des(String s) { return new File(basePath, s); }
		});

		replacement = new TrieTree<>();
		replacement.put("\r", "");
		replacement.put("\n", "");
		replacement.put("\t", "");
		replacement.put(" ", "");
		replacement.put("　", "");

		if (args[0].equals("find")) {
			cfg = new ToYaml().to(new FileOutputStream(args[2]));
			cfg.valueList();

			IntMap<List<File>> files = new IntMap<>();
			for (File file : IOUtil.findAllFiles(basePath, file -> file.getName().endsWith(".txt"))) {
				files.computeIfAbsentIntS((int) (file.length() / BOUND), SimpleList::new).add(file);
			}
			diff(files);

			cfg.finish();
			bar.end("end");
		} else if (args[0].equals("apply")) {
			CAdapter<List<Diff>> adapter = sf.listOf(Diff.class);
			List<Diff> diffs = ConfigMaster.adapt(adapter, new File(args[2]));
			for (int i = 0; i < diffs.size();) {
				Diff diff = diffs.get(i);
				if (!diff.left.isFile() || !diff.right.isFile()) {
					diffs.remove(i);
					continue;
				}

				TaskPool.CpuMassive().pushTask(diff);
				i++;
			}

			TaskPool.CpuMassive().awaitFinish();
			diffs.sort((o1, o2) -> Float.compare(o1.difference, o2.difference));
			System.out.println("total:" + diffs.size());
			ConfigMaster.write(diffs, "D:\\Desktop\\novel\\similar2.yml", "yml", adapter);

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
				args2.add("/dl");
				args2.add(IOUtil.noExtName(d.left.getName()));
				args2.add("/dr");
				args2.add(IOUtil.noExtName(d.right.getName()));
				args2.add(d.left.getAbsolutePath());
				args2.add(d.right.getAbsolutePath());

				bar.update((double) i / diffs.size(), 1);

				System.out.println("left file:"+d.left);
				System.out.println("right file:"+d.right);
				new ProcessBuilder().command("D:\\Everything\\Everything.exe", "-s", "<"+Tokenizer.addSlashes(IOUtil.noExtName(d.left.getName())) + ">|<" + Tokenizer.addSlashes(IOUtil.noExtName(d.right.getName()))+'>').start();
				int exit = new ProcessBuilder().command(args2).start().waitFor();

				if (exit == 0) UIUtil.userInput("请按任意键继续");

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

	static final int BOUND = 32768, PRE_COMPARE = 4096;
	static final double DIFF_MAX = 1/3d;

	private static File basePath;
	private static int pathLen;

	static EasyProgressBar bar = new EasyProgressBar("进度");
	static LongAdder fin = new LongAdder();
	static long tot;

	private static TrieTree<String> replacement;
	private static ToSomeString cfg;
	private static SerializerFactory sf;
	private static int limit;

	public static void diff(IntMap<List<File>> files) {
		ConcurrentHashMap<File, byte[]> cached = new ConcurrentHashMap<>();
		Function<File, byte[]> fileLoader = file -> {
			CharList sb = new CharList(PRE_COMPARE);
			try (StreamReader in = StreamReader.auto(file)) {
				int len = PRE_COMPARE;
				int off = 0;
				while (len > 0) {
					int r = in.read(sb.list, off, len);
					if (r < 0) break;
					len -= r;
					off += r;
				}

				sb.setLength(off);
				sb.replaceMulti(replacement);
				byte[] out = new byte[sb.length()*2];
				u.copyMemory(sb.list, Unsafe.ARRAY_CHAR_BASE_OFFSET, out, Unsafe.ARRAY_BYTE_BASE_OFFSET, out.length);
				return out;
			} catch (IOException e) {
				System.out.println("error is " + file.getName());
				e.printStackTrace();
				return new byte[1];
			} finally {
				sb._free();
			}
		};

		SimpleList<IntMap.Entry<List<File>>> entries = new SimpleList<>(files.selfEntrySet());
		MyBitSet oldMemory = new MyBitSet(entries.size());
		entries.sort((o1, o2) -> Integer.compare(o1.getIntKey(), o2.getIntKey()));
		for (int i = 0; i < entries.size(); i++) {
			List<File> prev = i==0?Collections.emptyList():entries.get(i-1).getValue();
			List<File> self = entries.get(i).getValue();

			int cmpCount = self.size() * prev.size() + (self.size() - 1) * self.size() / 2;
			tot += cmpCount;

			int finalI = i;
			TaskPool.CpuMassive().pushTask(() -> {
				BsDiff diff = new BsDiff();
				List<Diff> sorter = new SimpleList<>();

				for (int j = 0; j < self.size(); j++) {
					File me = self.get(j);

					byte[] dataA = cached.computeIfAbsent(me, fileLoader);
					diff.initSuffix(dataA);
					sorter.clear();

					for (int k = 0; k < prev.size(); k++) {
						File tc = prev.get(k);

						byte[] dataB = cached.computeIfAbsent(tc, fileLoader);
						int byteDiff = diff.bscompare(dataA, dataB, (int) (Math.min(dataA.length, dataB.length) * DIFF_MAX));
						if (byteDiff >= 0) {
							Diff diff1 = new Diff();
							diff1.left = me;
							diff1.right = tc;
							diff1.diff = byteDiff;
							sorter.add(diff1);
						}
					}

					for (int k = j+1; k < self.size(); k++) {
						File tc = self.get(k);

						byte[] dataB = cached.computeIfAbsent(tc, fileLoader);
						int byteDiff = diff.bscompare(dataA, dataB, (int) (Math.min(dataA.length, dataB.length) * DIFF_MAX));
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

					int count = prev.size() + self.size()-j-1;
					fin.add(count);
					long sum = fin.sum();
					bar.setPercentStr(sum + "/" + tot);
					bar.update((double) sum / tot, count);
				}

				synchronized (oldMemory) {
					oldMemory.add(finalI);
					if (oldMemory.contains(finalI+1)) {
						System.out.print("range ["+finalI+"] finished, clean up ");
						long mem = 0;
						for (File file : self) {
							byte[] data = cached.remove(file);
							mem += data.length + 128;
						}
						System.out.println(TextUtil.scaledNumber(mem).toUpperCase(Locale.ROOT)+"B");
					}
					if (oldMemory.contains(finalI-1)) {
						System.out.print("range ["+(finalI-1)+"] finished, clean up ");
						long mem = 0;
						for (File file : prev) {
							byte[] data = cached.remove(file);
							mem += data.length + 128;
						}
						System.out.println(TextUtil.scaledNumber(mem).toUpperCase(Locale.ROOT)+"B");
					}
				}
			});
		}

		TaskPool.CpuMassive().awaitFinish();
	}
}
