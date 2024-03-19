package roj.archive.ui;

import roj.archive.qz.*;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.collect.CollectionX;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.platform.Plugin;
import roj.ui.CLIUtil;
import roj.ui.EasyProgressBar;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.ui.terminal.CommandContext;
import roj.ui.terminal.CommandNode;
import roj.util.ArrayCache;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/5/15 0015 14:10
 */
public final class Archiver4J extends Plugin {
	private final CommandConsole c;
	private static final TaskPool pool = TaskPool.Common();

	public Archiver4J() {c = (CommandConsole) CLIUtil.getConsole();}
	private Archiver4J(int v) {c = new CommandConsole("\u001b[96mArchiver4J \u001b[97m> ");}

	public static void main(String[] args) {
		Archiver4J a4j = new Archiver4J(0);
		CommandConsole c = a4j.c;

		CommandNode sevenZ = literal("7z"), zip = literal("zip");
		c.register(sevenZ).register(zip);
		a4j.registerCommands(zip, sevenZ);
		CLIUtil.setConsole(c);

		HighResolutionTimer.activate();
	}

	@Override
	protected void onEnable() throws Exception {
		CommandNode sevenZ = literal("7z"), zip = literal("zip");
		registerCommand(literal("a4j").then(sevenZ).then(zip));
		registerCommands(zip, sevenZ);
	}

	private void registerCommands(CommandNode zip, CommandNode sevenZ) {
		zip.then(literal("update").then(argument("path", Argument.file(false)).executes(this::zipUpdate)));
		sevenZ.then(literal("verify").then(argument("path", Argument.file(false)).executes(this::qzVerify)));
		sevenZ.then(literal("diff").then(argument("file1", Argument.file(false)).then(argument("file2", Argument.file(false)).executes(this::qzDiff))));
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

			in1.parallelDecompress(pool, (entry, in) -> {
				String prefix = in1_should_copy.get(entry);
				if (prefix == null) return;

				try (QZWriter w = out.parallel()) {
					w.beginEntry(new QZEntry(prefix));
					IOUtil.copyStream(in, w);
					bar.addCurrent(1);
				} catch (Exception e) {
					Helpers.athrow(e);
				}
			});
			in2.parallelDecompress(pool, (entry, in) -> {
				String prefix = in2_should_copy.get(entry);
				if (prefix == null) return;

				try (QZWriter w = out.parallel()) {
					w.beginEntry(new QZEntry(prefix));
					IOUtil.copyStream(in, w);
					bar.addCurrent(1);
				} catch (Exception e) {
					Helpers.athrow(e);
				}
			});

			pool.awaitFinish();
			in1.close();
			in2.close();

			out.close();
			bar.end("Diff已保存");

			CLIUtil.setConsole(this.c);
		})));
		c1.register(literal("exit").executes(c -> CLIUtil.setConsole(this.c)));
	}
}