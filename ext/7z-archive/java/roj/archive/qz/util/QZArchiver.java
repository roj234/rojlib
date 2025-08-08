package roj.archive.qz.util;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import roj.archive.ArchiveUtils;
import roj.archive.qz.*;
import roj.archive.xz.LZMA2Options;
import roj.collect.ArrayList;
import roj.collect.HashBiMap;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.concurrent.Executor;
import roj.concurrent.TaskGroup;
import roj.concurrent.TaskPool;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.io.source.CacheSource;
import roj.io.source.CompositeSource;
import roj.math.MathUtils;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.OS;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static roj.archive.WinAttributes.*;

/**
 * @author Roj234
 * @since 2023/6/4 3:54
 */
public class QZArchiver {
	private static final Logger LOGGER = Logger.getLogger();

	/**
	 * 待归档的目录列表 必须为目录
	 */
	public List<File> inputDirectories;
	//region 预处理
	/** 完全替换：完全覆盖目标归档文件 */
	public static final int UM_REPLACE = 0;
	/** 添加并替换文件：替换(不检测是否和源相同)归档中存在的文件，并添加新文件 */
	public static final int UM_REPLACE_EXISTING = 1;
	/** 更新并添加文件：更新源中已修改的文件，并添加新文件 */
	public static final int UM_UPDATE = 2;
	/** 只更新已存在的文件：更新源中已修改的文件，不添加新文件 */
	public static final int UM_UPDATE_EXISTING = 3;
	/** 同步压缩包内容：删除归档中未在源中出现的文件 */
	public static final int UM_SYNC = 4;
	/** 仅保留差异：仅保留源中被修改或删除的文件 */
	public static final int UM_DIFF = 5;
	/**
	 * 归档追加操作模式
	 * @see #UM_REPLACE
	 * @see #UM_REPLACE_EXISTING
	 * @see #UM_UPDATE
	 * @see #UM_UPDATE_EXISTING
	 * @see #UM_SYNC
	 * @see #UM_DIFF
	 */
	@MagicConstant(intValues = {UM_REPLACE_EXISTING, UM_UPDATE, UM_UPDATE_EXISTING, UM_SYNC, UM_REPLACE, UM_DIFF})
	public int updateMode;

	/**
	 * 启用快速追加检查（仅检查CRC32和修改时间，而不是完整比较内容）
	 */
	public boolean fastAppendCheck;

	/** 相对路径：相对于当前输入目录 */
	public static final int PF_RELATIVE = 0;
	/** 完整路径：相对于所有输入目录的最近共同祖先 */
	public static final int PF_FULL = 1;
	/** 绝对路径：系统绝对路径 */
	public static final int PF_ABSOLUTE = 2;
	/**
	 * 归档内路径存储格式
	 * @see #PF_RELATIVE
	 * @see #PF_FULL
	 * @see #PF_ABSOLUTE
	 */
	@MagicConstant(intValues = {PF_RELATIVE, PF_FULL, PF_ABSOLUTE})
	public int pathFormat;

	/**
	 * 是否启用两阶段压缩（提升压缩率但降低速度）
	 * <p><b>实验性：</b>速度慢的不可思议的同时还可能降低压缩率</p>
	 * @see ContentAwareChunkOrganizer
	 */
	public boolean twoPass;

	/**
	 * 文件路径排序比较器（null表示使用默认顺序）
	 */
	public Comparator<File> fileSorter;
	//endregion
	//region 属性存储/过滤
	/**
	 * 是否存储文件夹条目
	 */
	public boolean storeDirectories;
	/**
	 * 是否存储文件修改时间
	 */
	public boolean storeModifiedTime;
	/**
	 * 是否存储文件创建时间
	 */
	public boolean storeCreationTime;
	/**
	 * 是否存储文件访问时间
	 */
	public boolean storeAccessTime;
	/**
	 * 是否存储文件属性（如只读、隐藏等）
	 */
	public boolean storeAttributes;
	/**
	 * 是否检测并存储符号链接（软链接）
	 * <p>自定义格式与其他工具不兼容，只能使用本项目解压</p>
	 */
	public boolean storeSymbolicLinks;
	/**
	 * 是否检测并存储硬链接
	 * <p>自定义格式与其他工具不兼容，只能使用本项目解压</p>
	 */
	public boolean storeHardLinks;
	/**
	 * 是否仅处理带有"归档"属性的文件
	 * <p>仅在Windows系统上有意义</p>
	 */
	public boolean filterByArchiveAttribute;
	/**
	 * 压缩完成后是否清除文件的"归档"属性 (未实现)
	 * <p>仅在Windows系统上有意义</p>
	 */
	public boolean clearArchiveAttribute;
	//endregion
	//region 并行
	/**
	 * 压缩线程数
	 * <p>用于计算固实块大小</p>
	 * 如果设置为1并且线程池不是单线程的，会导致JVM崩溃
	 */
	public int threads;
	/**
	 * 是否自动计算固实块大小
	 * <p>启用时，将尝试生成恰好 {@link #threads} 个固实块</p>
	 */
	public boolean autoSolidSize;
	/**
	 * 手动设置的固实块大小（单位：字节）
	 * <p>仅当{@link #autoSolidSize}为false时生效</p>
	 * 0 为 非固实
	 * <0 为 完全固实
	 */
	public long solidSize;
	/**
	 * 自动拆分任务阈值（活动线程数低于此值时拆分任务）
	 * <p><b>限制：</b>仅支持LZMA2压缩算法，可能降低压缩率</p>
	 */
	public int autoSplitTaskThreshold;
	/**
	 * 任务拆分时使用的LZMA2配置参数
	 */
	public LZMA2Options autoSplitTaskOptions;
	//endregion
	//region 压缩
	/**
	 * 分卷压缩大小（单位：字节） 0禁用
	 */
	public long splitSize;

	/**
	 * LZMA2压缩算法参数
	 */
	@NotNull public LZMA2Options options = new LZMA2Options();
	/**
	 * 是否启用可执行文件跳转地址预处理器
	 * <p>目前仅支持X86体系结构的文件，如果不是，请勿开启，可能降低压缩率</p>
	 */
	public boolean useBCJ, useBCJ2;
	/**
	 * 压缩归档头 (建议启用)
	 */
	public boolean compressHeader = true;
	//endregion
	//region 加密
	/**
	 * 归档密码（null表示无加密）
	 */
	public String encryptionPassword;
	/**
	 * 加密文件名
	 */
	public boolean encryptFileName;
	/**
	 * 加密算法迭代次数（强度级别，通常19 ≈ 600000 iters）
	 */
	public int encryptionPower = 19;
	/**
	 * 盐值长度（字节数，0表示不使用盐值）
	 */
	public int encryptionSaltLength;
	//endregion
	//region 输出
	/**
	 * 文件缓存目录（null表示禁用文件缓存）
	 * <p>在与某些特定参数组合并压缩大文件时可能消耗很多内存甚至失败</p>
	 */
	public File cacheDirectory;
	/**
	 * 输出目录（用于存放生成的归档文件）
	 */
	@NotNull public File outputDirectory;
	/**
	 * 输出文件名（不含路径，例如"archive.7z"）
	 * <p>与{@link #outputDirectory}组合形成完整输出路径</p>
	 */
	@NotNull public String outputFilename;
	/**
	 * 是否保留原始归档文件（对部分追加操作模式无效 - 因为直接修改原始文件有检测&优化）
	 * 如果保留，那么请处理compress函数返回的File，它是临时文件
	 */
	public boolean keepOldArchive;
	//endregion

	private QZArchive oldArchive;
	private long keepSize;

	private final HashBiMap<String, File> byPath = new HashBiMap<>();
	private final Map<WordBlock, List<QZEntry>> keep = new HashMap<>();
	private final List<BlockData> appends = new ArrayList<>();
	private final List<QZEntry> empties = new ArrayList<>();
	private final List<QZEntry> symbolicLinks = new ArrayList<>();
	private final List<String> symbolicLinkValues = new ArrayList<>();
	private final Map<String, QZEntry> hardlinkRef = new HashMap<>();
	private boolean firstIsUncompressed;

	private static final HashSet<String> UNCOMPRESSED = ArchiveUtils.INCOMPRESSIBLE_FILE_EXT;
	private static final HashSet<String> EXECUTABLE_X86 = new HashSet<>("exe", "dll", "sys", "so");

	public long prepare() throws IOException {
		firstIsUncompressed = false;
		appends.clear();
		keep.clear();
		empties.clear();

		List<File> paths = inputDirectories;
		String inAbsPath = new File(outputDirectory, outputFilename).getAbsolutePath();
		int[] prefix = new int[1];

		Map<String, File> byPath = this.byPath; byPath.clear();
		List<File>
			uncompressed = new ArrayList<>(),
			executable = new ArrayList<>(),
			compressed = new ArrayList<>(),
			emptyOrFolder = new ArrayList<>();
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

		if (fileSorter != null) {
			compressed.sort(fileSorter);
			executable.sort(fileSorter);
			uncompressed.sort(fileSorter);
		}

		if (pathFormat == PF_FULL) {
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
				if (pathFormat == PF_RELATIVE) prefix[0] = path.getAbsolutePath().length() - path.getName().length();
				callback.accept(path);
			} else {
				if (pathFormat == PF_RELATIVE) prefix[0] = path.getAbsolutePath().length()+1;
				traverseFolder(path, callback, emptyOrFolder);
			}
		}

		File out = new File(outputDirectory, outputFilename.concat(splitSize == 0 ? "" : ".001"));
		if (out.isFile()) {
			var io = ArchiveUtils.tryOpenSplitArchive(out, false);
			try {
				oldArchive = new QZArchive(io, encryptionPassword);
			} catch (Exception e) {
				IOUtil.closeSilently(io);
				throw e;
				//e.printStackTrace();
			}
		}

		keep.clear();
		keepSize = 0;
		switch (updateMode) {
			case UM_REPLACE -> {} // 重新建立压缩包
			case UM_REPLACE_EXISTING -> { // 添加并替换文件
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						if (!byPath.containsKey(entry.getName())) {
							keepEntry(entry);
						}
					}
				}
			}
			case UM_UPDATE -> { // 更新并添加文件
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
			case UM_UPDATE_EXISTING -> { // 只更新已存在的文件
				if (oldArchive == null) return -1;
				HashMap<String, QZEntry> inEntry = oldArchive.getEntries();
				for (Iterator<String> itr = byPath.keySet().iterator(); itr.hasNext(); ) {
					if (null == inEntry.remove(itr.next())) itr.remove();
				}
				for (QZEntry value : inEntry.values()) keepEntry(value);
			}
			case UM_SYNC -> { // 同步压缩包内容
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
			case UM_DIFF -> { // 仅保留差异的文件
				if (oldArchive != null) {
					for (QZEntry entry : oldArchive.getEntriesByPresentOrder()) {
						File file = byPath.get(entry.getName());
						if (file == null) {
							entry.setName("@deleted/"+entry.getName());
							keepEntry(entry);
						} else if (isSame(file, entry)) {
							byPath.remove(entry.getName());
						} else {
							entry.setName("@modified/"+entry.getName());
							keepEntry(entry);
						}
					}
				}
			}
		}

		// EMPTY
		for (int i = 0; i < emptyOrFolder.size(); i++) {
			QZEntry entry = entryFor(emptyOrFolder.get(i));
			if (entry != null) empties.add(entry);
		}

		List<Object> tmpa = new ArrayList<>();
		List<QZEntry> tmpb = new ArrayList<>();

		QzAES qzAes = encryptionPassword == null ? null : new QzAES(encryptionPassword, encryptionPower, encryptionSaltLength);

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

		if (twoPass) makeBlockContentAware(compressed, chunkSize, coders, tmpb);
		else makeBlock(compressed, chunkSize, coders, tmpa, tmpb);

		makeBlock(executable, chunkSize, useBCJ ? getBcjCoder(qzAes, lzma2) : coders, tmpa, tmpb);

		hardlinkRef.clear();
		if (!symbolicLinks.isEmpty()) {
			List<String> contents = symbolicLinkValues;
			appendBinary(coders, contents, symbolicLinks);
			contents.clear();
		}

		return chunkSize;
	}

	public void appendBinary(QZCoder[] coders, List<?> contents, List<QZEntry> entries) {
		BlockData block = new BlockData();

		block.coders = coders;
		block.data = contents.toArray(new Object[contents.size()]);
		block.entries = entries.toArray(new QZEntry[entries.size()]);
		block.size = entries.size();

		appends.add(block);
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
				if (storeDirectories) {
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
			int crc = CRC32.initial;
			while (true) {
				int r = in.read(data);
				if (r < 0) break;
				crc = CRC32.update(crc, data, 0, r);
			}
			crc = CRC32.finish(crc);
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
	private void makeBlockContentAware(List<File> compressed, long chunkSize, QZCoder[] coders, List<QZEntry> tmpb) {
		for (List<File> files : ContentAwareChunkOrganizer.organizeChunks(compressed, chunkSize)) {
			long size = 0;
			for (int i = 0; i < files.size(); i++) {
				File file = files.get(i);
				QZEntry entry = entryFor(file);
				if (entry != null) {
					tmpb.add(entry);
					size += file.length();
				} else {
					files.remove(i--);
				}
			}

			addBlock(coders, Helpers.cast(files), tmpb, size);
		}
	}
	private void addBlock(QZCoder[] coders, List<Object> tmpa, List<QZEntry> tmpb, long size) {
		BlockData block = new BlockData();
		block.coders = coders;
		block.data = tmpa.toArray(new Object[tmpa.size()]);
		block.entries = tmpb.toArray(new QZEntry[tmpb.size()]);
		block.size = size;
		appends.add(block);

		tmpa.clear();
		tmpb.clear();
	}

	private static final LinkOption[] READ_LINK = {LinkOption.NOFOLLOW_LINKS}, FOLLOW_LINK = new LinkOption[0];
	@SuppressWarnings("unchecked")
	private QZEntry entryFor(File file) {
		String name = byPath.getByValue(file);
		if (null == name) return null;

		BasicFileAttributes attr;
		try {
			attr = Files.readAttributes(file.toPath(), (Class<BasicFileAttributes>) (OS.CURRENT == OS.WINDOWS ? DosFileAttributes.class : BasicFileAttributes.class), storeSymbolicLinks ? READ_LINK : FOLLOW_LINK);
		} catch (IOException e) {
			LOGGER.error("ReadAttributes for "+file+" failed", e);
			return null;
		}

		QZEntry entry = QZEntry.of(name);

		if (attr.isDirectory()) entry.setIsDirectory(true);
		if (storeModifiedTime) entry.setModificationTime(attr.lastModifiedTime().toMillis());
		if (storeCreationTime) entry.setCreationTime(attr.creationTime().toMillis());
		if (storeAccessTime) entry.setAccessTime(attr.lastAccessTime().toMillis());

		if (storeSymbolicLinks) {
			if (attr.isSymbolicLink()) {
				try {
					String linkTarget = Files.readSymbolicLink(file.toPath()).toString();
					entry.setAttributes(entry.getWinAttributes() | FILE_ATTRIBUTE_REPARSE_POINT);

					symbolicLinks.add(entry);
					symbolicLinkValues.add(linkTarget);
					return null;
				} catch (IOException e) {
					LOGGER.error("Read SymbolLink for "+file+" failed", e);
				}
			}
		}

		if (storeHardLinks) {
			String uuid = IOUtil.getHardLinkUUID(file.getAbsolutePath());
			if (uuid != null) {
				QZEntry prevEntry = hardlinkRef.putIfAbsent(uuid, entry);
				if (prevEntry != null) {
					entry.setAttributes(entry.getWinAttributes() | FILE_ATTRIBUTE_REPARSE_POINT | FILE_ATTRIBUTE_NORMAL);

					symbolicLinks.add(entry);
					symbolicLinkValues.add(prevEntry.getName());
					return null;
				}
			}
		}

		if (!(attr instanceof DosFileAttributes dosAttr)) return entry;

		if (filterByArchiveAttribute) {
			if (!dosAttr.isArchive()) return null;
		}

		if (storeAttributes) {
			int flag = 0;
			if (dosAttr.isDirectory()) flag |= FILE_ATTRIBUTE_DIRECTORY;
			if (dosAttr.isReadOnly()) flag |= FILE_ATTRIBUTE_READONLY;
			if (dosAttr.isHidden()) flag |= FILE_ATTRIBUTE_HIDDEN;
			if (dosAttr.isSystem()) flag |= FILE_ATTRIBUTE_SYSTEM;
			if (dosAttr.isArchive()) flag |= FILE_ATTRIBUTE_ARCHIVE;
			if (dosAttr.isSymbolicLink()) flag |= FILE_ATTRIBUTE_REPARSE_POINT;
			entry.setAttributes(flag);
		}

		return entry;
	}

	static final class BlockData {
		QZCoder[] coders;
		QZEntry[] entries;
		Object[] data;
		long size;
	}

	private TaskGroup group;
	public void interrupt() {
		var w = group;
		if (w != null) w.cancel(true);
	}
	public File compress(Executor th, EasyProgressBar bar) throws IOException {
		group = th.newGroup();
		QZFileWriter writer;
		File tmp;

		long totalUncompressedSize = 0;
		for (BlockData block : appends) {
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
						HashSet<QZEntry> set = new HashSet<>(emptyFiles);

						ArrayList<QZEntry> files = writer.getEmptyFiles();
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
				tmp = new File(outputDirectory, outputFilename +"."+Integer.toString((int) System.nanoTime()&Integer.MAX_VALUE,36)+".tmp");
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

			QzAES qzAes = encryptionPassword == null ? null : new QzAES(encryptionPassword, encryptionPower, encryptionSaltLength);
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

			group.executeUnsafe(() -> {
				try (QZReader _in = oldArchive.parallel()) {
					try (QZWriter _out = parallel(writer)) {
						_out.setCodec(coders);

						List<QZEntry> value = entry.getValue();
						for (int i = 0; i < value.size(); i++) {
							QZEntry _file = value.get(i);

							_out.beginEntry(_file.clone());
							try (InputStream in = _in.getInput(_file)) {
								copyStreamWithProgress(in, _out, bar);
							}
						}
					}
				}
			});
		}
		group.await();

		if (oldArchive != null) {
			oldArchive.close();
			if (!keepOldArchive)
				Files.delete(new File(outputDirectory, outputFilename).toPath());
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

		writer.flush();
		writer.setIgnoreClose(true);
		for (int i = 0; i < appends.size(); i++) {
			int myi = i;
			group.executeUnsafe(() -> {
				try (QZWriter writer1 = /*myi == 0 ? writer : */parallel(writer)) {
					writeBlock(bar, appends.get(myi), writer1, group);
				}
				if (bar != null) bar.setName("3/4 压缩("+blockCompleted.incrementAndGet()+"/"+appends.size()+")");
			});

			if (!symbolicLinks.isEmpty() && i == appends.size()-2) {
				group.await();
			}
		}
		group.await();

		// finally write header
		if (bar != null) bar.setName("4/4 写入文件头");

		List<QZCoder> coders = new ArrayList<>();
		if (compressHeader) coders.add(new LZMA2(new LZMA2Options(9).setDictSize(524288)));
		if (encryptionPassword != null && encryptFileName) coders.add(new QzAES(encryptionPassword, encryptionPower, encryptionSaltLength));
		if (coders.isEmpty()) {
			writer.setCompressHeader(-1);
		} else {
			writer.setCodec(coders.toArray(new QZCoder[coders.size()]));
			writer.setCompressHeader(0);
		}

		} finally {
			writer.setIgnoreClose(false);
			writer.close();
			if (tmp != null && !keepOldArchive) Files.move(tmp.toPath(), new File(outputDirectory, outputFilename).toPath());
			if (bar != null) bar.end(group.isCancelled() ? "部分成功" : "成功");
		}

		return tmp;
	}

	private QZWriter parallel(QZFileWriter qfw) throws IOException {
		return threads == 1 ? qfw :
			cacheDirectory == null ? qfw.newParallelWriter() : qfw.newParallelWriter(new CacheSource(1048576, 134217728, "qzx-", cacheDirectory));
	}

	private void writeBlock(EasyProgressBar bar, BlockData block, QZWriter writer, TaskGroup canSplit) throws IOException {
		writer.setCodec(block.coders);
		QZEntry[] file = block.entries;
		for (int i = 0; i < file.length; i++) {
			if (group.isCancelled()) return;

			writer.beginEntry(file[i]);
			if (block.data[i] instanceof File data) {
				try (FileInputStream in = new FileInputStream(data)) {
					copyStreamWithProgress(in, writer, bar);
				} catch (InterruptedIOException e) {
					group.cancel();
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else if (block.data[i] instanceof DynByteBuf buf) {
				buf.writeToStream(writer);
				buf.release();
				bar.increment();
			} else {
				IOUtil.getSharedByteBuf().putUTFData(block.data[i].toString()).writeToStream(writer);
				bar.increment();
			}

			if (autoSplitTaskThreshold > 0 && canSplit != null && ((TaskPool)canSplit.owner()).busyCount() <= autoSplitTaskThreshold) {
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

	public static void copyStreamWithProgress(InputStream in, OutputStream out, EasyProgressBar bar) throws IOException {
		byte[] tmp = ArrayCache.getByteArray(65536, false);
		try {
			while (true) {
				if (Thread.interrupted()) throw new InterruptedIOException();

				int len = in.read(tmp);
				if (len < 0) break;
				out.write(tmp, 0, len);

				if (bar != null) bar.increment(len);
			}
		} finally {
			ArrayCache.putArray(tmp);
		}
	}
}