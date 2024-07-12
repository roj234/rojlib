package roj.plugins;

import roj.archive.qz.*;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.collect.CollectionX;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.crypt.Base64;
import roj.crypt.VoidCrypt;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.net.http.server.AsyncResponse;
import roj.net.http.server.DiskFileInfo;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.auto.*;
import roj.plugin.Panger;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextWriter;
import roj.ui.CLIUtil;
import roj.ui.EasyProgressBar;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.ui.terminal.CommandContext;
import roj.ui.terminal.CommandNode;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/5/15 0015 14:10
 */
@SimplePlugin(id = "lazyBox", version = "1.3", desc = """
	高仿瑞士军刀/doge
	
	[文件工具]
	复制文件(夹):  cp[copy] <src> <dst>
	移动文件(夹):  mv[move] <src> <dst>
	删除文件(夹):  rd[rmdir] <path>
	读取修改时间:  mtime <file>
	设置修改时间:  mtime <file> <time>
	修改时间与创建时间的差值: writecost <file>
	批量正则替换:  batchrpl <path> <regexp> <replacement>
	
	[压缩包工具]
	多线程7z验证: 7zverify <file>
	7z差异计算:   7zdiff <file1> <file2>
	zip增量更新:  zipupdate <file>
	
	[网页工具]
	多线程下载文件: curl <url> <saveTo> [threads]
	Web: /file/encrypt: 抵赖加密测试
	""")
public class LazyBox extends Plugin {
	private static final TaskPool pool = TaskPool.Common();

	@Override
	protected void onEnable() throws Exception {
		registerCommand(literal("zipupdate").then(argument("path", Argument.file()).executes(this::zipUpdate)));
		registerCommand(literal("7zverify").then(argument("path", Argument.file()).executes(this::qzVerify)));
		registerCommand(literal("7zdiff").then(argument("file1", Argument.file()).then(argument("file2", Argument.file()).executes(this::qzDiff))));

		registerCommand(literal("mtime")
			.then(argument("文件", Argument.path())
				.then(argument("源", Argument.path()).executes(ctx -> {

			File s = ctx.argument("文件", File.class);
			File d = ctx.argument("源", File.class);
			System.out.println(d.setLastModified(s.lastModified()));
		})).then(argument("时间", Argument.Long(0, Long.MAX_VALUE))
					.executes(ctx -> {
			File s = ctx.argument("文件", File.class);
			long d = ctx.argument("时间", Long.class);
			System.out.println(s.setLastModified(d));
		}))));
		registerCommand(literal("writecost").then(argument("file", Argument.path()).executes(ctx -> {
				File s = ctx.argument("file", File.class);
				BasicFileAttributes attr = Files.readAttributes(s.toPath(), BasicFileAttributes.class);
				long delta = attr.lastModifiedTime().to(TimeUnit.NANOSECONDS) - attr.creationTime().to(TimeUnit.NANOSECONDS);
				System.out.println("delta= "+delta+"ns");
			})));

		CommandNode child = argument("源", Argument.path())
			.then(argument("目标", Argument.path())
				.executes(ctx -> {
			File src = ctx.argument("源", File.class);
			File dst = ctx.argument("目标", File.class);

			if (!dst.exists() && !dst.mkdirs()) {
				getLogger().error("目标不存在且无法创建");
				return;
			}

			IOUtil.movePath(src, dst, ctx.context.startsWith("mv"));
		}));
		registerCommand(literal("cp").then(child));
		registerCommand(literal("mv").then(child));

		registerCommand(literal("rd").then(argument("路径", Argument.path()).executes(ctx -> {
			File path = ctx.argument("路径", File.class);
			System.out.println("删除"+path.getAbsolutePath()+"及其所有文件？[y/n]");
			char c = CLIUtil.awaitCharacter(MyBitSet.from("YyNn"));
			if (c != 'y' && c != 'Y') return;

			System.out.println(IOUtil.deletePath(path));
		})));

		registerCommand(literal("batchrpl").then(
			argument("文件夹", Argument.folder()).then(
				argument("正则", Argument.string()).then(
					argument("替换", Argument.string()).executes(ctx -> {

			Pattern regex = Pattern.compile(ctx.argument("正则", String.class));
			String replace = ctx.argument("替换", String.class);
			File path = ctx.argument("文件夹", File.class);
			for (File file : IOUtil.findAllFiles(path)) {
				try (TextReader in = TextReader.auto(file)) {
					AtomicBoolean change = new AtomicBoolean(false);
					CharList sb = IOUtil.getSharedCharBuf().readFully(in);
					sb.preg_replace_callback(regex, m -> {
						CharList tmp = new CharList(replace);
						for (int i = 0; i < m.groupCount(); i++)
							tmp.replace("$"+i, m.group(i));
						String str = tmp.toStringAndFree();

						if (!str.equals(m.group())) change.set(true);
						return str;
					});

					if (!change.get()) continue;
					try (TextWriter out = TextWriter.to(file, Charset.forName(in.charset()))) {
						out.append(sb);
					}
				}
			}
		})))));

		registerCommand(literal("curl").then(argument("网址", Argument.string())
			.then(argument("保存到", Argument.fileOptional(true))
				.executes(this::download)
				.then(argument("线程数", Argument.number(1, 256))
					.executes(this::download)))));

		registerCommand(literal("lb_update_exe")
			.then(argument("自解压模板", Argument.file())
				.then(argument("压缩包", Argument.file())
				.executes(this::updateExe))));

		registerRoute("file", new OKRouter().register(this));
	}

	private void zipUpdate(CommandContext ctx) throws IOException {
		var za = new ZipArchive(ctx.argument("path", File.class));

		Collection<String> entryName = CollectionX.mapToView(za.entries(), ZEntry::getName, ZEntry::new);
		Map<String, String> fileView = CollectionX.toMap(entryName, x -> x.endsWith("/") ? null : x);

		var update = new CommandConsole("\u001b[96mZUpdate \u001b[97m> ");
		update.register(literal("set").then(argument("name", Argument.suggest(fileView)).then(argument("path", Argument.file()).executes(c -> {
			String out = c.argument("name", String.class);
			File in = c.argument("path", File.class);
			if (out == null || out.endsWith("/")) {
				System.out.println("位置是目录");
				return;
			}

			za.putStream(out, () -> {
				try {
					return new FileInputStream(in);
				} catch (FileNotFoundException e) {
					Helpers.athrow(e);
					return null;
				}
			}, true);
		}))));
		update.register(literal("del").then(argument("name", Argument.oneOf(fileView)).executes(c -> za.put(ctx.argument("name", String.class), null))));
		update.register(literal("reload").executes(c -> za.getModified().clear()));
		update.register(literal("save").executes(c -> za.save()));
		update.register(literal("exit").executes(c -> {
			za.close();
			CLIUtil.setConsole(Panger.console());
		}));
		update.sortCommands();
		CLIUtil.setConsole(update);
	}

	private void qzVerify(CommandContext ctx) {
		File file = ctx.argument("path", File.class);

		EasyProgressBar bar = new EasyProgressBar("验证压缩文件");
		bar.setUnit("B");

		AtomicReference<Throwable> failed = new AtomicReference<>();
		try (QZArchive archive = new QZArchive(file)) {
			for (QZEntry entry : archive.getEntriesByPresentOrder()) {
				bar.addMax(entry.getSize());
			}

			archive.parallelDecompress(pool, (entry, in) -> {
				byte[] arr = ArrayCache.getByteArray(40960, false);
				try {
					while (true) {
						int r = in.read(arr);
						if (r < 0) break;

						if (failed.get() != null) throw new FastFailException("-other thread failed-");
						bar.addCurrent(r);
					}
				} catch (FastFailException e) {
					throw e;
				} catch (Throwable e) {
					failed.set(e);
					throw new FastFailException("-验证失败-");
				} finally {
					ArrayCache.putArray(arr);
				}
			}, null);

			pool.awaitFinish();
		} catch (Exception e) {
			failed.set(e);
		}

		Throwable exception = failed.getAndSet(null);
		if (exception != null) {
			bar.end("验证失败");
			exception.printStackTrace();
		} else {
			bar.end("验证成功");
		}
	}

	private void qzDiff(CommandContext ctx) throws IOException {
		QZArchive in1 = new QZArchive(ctx.argument("file1", File.class));
		QZArchive in2 = new QZArchive(ctx.argument("file2", File.class));
		MyHashMap<String, QZEntry> remain = in1.getEntries();

		int add = 0, change = 0, del = 0, move = 0;

		IntMap<QZEntry> in2_by_crc32 = new IntMap<>();
		MyHashMap<QZEntry, String> in1_should_copy = new MyHashMap<>(), in2_should_copy = new MyHashMap<>();
		for (QZEntry entry : in2.getEntriesByPresentOrder()) {
			if (entry.isDirectory()) continue;

			QZEntry oldEntry = remain.remove(entry.getName());
			if (oldEntry == null) {
				QZEntry prev = in2_by_crc32.putInt(entry.getCrc32(), entry);
				if (prev != null) System.out.println("警告：在"+entry.getCrc32()+"["+entry.getName()+"]上出现了CRC冲突");

				in2_should_copy.put(entry, "add/"+entry.getName());
				add++;
			} else if (oldEntry.getCrc32() != entry.getCrc32()) {
				in1_should_copy.put(oldEntry, "mod_old/"+oldEntry.getName());
				in2_should_copy.put(entry, "mod_new/"+entry.getName());

				change++;
			}
		}

		for (QZEntry oldEntry : remain.values()) {
			if (oldEntry.isDirectory()) continue;

			QZEntry entry = in2_by_crc32.get(oldEntry.getCrc32());
			if (entry != null) {
				in2_should_copy.remove(entry);

				add--;
				move++;
			} else {
				in1_should_copy.put(oldEntry, "del/"+oldEntry.getName());
				del++;
			}
		}

		if ((add|change|del|move) == 0) {
			System.out.println("\u001b[92m两个压缩包完全相同");
			return;
		}

		System.out.println("\u001b[93m新增\u001b[94m"+add+" \u001b[93m删除\u001b[94m"+del+" \u001b[93m修改\u001b[94m"+change+" \u001b[93m移动\u001b[94m"+move);
		CommandConsole c1 = new CommandConsole("\u001b[96m7zDiff \u001b[97m> ");
		CLIUtil.setConsole(c1);
		c1.register(literal("save").then(argument("out", Argument.string()).executes(c -> {
			QZFileWriter out = new QZFileWriter(c.argument("out", String.class));
			out.setCodec(new LZMA2(3));

			for (QZEntry oldEntry : remain.values()) {
				if (oldEntry.isDirectory()) continue;
				QZEntry entry = in2_by_crc32.remove(oldEntry.getCrc32());
				if (entry != null) {
					in2_should_copy.remove(entry);
					out.beginEntry(new QZEntry("renamed/"+oldEntry.getName()));
					out.write(entry.getName().getBytes(StandardCharsets.UTF_8));
					out.closeEntry();
				} else {
					in1_should_copy.put(oldEntry, "del/"+oldEntry.getName());
				}
			}

			EasyProgressBar bar = new EasyProgressBar("复制块");
			bar.setUnit("Block");
			bar.addMax(in1_should_copy.size()+in2_should_copy.size());

			copy(in1, in1_should_copy, out, bar);
			copy(in2, in2_should_copy, out, bar);

			pool.awaitFinish();
			in1.close();
			in2.close();

			out.close();
			bar.end("Diff已保存");

			CLIUtil.setConsole(Panger.console());
		})));
		c1.register(literal("exit").executes(c -> CLIUtil.setConsole(Panger.console())));
	}
	private static void copy(QZArchive arc, MyHashMap<QZEntry, String> should_copy, QZFileWriter out, EasyProgressBar bar) {
		arc.parallelDecompress(pool, (entry, in) -> {
			String prefix = should_copy.get(entry);
			if (prefix == null) return;

			try (QZWriter w = out.parallel()) {
				w.beginEntry(new QZEntry(prefix));
				IOUtil.copyStream(in, w);
				bar.addCurrent(1);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		});
	}

	private void download(CommandContext ctx) {
		String url = ctx.argument("网址", String.class);
		File saveTo = ctx.argument("保存到", File.class);

		// TODO 在Lavac里设计一个操作符避免这种傻逼操作, 比如JavaScript的 ??
		int threads = ctx.argument("线程数", Integer.class) != null ? ctx.argument("线程数", Integer.class) : Math.min(Runtime.getRuntime().availableProcessors() << 2, 64);

		DownloadTask.useETag = false;
		DownloadTask.userAgent = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36";
		DownloadTask.defMaxChunks = threads;
		DownloadTask.defChunkStart = 0;

		int retry = 2;
		do {
			try {
				DownloadTask.downloadMTD(url, saveTo).get();
				break;
			} catch (Throwable e) {
				getLogger().warn("文件{}下载失败, 重试次数: {}/3", e, saveTo.getName(), (3-retry)+"/3");
			}
		} while (retry-- > 0);
	}

	private void updateExe(CommandContext ctx) throws IOException {
		File exe = ctx.argument("自解压模板", File.class);
		File zip = ctx.argument("压缩包", File.class);

		long offset = Long.MAX_VALUE;
		try (var zf = new ZipFile(exe, ZipFile.FLAG_VERIFY|ZipFile.FLAG_BACKWARD_READ)) {
			for (ZEntry entry : zf.entries()) {
				offset = Math.min(offset, entry.startPos());
			}
		}

		var from = FileChannel.open(zip.toPath(), StandardOpenOption.READ);
		var to = FileChannel.open(exe.toPath(), StandardOpenOption.WRITE, StandardOpenOption.READ).position(offset);
		from.transferTo(0, from.size(), to);
		from.close();

		if (to.size() != to.position()) to.truncate(to.position());
		to.close();
	}

	public static final class EncryptRequest {
		List<String> keys, texts, types, paddings;
		String algorithm;
	}

	@GET
	public Object encrypt(Request req) {return Response.file(req, new DiskFileInfo(new File("plugins/Core/deniable_encryption.html")));}

	@POST
	@Body(From.JSON)
	public Object encrypt(Request req, EncryptRequest json) {
		req.responseHeader().put("content-type", "text/plain");

		if (json.keys.isEmpty() || json.keys.size() != json.texts.size()) return "参数错误";

		int length = 0;
		var pairs = new VoidCrypt.CipherPair[json.keys.size()];
		List<String> keys = json.keys;
		for (int i = 0; i < keys.size(); i++) {
			byte[] text;
			var str = json.texts.get(i);
			switch (json.types.get(i)) {
				case "UTF8" -> text = IOUtil.getSharedByteBuf().putUTFData(str).toByteArray();
				case "GB18030" -> text = IOUtil.getSharedByteBuf().putGBData(str).toByteArray();
				case "UTF-16LE" -> text = str.getBytes(StandardCharsets.UTF_16LE);
				case "hex" -> text = IOUtil.SharedCoder.get().decodeHex(str);
				case "base64" -> text = IOUtil.SharedCoder.get().decodeBase64(str).toByteArray();
				default -> {return "参数错误";}
			}

			pairs[i] = new VoidCrypt.CipherPair(keys.get(i).getBytes(StandardCharsets.UTF_8), new ByteList(text));
			length += pairs[i].key.length + text.length;
		}
		if (length > 524288) return "内容过多(512KB max)";

		if (!json.algorithm.equals("r")) req.server().enableCompression();

		var callback = new AsyncResponse();
		TaskPool.Common().submit(() -> {
			ByteList buf = IOUtil.getSharedByteBuf();
			try {
				switch (json.algorithm) {
					case "r" -> VoidCrypt._encrypt2r(new SecureRandom(), buf, pairs);
					case "i" -> VoidCrypt._encrypt1i(new SecureRandom(), buf, pairs);
					case "b" -> VoidCrypt._encrypt1b(new SecureRandom(), buf, pairs);
				}
				callback.offerAndRelease(Base64.encode(buf, new ByteList()));
			} catch (Exception e){
				buf.clear();
				callback.offer(buf.putUTFData("加密失败: "+e));
			} finally {
				callback.setEof();
			}
		});
		return callback;
	}

	@POST
	public Object decrypt(Request req, String key, String text, String type, String padding) {
		var sc = IOUtil.SharedCoder.get();
		try {
			DynByteBuf plaintext = VoidCrypt.statistic_decrypt(key.getBytes(StandardCharsets.UTF_8), sc.decodeBase64(text), new ByteList());
			switch (padding) {
				case "zero":
					int i = plaintext.wIndex();
					if (i > 0) {
						while (plaintext.get(i - 1) == 0) i--;
						plaintext.wIndex(i);
					}
					break;
			}

			switch (type) {
				case "UTF8","GB18030","UTF-16LE":
					req.responseHeader().put("content-type", "text/plain; charset="+type);
					req.responseHeader().put("content-length", String.valueOf(plaintext.readableBytes()));
					return (Response) rh -> {
						rh.write(plaintext);
						return plaintext.isReadable();
					};
				case "hex": return plaintext.hex();
				case "base64": return plaintext.base64();
			}
		} catch (Exception e) {
			return "解密失败: "+e;
		}
		return "参数错误";
	}
}