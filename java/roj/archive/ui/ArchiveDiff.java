package roj.archive.ui;

import roj.archive.qz.*;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.io.IOUtil;
import roj.ui.EasyProgressBar;
import roj.util.Helpers;

import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2024/3/2 0002 3:56
 */
public class ArchiveDiff {
	static EasyProgressBar bar = new EasyProgressBar("x");
	public static void main(String[] args) throws Exception {
		QZFileWriter out = new QZFileWriter(args[2]);
		out.setCodec(new LZMA2(1));
		QZArchive in1 = new QZArchive(args[0]);
		QZArchive in2 = new QZArchive(args[1]);
		MyHashMap<String, QZEntry> remain = in1.getEntries();

		IntMap<QZEntry> in2_by_crc32 = new IntMap<>();
		MyHashMap<QZEntry, String> in1_should_copy = new MyHashMap<>();
		MyHashMap<QZEntry, String> in2_should_copy = new MyHashMap<>();
		int index = 0;
		for (QZEntry entry : in2.getEntriesByPresentOrder()) {
			if (entry.isDirectory()) continue;
			QZEntry oldEntry = remain.remove(entry.getName());
			if (oldEntry == null) {
				QZEntry prev = in2_by_crc32.putInt(entry.getCrc32(), entry);
				if (prev != null) System.out.println("警告：在"+entry.getCrc32()+"上出现了CRC冲突");
				in2_should_copy.put(entry, "add/"+entry.getName());
			} else if (oldEntry.getCrc32() != entry.getCrc32()) {
				in1_should_copy.put(oldEntry, "mod_old/"+oldEntry.getName().substring(index));
				in2_should_copy.put(entry, "mod_new/"+entry.getName());
			}
		}

		for (QZEntry oldEntry : remain.values()) {
			if (oldEntry.isDirectory()) continue;
			QZEntry entry = in2_by_crc32.remove(oldEntry.getCrc32());
			if (entry != null) {
				in2_should_copy.remove(entry);
				out.beginEntry(new QZEntry("renamed/"+oldEntry.getName().substring(index)));
				out.write(entry.getName().getBytes(StandardCharsets.UTF_8));
				out.closeEntry();
			} else {
				in1_should_copy.put(oldEntry, "del/"+oldEntry.getName().substring(index));
			}
		}
		bar.addMax(in1_should_copy.size()+in2_should_copy.size());
		TaskPool pool = TaskPool.Common();
		in1.parallelDecompress(pool, (entry, in) -> {
			String prefix = in1_should_copy.get(entry);
			if (prefix == null) return;

			try (QZWriter w = out.parallel()) {
				w.beginEntry(new QZEntry(prefix));
				IOUtil.copyStream(in, w);
				bar.addCurrent(1);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		});
		pool.awaitFinish();
		in1.close();
		in2.parallelDecompress(pool, (entry, in) -> {
			String prefix = in2_should_copy.get(entry);
			if (prefix == null) return;

			try (QZWriter w = out.parallel()) {
				w.beginEntry(new QZEntry(prefix));
				IOUtil.copyStream(in, w);
				bar.addCurrent(1);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		});
		pool.awaitFinish();
		in2.close();
		out.close();
		System.out.println("done");
	}
}