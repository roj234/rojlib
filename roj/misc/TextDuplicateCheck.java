package roj.misc;

import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.concurrent.TaskPool;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.GB18030;
import roj.text.StreamReader;
import roj.ui.BottomLine;
import roj.ui.EasyProgressBar;
import roj.util.BsDiff;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/8/2 0002 6:39
 */
public class TextDuplicateCheck {
	IntMap<List<File>> files = new IntMap<>();

	public static void main(String[] args) {
		replacement = new TrieTree<>();
		replacement.put("\r", "");
		replacement.put("\n", "");
		replacement.put("\t", "");
		replacement.put(" ", "");
		replacement.put("　", "");

		TextDuplicateCheck inst = new TextDuplicateCheck();
		File f = new File(args[0]);
		pathLen = f.getAbsolutePath().length()+1;
		for (File file : IOUtil.findAllFiles(f, file -> file.getName().endsWith(".txt"))) {
			inst.add(file);
		}
		inst.diff();
		bar.end("fin.");
	}

	public void add(File file) {
		files.computeIfAbsentIntS((int) (file.length() / 4096), SimpleList::new).add(file);
	}

	static EasyProgressBar bar = new EasyProgressBar("进度");
	static LongAdder fin = new LongAdder();
	static long tot;
	static int pathLen;
	static TrieTree<String> replacement;

	public void diff() {
		ConcurrentHashMap<File, byte[]> cached = new ConcurrentHashMap<>();
		ConcurrentHashMap<File, Set<File>> checked = new ConcurrentHashMap<>();
		Function<File, byte[]> fileLoader = file -> {
			CharList sb = new CharList(8192);
			ByteList ob = new ByteList(16384);
			try (StreamReader in = StreamReader.auto(file)) {
				int len = 8192;
				int off = 0;
				while (len > 0) {
					int r = in.read(sb.list, off, len);
					if (r < 0) break;
					len -= r;
					off += r;
				}

				sb.setLength(off);
				sb.replaceMulti(replacement);
				GB18030.CODER.encodeFixedIn(sb, ob);
				if (ob.wIndex() > 16384) ob.wIndex(16384);
				return ob.toByteArray();
			} catch (IOException e) {
				e.printStackTrace();
				return new byte[1];
			} finally {
				ob._free();
				sb._free();
			}
		};

		SimpleList<IntMap.Entry<List<File>>> entries = new SimpleList<>(files.selfEntrySet());
		entries.sort((o1, o2) -> Integer.compare(o1.getIntKey(), o2.getIntKey()));
		for (int i = 0; i < entries.size(); i++) {
			List<File> prev = i==0?Collections.emptyList():entries.get(i-1).getValue();
			List<File> next = i==entries.size()-1?Collections.emptyList():entries.get(i+1).getValue();
			List<File> self = entries.get(i).getValue();

			List<File> ok = new SimpleList<>(prev.size()+next.size()+self.size());
			ok.addAll(prev);
			ok.addAll(next);
			ok.addAll(self);
			int begin = prev.size()+next.size();

			int cmpCount = (ok.size()-begin) * (ok.size() - 1);
			tot += cmpCount;

			TaskPool.CpuMassive().pushTask(() -> {
				BsDiff diff = new BsDiff();
				ByteList patch = new ByteList(4096);
				List<IntMap.Entry<File>> sorter = new SimpleList<>();

				for (int j = begin; j < ok.size(); j++) {
					File me = ok.get(j);
					long boundMin = me.length() - me.length()/20;
					long boundMax = me.length() + me.length()/20;

					byte[] dataA = cached.computeIfAbsent(me, fileLoader);
					diff.initSuffix(dataA);
					sorter.clear();

					for (int k = 0; k < ok.size(); k++) {
						if (k == j) continue;

						File tc = ok.get(k);
						if (tc.length() < boundMin || tc.length() > boundMax) continue;

						Set<File> seta = checked.computeIfAbsent(tc, Helpers.fnMyHashSet());
						Set<File> setb = checked.computeIfAbsent(me, Helpers.fnMyHashSet());
						synchronized (seta) {
							synchronized (setb) {
								if (!seta.add(me) || !setb.add(tc)) continue;
							}
						}

						byte[] dataB = cached.computeIfAbsent(tc, fileLoader);

						patch.clear();
						diff.bsdiff1(dataA, dataB, patch);
						int len = 0;
						for (int l = 0; l < patch.length(); l++) {
							byte b = patch.list[l];
							if (b != 0) len++;
						}
						if (len < Math.min(dataA.length, dataB.length) / 3) {
							sorter.add(new IntMap.Entry<>(len, ok.get(k)));
						}
					}

					if (sorter.size() > 0) {
						sorter.sort((o1, o2) -> Integer.compare(o1.getIntKey(), o2.getIntKey()));
						CharList tmp = new CharList().append("================================\n");
						tmp.append("差异自 " + me.getAbsolutePath().substring(pathLen) + "("+ me.length()+" bytes)\n");
						for (int k = 0; k < Math.min(5, sorter.size()); k++) {
							IntMap.Entry<File> e = sorter.get(k);
							tmp.append((k+1)+". " + e.getValue().getAbsolutePath().substring(pathLen) + "("+e.getValue().length()+" bytes): " + e.getIntKey()+"\n");
						}
						if (sorter.size() > 5) {
							tmp.append("和 " + (sorter.size()-5) + " 更多项目\n");
						}
						tmp.append("================================");
						BottomLine.sysErr.println(tmp.toStringAndFree());
					}

					fin.add(ok.size()-1);
					bar.update((double) fin.sum() / tot, ok.size()-1);
				}
			});
		}

		TaskPool.CpuMassive().awaitFinish();
	}
}
