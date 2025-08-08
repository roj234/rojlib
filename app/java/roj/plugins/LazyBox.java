package roj.plugins;

import roj.archive.qz.*;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.asm.ClassNode;
import roj.asmx.Context;
import roj.asmx.injector.CodeWeaver;
import roj.asmx.injector.WeaveException;
import roj.collect.CollectionX;
import roj.collect.HashMap;
import roj.collect.IntMap;
import roj.concurrent.TaskGroup;
import roj.concurrent.TaskPool;
import roj.crypt.CRC32;
import roj.crypt.ReedSolomonECC;
import roj.http.curl.DownloadTask;
import roj.io.IOUtil;
import roj.io.LimitInputStream;
import roj.io.source.FileSource;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextReader;
import roj.text.TextWriter;
import roj.ui.*;
import roj.util.*;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/5/15 14:10
 */
@SimplePlugin(id = "lazyBox", version = "1.4", desc = """
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
	删除文件夹:   archive_del_folder <file>
	
	[网页工具]
	多线程下载文件: curl <url> <saveTo> [threads]
	
	[Java工具]
	Cpk压缩: cpk <input> [output]
	Nixim注入: nixim <injector> <reference> [output]
	""")
public class LazyBox extends Plugin {
	private static final TaskPool pool = TaskPool.common();

	@Override
	protected void onEnable() throws Exception {
		registerCommand(literal("7zverify").then(argument("path", Argument.file()).executes(this::qzVerify)));
		registerCommand(literal("7zdiff").then(argument("file1", Argument.file()).then(argument("file2", Argument.file()).executes(this::qzDiff))));

		registerCommand(literal("zipupdateexe")
				.then(argument("自解压模板", Argument.file())
						.then(argument("压缩包", Argument.file())
								.executes(this::updateExe))));
		registerCommand(literal("zipupdate").then(argument("path", Argument.file()).executes(this::zipUpdate)));
		registerCommand(literal("ziprmfolder").then(argument("file", Argument.file()).executes(ctx -> {
			try (var za = new ZipArchive(ctx.argument("file", File.class))) {
				for (ZEntry ze : za.entries()) {
					String name = ze.getName();
					if (name.endsWith("/")) {
						if (ze.getSize() == 0) {
							za.put(name, null);
						} else {
							getLogger().warn("'文件夹'{}的大小不为零: {}", name, ze.getSize());

							za.put(name, null);
							za.put(name.substring(0, name.length()-1), DynByteBuf.wrap(za.get(ze)));
						}
					}
				}
				za.save();
			}
		})));

		registerCommand(literal("reccgen").then(argument("file", Argument.file()).then(argument("ratio", Argument.real(0.001, 0.5)).executes(ctx -> {
			float exceptingRatio = (float)(double)ctx.argument("ratio", Double.class);
			var file = ctx.argument("file", File.class);

			int lastDataByte = -1;
			int lastEccByte = -1;
			float lastDelta = 1;
			float lastRatio = -1;

			for (int dataBytes = 253; dataBytes > 0; dataBytes--) {
				for (int eccBytes = 2; dataBytes+eccBytes <= 255; eccBytes += 2) {
					float ratio = (eccBytes/2f) / (dataBytes+eccBytes);
					float delta = ratio - exceptingRatio;
					if (delta > 0 && delta < 0.005 && delta < lastDelta) {
						lastDelta = delta;
						lastRatio = ratio;
						lastDataByte = dataBytes;
						lastEccByte = eccBytes;
					}
				}
			}

			long fileSize = file.length();
			var recc = new ReedSolomonECC(lastDataByte, lastEccByte);
			int stride = (int) Math.min((fileSize+recc.dataSize()-1) / recc.dataSize(), (Math.min(fileSize, 1048576) / recc.maxError()));

			var metadata = new ByteList(16).put(0x00/*KIND_RS*/).put(lastDataByte).put(lastEccByte).putVUInt(stride).putVULong(fileSize);
			metadata.putInt(CRC32.crc32(metadata.array(), 0, metadata.wIndex())).put(metadata.wIndex());

			System.out.println("RS("+lastDataByte+","+(lastDataByte+lastEccByte)+") 将追加"+((float)lastEccByte)/(lastDataByte+lastEccByte)*100+"%的纠错码，允许在总体中出现"+lastRatio*100+"%的任意损坏");
			System.out.println("牢记右侧参数：【"+ metadata.base64UrlSafe()+"】。它们追加在文件尾部不受纠错码保护，如果文件损坏严重，请手动输入以纠错");
			//System.out.println("这个文件尾部看起来已经追加过纠错码了");
			System.out.println("分块卷积算法的矩阵大小为"+stride+", 使能纠错长度不大于"+(recc.maxError()*stride)+"的连续错误");
			System.out.println("按任意键（Ctrl+C以取消）以开始在文件"+file.getName()+"后追加纠错码");
			char c = TUI.key(null, new CharList());
			if (c == 0) return;

			try (var in = new LimitInputStream(new FileInputStream(file), fileSize);
				 var out = new FileOutputStream(file, true);
				 var bar = new EasyProgressBar("生成纠错码")) {

				bar.setTotal(fileSize);
				recc.generateInterleavedCode(in, out, stride, bar);
				metadata.writeToStream(out);
				bar.end("生成完毕");
			} catch (IOException e) {
				getLogger().warn("生成失败", e);
			}
		}))));
		registerCommand(literal("reccfix").then(argument("file", Argument.file()).executes(ctx -> {
			var file = ctx.argument("file", File.class);

			try (var in = new FileSource(file);
				 var bar = new EasyProgressBar("校验&纠错")) {

				var metadata = new ByteList();

				in.seek(in.length()-1);
				int dataLength = in.read();
				in.seek(in.length()-1-dataLength);
				in.readFully(metadata, dataLength);

				var crc = CRC32.crc32(metadata.array(), 0, dataLength - 4);
				if (crc != metadata.readInt(dataLength - 4)) throw new FastFailException("文件尾校验失败");

				int eccType = metadata.readUnsignedByte();
				if (eccType != 0) throw new FastFailException("不是RS纠错码："+eccType);

				var recc = new ReedSolomonECC(metadata.readUnsignedByte(), metadata.readUnsignedByte());
				var stride = metadata.readVUInt();

				var originalSize = metadata.readVULong();

				in.seek(0);
				bar.setTotal(originalSize);

				int i = recc.interleavedErrorCorrection(in, originalSize, stride, bar);

				bar.end("发现并纠正了"+(i&Integer.MAX_VALUE)+"个错误");
				if (i != 0) {
					System.out.println("纠正了一些错误，不过，纠错码中的错误并不会被纠正，建议你删除并重新生成纠错码");
				}
			} catch (Exception e) {
				getLogger().warn("校验失败", e);
			}
		})));
		registerCommand(literal("reccdel").then(argument("file", Argument.file()).executes(ctx -> {
			var file = ctx.argument("file", File.class);

			try (var in = new FileSource(file)) {
				var metadata = new ByteList();

				in.seek(in.length()-1);
				int dataLength = in.read();
				in.seek(in.length()-1-dataLength);
				in.readFully(metadata, dataLength);

				var crc = CRC32.crc32(metadata.array(), 0, dataLength - 4);
				if (crc != metadata.readInt(dataLength - 4)) throw new FastFailException("文件尾校验失败");

				int eccType = metadata.readUnsignedByte();
				if (eccType != 0) throw new FastFailException("不是RS纠错码："+eccType);

				var recc = new ReedSolomonECC(metadata.readUnsignedByte(), metadata.readUnsignedByte());
				int stride = metadata.readVUInt();

				var originalSize = metadata.readVULong();

				in.setLength(originalSize);
				System.out.println("纠错码已删除");
			} catch (Exception e) {
				getLogger().warn("校验失败", e);
			}
		})));

		//region 文件工具
		{
		CommandNode child = argument("源", Argument.path())
			.then(argument("目标", Argument.path())
				.executes(ctx -> {
			File src = ctx.argument("源", File.class);
			File dst = ctx.argument("目标", File.class);

			if (!dst.exists() && !dst.mkdirs()) {
				getLogger().error("目标不存在且无法创建");
				return;
			}

			IOUtil.movePath(src, dst, ctx.context.startsWith("fmove"));
		}));
		registerCommand(literal("fcopy").then(child));
		registerCommand(literal("fmove").then(child));
		registerCommand(literal("frmdir").then(argument("路径", Argument.path()).executes(ctx -> {
			File path = ctx.argument("路径", File.class);
			System.out.println("删除"+path.getAbsolutePath()+"及其所有文件？[y/n]");
			char c = TUI.key("YyNn");
			if (c != 'y' && c != 'Y') return;

			System.out.println(IOUtil.deletePath(path));
		})));
		registerCommand(literal("fmtime")
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
		registerCommand(literal("ftimegap").then(argument("file", Argument.path()).executes(ctx -> {
				File s = ctx.argument("file", File.class);
				BasicFileAttributes attr = Files.readAttributes(s.toPath(), BasicFileAttributes.class);
				long delta = attr.lastModifiedTime().to(TimeUnit.NANOSECONDS) - attr.creationTime().to(TimeUnit.NANOSECONDS);
				System.out.println("delta= "+delta+"ns");
		})));

		registerCommand(literal("fregreplace").then(
			argument("文件夹", Argument.folder()).then(
				argument("正则", Argument.string()).then(
					argument("替换", Argument.string()).executes(ctx -> {

			Pattern regex = Pattern.compile(ctx.argument("正则", String.class));
			String replace = ctx.argument("替换", String.class);
			File path = ctx.argument("文件夹", File.class);
			for (File file : IOUtil.listFiles(path)) {
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
		})).executes(ctx -> {
					Pattern regex = Pattern.compile(ctx.argument("正则", String.class));
					File path = ctx.argument("文件夹", File.class);
					var pool = TaskPool.common().newGroup();
					for (File file : IOUtil.listFiles(path)) {
						pool.executeUnsafe(() -> {
							try (TextReader in = TextReader.auto(file)) {
								CharList sb = IOUtil.getSharedCharBuf().readFully(in);
								sb.preg_match_callback(regex, m -> {
									System.out.println(file.getName()+":"+m.start()+": "+m.group());
								});
							}
						});
					}
					pool.await();
		}))));
		}
		//endregion

		registerCommand(literal("curl").then(argument("网址", Argument.string())
			.then(argument("保存到", Argument.fileOptional(true))
				.executes(this::download)
				.then(argument("线程数", Argument.number(1, 256))
					.executes(this::download)))));

		Command nixim = ctx -> {
			var nx = new CodeWeaver();
			File src = ctx.argument("注入(Nixim)", File.class);
			if (src.isDirectory()) {
				IOUtil.listFiles(src, file -> {
					if (IOUtil.extensionName(file.getName()).equals("class")) {
						try {
							nx.read(ClassNode.parseSkeleton(IOUtil.read(file)));
						} catch (WeaveException | IOException e) {
							Helpers.athrow(e);
						}
					}
					return false;
				});
			} else {
				if (IOUtil.extensionName(src.getName()).equals("class")) {
					nx.read(ClassNode.parseSkeleton(IOUtil.read(src)));
				} else {
					try (var zf = new ZipFile(src)) {
						for (var ze : zf.entries()) {
							if (IOUtil.extensionName(ze.getName()).equals("class")) {
								nx.read(ClassNode.parseSkeleton(zf.get(ze)));
							}
						}
					}
				}
			}

			src = ctx.argument("源", File.class);
			File dst = ctx.argument("保存至", File.class);
			if (dst == null) dst = IOUtil.deriveOutput(src, "-注入");
			IOUtil.copyFile(src, dst);

			try (var archive = new ZipArchive(dst)) {
				for (var entry : nx.registry().entrySet()) {
					String file = entry.getKey().replace('.', '/')+".class";
					InputStream in = archive.getStream(file);
					if (in == null) {
						System.err.println("nixim target "+file+" not found");
						continue;
					}

					try {
						var klass = new Context(entry.getKey(), in);
						nx.transform(entry.getKey(), klass);
						archive.put(file, klass::getCompressedShared, true);
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						in.close();
					}
				}

				archive.save();
			}

			System.out.println("注入完成");
		};
		registerCommand(literal("nixim")
			.then(argument("注入(Nixim)", Argument.file())
				.then(argument("源", Argument.file())
					.executes(nixim)
					.then(argument("保存至", Argument.fileOptional(true)).executes(nixim)))));
	}

	private void zipUpdate(CommandContext ctx) throws IOException {
		var za = new ZipArchive(ctx.argument("path", File.class));

		Collection<String> entryName = CollectionX.mapToView(za.entries(), ZEntry::getName, ZEntry::new);
		Map<String, String> fileView = CollectionX.toMap(entryName, x -> x.endsWith("/") ? null : x);

		var update = new Shell("\u001b[96mZUpdate \u001b[97m> ");
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
					return Helpers.athrow2(e);
				}
			}, true);
		}))));
		update.register(literal("del").then(argument("name", Argument.oneOf(fileView)).executes(c -> za.put(ctx.argument("name", String.class), null))));
		update.register(literal("reload").executes(c -> za.getModified().clear()));
		update.register(literal("save").executes(c -> za.save()));
		update.register(literal("exit").executes(c -> {
			za.close();
			Tty.popHandler();
		}));
		update.onKeyboardInterrupt(() -> update.executeSync("exit"));
		update.sortCommands();
		Tty.pushHandler(update);
	}

	private void qzVerify(CommandContext ctx) {
		File file = ctx.argument("path", File.class);

		EasyProgressBar bar = new EasyProgressBar("验证压缩文件", "B");

		AtomicReference<Throwable> failed = new AtomicReference<>();
		try (QZArchive archive = new QZArchive(file)) {
			for (QZEntry entry : archive.getEntriesByPresentOrder()) {
				bar.addTotal(entry.getSize());
			}

			TaskGroup monitor = pool.newGroup();
			archive.parallelDecompress(monitor, (entry, in) -> {
				byte[] arr = ArrayCache.getByteArray(40960, false);
				try {
					while (true) {
						int r = in.read(arr);
						if (r < 0) break;

						if (failed.get() != null) throw new FastFailException("-other thread failed-");
						bar.increment(r);
					}
				} catch (FastFailException e) {
					throw e;
				} catch (Throwable e) {
					monitor.cancel();
					failed.set(e);
					throw new FastFailException("-验证失败-");
				} finally {
					ArrayCache.putArray(arr);
				}
			}, null);

			monitor.await();
		} catch (Exception e) {
			failed.set(e);
		}

		Throwable exception = failed.getAndSet(null);
		if (exception != null) {
			bar.end("验证失败", Tty.RED);
			exception.printStackTrace();
		} else {
			bar.end("验证成功");
		}
	}

	private void qzDiff(CommandContext ctx) throws IOException {
		QZArchive in1 = new QZArchive(ctx.argument("file1", File.class));
		QZArchive in2 = new QZArchive(ctx.argument("file2", File.class));
		HashMap<String, QZEntry> remain = in1.getEntries();

		int add = 0, change = 0, del = 0, move = 0;

		IntMap<QZEntry> in2_by_crc32 = new IntMap<>();
		HashMap<QZEntry, String> in1_should_copy = new HashMap<>(), in2_should_copy = new HashMap<>();
		for (QZEntry entry : in2.getEntriesByPresentOrder()) {
			if (entry.isDirectory()) continue;

			QZEntry oldEntry = remain.remove(entry.getName());
			if (oldEntry == null) {
				QZEntry prev = in2_by_crc32.put(entry.getCrc32(), entry);
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
		Shell c1 = new Shell("\u001b[96m7zDiff \u001b[97m> ");
		Tty.pushHandler(c1);
		c1.register(literal("save").then(argument("out", Argument.string()).executes(c -> {
			QZFileWriter out = new QZFileWriter(c.argument("out", String.class));
			out.setCodec(new LZMA2(3));

			for (QZEntry oldEntry : remain.values()) {
				if (oldEntry.isDirectory()) continue;
				QZEntry entry = in2_by_crc32.remove(oldEntry.getCrc32());
				if (entry != null) {
					in2_should_copy.remove(entry);
					out.beginEntry(QZEntry.ofNoAttribute("renamed/"+oldEntry.getName()));
					out.write(entry.getName().getBytes(StandardCharsets.UTF_8));
				} else {
					in1_should_copy.put(oldEntry, "del/"+oldEntry.getName());
				}
			}

			out.flush();

			EasyProgressBar bar = new EasyProgressBar("复制块", "块");
			bar.addTotal(in1_should_copy.size()+in2_should_copy.size());

			var monitor = pool.newGroup();

			copy(monitor, in1, in1_should_copy, out, bar);
			copy(monitor, in2, in2_should_copy, out, bar);

			monitor.await();

			in1.close();
			in2.close();

			out.close();
			bar.end("Diff已保存");

			Tty.popHandler();
		})));
		c1.register(literal("exit").executes(c -> {
			in1.close();
			in2.close();

			Tty.popHandler();
		}));
	}
	private static void copy(TaskGroup monitor, QZArchive arc, HashMap<QZEntry, String> should_copy, QZFileWriter out, EasyProgressBar bar) {
		arc.parallelDecompress(monitor, (entry, in) -> {
			String prefix = should_copy.get(entry);
			if (prefix == null) return;

			try (QZWriter w = out.newParallelWriter()) {
				w.beginEntry(QZEntry.ofNoAttribute(prefix));
				IOUtil.copyStream(in, w);
				bar.increment(1);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
		});
	}

	private void download(CommandContext ctx) {
		String url = ctx.argument("网址", String.class);
		File saveTo = ctx.argument("保存到", File.class);

		// Solved, you can use ?? next time
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
}