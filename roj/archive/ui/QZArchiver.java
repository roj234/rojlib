package roj.archive.ui;

import roj.archive.qz.*;
import roj.archive.qz.xz.LZMA2Options;
import roj.collect.HashBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.crypt.CRCAny;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.SplittedSource;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/6/4 0004 3:54
 */
public class QZArchiver {
	public static Consumer<QZWriter> fileData(File f) {
		return out -> {
			try (FileInputStream in = new FileInputStream(f)) {
				IOUtil.copyStream(in, out);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		};
	}

	public File input;
	public boolean storeFolder, storeMT, storeCT, storeAT, storeAttr;
	public int threads;
	public long memoryLimit;
	public boolean autoSolidSize;
	public long solidSize, splitSize;
	public boolean compressHeader;
	public String password;
	public int cryptPower, cryptSalt;
	public boolean encryptFileName;
	public boolean useBCJ, useBCJ2;
	public LZMA2Options options;
	public boolean fastAppendCheck;
	public int appendOptions;
	public File cacheFolder;
	public File outputFolder;
	public String outputName;

	private QZArchive oldArchive;
	private long keepSize;

	private final HashBiMap<String, File> byPath = new HashBiMap<>();
	private final Map<WordBlock, List<QZEntry>> keep = new MyHashMap<>();
	private final List<WordBlockAppend> appends = new SimpleList<>();
	private final List<QZEntry> empties = new SimpleList<>();
	private boolean firstIsUncompressed;

	private static final MyHashSet<String> UNCOMPRESSED = new MyHashSet<>("7z", "rar", "xz", "txz", "lzma", "bz2", "bzip2", "tbz", "tbz2", "gz", "gzip", "esd", "wim");
	private static final MyHashSet<String> EXECUTABLE_X86 = new MyHashSet<>("exe", "dll", "sys", "so");

	public long prepare() throws IOException {
		firstIsUncompressed = false;
		appends.clear();
		keep.clear();
		empties.clear();

		File path = input;
		String inAbsPath = new File(outputFolder, outputName).getAbsolutePath();
		int[] prefix = new int[1];

		Map<String, File> byPath = this.byPath; byPath.clear();
		List<File>
			uncompressed = new SimpleList<>(),
			executable = new SimpleList<>(),
			compressed = new SimpleList<>(),
			emptyOrFolder = new SimpleList<>();
		final long[] compressedLen = {0};

		Consumer<File> callback = file -> {
			if (file.getAbsolutePath().startsWith(inAbsPath)) return;

			byPath.put(file.getAbsolutePath().substring(prefix[0]).replace(File.separatorChar, '/'), file);

			long length = file.length();
			if (length == 0) {
				emptyOrFolder.add(file);
				return;
			}

			String ext = IOUtil.extensionName(file.getName()).toLowerCase();
			if (UNCOMPRESSED.contains(ext)) uncompressed.add(file);
			else {
				if (useBCJ && EXECUTABLE_X86.contains(ext)) executable.add(file);
				else compressed.add(file);

				compressedLen[0] += file.length();
			}
		};

		if (path.isFile()) {
			prefix[0] = path.getAbsolutePath().length() - path.getName().length();
			callback.accept(path);
		} else {
			prefix[0] = path.getAbsolutePath().length()+1;
			traverseFolder(path, callback, emptyOrFolder);
		}

		File out = new File(outputFolder, outputName.concat(splitSize == 0 ? "" : ".001"));
		if (out.isFile()) {
			try {
				oldArchive = new QZArchive(out, password);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		keep.clear();
		keepSize = 0;
		switch (appendOptions) {
			case 0: {
				// 添加并替换文件
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						if (!byPath.containsKey(entry.getName())) {
							keepEntry(entry);
						}
					}
				}
			}
			break;
			case 1: {
				// 更新并添加文件
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						File file = byPath.get(entry.getName());
						if (file == null || file.length() != entry.getSize() ||
							(!fastAppendCheck ? contentSame(entry, file) :
							(entry.hasModificationTime() && file.lastModified() <= entry.getModificationTime()) ||
								(entry.hasCrc32() && checkCrc32(file, entry.getCrc32())))) {

							byPath.remove(entry.getName());
							keepEntry(entry);
						}
					}
				}
			}
			break;
			case 2: {
				// 只更新已存在的文件
				if (oldArchive == null) return -1;
				MyHashMap<String, QZEntry> inEntry = oldArchive.getEntries();
				for (Iterator<String> itr = byPath.keySet().iterator(); itr.hasNext(); ) {
					if (null == inEntry.remove(itr.next())) itr.remove();
				}
				for (QZEntry value : inEntry.values()) keepEntry(value);
			}
			break;
			case 3: {
				// 同步压缩包内容
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						File file = byPath.get(entry.getName());
						if (file != null && file.length() == entry.getSize() &&
							(!fastAppendCheck ? contentSame(entry, file) :
							(entry.hasModificationTime() && file.lastModified() <= entry.getModificationTime()) ||
								(entry.hasCrc32() && checkCrc32(file, entry.getCrc32())))) {

							byPath.remove(entry.getName());
							keepEntry(entry);
						}
					}
				}
			}
			break;
			case 4: // 重新建立压缩包
		}

		// EMPTY
		for (int i = 0; i < emptyOrFolder.size(); i++) {
			QZEntry entry = entryFor(emptyOrFolder.get(i));
			if (entry != null) empties.add(entry);
		}

		List<Consumer<QZWriter>> tmpa = new SimpleList<>();
		List<QZEntry> tmpb = new SimpleList<>();

		QzAES qzAes = password == null ? null : new QzAES(password, cryptPower, cryptSalt);

		// UNCOMPRESSED
		if (!uncompressed.isEmpty()) {
			firstIsUncompressed = true;
			for (File file : uncompressed) {
				QZEntry entry = entryFor(file);
				if (entry != null) {
					tmpa.add(fileData(file));
					tmpb.add(entry);
				}
			}

			addBlock(new QZCoder[] {qzAes == null ? Copy.INSTANCE : qzAes}, tmpa, tmpb, 0);
		}

		long chunkSize = autoSolidSize ? compressedLen[0] / threads : solidSize;
		if (chunkSize < 0) chunkSize = Long.MAX_VALUE;

		QZCoder lzma2 = options.getMode() == LZMA2Options.MODE_UNCOMPRESSED ? Copy.INSTANCE : new LZMA2(options);

		QZCoder[] coders = qzAes == null ? new QZCoder[] {lzma2} : new QZCoder[] {lzma2, qzAes};
		makeBlock(compressed, chunkSize, coders, tmpa, tmpb);

		if (useBCJ) coders = getBcjCoder(qzAes, lzma2);
		makeBlock(executable, chunkSize, coders, tmpa, tmpb);

		return chunkSize;
	}

	/**
	 * 自定义文件处理函数
	 */
	protected void traverseFolder(File path, Consumer<File> callback, List<File> emptyOrFolder) throws IOException {
		Files.walkFileTree(path.toPath(), new SimpleFileVisitor<Path>() {
			boolean next;
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (storeFolder) {
					if (!next) next = true;
					else emptyOrFolder.add(dir.toFile());
				}
				return FileVisitResult.CONTINUE;
			}
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				callback.accept(file.toFile());
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private QZCoder[] getBcjCoder(QzAES qzAes, QZCoder lzma2) {
		if (useBCJ2) {
			// 需要全部加密么？
			LZMA lc0lp2 = new LZMA(new LZMA2Options().setLcLp(0, 2));
			return qzAes == null ?
				new QZCoder[] {new BCJ2(), lzma2, null, lc0lp2, null, lc0lp2, null, null} :
				new QZCoder[] {new BCJ2(), lzma2, qzAes, null, lc0lp2, qzAes, null, lc0lp2, qzAes, null, qzAes, null};
		} else {
			return qzAes == null ? new QZCoder[] {BCJ.X86, lzma2} : new QZCoder[] {BCJ.X86, lzma2, qzAes};
		}
	}

	private void keepEntry(QZEntry entry) {
		WordBlock block = entry.getBlock();
		if (block == null) empties.add(entry);
		else {
			keep.computeIfAbsent(block, Helpers.fnArrayList()).add(entry);
			keepSize += entry.getSize();
		}
	}

	private boolean contentSame(QZEntry entry, File file) {
		byte[] data = ArrayCache.getByteArray(4096, false);
		byte[] data2 = ArrayCache.getByteArray(4096, false);
		try (FileInputStream in = new FileInputStream(file)) {
			try (InputStream in2 = oldArchive.getInput(entry)) {
				while (true) {
					int r = in.read(data);
					if (r < 0) break;
					IOUtil.readFully(in2, data2, 0, r);
					for (int i = 0; i < r; i++) {
						if (data[i] != data2[i]) return false;
					}
				}
			}
			return true;
		} catch (Exception e) {
			return false;
		} finally {
			ArrayCache.putArray(data);
			ArrayCache.putArray(data2);
		}
	}
	private static boolean checkCrc32(File file, int crc2) {
		byte[] data = ArrayCache.getByteArray(4096, false);
		try (FileInputStream in = new FileInputStream(file)) {
			int crc = CRCAny.CRC_32.INIT_VALUE;
			while (true) {
				int r = in.read(data);
				if (r < 0) break;
				crc = CRCAny.CRC_32.update(crc, data, 0, r);
			}
			crc = CRCAny.CRC_32.retVal(crc);
			return crc == crc2;
		} catch (Exception e) {
			return false;
		} finally {
			ArrayCache.putArray(data);
		}
	}

	private void makeBlock(List<File> compressed, long chunkSize, QZCoder[] coders, List<Consumer<QZWriter>> tmpa, List<QZEntry> tmpb) {
		long size = 0;
		for (File file : compressed) {
			QZEntry entry = entryFor(file);
			if (entry != null) {
				long nextSize = size + file.length();
				if (nextSize > chunkSize) {
					addBlock(coders, tmpa, tmpb, size);

					size = file.length();
				} else {
					size = nextSize;
				}

				tmpa.add(fileData(file));
				tmpb.add(entry);
			}

		}
		if (!tmpa.isEmpty()) addBlock(coders, tmpa, tmpb, size);
	}
	private void addBlock(QZCoder[] coders, List<Consumer<QZWriter>> tmpa, List<QZEntry> tmpb, long size) {
		WordBlockAppend block = new WordBlockAppend();
		block.coders = coders;
		block.data = tmpa.toArray(Helpers.cast(new Consumer<?>[tmpa.size()]));
		block.file = tmpb.toArray(new QZEntry[tmpb.size()]);
		block.size = size;
		appends.add(block);

		tmpa.clear();
		tmpb.clear();
	}
	private QZEntry entryFor(File file) {
		String name = byPath.getByValue(file);
		if (null == name) return null;

		BasicFileAttributes attr;
		try {
			attr = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
		} catch (IOException e) {
			throw new RuntimeException("Read BasicAttributes for "+file+" failed");
		}

		QZEntry entry = new QZEntry(name, attr.size());

		if (attr.isDirectory()) entry.setIsDirectory(true);
		if (storeMT) entry.setModificationTime(attr.lastModifiedTime().toMillis());
		if (storeCT) entry.setCreationTime(attr.creationTime().toMillis());
		if (storeAT) entry.setAccessTime(attr.lastAccessTime().toMillis());
		if (storeAttr) {
			try {
				DosFileAttributes dosAttr = Files.readAttributes(file.toPath(), DosFileAttributes.class);
				int flag = 0;
				if (dosAttr.isReadOnly()) flag |= 1;
				if (dosAttr.isHidden()) flag |= 2;
				if (dosAttr.isSystem()) flag |= 4;
				if (dosAttr.isArchive()) flag |= 32;
				if (dosAttr.isSymbolicLink()) flag |= 1024;
				entry.setAttributes(flag);
			} catch (IOException e) {
				System.err.println("Read DosAttributes for "+file+" failed");
			}
		}

		return entry;
	}

	static final class WordBlockAppend {
		QZCoder[] coders;
		QZEntry[] file;
		Consumer<QZWriter>[] data;
		long size;
	}

	public void compress(TaskPool pool, EasyProgressBar bar) throws IOException {
		File tmp;
		do {
			tmp = new File(outputFolder, outputName+"."+Integer.toString((int) System.nanoTime(),36)+".tmp");
		} while (tmp.isFile());

		long totalLength = 0;
		for (WordBlockAppend block : appends) {
			totalLength += block.size;
		}

		QZFileWriter writer;
		if (splitSize == 0) {
			IOUtil.allocSparseFile(tmp, totalLength);
			writer = new QZFileWriter(new FileSource(tmp));
		} else {
			// .tmp.001
			writer = new QZFileWriter(SplittedSource.fixedSize(tmp, splitSize));
		}

		if (bar != null) {
			bar.setName("1/4 复制未修改的文件");
			bar.updateForce(0);
			bar.addMax(keepSize);
		}

		// first copy unchanged
		for (Iterator<Map.Entry<WordBlock, List<QZEntry>>> itr = keep.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<WordBlock, List<QZEntry>> entry = itr.next();
			WordBlock block = entry.getKey();
			if (entry.getValue().size() == block.getFileCount()) {
				writer.copy(oldArchive, block);
				itr.remove();
				if (bar != null) bar.addCurrent((int)block.getuSize());
			}
		}

		for (Map.Entry<WordBlock, List<QZEntry>> entry : keep.entrySet()) {
			QZCoder[] coders;

			QzAES qzAes = password == null ? null : new QzAES(password, cryptPower, cryptSalt);
			if (entry.getKey().getuSize() <= entry.getKey().size()) {
				coders = new QZCoder[] {qzAes == null ? Copy.INSTANCE : qzAes};
			} else {
				QZCoder lzma2 = options.getMode() == LZMA2Options.MODE_UNCOMPRESSED ? Copy.INSTANCE : new LZMA2(options);
				if (useBCJ && EXECUTABLE_X86.contains(IOUtil.extensionName(entry.getValue().get(0).getName()))) {
					coders = getBcjCoder(qzAes, lzma2);
				} else {
					coders = qzAes == null ? new QZCoder[] {lzma2} : new QZCoder[] {lzma2, qzAes};
				}
			}

			pool.pushTask(() -> {
				try (QZArchive _in = oldArchive.parallel()) {
					try (QZWriter _out = parallel(writer)) {
						_out.setCodec(coders);

						List<QZEntry> value = entry.getValue();
						for (int i = 0; i < value.size(); i++) {
							QZEntry _file = value.get(i);

							_out.beginEntry(_file.clone());
							try (InputStream in = _in.getInput(_file)) {
								IOUtil.copyStream(in, _out);
							}
							if (bar != null) updateProgress(bar, _file);
						}
					}
				}
			});
		}
		pool.awaitFinish();

		if (oldArchive != null) {
			oldArchive.close();
			Files.delete(new File(outputFolder, outputName).toPath());
		}

		if (bar != null) {
			bar.reset();

			bar.setName("2/4 复制不压缩的文件");
			bar.addMax(totalLength);
			bar.updateForce(0);
		}

		// copy empty
		for (QZEntry empty : empties) writer.beginEntry(empty);

		// then copy uncompressed
		if (firstIsUncompressed) writeBlock(bar, appends.remove(0), writer);

		// and copy compressed
		AtomicInteger blockCompleted = new AtomicInteger();
		if (bar != null) {
			bar.setName("3/4 压缩("+blockCompleted+"/"+appends.size()+")");
			bar.updateForce(0);
		}

		for (WordBlockAppend task : appends) {
			pool.pushTask(() -> {
				try (QZWriter writer1 = parallel(writer)) {
					writeBlock(bar, task, writer1);
				}
				if (bar != null) bar.setName("3/4 压缩("+blockCompleted.incrementAndGet()+"/"+appends.size()+")");
			});
		}

		pool.awaitFinish();

		// finally write header
		if (bar != null) {
			bar.setName("4/4 写入文件头");
			bar.setHideBar(true);
			bar.setHideSpeed(true);
			bar.updateForce(0);
		}

		List<QZCoder> coders = new SimpleList<>();
		if (compressHeader) coders.add(new LZMA2(new LZMA2Options(9).setDictSize(524288)));
		if (password != null && encryptFileName) coders.add(new QzAES(password, cryptPower, cryptSalt));
		if (coders.isEmpty()) {
			writer.setCompressHeader(-1);
		} else {
			writer.setCodec(coders.toArray(new QZCoder[coders.size()]));
			writer.setCompressHeader(0);
		}

		writer.close();
		if (bar != null) bar.end("压缩成功");
		boolean b = tmp.renameTo(new File(outputFolder, outputName));
	}

	private QZWriter parallel(QZFileWriter qfw) throws IOException {
		return cacheFolder == null ? qfw.parallel() : qfw.parallel(new FileSource(File.createTempFile("qzx-", ".bin", cacheFolder)));
	}

	private void writeBlock(EasyProgressBar bar, WordBlockAppend block, QZWriter writer) throws IOException {
		writer.setCodec(block.coders);
		try {
			QZEntry[] file = block.file;
			for (int i = 0; i < file.length; i++) {
				writer.beginEntry(file[i]);
				block.data[i].accept(writer);

				if (bar != null) updateProgress(bar, file[i]);
			}
		} finally {
			writer.closeEntry();
		}
	}
	private static void updateProgress(EasyProgressBar bar, QZEntry entry) {
		long size = entry.getSize();
		while (size > Integer.MAX_VALUE) {
			bar.addCurrent(Integer.MAX_VALUE);
			size -= Integer.MAX_VALUE;
		}
		bar.addCurrent((int) size);
	}
}