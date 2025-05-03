package roj.plugins.bkcrack;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipCrypto;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.collect.IntMap;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskPool;
import roj.crypt.CipherInputStream;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.TextUtil;
import roj.ui.Argument;
import roj.ui.EasyProgressBar;
import roj.ui.Terminal;
import roj.util.ByteList;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2022/11/12 18:12
 */
@SimplePlugin(id = "bkCrack", desc = """
	对ZipCrypto算法的已知明文攻击[Biham、Kocher]
	设置字符集: bkcrack charset <charset>
	破解: bkcrack <zip> crack/crackall <包内文件> <偏移 明文> [偏移 明文]
	  crack会在找到第一个可能的密钥时终止
	解密: bkcrack <zip> decipher
	猜测明文密码: bkcrack <zip> recoverplain <length>

	密钥将会输出在STDERR 日志为STDOUT
	默认字符集为file.encoding""", version = "0.1.0")
public class Main extends Plugin {
	static Charset charset = Charset.defaultCharset();

	@Override
	protected void onEnable() throws Exception {
		// ADOBE_JPEG:  0 "FFD8 FFEE 000E 4164 6F62 6500 64C0 0000" -2 "FFD9"
		var node = argument("zip", Argument.file());
		registerCommand(literal("bkcrack").then(node).then(literal("charset").then(argument("charset", Argument.string()).executes(ctx -> {
			charset = Charset.forName(ctx.argument("charset", String.class));
		}))));

		var crack = argument("entry", Argument.string()).then(argument("data", Argument.restArray()).executes(ctx -> {
			File zip = ctx.argument("zip", File.class);
			tryFindPass(ctx.argument("entry", String.class), ctx.argument("data", String[].class), zip, 0, ctx.context.equals("crack"));
		}));
		node.then(literal("crack").then(crack))
		.then(literal("crackall").then(crack))
		.then(literal("decipher").executes(ctx -> {
			File zip = ctx.argument("zip", File.class);
			tryUnlock(zip);
		})).then(literal("recoverplain").then(argument("length", Argument.number(1, 20))).executes(ctx -> {
			File zip = ctx.argument("zip", File.class);
			tryGetPlain(null, zip, 0);
		}));
	}

	private static void tryGetPlain(TaskExecutor pool, File f, int off) throws IOException {
		File key = new File(f.getAbsolutePath() + ".key");
		if (!key.exists()) return;

		ByteList r = ByteList.wrap(IOUtil.read(key));
		Cipher key1 = new Cipher().set(r.readInt(), r.readInt(), r.readInt());

		byte[] plainTable = new byte[128-32];
		for (int i = 32; i < 128; i++) {
			plainTable[i-32] = (byte) i;
		}

		byte[] x = PlainPassRecover.recoverPassword(pool, key1, plainTable, 0, 12);
		System.out.println("密码是: "+TextUtil.dumpBytes(x));
	}

	private static void tryFindPass(String entryName, String[] args, File file, int argOff, boolean stopOnFirstKey) throws IOException {
		ZipFile mzf = new ZipFile(file, ZipFile.FLAG_BACKWARD_READ, charset);
		ZEntry entry = mzf.getEntry(entryName);
		if (entry == null || entry.getEncryptType() != ZipFile.CRYPT_ZIP2) {
			System.out.println("没找到或不符合要求:"+entryName);
			return;
		}
		System.err.println("文件: "+file+"#!"+entry.getName());

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
				data = IOUtil.decodeHex(str);
			}

			plains.putInt(off, data);
		}

		ZCKiller ctx = new ZCKiller(IOUtil.read(mzf.getRawStream(entry)), plains);
		mzf.close();
		ctx.stopOnFirstKey = stopOnFirstKey;

		TaskPool pool = TaskPool.MaxThread(Runtime.getRuntime().availableProcessors(), "BKCrack-Worker-");

		List<Cipher> keys = ctx.find(pool);

		// print the keys
		if(keys.isEmpty()) {
			System.err.println("没有合适的密钥... ？？");
		} else {
			System.err.println("有 "+keys.size()+" 个密钥符合你提供的所有明文-密文对: ");
			for (int i = 0; i < keys.size(); i++) {
				System.err.println();
				System.err.print(keys.get(i)+" 的解密结果: ");
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
				selection = Terminal.readInt(0, keys.size());
			} else {
				selection = 0;
			}

			IOUtil.copyFile(new File(file.getAbsolutePath()+".key."+selection), new File(file.getAbsolutePath()+".key"));
			System.out.println("正在反查密码，若五秒内未能找到则放弃，直接用内部密钥破解");
			Thread thread = new Thread(() -> {
				try {
					tryGetPlain(pool, file, 0);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			thread.start();
			try {
				Thread.sleep(5000);
				pool.shutdownNow();
			} catch (InterruptedException ignored) {}
			tryUnlock(file);
		}
	}

	private static void tryUnlock(File f) throws IOException {
		File key = new File(f.getAbsolutePath()+".key");
		if (!key.exists()) return;

		ByteList r = ByteList.wrap(key.length() == 12 ? IOUtil.read(key) : IOUtil.decodeHex(IOUtil.readString(key)));
		ZipCrypto zc = new ZipCrypto();
		zc.encrypt = false;
		int[] state = {r.readInt(), r.readInt(), r.readInt()};
		Inflater inf = new Inflater(true);

		var bar = new EasyProgressBar("解密数据", "文件");

		try (var zip = new ZipFile(f);
			 var out = new ZipFileWriter(new File(f.getAbsolutePath() + ".crk.zip"))) {
			bar.addTotal(zip.entries().size());

			for (ZEntry entry : zip.entries()) {
				if (entry.isEncrypted()) {
					zc.copyState(state, true);
					inf.reset();

					try (var in = new CipherInputStream(zip.getRawStream(entry), zc)) {
						in.skip(12);
						InputStream iin = entry.getMethod() == 0 ? in : new InflaterInputStream(in, inf);
						ZEntry entry1 = new ZEntry(entry.getName());
						entry1.setMethod(ZipEntry.STORED);
						out.beginEntry(entry1);
						IOUtil.copyStream(iin, out);
						out.closeEntry();
					}
					bar.increment(1);
				}
			}
		} finally {
			inf.end();
			bar.end("fin");
		}
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