package roj.plugins.minecraft.diff;

import roj.archive.qz.*;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMAOutputStream;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.crypt.Blake3;
import roj.crypt.CRC32s;
import roj.crypt.KeyType;
import roj.crypt.SM3;
import roj.io.CorruptedInputException;
import roj.io.DummyOutputStream;
import roj.io.IOUtil;
import roj.io.MyRegionFile;
import roj.io.buf.BufferPool;
import roj.io.source.MemorySource;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.BsDiff;
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
 * @since 2023/12/21 0021 1:30
 */
public final class McDiffServer {
	static final byte REGION = 1, GZIP = 2, DIFF = 3;

	private final KeyPair userCert;
	public McDiffServer(KeyPair kp) {userCert = kp;}

	public void makeDiff(File fullPack, File directory, Predicate<File> filter, File diffFile) throws IOException {
		QZArchive pack = new QZArchive(fullPack);

		List<String> empty = new SimpleList<>();
		MyHashSet<String> added = new MyHashSet<>();
		MyHashMap<String, String> moved = new MyHashMap<>();
		List<String> deleted = new SimpleList<>();

		computeDiff(directory, filter, pack, empty, added, deleted, moved);

		QZFileWriter qzfw = new QZFileWriter(diffFile.getAbsolutePath());

		System.out.println("正在处理删除 (2/4)");
		for (String path : deleted) qzfw.beginEntry(new QZEntry(".vcs/"+path));
		for (Map.Entry<String, String> entry : moved.entrySet()) {
			qzfw.beginEntry(new QZEntry(".vcs/"+entry.getKey()));
			qzfw.write(entry.getValue().getBytes(StandardCharsets.UTF_16LE));
		}
		qzfw.closeWordBlock();
		for (String path : empty) {
			qzfw.beginEntry(new QZEntry(path));
		}

		int affinity = Runtime.getRuntime().availableProcessors();
		long myMem = (1L<<24) * affinity;
		System.out.println("Allocating "+TextUtil.scaledNumber1024(myMem)+" of memory");
		LZMA2Options opt = new LZMA2Options();
		opt.setAsyncMode(1<<24, TaskPool.Common(), affinity, new BufferPool(myMem,0,myMem,0,0,0, 0,0,10,0,(byte) 2), LZMA2Options.ASYNC_DICT_NONE);
		QZWriter genericParallel = qzfw.parallel();
		genericParallel.setCodec(new LZMA2(opt));

		System.out.println("正在处理新增 (3/4)");
		TaskPool pool = TaskPool.Common();
		for (String path : added) {
			File file = new File(directory, path);
			QZEntry entry = new QZEntry(path);

			int flag = 0;

			checkSpecialFileType:
			if (path.contains(".dat") || path.contains(".nbt")) {
				byte[] tmp = ArrayCache.getByteArray(1024, false);

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
			} else if (path.contains(".mca")) {
				MyRegionFile rin;
				try {
					rin = new MyRegionFile(file);
				} catch (Exception ignored) {
					break checkSpecialFileType;
				}

				entry.setModificationTime(REGION);
				flag = REGION;

				MyRegionFile prevRin = null;
				try {
					InputStream in = pack.getStream(path);
					if (in != null) {
						prevRin = new MyRegionFile(new MemorySource(new ByteList(IOUtil.read(in))), 4096, 1024, 0);
						prevRin.load();
					}
				} catch (Exception ignored) {}

				MyRegionFile javac傻逼 = prevRin;
				QZWriter w = qzfw.parallel();
				w.setCodec(new LZMA2(7));
				w.beginEntry(entry);

				pool.submit(() -> {
					try (ByteList.WriteOut out = new ByteList.WriteOut(w)) {
						for (int i = 0; i < 1024; i++) {
							if (!rin.hasData(i)) continue;

							DataInputStream din = rin.getDataInput(i);
							if (javac傻逼 != null && javac傻逼.hasData(i)) {
								if (compare(javac傻逼.getDataInput(i), din)) continue;

								byte[] l = IOUtil.read(javac傻逼.getDataInput(i));
								byte[] r = IOUtil.read(rin.getDataInput(i));

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

							IOUtil.copyStream(din, w);
							IOUtil.closeSilently(din);
						}
					}

					IOUtil.closeSilently(javac傻逼);
					IOUtil.closeSilently(rin);
				});
			}

			if (flag == 0) try (FileInputStream in = new FileInputStream(file)) {
				genericParallel.beginEntry(entry);
				IOUtil.copyStream(in, genericParallel);
			}
		}
		pool.awaitFinish();

		genericParallel.close();

		qzfw.beginEntry(new QZEntry(".vcs|hashes"));
		try (ByteList.WriteOut out = new ByteList.WriteOut(qzfw, false)) {
			for (QZEntry file : qzfw.getFiles()) {
				// or other kind that need original file
				if (file.getModificationTime() == REGION) {
					QZEntry entry = pack.getEntry(file.getName());
					if (entry != null) out.putVUIGB(entry.getName()).putLong(entry.getSize()).putInt(entry.getCrc32());
				}
			}
		}

		addWarning(qzfw);

		System.out.println("正在签名 (4/4)");

		addSignature(diffFile, qzfw);
		qzfw.close();
	}

	private static void addWarning(QZFileWriter qzfw) throws IOException {
		QZEntry entry = new QZEntry(".vcs|请勿修改包内文件");
		entry.setModificationTime(System.currentTimeMillis());
		qzfw.beginEntry(entry);
	}

	private void addSignature(File file, QZFileWriter qzfw) throws IOException {
		// 主要是让签名能存入同一个压缩包，不要写两次磁盘，也不要分成两个文件
		// 验证了Metadata的文件名、大小、顺序，和WordBlock的数据
		// 修改日期之类的就没有验证了

		qzfw.closeWordBlock();

		Blake3 hash1 = new Blake3(32);
		SM3 hash2 = new SM3();

		long count = qzfw.s.position()-32;
		byte[] tmp = ArrayCache.getByteArray(1024, false);
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

			qzfw.beginEntry(new QZEntry(".vcs|signature"));
			buf.writeToStream(qzfw);
		} catch (Exception e) {
			System.out.println("签名失败！");
			e.printStackTrace();
		}
	}

	private static void computeDiff(File directory, Predicate<File> filter,
									QZArchive pack,
									List<String> empty,
									MyHashSet<String> added,
									List<String> deleted,
									MyHashMap<String, String> moved) throws IOException {
		var oldEntries = new MyHashMap<>(pack.getEntries());

		IntMap<String> moveCheck = new IntMap<>();

		int prefix = directory.getAbsolutePath().length()+1;
		List<File> newEntries = IOUtil.findAllFiles(directory, filter);
		byte[] tmp = new byte[1024];

		TaskPool pool = TaskPool.Common();
		System.out.println("正在计算哈希 (1/4)");

		for (File file : newEntries) {
			String name = file.getAbsolutePath().substring(prefix).replace(File.separatorChar, '/');
			if (file.length() == 0) {
				empty.add(name);
				continue;
			}

			int crc = computeCrc32(file, tmp);

			QZEntry oldEntry = oldEntries.remove(name);
			synchronized (added) {
				if (oldEntry == null) {
					String prev = moveCheck.putIfAbsent(crc, name);
					if (prev != null) {
						System.out.println("警告：在"+crc+"["+name+"]上出现了CRC冲突, 这可能会导致diff略大");
						moveCheck.putInt(crc, null);
					}

					added.add(name);
				} else if (oldEntry.getSize() != file.length()) {
					added.add(name);
				} else {
					added.add(name);

					pool.submit(() -> {
						long delta = oldEntry.getSize() - file.length();
						if (delta == 0 && compare(file, pack, oldEntry)) {
							synchronized (added) {
								added.remove(name);
							}
						}
					});
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

			pool.submit(() -> {
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
		pool.awaitFinish();
	}

	static int computeCrc32(File file, byte[] tmp) throws IOException {
		int crc = CRC32s.INIT_CRC;
		try (FileInputStream in = new FileInputStream(file)) {
			while (true) {
				int r = in.read(tmp);
				if (r < 0) break;
				crc = CRC32s.update(crc, tmp, 0, r);
			}
		}
		crc = CRC32s.retVal(crc);
		return crc;
	}

	private static boolean compare(File left, QZArchive pack, QZEntry entry) throws IOException {return compare(new FileInputStream(left), pack.getInputUncached(entry));}
	private static boolean compare(InputStream ina, InputStream inb) {
		byte[] a = ArrayCache.getByteArray(1024, false);
		byte[] b = ArrayCache.getByteArray(1024, false);

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