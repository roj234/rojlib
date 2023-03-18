package roj.archive.zip.crack;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipCrypto;
import roj.archive.zip.ZipFileWriter;
import roj.collect.IntMap;
import roj.concurrent.TaskPool;
import roj.crypt.CipherInputStream;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

/**
 * @author Roj234
 * @since 2022/11/12 0012 18:12
 */
public class Main {
	static TaskPool pool = TaskPool.CpuMassive();

	public static void main(String[] args) throws Exception {
		// ADOBE_JPEG:  0 "FFD8 FFEE 000E 4164 6F62 6500 64C0 0000" -2 "FFD9"

		if (args.length < 2) {
			System.out.println("ZCKiller 0.0.2\n"
				+ "用法: 文件/文件夹 模式\n"
				+ "\n"
				+ "模式=C <偏移 明文...>\n"
				+ "	用Biham和Kocher的算法对ZipCrypto进行明文攻击.\n"
				+ "	在 <文件夹> 中所有加密且使用了Store算法的zip文件\n"
				+ "	并生成同名.key保存密码\n"
				+ "\n"
				+ "模式=D\n"
				+ "	使用保存的.key解密zip文件\n"
				+ "\n"
				+ "模式=P <字典> [最大长度] [最小长度]\n"
				+ "	使用保存的.key尝试计算明文密码\n"
				+ "\n"
				+ "密钥将会输出在STDERR 日志为STDOUT\n"
				+ "Change code if you want\n"
				+ "\n"
				+ "properties(可选):\n"
				+ " threads 并行计算线程数\n"
				+ " file.encoding zip字符集");
			return;
		}

		File file = new File(args[0]);
		List<File> files = file.isDirectory() ? IOUtil.findAllFiles(file, file1 -> file1.getName().endsWith(".zip") && !file1.getName().endsWith(".crk.zip")) :
			Collections.singletonList(file);
		for (File f : files) {
			try {
				switch (args[1]) {
					case "C": tryFindPass(args, f, 2); break;
					case "D": tryUnlock(f); break;
					case "P": tryGetPlain(args, f, 2); break;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void tryGetPlain(String[] args, File f, int off) throws IOException {
		File key = new File(f.getAbsolutePath() + ".key");
		if (!key.exists()) return;

		ByteList r = ByteList.wrap(IOUtil.read(key));
		Cipher key1 = new Cipher().set(r.readInt(), r.readInt(), r.readInt());

		byte[] plainTable = new byte[128-32];
		for (int i = 32; i < 128; i++) {
			plainTable[i-32] = (byte) i;
		}

		byte[] x = PlainPassRecover.recoverPassword(key1, plainTable, 0, 12);
		System.out.println("文件 " + f + " 的密码是: " + TextUtil.dumpBytes(x));
	}

	private static void tryFindPass(String[] args, File file, int argOff) throws IOException {
		System.err.println("文件: " + file);

		ZipArchive mzf = new ZipArchive(file, 0, 0, Charset.defaultCharset());
		ZEntry entry = findBestFile(mzf);
		if (entry == null) {
			mzf.close();
			return;
		}
		if (entry.getMethod() != 0) {
			mzf.close();
			System.err.println("没有ZC+Store，无法破解("+entry.getMethod()+")");
			return;
		}
		System.out.println("Using: " + entry.getName());

		IntMap<byte[]> plains = new IntMap<>();
		for (int i = argOff; i < args.length; i++) {
			int off = Integer.parseInt(args[i++]);
			String str = args[i];
			byte[] data = str.startsWith("!") ?
				str.substring(1).getBytes(StandardCharsets.UTF_8) :
				IOUtil.SharedCoder.get().decodeHex(str);
			if (off < 0) off += entry.getCompressedSize() - 12;

			plains.putInt(off, data);
		}

		ZCKiller ctx = new ZCKiller(IOUtil.read(mzf.i_getRawData(entry)), plains);
		mzf.close();
		ctx.stopOnFirstKey = true;

		List<Cipher> keys = ctx.find(pool);

		// print the keys
		if(keys.isEmpty()) {
			System.err.println("没有合适的密钥... ？？");
		} else {
			System.err.println("有 " + keys.size() + " 个密钥符合你提供的所有明文-密文对: ");
			for (int i = 0; i < keys.size(); i++) {
				System.err.println();
				System.err.print(keys.get(i) + " 测试的解密结果: ");
				System.err.println(TextUtil.dumpBytes(getExample(ctx.cipher, keys.get(i).keys())));
			}
			try (FileOutputStream out = new FileOutputStream(file.getAbsolutePath() + ".key")) {
				int[] list = keys.get(0).keys();
				IOUtil.getSharedByteBuf().putInt(list[0]).putInt(list[1]).putInt(list[2]).writeToStream(out);
			}
		}
	}

	private static void tryUnlock(File f) throws IOException {
		File key = new File(f.getAbsolutePath() + ".key");
		if (!key.exists()) return;

		ByteList r = ByteList.wrap(IOUtil.read(key));
		ZipCrypto zc = new ZipCrypto();
		zc.encrypt = false;
		int[] state = {r.readInt(), r.readInt(), r.readInt()};
		Inflater inf = new Inflater(true);

		EasyProgressBar ep = new EasyProgressBar("重新压缩");
		ep.setUnit("");

		ZipArchive zip = new ZipArchive(f, 0, 0, Charset.defaultCharset()); zip.close();
		try (ZipFileWriter dst = new ZipFileWriter(new File(f.getAbsolutePath() + ".crk.zip"), false)) {
			int i = 0;
			int size = zip.getEntries().size();
			for (ZEntry entry : zip.getEntries().values()) {
				if (entry.isEncrypted()) {
					zc.copyState(state, true);
					inf.reset();

					try (InputStream in = new CipherInputStream(zip.i_getRawData(entry), zc)) {
						in.skip(12);
						InputStream iin = entry.getMethod() == 0 ? in : new InflaterInputStream(in, inf);
						entry.setMethod(ZipEntry.STORED); // 不压缩
						dst.beginEntry(entry);
						IOUtil.copyStream(iin, dst);
						dst.closeEntry();
					}
					ep.update((float) ++i / size, 1);
				}
			}
		}
		ep.updateForce(1);
		ep.dispose();
	}

	private static ZEntry findBestFile(ZipArchive mzf) {
		ZEntry lowestFile = null;
		long lowestSize = Long.MAX_VALUE;
		int store = 8;
		for (ZEntry file : mzf.getEntries().values()) {
			if (file.getEncryptType() != ZipArchive.CRYPT_ZIP2) continue;
			if (file.getMethod() <= store) {
				if (file.getMethod() < store || file.getCompressedSize() < lowestSize) {
					lowestFile = file;
					lowestSize = file.getCompressedSize();
					store = file.getMethod();
				}
			}
		}
		return lowestFile;
	}

	private static byte[] getExample(byte[] cipher, int[] key) throws IOException {
		ZipCrypto zc = new ZipCrypto();

		zc.init(ZipCrypto.DECRYPT_MODE, new byte[0], null, null);
		zc.copyState(key, true);

		CipherInputStream cs = new CipherInputStream(new ByteArrayInputStream(cipher), zc);
		cs.skip(12);

		byte[] fs = new byte[32];
		return Arrays.copyOf(fs, cs.read(fs));
	}
}
