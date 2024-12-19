package roj.archive.ui;

import roj.archive.ArchiveUtils;
import roj.archive.qz.*;
import roj.archive.qz.xz.LZMA2Options;
import roj.collect.HashBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.io.source.CacheSource;
import roj.io.source.CompositeSource;
import roj.math.MathUtils;
import roj.text.TextUtil;
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
import java.util.Comparator;
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
	public static final int AOP_REPLACE = 0, AOP_UPDATE = 1, AOP_UPDATE_EXISTING = 2, AOP_SYNC = 3, AOP_IGNORE = 4, AOP_DIFF = 5;
	public static final int PATH_RELATIVE = 0, PATH_FULL = 1, PATH_ABSOLUTE = 2;

	public List<File> input;
	public boolean storeFolder, storeMT, storeCT, storeAT, storeAttr;
	public int threads;
	public boolean autoSolidSize;
	public long solidSize, splitSize;
	public boolean compressHeader;
	public String password;
	public int cryptPower, cryptSalt;
	public boolean encryptFileName;
	public boolean useBCJ, useBCJ2;
	public LZMA2Options options;
	public boolean fastAppendCheck;
	public int appendOptions, pathType;
	public File cacheFolder;
	public File outputFolder;
	public String outputName;
	public boolean keepArchive;
	public Comparator<File> sorter;

	public int autoSplitTaskSize;
	public LZMA2Options autoSplitTaskOptions;

	private QZArchive oldArchive;
	private long keepSize;

	private final HashBiMap<String, File> byPath = new HashBiMap<>();
	private final Map<WordBlock, List<QZEntry>> keep = new MyHashMap<>();
	private final List<WordBlockAppend> appends = new SimpleList<>();
	private final List<QZEntry> empties = new SimpleList<>();
	private boolean firstIsUncompressed;

	private static final MyHashSet<String> UNCOMPRESSED = ArchiveUtils.INCOMPRESSIBLE_FILE_EXT;
	private static final MyHashSet<String> EXECUTABLE_X86 = new MyHashSet<>("exe", "dll", "sys", "so");

	public long prepare() throws IOException {
		firstIsUncompressed = false;
		appends.clear();
		keep.clear();
		empties.clear();

		List<File> paths = input;
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

			String ext = IOUtil.extensionName(file.getName());
			if (UNCOMPRESSED.contains(ext)) uncompressed.add(file);
			else {
				if (useBCJ && EXECUTABLE_X86.contains(ext)) executable.add(file);
				else compressed.add(file);

				compressedLen[0] += file.length();
			}
		};

		if (sorter != null) {
			compressed.sort(sorter);
			executable.sort(sorter);
			uncompressed.sort(sorter);
		}

		if (pathType == PATH_FULL) {
			String shortestCommonParent = paths.get(0).getAbsolutePath();
			int prefixLength = shortestCommonParent.length();
			for (int i = 1; i < paths.size(); i++) {
				String path = paths.get(i).getAbsolutePath();
				for (int j = 0; j < Math.min(prefixLength, path.length()); j++) {
					if (path.charAt(j) != shortestCommonParent.charAt(j)) {
						prefixLength = j;
						break;
					}
				}
			}
			prefix[0] = prefixLength;
		}
		for (File path : paths) {
			if (path.isFile()) {
				if (pathType == PATH_RELATIVE) prefix[0] = path.getAbsolutePath().length() - path.getName().length();
				callback.accept(path);
			} else {
				if (pathType == PATH_RELATIVE) prefix[0] = path.getAbsolutePath().length()+1;
				traverseFolder(path, callback, emptyOrFolder);
			}
		}

		File out = new File(outputFolder, outputName.concat(splitSize == 0 ? "" : ".001"));
		if (out.isFile()) {
			try {
				oldArchive = new QZArchive(ArchiveUtils.tryOpenSplitArchive(out, false), password);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		keep.clear();
		keepSize = 0;
		switch (appendOptions) {
			case AOP_REPLACE: {
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
			case AOP_UPDATE: {
				// 更新并添加文件
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						File file = byPath.get(entry.getName());
						if (file == null || isSame(file, entry)) {
							byPath.remove(entry.getName());
							keepEntry(entry);
						}
					}
				}
			}
			break;
			case AOP_UPDATE_EXISTING: {
				// 只更新已存在的文件
				if (oldArchive == null) return -1;
				MyHashMap<String, QZEntry> inEntry = oldArchive.getEntries();
				for (Iterator<String> itr = byPath.keySet().iterator(); itr.hasNext(); ) {
					if (null == inEntry.remove(itr.next())) itr.remove();
				}
				for (QZEntry value : inEntry.values()) keepEntry(value);
			}
			break;
			case AOP_SYNC: {
				// 同步压缩包内容
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						File file = byPath.get(entry.getName());
						if (file != null && isSame(file, entry)) {
							byPath.remove(entry.getName());
							keepEntry(entry);
						}
					}
				}
			}
			break;
			case AOP_IGNORE: // 重新建立压缩包
				break;
			case AOP_DIFF: // 仅保留差异的文件
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						File file = byPath.get(entry.getName());
						if (file == null) {
							entry.setName("@@was_deleted/"+entry.getName());
							keepEntry(entry);
						} else if (isSame(file, entry)) {
							byPath.remove(entry.getName());
						} else {
							entry.setName("@@was_changed/"+entry.getName());
							keepEntry(entry);
						}
					}
				}
		}

		// EMPTY
		for (int i = 0; i < emptyOrFolder.size(); i++) {
			QZEntry entry = entryFor(emptyOrFolder.get(i));
			if (entry != null) empties.add(entry);
		}

		List<Object> tmpa = new SimpleList<>();
		List<QZEntry> tmpb = new SimpleList<>();

		QzAES qzAes = password == null ? null : new QzAES(password, cryptPower, cryptSalt);

		boolean isUncompressed = options.getMode() == LZMA2Options.MODE_UNCOMPRESSED;
		if (isUncompressed) {
			uncompressed.addAll(compressed);
			uncompressed.addAll(executable);
		}

		// UNCOMPRESSED
		if (!uncompressed.isEmpty()) {
			long size = 0;
			firstIsUncompressed = true;
			for (File file : uncompressed) {
				QZEntry entry = entryFor(file);
				if (entry != null) {
					tmpa.add(file);
					tmpb.add(entry);
					size += file.length();
				}
			}

			addBlock(new QZCoder[] {qzAes == null ? Copy.INSTANCE : qzAes}, tmpa, tmpb, size);
		}
		if (isUncompressed) return Long.MAX_VALUE;

		long chunkSize = autoSolidSize ? MathUtils.clamp(compressedLen[0] / threads, LZMA2Options.ASYNC_BLOCK_SIZE_MIN, LZMA2Options.ASYNC_BLOCK_SIZE_MAX) : solidSize;
		if (chunkSize < 0) chunkSize = Long.MAX_VALUE;

		QZCoder lzma2 = new LZMA2(options);
		QZCoder[] coders = qzAes == null ? new QZCoder[] {lzma2} : new QZCoder[] {lzma2, qzAes};
		makeBlock(compressed, chunkSize, coders, tmpa, tmpb);

		if (useBCJ) coders = getBcjCoder(qzAes, lzma2);
		makeBlock(executable, chunkSize, coders, tmpa, tmpb);

		return chunkSize;
	}

	private boolean isSame(File file, QZEntry entry) {
		return file.length() == entry.getSize() &&
			(!fastAppendCheck ? contentSame(entry, file) :
				(entry.hasModificationTime() && file.lastModified() <= entry.getModificationTime()) ||
					(entry.hasCrc32() && checkCrc32(file, entry.getCrc32())));
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
		WordBlock block = entry.block();
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
			int crc = CRC32s.INIT_CRC;
			while (true) {
				int r = in.read(data);
				if (r < 0) break;
				crc = CRC32s.update(crc, data, 0, r);
			}
			crc = CRC32s.retVal(crc);
			return crc == crc2;
		} catch (Exception e) {
			return false;
		} finally {
			ArrayCache.putArray(data);
		}
	}

	private void makeBlock(List<File> compressed, long chunkSize, QZCoder[] coders, List<Object> tmpa, List<QZEntry> tmpb) {
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

				tmpa.add(file);
				tmpb.add(entry);
			}

		}
		if (!tmpa.isEmpty()) addBlock(coders, tmpa, tmpb, size);
	}
	private void addBlock(QZCoder[] coders, List<Object> tmpa, List<QZEntry> tmpb, long size) {
		WordBlockAppend block = new WordBlockAppend();
		block.coders = coders;
		block.data = Helpers.cast(tmpa.toArray(new Object[tmpa.size()]));
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

		QZEntry entry = QZEntry.of(name);

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
		Object[] data;
		long size;
	}

	private Thread worker;
	public void interrupt() {
		Thread w = worker;
		if (w != null) w.interrupt();
	}
	public void compress(TaskPool pool, EasyProgressBar bar) throws IOException {
		worker = Thread.currentThread();
		QZFileWriter writer;
		File tmp;

		long totalUncompressedSize = 0;
		for (WordBlockAppend block : appends) {
			totalUncompressedSize += block.size;
		}

		createNewQZFW: {
			if (keepSize > 0) {
				long mySize = 0;
				for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
					mySize += entry.getSize();
				}

				if (keepSize == mySize) {
					System.out.println("FastPath: 尾部追加");

					writer = oldArchive.append();

					List<QZEntry> emptyFiles = keep.get(null);
					if (emptyFiles != null) {
						MyHashSet<QZEntry> set = new MyHashSet<>(emptyFiles);

						SimpleList<QZEntry> files = writer.getEmptyFiles();
						for (int i = files.size() - 1; i >= 0; i--) {
							QZEntry file = files.get(i);
							if (!set.contains(file)) {
								writer.removeEmptyFile(i);
							}
						}
					}

					tmp = null;
					keep.clear();
					keepSize = 0;
					oldArchive = null;
					break createNewQZFW;
				}
			}

			do {
				tmp = new File(outputFolder, outputName+"."+Integer.toString((int) System.nanoTime()&Integer.MAX_VALUE,36)+".tmp");
			} while (tmp.isFile());

			if (splitSize == 0) {
				if (totalUncompressedSize > 1073741823) IOUtil.createSparseFile(tmp, totalUncompressedSize);
				writer = new QZFileWriter(tmp);
			} else {
				// .tmp.001
				writer = new QZFileWriter(CompositeSource.fixed(tmp, splitSize));
			}
		}

		if (bar != null) {
			bar.setName("1/4 复制未修改的文件");
			bar.setUnit("B");
			bar.setProgress(0);
			bar.addTotal(keepSize);
		}

		try {
		// first copy unchanged
		for (Iterator<Map.Entry<WordBlock, List<QZEntry>>> itr = keep.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<WordBlock, List<QZEntry>> entry = itr.next();
			WordBlock block = entry.getKey();
			if (entry.getValue().size() == block.getFileCount()) {
				writer.copy(oldArchive, block);
				itr.remove();
				if (bar != null) bar.increment((int)block.getuSize());
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

			pool.submit(() -> {
				try (QZReader _in = oldArchive.parallel()) {
					try (QZWriter _out = parallel(writer)) {
						_out.setCodec(coders);

						List<QZEntry> value = entry.getValue();
						for (int i = 0; i < value.size(); i++) {
							QZEntry _file = value.get(i);

							_out.beginEntry(_file.clone());
							try (InputStream in = _in.getInput(_file)) {
								QZUtils.copyStreamWithProgress(in, _out, bar);
							}
						}
					}
				}
			});
		}
		pool.awaitFinish();

		if (oldArchive != null) {
			oldArchive.close();
			if (!keepArchive)
				Files.delete(new File(outputFolder, outputName).toPath());
		}

		if (bar != null) {
			bar.setName("2/4 复制不压缩的文件");
			bar.setTotal(totalUncompressedSize);
			bar.setProgress(0);
		}

		// copy empty
		for (QZEntry empty : empties) writer.beginEntry(empty);

		// then copy uncompressed
		if (firstIsUncompressed) writeBlock(bar, appends.remove(0), writer, null);

		// and copy compressed
		AtomicInteger blockCompleted = new AtomicInteger();
		if (bar != null) {
			bar.setName("3/4 压缩("+blockCompleted+"/"+appends.size()+")");
			bar.setProgress(0);
		}

		writer.closeWordBlock();
		writer.setIgnoreClose(true);
		for (int i = 0; i < appends.size(); i++) {
			int myi = i;
			pool.submit(() -> {
				try (QZWriter writer1 = /*myi == 0 ? writer : */parallel(writer)) {
					writeBlock(bar, appends.get(myi), writer1, pool);
				}
				if (bar != null) bar.setName("3/4 压缩("+blockCompleted.incrementAndGet()+"/"+appends.size()+")");
			});
		}

		pool.awaitFinish();

		// finally write header
		if (bar != null) bar.setName("4/4 写入文件头");

		List<QZCoder> coders = new SimpleList<>();
		if (compressHeader) coders.add(new LZMA2(new LZMA2Options(9).setDictSize(524288)));
		if (password != null && encryptFileName) coders.add(new QzAES(password, cryptPower, cryptSalt));
		if (coders.isEmpty()) {
			writer.setCompressHeader(-1);
		} else {
			writer.setCodec(coders.toArray(new QZCoder[coders.size()]));
			writer.setCompressHeader(0);
		}

		} finally {
			writer.setIgnoreClose(false);
			writer.close();
			if (tmp != null && !keepArchive) Files.move(tmp.toPath(), new File(outputFolder, outputName).toPath());
			if (bar != null) bar.end("压缩成功");
		}
	}

	private QZWriter parallel(QZFileWriter qfw) throws IOException {
		return threads == 1 ? qfw :
			cacheFolder == null ? qfw.parallel() : qfw.parallel(new CacheSource(1048576, 134217728, "qzx-", cacheFolder));
	}

	private void writeBlock(EasyProgressBar bar, WordBlockAppend block, QZWriter writer, TaskPool canSplit) throws IOException {
		writer.setCodec(block.coders);
		QZEntry[] file = block.file;
		for (int i = 0; i < file.length; i++) {
			writer.beginEntry(file[i]);
			File data = (File) block.data[i];
			try (FileInputStream in = new FileInputStream(data)) {
				QZUtils.copyStreamWithProgress(in, writer, bar);
			}

			if (canSplit != null && canSplit.busyCount() <= autoSplitTaskSize) {
				QZCoder[] copyCoder = block.coders.clone();
				for (int j = 0; j < copyCoder.length; j++) {
					QZCoder coder = copyCoder[j];
					if (coder instanceof LZMA2) {
						copyCoder[j] = new LZMA2(autoSplitTaskOptions);
						System.out.println("正在拆分任务 "+TextUtil.scaledNumber1024(block.size));
					}
				}
				writer.setCodec(copyCoder);
				canSplit = null;
			}
		}
	}
}