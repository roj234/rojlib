package roj.plugins.minecraft.diff;

import roj.archive.qz.*;
import roj.archive.xz.LZMA2Options;
import roj.archive.xz.LZMAOutputStream;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.concurrent.TaskGroup;
import roj.concurrent.TaskPool;
import roj.config.NbtEncoder;
import roj.config.NbtParser;
import roj.crypt.CRC32;
import roj.crypt.CryptoFactory;
import roj.crypt.KeyType;
import roj.io.*;
import roj.text.TextUtil;
import roj.text.diff.BsDiff;
import roj.ui.EasyProgressBar;
import roj.util.ArrayCache;
import roj.util.ByteList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.GZIPInputStream;

/**
 * @author Roj234
 * @since 2023/12/21 1:30
 */
public final class McDiffServer {
	static final byte REGION = 1, GZIP = 2, DIFF = 3;
	private static final EasyProgressBar bar = new EasyProgressBar("压缩文件");

	private final KeyPair userCert;
	public McDiffServer(KeyPair kp) {userCert = kp;}

	public void makeDiff(File fullPack, File directory, Predicate<File> filter, File diffFile) throws IOException {
		QZArchive pack = new QZArchive(fullPack);

		List<String> empty = new ArrayList<>();
		HashSet<String> added = new HashSet<>();
		HashMap<String, String> moved = new HashMap<>();
		List<String> deleted = new ArrayList<>();

		var pool = TaskPool.common().newGroup();
		//TaskPool.MaxThread(Runtime.getRuntime().availableProcessors(), "MCDiff-CRC32-Worker");

		computeDiff(directory, filter, pack, empty, added, deleted, moved, pool);

		QZFileWriter qzfw = new QZFileWriter(diffFile.getAbsolutePath());

		System.out.println("正在处理删除 (2/4)");
		for (String path : deleted) qzfw.beginEntry(QZEntry.ofNoAttribute(".vcs/"+path));
		for (Map.Entry<String, String> entry : moved.entrySet()) {
			qzfw.beginEntry(QZEntry.ofNoAttribute(".vcs/"+entry.getKey()));
			qzfw.write(entry.getValue().getBytes(StandardCharsets.UTF_16LE));
		}
		qzfw.flush();
		for (String path : empty) {
			qzfw.beginEntry(QZEntry.ofNoAttribute(path));
		}

		int affinity = Runtime.getRuntime().availableProcessors();
		long myMem = (1L<<24) * affinity;
		System.out.println("Allocating "+TextUtil.scaledNumber1024(myMem)+" of memory");
		LZMA2Options opt = new LZMA2Options();
		opt.setAsyncMode(1<<24, TaskPool.common(), affinity, new BufferPool(myMem,0,myMem, 0,0, 0, 10,0), LZMA2Options.ASYNC_DICT_NONE);
		QZWriter genericParallel = qzfw.newParallelWriter();
		genericParallel.setCodec(new LZMA2(opt));

		System.out.println("正在处理新增 (3/4)");
		bar.setTotal(added.size());
		for (String path : added) {
			File file = new File(directory, path);
			QZEntry entry = QZEntry.of(path);

			int flag = 0;

			checkSpecialFileType:
			if (path.contains(".dat") || path.contains(".nbt")) {
				byte[] tmp = ArrayCache.getByteArray(4096, false);

				try (InputStream in = new GZIPInputStream(new FileInputStream(file))) {
					while (true) {
						int r = in.read(tmp);
						if (r < 0) break;
					}
				} catch (Exception ignored) {
					break checkSpecialFileType;
				} finally {
					ArrayCache.putArray(tmp);
				}

				entry.setModificationTime(GZIP);
				flag = GZIP;
				try (InputStream in = new GZIPInputStream(new FileInputStream(file))) {
					genericParallel.beginEntry(entry);
					IOUtil.copyStream(in, genericParallel);
				}
				bar.increment(1);
			} else if (path.contains(".mca")) {
				RegionFile rin;
				try {
					rin = new RegionFile(file);
				} catch (Exception ignored) {
					break checkSpecialFileType;
				}

				entry.setModificationTime(REGION);
				flag = REGION;

				File tempFile = null;
				RegionFile prevRin = null;
				try {
					var in = pack.getStream(path);
					if (in != null) {
						// 加载会失败的，如果对于diff
						prevRin = new RegionFile(tempFile = File.createTempFile("mcDiffChunkTemp", ".tmp"));
						try (var in1 = new MyDataInputStream(in)) {
							while (in1.isReadable()) {
								int block = in1.readShort();
								int timestamp = in1.readInt();
								int length = in1.readInt();
								if (length != 0) {
									IOUtil.skipFully(in1, length);
									continue;
								}

								var buf = IOUtil.getSharedByteBuf();
								new NbtParser().parse(in1, 0, new NbtEncoder(buf));
								prevRin.write(block, buf);
								prevRin.setTimestamp(block, timestamp);
							}
						}
					}
				} catch (Exception ignored) {}

				RegionFile javac傻逼 = prevRin;
				File javac大傻逼 = tempFile;
				QZWriter w = qzfw.newParallelWriter();
				w.setCodec(new LZMA2(7));
				w.beginEntry(entry);

				pool.executeUnsafe(() -> {
					try (ByteList.ToStream out = new ByteList.ToStream(w)) {
						for (int i = 0; i < 1024; i++) {
							if (!rin.hasData(i)) continue;

							var din = rin.getInputStream(i);
							if (javac傻逼 != null && javac傻逼.hasData(i)) {
								if (compare(javac傻逼.getInputStream(i), din)) continue;

								byte[] l = IOUtil.read(javac傻逼.getInputStream(i));
								byte[] r = IOUtil.read(rin.getInputStream(i));

								BsDiff diff = new BsDiff();
								diff.setLeft(l);
								ByteList patch = IOUtil.getSharedByteBuf();
								diff.makePatch(r, patch);

								DummyOutputStream tmp2 = new DummyOutputStream();
								try (OutputStream tmp3 = new LZMAOutputStream(tmp2, new LZMA2Options(), patch.readableBytes())) {
									patch.writeToStream(tmp3);
								}

								if (tmp2.wrote < file.length()/2) {
									out.putShort(i).putInt(rin.getTimestamp(i)).putInt(patch.readableBytes());
									out.flush();
									patch.writeToStream(w);
									continue;
								}
							}

							out.putShort(i).putInt(rin.getTimestamp(i)).putInt(0);
							out.flush();

							try {
								IOUtil.copyStream(din, w);
							} finally {
								IOUtil.closeSilently(din);
							}
						}
						bar.increment(1);
					} finally {
						IOUtil.closeSilently(javac傻逼);
						IOUtil.closeSilently(rin);
						if (javac大傻逼 != null) javac大傻逼.delete();
					}
				});
			}

			if (flag == 0) try (FileInputStream in = new FileInputStream(file)) {
				genericParallel.beginEntry(entry);
				try {
					IOUtil.copyStream(in, genericParallel);
				} catch (Exception e) {
					System.out.println("Warning: failed read file");
					e.printStackTrace();
					entry.setName("出现异常/"+entry.getName());
				}
				bar.increment(1);
			}
		}
		pool.await();
		bar.end();

		genericParallel.close();

		qzfw.beginEntry(QZEntry.ofNoAttribute(".vcs|hashes"));
		try (ByteList.ToStream out = new ByteList.ToStream(qzfw, false)) {
			for (QZEntry file : qzfw.getFiles()) {
				// or other kind that need original file
				if (file.getModificationTime() == REGION) {
					QZEntry entry = pack.getEntry(file.getName());
					if (entry != null) out.putVUIGB(entry.getName()).putLong(entry.getSize()).putInt(entry.getCrc32());
				}
			}
		}

		addWarning(qzfw);

		if (userCert != null) {
			System.out.println("正在签名 (4/4)");
			addSignature(diffFile, qzfw);
			return;
		}

		System.out.println("成功.");
		qzfw.close();
	}

	private static void addWarning(QZFileWriter qzfw) throws IOException {
		QZEntry entry = QZEntry.of(".vcs|请勿修改包内文件");
		entry.setModificationTime(System.currentTimeMillis());
		qzfw.beginEntry(entry);
	}

	private void addSignature(File file, QZFileWriter qzfw) throws IOException {
		// 主要是让签名能存入同一个压缩包，不要写两次磁盘，也不要分成两个文件
		// 验证了Metadata的文件名、大小、顺序，和WordBlock的数据
		// 修改日期之类的就没有验证了

		qzfw.flush();

		var hash1 = CryptoFactory.Blake3(32);
		var hash2 = CryptoFactory.SM3();

		long count = qzfw.source().position()-32;
		byte[] tmp = ArrayCache.getByteArray(4096, false);
		try (InputStream in = new FileInputStream(file)) {
			IOUtil.readFully(in, tmp, 0, 32); // 跳过文件头
			while (count > 0) {
				int r = in.read(tmp);
				if (r < 0) throw new CorruptedInputException("未预料的错误");

				r = (int) Math.min(count, r);
				count -= r;

				hash1.update(tmp, 0, r);
				hash2.update(tmp, 0, r);
			}
		}
		ArrayCache.putArray(tmp);

		ByteList buf = IOUtil.getSharedByteBuf();
		for (QZEntry entry1 : qzfw.getFiles()) {
			buf.putChars(entry1.getName()).putLong(entry1.getSize());

			if (buf.wIndex() > 1024) {
				hash1.update(buf);
				buf.rIndex = 0;
				hash2.update(buf);
				buf.clear();
			}
		}
		for (QZEntry entry1 : qzfw.getEmptyFiles()) {
			buf.putChars(entry1.getName());

			if (buf.wIndex() > 1024) {
				hash1.update(buf);
				buf.rIndex = 0;
				hash2.update(buf);
				buf.clear();
			}
		}

		hash1.update(buf);
		buf.rIndex = 0;
		hash2.update(buf);
		buf.clear();

		buf.clear();
		buf.put(hash1.digest()).put(hash2.digest());

		try {
			Signature dsa = Signature.getInstance("EdDSA");
			dsa.initSign(userCert.getPrivate());
			dsa.update(buf.list, 0, buf.wIndex());
			byte[] sign = dsa.sign();

			buf.clear();
			String hexSign = buf.put(sign).hex();

			buf.clear();
			String algorithm = userCert.getPublic().getAlgorithm();
			buf.putAscii(algorithm).put('\n')
			   .putAscii(hexSign).put('\n')
			   .putAscii(KeyType.getInstance(algorithm).toPEM(userCert.getPublic()));

			qzfw.beginEntry(QZEntry.ofNoAttribute(".vcs|signature"));
			buf.writeToStream(qzfw);
		} catch (Exception e) {
			System.out.println("签名失败！");
			e.printStackTrace();
		}
	}

	private static void computeDiff(File directory, Predicate<File> filter,
									QZArchive pack,
									List<String> empty,
									HashSet<String> added,
									List<String> deleted,
									HashMap<String, String> moved,
									TaskGroup pool) throws IOException {
		var oldEntries = new HashMap<>(pack.getEntries());

		IntMap<String> moveCheck = new IntMap<>();

		int prefix = directory.getAbsolutePath().length()+1;
		List<File> newEntries = IOUtil.listFiles(directory, filter);
		byte[] tmp = new byte[4096];

		System.out.println("正在计算哈希 (1/4)");

		for (File file : newEntries) {
			String name = file.getAbsolutePath().substring(prefix).replace(File.separatorChar, '/');
			if (file.length() == 0) {
				empty.add(name);
				continue;
			}

			QZEntry oldEntry = oldEntries.remove(name);
			synchronized (added) {
				added.add(name);
				if (oldEntry != null && oldEntry.getSize() == file.length()) {
					pool.executeUnsafe(() -> {
						long delta = oldEntry.getSize() - file.length();
						if (delta == 0 && compare(file, pack, oldEntry)) {
							synchronized (added) {
								added.remove(name);
							}
						}
					});
				} else if (oldEntry == null && !oldEntries.isEmpty()) {
					int crc = computeCrc32(file, tmp);
					String prev = moveCheck.putIfAbsent(crc, name);
					if (prev != null) {
						System.out.println("警告：在"+crc+"["+name+"]上出现了CRC冲突, 这可能会导致diff略大");
						moveCheck.put(crc, null);
					}
				}
			}
		}

		for (QZEntry oldEntry : oldEntries.values()) {
			if (oldEntry.isDirectory()) continue;

			String entry = moveCheck.get(oldEntry.getCrc32());
			if (entry == null) {
				if (oldEntry.getSize() > 0)
					deleted.add(oldEntry.getName());
				continue;
			}

			pool.executeUnsafe(() -> {
				boolean same = compare(new File(directory, entry), pack, oldEntry);
				synchronized (added) {
					if (same) {
						added.remove(entry);
						moved.put(oldEntry.getName(), entry);
					} else {
						deleted.add(oldEntry.getName());
					}
				}
			});
		}
		pool.await();
	}

	static int computeCrc32(File file, byte[] tmp) throws IOException {
		int crc = CRC32.initial;
		try (FileInputStream in = new FileInputStream(file)) {
			while (true) {
				int r = in.read(tmp);
				if (r < 0) break;
				crc = CRC32.update(crc, tmp, 0, r);
			}
		}
		crc = CRC32.finish(crc);
		return crc;
	}

	private static boolean compare(File left, QZArchive pack, QZEntry entry) throws IOException {return compare(new FileInputStream(left), pack.getInputUncached(entry));}
	private static boolean compare(InputStream ina, InputStream inb) {
		byte[] a = ArrayCache.getByteArray(4096, false);
		byte[] b = ArrayCache.getByteArray(4096, false);

		try {
			while (true) {
				int ra = ina.read(a);
				if (ra < 0) return inb.read() < 0;

				IOUtil.readFully(inb, b, 0, ra);
				if (!Arrays.equals(a, 0, ra, b, 0, ra)) return false;
			}
		} catch (IOException e) {
			return false;
		} finally {
			ArrayCache.putArray(a);
			ArrayCache.putArray(b);
			IOUtil.closeSilently(ina);
			IOUtil.closeSilently(inb);
		}
	}
}