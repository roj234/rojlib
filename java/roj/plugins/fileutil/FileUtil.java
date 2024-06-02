package roj.plugins.fileutil;

import roj.archive.qz.*;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.collect.CollectionX;
import roj.collect.IntMap;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.platform.Plugin;
import roj.platform.SimplePlugin;
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
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
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
@SimplePlugin(id = "fileutil", version = "1.0", desc = "方便的文件工具")
public class FileUtil extends Plugin {
	private final CommandConsole c;
	private static final TaskPool pool = TaskPool.Common();

	public FileUtil() {c = (CommandConsole) CLIUtil.getConsole();}

	@Override
	protected void onEnable() throws Exception {
		registerCommand(literal("zipupdate").then(argument("path", Argument.file(false)).executes(this::zipUpdate)));
		registerCommand(literal("7zverify").then(argument("path", Argument.file(false)).executes(this::qzVerify)));
		registerCommand(literal("7zdiff").then(argument("file1", Argument.file(false)).then(argument("file2", Argument.file(false)).executes(this::qzDiff))));

		registerCommand(literal("filemtime").then(
				argument("src", Argument.file(null)).then(
					argument("dst", Argument.file(null)).executes(ctx -> {

						File s = ctx.argument("src", File.class);
						File d = ctx.argument("dst", File.class);
						System.out.println(d.setLastModified(s.lastModified()));
					})).then(
					argument("time", Argument.Long(0, Long.MAX_VALUE)).executes(ctx -> {
						File s = ctx.argument("src", File.class);
						long d = ctx.argument("time", Long.class);
						System.out.println(s.setLastModified(d));
					}))));
		registerCommand(literal("writecost").then(
			argument("file", Argument.file(null)).executes(ctx -> {
				File s = ctx.argument("file", File.class);
				BasicFileAttributes attr = Files.readAttributes(s.toPath(), BasicFileAttributes.class);
				long delta = attr.lastModifiedTime().to(TimeUnit.NANOSECONDS) - attr.creationTime().to(TimeUnit.NANOSECONDS);
				System.out.println("delta= "+delta+"ns");
			})));

		CommandNode child = argument("src", Argument.file(null)).then(
			argument("dst", Argument.file(null)).executes(ctx -> {

			File src = ctx.argument("src", File.class);
			File dst = ctx.argument("dst", File.class);

			if (!dst.exists() && !dst.mkdirs()) {
				getLogger().error("目标不存在且无法创建");
				return;
			}

			IOUtil.movePath(src, dst, ctx.context.startsWith("mv"));
		}));
		registerCommand(literal("cp").then(child));
		registerCommand(literal("mv").then(child));

		registerCommand(literal("rd").then(
			argument("src", Argument.file(null)).executes(ctx -> {
			File path = ctx.argument("src", File.class);
			System.out.println("删除"+path.getAbsolutePath()+"及其所有文件？[y/n]");
			char c = CLIUtil.awaitCharacter(MyBitSet.from("YyNn"));
			if (c != 'y' && c != 'Y') return;

			System.out.println(IOUtil.deletePath(path));
		})));

		registerCommand(literal("batchreplace").then(
			argument("path", Argument.file(true)).then(
				argument("regex", Argument.string()).then(
					argument("replace", Argument.string()).executes(ctx -> {

			Pattern regex = Pattern.compile(ctx.argument("regex", String.class));
			String replace = ctx.argument("replace", String.class);
			File path = ctx.argument("path", File.class);
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
	}

	private void zipUpdate(CommandContext ctx) throws IOException {
		ZipArchive za = new ZipArchive(ctx.argument("path", File.class));

		Collection<String> entryName = CollectionX.mapToView(za.entries(), ZEntry::getName, ZEntry::new);
		Map<String, String> fileView = CollectionX.toMap(entryName, x -> x.endsWith("/") ? null : x);

		CommandConsole update = new CommandConsole("\u001b[96mZUpdate \u001b[97m> ");
		CLIUtil.setConsole(update);

		update.register(literal("set").then(argument("name", Argument.suggest(fileView)).then(argument("path", Argument.file(false)).executes(c -> {
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
			CLIUtil.setConsole(this.c);
		}));
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

			CLIUtil.setConsole(this.c);
		})));
		c1.register(literal("exit").executes(c -> CLIUtil.setConsole(this.c)));
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
}