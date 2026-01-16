package roj.gui.impl;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.sevenz.LZMA2;
import roj.archive.sevenz.SevenZEntry;
import roj.archive.sevenz.SevenZFile;
import roj.archive.sevenz.WordBlock;
import roj.archive.sevenz.util.SevenZArchiver;
import roj.archive.zip.ZipEntry;
import roj.archive.zip.ZipFile;
import roj.collect.ArrayList;
import roj.collect.TrieTreeSet;
import roj.concurrent.*;
import roj.config.node.IntValue;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.Tokenizer;
import roj.text.logging.LogHelper;
import roj.text.logging.Logger;
import roj.ui.EasyProgressBar;
import roj.util.Helpers;
import roj.util.JVM;
import roj.webui.RPCInstance;
import roj.webui.WebUI;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static roj.archive.WinAttributes.*;

/**
 * @author Roj234
 * @since 2026/01/19 23:39
 */
public class NewUnarchiver {
	private final WebUI ui;
	private final RPCInstance rpc;
	private final Logger logger;

	public NewUnarchiver(String appid, WebUI ui) throws IOException {
		this.ui = ui;
		this.rpc = ui.newApplication(appid)
				.resource("gui/new_unarchiver/")
				//.parallel(true)
				.sends(ExtractProgress.class).sends(OpenResult.class).sends(Destination.class).sends(Act.class).sends(ListPathResult.class)
				.on(Act.class, this::handleSimpleAction)
				.on(Open.class, this::openFile)
				.on(Extract.class, this::extractFile)
				.on(ListPath.class, this::listPath)
				.windowClosed(this::windowClosed)
				.windowOpened(this::windowOpened)
				.build();
		this.logger = rpc.getLogger();
	}

	// 我怎么又来设计RPC了，这回是基于WebSocket的，请求时
	// 连接方按需请求元数据：被连接方提供的和需求的【函数名称、参数和返回值，schema】
	// 连接方检查自身是否提供了这些函数，以及类型是否匹配
	// 连接方利用这些数据生成ASM接口
	// 这个应该替换掉之前那个RPC框架，反正WS可以走gRPC
	public static void main(String[] args) throws IOException {
		JVM.AccurateTimer.setEventDriven();
		try {
			WebUI ui = WebUI.create(".webui_cache");

			String appid = "new_unarchiver";
			var app = new NewUnarchiver(appid, ui);
			ui.launch(appid, 900, 800);

			app.logger.info("如果界面无法正常打开，可以在浏览器中访问: "+ui.getServerUrl()+appid+"/");

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				var close = app.rpc.close();
				Timer.getDefault().delay(() -> ((Promise.Result) close).resolve(0), 500);
				close.then((errCode, next) -> {
					IOUtil.closeSilently(app.ui);
					app.logger.info("Bye. "+errCode);
				});
			}));
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void windowOpened() throws IOException {
		if (JVM.isRoot())
			rpc.sendData(new Act("root"));
	}

	static final class Act {
		String action;
		Act() {}

		public Act(String s) {
			action = s;
		}
	}

	static final class Open {
		File archiveFile;
		String charset;
		String[] passwords;
	}
	static final class OpenResult {
		String type;
		String password;
		String error;
	}
	static final class ListPath {
		String path;
	}
	static final class ListPathResult {
		String path;
		List<FileInfo> files = new ArrayList<>();
	}
	static final class FileInfo {
		String path;
		boolean isDir;
		long size;
		long lastModified;

		public FileInfo(String segmentName, ArchiveEntry entry) {
			path = segmentName;
			isDir = entry.isDirectory();
			size = entry.getSize();
			lastModified = entry.getModificationTime();
		}
	}
	static final class Destination {
		String path;

		public Destination(String path) {this.path = path;}
	}

	static final class Extract {
		String destPath;
		int threads;
		String conflict;

		boolean mtime, ctime, atime, symlink, antiItem, dosAttr, ntfsAcl;
		boolean pathFilter, recovery, skipPassVerify;
		String[] passwords;
		TrieTreeSet selectedItems;
	}
	final class ExtractProgress extends EasyProgressBar {
		double progress;
		String info;
		String currentFile;
		double speed;

		@Override
		public void setProgress(double progress) {
			super.setProgress(progress);

			this.progress = progress * 100;
			this.speed = getAvgSpeed(System.currentTimeMillis());
			try {
				rpc.sendData(this);
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}

	private ExecutorService pool;
	private int lastThreadCount = -1;

	private ArchiveFile<?> archiveFile;
	private ZipFile zipFile;
	private SevenZFile sevenZFile;
	private TreeBuilder.Node<ArchiveEntry> directoryNode;

	private TaskGroup extractor;

	private void windowClosed() {
		cancelExtract();
		try {
			closeFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (pool != null) {
			pool.shutdown();
			lastThreadCount = -1;
		}
	}

	private void handleSimpleAction(Act act) throws IOException {
		switch (act.action) {
			case "closeFile" -> closeFile();
			case "cancelExtract" -> cancelExtract();
			case "requestAdmin" -> requestAdmin();
			case "browserDest" -> {
				String path = ui.pickFolder("选择解压路径");
				rpc.sendData(new Destination(path));
			}
		}
	}
	private void closeFile() throws IOException {
		IOUtil.closeSilently(archiveFile);
		archiveFile = null;
		zipFile = null;
		sevenZFile = null;
		directoryNode = null;
		OpenResult message = new OpenResult();
		rpc.sendData(message);
	}
	private void cancelExtract() {
		TaskGroup group = this.extractor;
		if (group != null) group.cancel(true);
	}
	private void requestAdmin() throws IOException {
		if (JVM.isRoot()) return;

		ProcessHandle.Info info = ProcessHandle.current().info();
		RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();

		String command = info.command().orElse(null);
		List<String> vmArgs = bean.getInputArguments();
		String classPath = bean.getClassPath();
		String mainClassAndParameter = System.getProperty("sun.java.command");

		if (command != null) {
			var sb = new CharList();
			for (String vmArg : vmArgs) {
				sb.append('"').append(Tokenizer.escape(vmArg)).append("\" ");
			}

			if (mainClassAndParameter.contains(".jar")) {
				sb.append("-jar ").append(mainClassAndParameter);
			} else {
				if (!classPath.isEmpty()) sb.append("-cp ").append(classPath).append(' ');
				sb.append(mainClassAndParameter);
			}

			// 构造 PowerShell 提权
			String command1 = String.format(
					"powershell.exe -Command \"Start-Process '%s' -ArgumentList '%s' -Verb RunAs\"",
					command, sb.replace("\"", "\\\"").toStringAndFree()
			);
			logger.debug(command1);

			int returnValue;
			try {
				returnValue = Runtime.getRuntime().exec(command1).waitFor();
			} catch (InterruptedException e) {
				return;
			}

			if (returnValue == 0) System.exit(0);
		}

		rpc.sendData(new Destination(null));
	}

	private void openFile(Open options) throws IOException {
		if (archiveFile != null) return;

		var result = new OpenResult();

		File archive = options.archiveFile;

		if (archive == null || !archive.isFile()) {
			String filePath = ui.pickFile("打开压缩文件", "所有支持的类型", "*.7z;*.zip");
			if (filePath == null) {
				rpc.sendData(new Act("done"));
				return;
			}

			archive = new File(filePath);
		}

		try {
			byte[] header = new byte[32];
			try (var in = new FileInputStream(archive)) {
				int r = in.read(header);
				if (r != header.length) header = Arrays.copyOf(header, Math.max(r, 0));
			}

			if (header[0] == 'P' && header[1] == 'K') {
				result.type = "zip";
				var charset = options.charset;

				archiveFile = zipFile = new ZipFile(archive, ZipFile.FLAG_ReadCENOnly, charset.isEmpty() ? Charset.defaultCharset() : Charset.forName(charset));
			} else if (header[0] == '7' && header[1] == 'z') {
				result.type = "7z";

				List<String> passwords = null;
				while (true) {
					try {
						String password = passwords == null ? null : passwords.remove(0);
						archiveFile = sevenZFile = new SevenZFile(archive, password);
						if (password != null) result.password = password;
						break;
					} catch (CorruptedInputException | IllegalArgumentException ex) {
						if (passwords == null) passwords = ArrayList.asModifiableList(options.passwords);
						if (passwords.isEmpty()) throw ex;
					}
				}
			}
		} catch (Exception ex) {
			CharList sb = new CharList().append("打开失败：\n");
			LogHelper.printError(ex, sb);
			result.error = sb.toStringAndFree();
		}

		if (archiveFile != null) {
			var pathTree = new TreeBuilder<ArchiveEntry>();
			for (var entry : archiveFile.entries()) {
				pathTree.add(entry.getName(), entry, entry.isDirectory());
			}
			directoryNode = pathTree.build(archive.getName());
		}

		rpc.sendData(result);

		ListPath options1 = new ListPath();
		options1.path = "";
		listPath(options1);
	}

	private void listPath(ListPath options) throws IOException {
		List<String> split = TextUtil.split(options.path, "/");

		TreeBuilder.Node<ArchiveEntry> node = directoryNode;
		for (int i = 0; i < split.size(); i++) {
			for (TreeBuilder.Node<ArchiveEntry> child : node.children) {
				if (child.name.equals(split.get(i))) {
					node = child;
					break;
				}
			}
		}

		ListPathResult result = new ListPathResult();
		result.path = TextUtil.join(split, "/");
		for (TreeBuilder.Node<ArchiveEntry> child : node.children) {
			result.files.add(new FileInfo(child.fullName, child.value));
		}
		rpc.sendData(result);
	}

	private void extractFile(Extract options) throws IOException {
		String basePath;
		if (options.destPath.isBlank()) basePath = ui.pickFolder("选择解压路径");
		else basePath = options.destPath;

		if (basePath == null) {
			rpc.sendData(new Act("done"));
			return;
		}
		int threads = options.threads;
		if (lastThreadCount != threads) {
			if (threads <= 0) threads = Runtime.getRuntime().availableProcessors() + threads;
			if (threads <= 0) threads = 1;
			pool = TaskPool.newFixed(threads, "newUnarchiver-extract");
			lastThreadCount = threads;
		}

		TrieTreeSet set = options.selectedItems;
		if (set.isEmpty()) set = null;

		int storeFlag = 0;
		if (options.mtime)    storeFlag |= 1;
		if (options.atime)    storeFlag |= 2;
		if (options.ctime)    storeFlag |= 4;
		if (options.antiItem) storeFlag |= 8;
		if (options.dosAttr)  storeFlag |= 16;
		// 请注意：链接在unarchive*函数中被设置

		var bar = new ExtractProgress();

		IntValue storeFlag1 = new IntValue(storeFlag);

		TrieTreeSet javacSb = set;
		BiConsumer<ArchiveEntry, InputStream> cb = (entry, in) -> {
			if (javacSb == null || javacSb.strStartsWithThis(entry.getName())) {
				bar.currentFile = entry.getName();

				int storeFlag2 = storeFlag1.value;

				boolean entryIsLink = (entry.getWinAttributes() & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
				boolean allowLink = (storeFlag2 & 32) != 0;
				if (entryIsLink != allowLink) {
					bar.increment(entry.getSize());
					return;
				}

				String name = entry.getName();
				if (options.pathFilter) name = IOUtil.escapeFilePath(IOUtil.normalize(name));

				File file1 = new File(basePath, name);

				if (entry instanceof SevenZEntry qze && qze.isAntiItem()) {
					if ((storeFlag2&8) != 0) {
						if (qze.isDirectory()) {
							IOUtil.deleteRecursively(file1);
						} else {
							try {
								Files.deleteIfExists(file1.toPath());
							} catch (IOException e) {
								logger.warn("文件{}无法删除", e, file1);
							}
						}
					}

					return;
				}

				if (entry.isDirectory()) {
					file1.mkdirs();
					return;
				} else {
					file1.getParentFile().mkdirs();
				}

				int ord = 0;
				loop:
				while (file1.exists()) {
					switch (options.conflict) {
						case "skip": return;
						case "overwrite": break loop;
						case "rename": break;
					}
					name = IOUtil.getBaseName(name)+"("+ ++ord +")."+IOUtil.getExtension(name);
					file1 = new File(basePath, name);
				}

				try {
					if (entryIsLink) {
						bar.increment(in.available());

						if ((entry.getWinAttributes()&FILE_ATTRIBUTE_NORMAL) != 0) {
							Files.createLink(file1.toPath(), Path.of(basePath+File.separatorChar+IOUtil.readUTF(in)));
							return;
						} else {
							Files.createSymbolicLink(file1.toPath(), Path.of(IOUtil.readUTF(in)));
						}
					} else {
						assert in.available() == Math.min(entry.getSize(), Integer.MAX_VALUE);
						try (var out = new FileSource(file1)) {
							out.setLength(entry.getSize());
							SevenZArchiver.copyStreamWithProgress(in, out, bar);
						}
						assert file1.length() == entry.getSize();
						if ((storeFlag2&16) != 0) {
							var view = Files.getFileAttributeView(file1.toPath(), DosFileAttributeView.class);
							int winAttributes = entry.getWinAttributes();
							view.setArchive((winAttributes & FILE_ATTRIBUTE_ARCHIVE) != 0);
							view.setHidden((winAttributes & FILE_ATTRIBUTE_HIDDEN) != 0);
							view.setSystem((winAttributes & FILE_ATTRIBUTE_SYSTEM) != 0);
							view.setReadOnly((winAttributes & FILE_ATTRIBUTE_READONLY) != 0);
						}
					}

					if ((storeFlag2&7) != 0) {
						var view = Files.getFileAttributeView(file1.toPath(), BasicFileAttributeView.class);
						view.setTimes(
								(storeFlag2&1) == 0 ? null : entry.getPrecisionModificationTime(),
								(storeFlag2&2) == 0 ? null : entry.getPrecisionAccessTime(),
								(storeFlag2&4) == 0 ? null : entry.getPrecisionCreationTime());
					}
				} catch (Exception ex) {
					logger.warn("文件{}解压错误", ex, name);
				}
			}
		};

		try {
			if (zipFile != null) {
				extractZip(options, bar, set, cb, storeFlag1);
			} else {
				extract7z(options, bar, set, cb, storeFlag1);
			}

			extractor.await();
			bar.setProgress(1);
		} finally {
			bar.close();
		}
	}

	private void extract7z(Extract options, ExtractProgress bar, TrieTreeSet filter, BiConsumer<ArchiveEntry, InputStream> cb, IntValue storeFlag) {
		byte[] password = null;
		for (SevenZEntry entry : sevenZFile.getEntriesByPresentOrder()) {
			if (filter == null || filter.strStartsWithThis(entry.getName())) {
				block:
				if (entry.isEncrypted() && password == null) {
					for (String pass : options.passwords) {
						password = verifyPassword(entry, pass, "UTF_16LE");
						if (password != null) {
							bar.info = "密码是"+pass;
							break block;
						}
					}
					throw new IllegalArgumentException("密码错误");
				}
				bar.addTotal(entry.getSize());
			}
		}

		if (options.recovery) {
			for (WordBlock block : sevenZFile.getWordBlocks()) {
				LZMA2 codec = block.getCodec(LZMA2.class);
				if (codec != null) {
					codec.setDecompressionMode(LZMA2.ERROR_RECOVERY);
				}
			}
		}

		var group = pool.newGroup();
		this.extractor = group;

		sevenZFile.parallelDecompress(group, cb, password);
		if (options.symlink) {
			group.await();
			storeFlag.value |= 32;
			sevenZFile.parallelDecompress(group, cb, password);
		}
	}
	private void extractZip(Extract options, ExtractProgress bar, TrieTreeSet filter, BiConsumer<ArchiveEntry, InputStream> cb, IntValue storeFlag) {
		var group = pool.newGroup();
		this.extractor = group;

		byte[] password = null;
		for (ZipEntry entry : zipFile.entries()) {
			if (filter == null || filter.strStartsWithThis(entry.getName())) {
				block:
				if (entry.isEncrypted() && password == null) {
					for (String pass : options.passwords) {
						for (String charset : new String[] {"UTF8", "UTF_16LE", "UTF_16BE", "GBK", "GB18030", "SHIFT_JIS"}) {
							password = verifyPassword(entry, pass, charset);
							if (password != null) {
								bar.info = "密码是"+charset+"@"+pass;
								break block;
							}
						}
					}
					throw new IllegalArgumentException("密码错误");
				}
				bar.addTotal(entry.getSize());
			}
		}

		byte[] javacSbAgain = password;
		for (ZipEntry entry : zipFile.entries()) {
			if ((entry.getWinAttributes()&FILE_ATTRIBUTE_REPARSE_POINT) == 0) {
				if (filter == null || filter.strStartsWithThis(entry.getName())) {
					group.executeUnsafe(() -> {
						try (InputStream in = zipFile.getInputStream(entry, javacSbAgain)) {
							cb.accept(entry, in);
						}
					});
				}
			}
		}

		if (options.symlink) {
			group.await();
			storeFlag.value |= 32;
			for (ZipEntry entry : zipFile.entries()) {
				if ((entry.getWinAttributes()&FILE_ATTRIBUTE_REPARSE_POINT) != 0) {
					if (filter == null || filter.strStartsWithThis(entry.getName())) {
						group.executeUnsafe(() -> {
							try (InputStream in = zipFile.getInputStream(entry, javacSbAgain)) {
								cb.accept(entry, in);
							}
						});
					}
				}
			}
		}
	}

	private byte[] verifyPassword(ArchiveEntry entry, String pass, String charset) {
		byte[] password;
		try (InputStream in = archiveFile.getInputStream(Helpers.cast(entry), password = pass.getBytes(Charset.forName(charset)))) {
			in.skip(1048576);
			return password;
		} catch (Exception ex) {
			return null;
		}
	}
}
