package roj.archive.bkcrack;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipCrypto;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.collect.IntMap;
import roj.concurrent.TaskPool;
import roj.crypt.CipherInputStream;
import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.ui.ProgressBar;
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
	static TaskPool pool = TaskPool.Common();

	public static void main(String[] args) throws Exception {
		// ADOBE_JPEG:  0 "FFD8 FFEE 000E 4164 6F62 6500 64C0 0000" -2 "FFD9"

		if (args.length < 2) {
			System.out.println("""
				ZCKiller 0.1.0
				用法: 文件/文件夹 模式

				模式=C 文件 <偏移 明文...>
					用Biham和Kocher的算法对ZipCrypto进行明文攻击.
					在 <文件夹> 中所有加密且使用了Store算法的zip文件
					并生成同名.key保存密码
				模式=CA 同上 但是查找所有可能的密码

				模式=D
					使用保存的.key解密zip文件

				模式=P <字典> [最大长度] [最小长度]
					使用保存的.key尝试计算明文密码

				密钥将会输出在STDERR 日志为STDOUT
				Change code if you want

				properties(可选):
				 threads 并行计算线程数
				 file.encoding zip字符集""");
			return;
		}

		File file = new File(args[0]);
		List<File> files = file.isDirectory() ? IOUtil.findAllFiles(file, file1 -> file1.getName().endsWith(".zip") && !file1.getName().endsWith(".crk.zip")) :
			Collections.singletonList(file);
		for (File f : files) {
			try {
				switch (args[1]) {
					case "C", "CA": tryFindPass(args, f, 2); break;
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
		ZipFile mzf = new ZipFile(file, ZipFile.FLAG_BACKWARD_READ, Charset.defaultCharset());
		ZEntry entry = mzf.getEntry(args[argOff++]);
		if (entry == null || entry.getEncryptType() != ZipFile.CRYPT_ZIP2) {
			System.out.println("没找到或不符合要求:"+args[argOff-1]);
			return;
		}
		System.err.println("文件: " + file+"#!"+entry.getName());

		IntMap<byte[]> plains = new IntMap<>();
		for (int i = argOff; i < args.length; i++) {
			int off = Integer.parseInt(args[i++]);
			if (off < 0) off += entry.getCompressedSize() - 12;

			String str = args[i];
			byte[] data;
			if (str.startsWith("@")) {
				data = IOUtil.read(new File(str.substring(1)));
			} else if (str.startsWith("!")) {
				data = str.substring(1).getBytes(StandardCharsets.UTF_8);
			} else {
				data = IOUtil.SharedCoder.get().decodeHex(str);
			}

			plains.putInt(off, data);
		}

		ZCKiller ctx = new ZCKiller(IOUtil.read(mzf.getFileStream(entry)), plains);
		mzf.close();
		ctx.stopOnFirstKey = args[argOff-1].equals("C");

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
			for (int i = 0; i < keys.size(); i++) {
				try (FileOutputStream out = new FileOutputStream(file.getAbsolutePath()+".key."+i)) {
					int[] list = keys.get(i).keys();
					IOUtil.getSharedByteBuf().putInt(list[0]).putInt(list[1]).putInt(list[2]).writeToStream(out);
				}
			}

			int selection;
			if (keys.size() > 1) {
				System.out.println("输入选择并按回车");
				selection = CLIUtil.readInt(0, keys.size());
			} else {
				selection = 0;
			}

			IOUtil.copyFile(new File(file.getAbsolutePath()+".key."+selection), new File(file.getAbsolutePath()+".key"));
			System.out.println("正在反查密码，若五秒内未能找到则放弃，直接用内部密钥破解");
			Thread thread = new Thread(() -> {
				try {
					tryGetPlain(null, file, 0);
					System.exit(0);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			thread.start();
			try {
				Thread.sleep(5000);
				thread.stop();
			} catch (InterruptedException ignored) {}
			tryUnlock(file);
		}
	}

	private static void tryUnlock(File f) throws IOException {
		File key = new File(f.getAbsolutePath()+".key");
		if (!key.exists()) return;

		ByteList r = ByteList.wrap(key.length() == 12 ? IOUtil.read(key) : IOUtil.SharedCoder.get().decodeHex(IOUtil.readString(key)));
		ZipCrypto zc = new ZipCrypto();
		zc.encrypt = false;
		int[] state = {r.readInt(), r.readInt(), r.readInt()};
		Inflater inf = new Inflater(true);

		ProgressBar ep = new ProgressBar("解密数据");
		ep.setUnit("");

		ZipFile zip = new ZipFile(f);
		try (ZipFileWriter dst = new ZipFileWriter(new File(f.getAbsolutePath() + ".crk.zip"))) {
			int i = 0;
			int size = zip.entries().size();
			for (ZEntry entry : zip.entries()) {
				if (entry.isEncrypted()) {
					zc.copyState(state, true);
					inf.reset();

					try (InputStream in = new CipherInputStream(zip.getFileStream(entry), zc)) {
						in.skip(12);
						InputStream iin = entry.getMethod() == 0 ? in : new InflaterInputStream(in, inf);
						ZEntry entry1 = new ZEntry(entry.getName());
						entry1.setMethod(ZipEntry.STORED);
						dst.beginEntry(entry1);
						IOUtil.copyStream(iin, dst);
						dst.closeEntry();
					}
					ep.update((float) ++i / size, 1);
				}
			}
		}
		ep.end("fin");
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