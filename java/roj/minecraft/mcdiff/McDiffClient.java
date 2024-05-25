package roj.minecraft.mcdiff;

import roj.archive.qz.QZArchive;
import roj.archive.qz.QZEntry;
import roj.archive.qz.WordBlock;
import roj.collect.MyHashMap;
import roj.config.NBTParser;
import roj.config.serial.ToNBT;
import roj.crypt.Blake3;
import roj.crypt.SM3;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.io.MyRegionFile;
import roj.io.source.FileSource;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.BsDiff;
import roj.util.ByteList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.zip.GZIPOutputStream;

/**
 * @author Roj234
 * @since 2023/12/21 0021 1:31
 */
public class McDiffClient {
	QZArchive archive;
	MyHashMap<String, String> renames = new MyHashMap<>();

	public void apply(File basePath) throws IOException {
		QZEntry hashes = archive.getEntry(".vcs|hashes");
		if (hashes != null) try (MyDataInputStream in = new MyDataInputStream(archive.getStream(hashes))) {
			byte[] tmp = ArrayCache.getByteArray(1024, false);
			while (in.isReadable()) {
				String name = in.readVUIGB();
				File file = new File(basePath, name);
				if (in.readLong() != file.length() || in.readInt() != McDiffServer.computeCrc32(file, tmp)) {
					throw new CorruptedInputException("文件 "+name+" 哈希错误，可能不是这个更新包对应的版本");
				}
			}
			ArrayCache.putArray(tmp);
		}

		for (QZEntry entry : archive.getEntriesByPresentOrder()) {
			String name = entry.getName();

			if (name.startsWith(".vcs")) {
				if (!name.startsWith(".vcs/")) continue;
				name = name.substring(5);
				File file = new File(basePath, name);

				if (entry.getSize() == 0) {
					if (!file.delete()) {
						System.out.println("删除文件 "+name+" 失败");
					}
				} else {
					File destFile = new File(basePath, renames.get(name));
					if (!file.renameTo(destFile)) {
						System.out.println("移动文件 "+name+" => "+destFile+" 失败");
					}
				}
			} else {
				File file = new File(basePath, name);
				file.getParentFile().mkdirs();

				switch ((int) entry.getModificationTime()) {
					case 0 -> {
						try (InputStream in = archive.getStream(entry)) {
							try (OutputStream out = new FileOutputStream(file)) {
								IOUtil.copyStream(in, out);
							}
						}
					}
					case McDiffServer.GZIP -> {
						try (InputStream in = archive.getStream(entry)) {
							try (OutputStream out = new GZIPOutputStream(new FileOutputStream(file))) {
								IOUtil.copyStream(in, out);
							}
						}
					}
					case McDiffServer.REGION -> {
						MyRegionFile rf = new MyRegionFile(new FileSource(file), 4096, 1024, 0);
						try {
							rf.load();
						} catch (Exception e) {
							rf.clear();
						}

						try (MyDataInputStream in = new MyDataInputStream(archive.getStream(entry))) {
							// short pos int timestamp int patchLength
							while (in.isReadable()) {
								int pos = in.readShort();
								int time = in.readInt();
								int patch = in.readInt();

								try (DataOutputStream dos = rf.getDataOutput(pos)) {
									if (patch == 0) {
										ByteList.WriteOut buf = new ByteList.WriteOut(dos);
										new NBTParser().parse(in, 0, new ToNBT(buf));
										buf.close();
									} else {
										if (!rf.hasData(pos)) throw new CorruptedInputException("diff source missing");

										ByteList xin = IOUtil.getSharedByteBuf().readStreamFully(rf.getData(pos, null));
										BsDiff.patchStream(xin, in, dos);
									}
								}

								rf.setTimestamp(pos, time);
							}
						}

						rf.close();
					}
					case McDiffServer.DIFF -> {
						throw new IllegalStateException("not supported yet");
					}
				}
			}
		}
	}

	public void load(File arc) throws IOException, GeneralSecurityException {
		archive = new QZArchive(arc);

		for (QZEntry entry : archive.getEntriesByPresentOrder()) {
			String name = entry.getName();
			if (name.indexOf('\\') >= 0 || name.contains("../")) throw new CorruptedInputException("文件不安全");

			if (name.startsWith(".vcs")) {
				if (!name.startsWith(".vcs/")) continue;

				if (entry.getSize() != 0) {
					InputStream in = archive.getStream(entry);
					String rename = new String(IOUtil.read(in), StandardCharsets.UTF_16LE);
					if (rename.indexOf('\\') >= 0 || rename.contains("../")) throw new CorruptedInputException("文件不安全");

					renames.put(entry.getName(), rename);
				}
			}
		}

		QZEntry entry = archive.getEntry(".vcs|signature");
		if (entry == null) throw new CorruptedInputException("更新包未签名");

		WordBlock[] blocks = archive.getWordBlocks();
		if (entry.getBlock() != blocks[blocks.length-1] || blocks[blocks.length-1].getFileCount() != 1) throw new CorruptedInputException("签名方式不合法");

		verifySign(arc, entry, blocks);
	}

	private void verifySign(File file, QZEntry entry, WordBlock[] blocks) throws IOException, GeneralSecurityException {
		byte[] sign;
		Signature dsa = Signature.getInstance("EdDSA");
		try (TextReader in = new TextReader(archive.getStream(entry), StandardCharsets.US_ASCII)) {
			sign = TextUtil.hex2bytes(in.readLine());
			String publicKey = IOUtil.read(in);

			PublicKey key = (PublicKey) McDiffServer.keyType.fromPEM(publicKey);
			dsa.initVerify(key);

			System.out.println("正在验证签名，请稍等 "+key);
		}

		long count = blocks[blocks.length - 1].getOffset()-32;
		Blake3 hash1 = new Blake3(32);
		SM3 hash2 = new SM3();

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
		for (QZEntry entry1 : archive.getEntriesByPresentOrder()) {
			if (entry1 == entry) continue;

			buf.putChars(entry1.getName());
			if (entry1.getSize() > 0) buf.putLong(entry1.getSize());

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

		dsa.update(buf.list, 0, buf.wIndex());
		if (!dsa.verify(sign)) throw new CorruptedInputException("无效的签名");
		System.out.println("签名验证通过");
	}
}