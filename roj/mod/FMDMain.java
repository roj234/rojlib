package roj.mod;

import roj.archive.zip.*;
import roj.asm.ATList;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.task.AsyncTask;
import roj.concurrent.timing.Scheduled;
import roj.config.data.*;
import roj.config.word.Tokenizer;
import roj.dev.ByteListOutput;
import roj.dev.Compiler;
import roj.io.IOUtil;
import roj.mapper.CodeMapper;
import roj.mapper.ConstMapper;
import roj.mapper.ConstMapper.State;
import roj.mapper.MapUtil;
import roj.mapper.util.Desc;
import roj.mapper.util.ResWriter;
import roj.mod.FileFilter.CmtATEntry;
import roj.mod.MCLauncher.RunMinecraftTask;
import roj.mod.plugin.PluginContext;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CmdUtil;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static javax.swing.JOptionPane.*;
import static roj.mod.Shared.*;

/**
 * FMD Main class
 *
 * @author Roj234
 * @since 2021/6/18 10:51
 */
public final class FMDMain {
	static boolean isCLI, noGUI;
	static Scheduled shinyTask;

	@SuppressWarnings("fallthrough")
	public static void main(String[] args) throws IOException, InterruptedException {
		if (!isCLI) {
			Shared.loadProject();
			if (args.length == 0) {
				CmdUtil.rainbow("FMD 更快的mod开发环境 " + VERSION + " By Roj234");
				System.out.println();

				shinyTask = PeriodicTask.executeTimer(() -> {
					synchronized (CmdUtil.originalOut) {
						CmdUtil.cursorBackup();
						CmdUtil.cursorUp(8000);
						CmdUtil.cursorDownCol0(1);
						CmdUtil.clearLine();
						CmdUtil.sonic("https://www.github.com/roj234/rojlib");
						CmdUtil.cursorRestore();
					}
				}, 150, 9999);

				System.out.println();
				CmdUtil.info("可用指令: build, run, project, edit, ref, at, reobf, deobf, gc, reload, auto");
				System.out.println();
			}
		}

		if (args.length == 0) {
			if (isCLI) {
				System.out.println();
				return;
			}

			isCLI = true;

			Map<String, String> shortcuts = Helpers.cast(CONFIG.getOrCreateMap("CLI Shortcuts").unwrap());

			SimpleList<String> tmp = new SimpleList<>();
			Tokenizer t = new Tokenizer().defaultC2C(0);
			while (true) {
				String input = UIUtil.userInput("> ");

				try {
					t.init(shortcuts.getOrDefault(input, input));
					tmp.clear();
					while (t.hasNext()) {
						tmp.add(t.next().val());
					}

					main(tmp.toArray(new String[tmp.size()]));
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		if (shinyTask != null) {
			shinyTask.cancel();
		}

		long startTime = System.currentTimeMillis();
		int exitCode = 0;

		switch (args[0]) {
			case "sd": case "stackdeobf":
				exitCode = stackDeobf();
				break;
			case "b": case "build":
				exitCode = build(buildArgs(args));
				break;
			case "r": case "run":
				exitCode = run(buildArgs(args));
				break;
			case "p": case "project":
				exitCode = _project(args, project);
				break;
			case "e": case "edit":
				exitCode = _edit(args);
				break;
			case "d": case "deobf":
				exitCode = deobf(args, false);
				break;
			case "reobf":
				exitCode = deobf(args, true);
				break;
			case "ref": case "reflect":
				ReflectTool.start(!isCLI);
				break;
			case "at": case "preAT":
				exitCode = preAT();
				break;
			case "a": case "auto":
				assert isCLI;
				if (args.length < 2) {
					System.out.println("auto <true/false>");
					break;
				}
				AutoCompile.setEnabled(Boolean.parseBoolean(args[1]));
				break;
			case "k": case "kill":
				assert isCLI;
				if (MCLauncher.task != null && !MCLauncher.task.isDone()) {
					MCLauncher.task.cancel(true);
				}
				break;
			case "res": case "restart":
				main(new String[] {"kill"});
				main(new String[] {"run zl"});
				break;
			case "gc":
				long used = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
				ATHelper.close();
				System.runFinalization();
				System.gc();
				System.runFinalization();
				System.out.println("释放了 " + TextUtil.scaledNumber(used-(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())) + "B内存.");
				break;
			default: CmdUtil.warning("参数错误");
		}

		if (isCLI) return;

		long costTime = System.currentTimeMillis() - startTime;
		CmdUtil.info("主线程运行时长" + (costTime / 1000d));

		if (exitCode != 0) System.exit(exitCode);
	}

	private static final Pattern EXCEPTION_PATTERN = Pattern.compile("at ([a-zA-Z\\d\\-_.]+)\\(.+?(?::\\d+)?\\)");
	private static int stackDeobf() throws IOException {
		System.out.println("输入任意数据，并以END终止");

		Shared.loadMapper();
		CharList out = IOUtil.getSharedCharBuf();
		while (true) {
			String line = UIUtil.in.readLine();
			if (line == null || line.equals("END")) break;

			Matcher exc = EXCEPTION_PATTERN.matcher(line);
			if (exc.matches()) {
				String data = line.substring(exc.start(1), exc.end(1));

				int i = data.lastIndexOf('.');
				String clazz = data.substring(0, i).replace('.', '/');
				String method = data.substring(i +1);

				clazz = mapperFwd.getClassMap().flip().getOrDefault(clazz, clazz);
				String methodA = method;
				for (Map.Entry<Desc, String> entry : mapperFwd.getMethodMap().entrySet()) {
					if (entry.getValue().equals(method)) {
						Desc d = entry.getKey();
						if (d.owner.equals(clazz)) {
							methodA = d.name;
							break;
						} else {
							methodA = d.name;
						}
					}
				}

				out.append(line, 0, exc.start(1)).append(clazz).append('.').append(methodA).append('\n');
			} else {
				out.append(line).append('\n');
			}
		}

		System.out.print(out);
		return 0;
	}

	private static void showAbout() {
		ImageIcon icon = null;
		try {
			InputStream iin = FMDMain.class.getClassLoader().getResourceAsStream("qrcode.png");
			if (iin != null) icon = new ImageIcon(ImageIO.read(iin));
		} catch (IOException ignored) {}

		JOptionPane.showMessageDialog(null,
			"FMD - 快速mod开发环境 - 作者 Roj234\n" +
				VERSION + "\n" +
				"\n" +
				"  优化处理流程,提高速度\n" +
				"  修复部分bug\n", "扫码支持我", INFORMATION_MESSAGE, icon);
	}

	// 在运行名称集和开发名称集中转换
	// 参数：[deobf a a的前置 a的前置的前置...]
	public static int deobf(String[] args, boolean reverse) throws IOException {
		List<File> files = new SimpleList<>();

		for (int i = 1; i < args.length; i++) files.add(new File(args[i]));
		if (files.isEmpty()) {
			if (noGUI) {
				files = Collections.singletonList(UIUtil.readFile("文件"));
			} else {
				JFileChooser fc = new JFileChooser(BASE);
				fc.setFileSelectionMode(JFileChooser.FILES_ONLY);

				while (true) {
					int status = fc.showOpenDialog(null);
					if (status == JFileChooser.APPROVE_OPTION) {
						files.add(fc.getSelectedFile());
						if (JOptionPane.showConfirmDialog(null, "有前置吗?", "询问", YES_NO_OPTION) == NO_OPTION) {
							break;
						}
					} else {
						break;
					}
				}
			}
		}

		loadMapper();
		ConstMapper m = reverse ? mapperFwd : loadReverseMapper();
		m.getExtendedSuperList().clear();

		MyHashMap<String, byte[]> res = new MyHashMap<>(100);

		for (int i = files.size() - 1; i >= 0; i--) {
			File file = files.get(i);
			List<Context> list = Context.fromZip(file, StandardCharsets.UTF_8, res);

			String path = file.getAbsolutePath();
			int index = path.lastIndexOf('.');
			File out = index == -1 ? new File(path + "-结果") : new File(path.substring(0, index) + "-结果.jar");
			ZipFileWriter zfw = new ZipFileWriter(out);

			AsyncTask<Void> resTask = new AsyncTask<>(new ResWriter(zfw, res));
			Task.pushTask(resTask);

			m.remap(DEBUG, list);
			m.getExtendedSuperList().add(m.snapshot(null));

			if (doMapClassName) new CodeMapper(m).remap(DEBUG, list);

			try {
				resTask.get();
			} catch (Exception e) {
				e.printStackTrace();
			}

			for (int j = 0; j < list.size(); j++) {
				Context ctx = list.get(j);
				zfw.writeNamed(ctx.getFileName(), ctx.get());
			}
			zfw.finish();

			res.clear();
		}

		CmdUtil.success("操作成功完成");

		return 0;
	}

	public static int preAT() throws IOException {
		// 关闭文件监控
		watcher.removeAll();

		Map<String, Collection<String>> map = ATList.getMapping(); map.clear();

		Shared.loadSrg2Mcp();

		readTextList((s) -> {
			int i = s.indexOf(' ');
			if (i < 0) {
				CmdUtil.warning("Unknown entry " + s);
				return;
			}
			ATList.add(s.substring(0,i), s.substring(i+1));
		}, "预AT.编译期");
		for (CEntry ce : CONFIG.getDot("预AT.编译期+运行期").asMap().values()) {
			if (ce.getType() == Type.MAP) loadATMap(ce.asMap(), true);
		}

		if (map.isEmpty()) {
			CmdUtil.info("没有AT");
			return 0;
		}

		try (ZipOutput zo = new ZipOutput(new File(BASE, "class/"+MC_BINARY+".jar"))) {
			zo.setCompress(true);
			zo.begin(false);
			ATHelper.transform(zo, map, true);

			// 备份
			ZipArchive atBackup = ATHelper.getBackupFile();
			ZipArchive mcBin = zo.getMZF();

			MyHashSet<String> removable = new MyHashSet<>(atBackup.getEntries().keySet());
			for (EntryMod mod : mcBin.getModified()) {
				// 删除老的
				removable.remove(mod.getName());

				// 已经包含
				if (atBackup.getEntries().containsKey(mod.getName())) continue;

				// 追加新的
				atBackup.putStream(mod.getName(), mcBin.getStream(mod.getName()), true);
			}
			for (String name : removable) atBackup.put(name, null);
			atBackup.store();

			// 应用修改
			zo.end();
		}

		CmdUtil.success("操作成功完成");

		return 0;
	}

	// region PreAT.Util

	private static void loadATMap(CMapping at, boolean dev) {
		if (at.size() == 0) return;

		for (Map.Entry<String, CEntry> entry : at.entrySet()) {
			String k = entry.getKey();

			CEntry ce = entry.getValue();
			switch (ce.getType()) {
				case LIST:
					List<CEntry> rl = ce.asList().raw();
					for (int i = 0; i < rl.size(); i++) {
						CEntry ce1 = rl.get(i);
						ATList.add(k, dev ? toDevName(ce1) : ce1.asString());
					}
					break;
				case STRING:
					ATList.add(k, dev ? toDevName(ce) : ce.asString());
					break;
			}
		}
	}
	private static String toDevName(CEntry ce1) {
		String s = TextUtil.split(ce1.asString(), ' ').get(0);
		int i = s.lastIndexOf('|');
		if (i != -1) {
			String s1 = s.substring(0, i);
			s = srg2mcp.getOrDefault(s1, s1) + s.substring(i);
		} else {
			s = srg2mcp.getOrDefault(s, s);
		}
		return s;
	}

	// endregion

	public static int _edit(String[] args) throws IOException {
		List<File> files = getProjectJson(args);

		File selected = files.get(UIUtil.selectOneFile(files, "配置文件"));

		Project conf = Project.load(selected.getName().substring(0, selected.getName().lastIndexOf('.')));
		ConfigEditWindow.open(conf, null);
		return 0;
	}

	public static int _project(String[] args, Project p) throws IOException {
		List<File> files = getProjectJson(args);

		CmdUtil.info("当前的配置文件: " + (p == null ? "无" : p.getFile()));
		System.out.println();

		if (files.isEmpty()) {
			CmdUtil.info("没有配置文件! 创建默认配置...");
			String name = UIUtil.userInput("新模组的modid");
			ConfigEditWindow.open(Project.load(name), null);
			files.add(new File(CONFIG_DIR, name+".json"));
		}

		File selected = files.get(UIUtil.selectOneFile(files, "配置文件"));

		Shared.setProject(selected.getName().substring(0, selected.getName().lastIndexOf('.')));
		CmdUtil.success("配置文件已选择: " + selected);

		return 0;
	}

	// region EditConfig.Util

	private static List<File> getProjectJson(String[] args) {
		List<File> files;
		if (args.length > 1) {
			files = Collections.singletonList(new File(CONFIG_DIR, args[1]+".json"));
		} else {
			files = IOUtil.findAllFiles(CONFIG_DIR, (f) -> {
				String name = f.getName().toLowerCase();
				return name.endsWith(".json");
			});
		}
		return files;
	}

	// endregion

	public static int run(Map<String, Object> args) throws IOException {
		MCLauncher.load();
		if (MCLauncher.task != null && !MCLauncher.task.isDone()) {
			if (UIUtil.readBoolean("是否结束游戏进程?")) {
				MCLauncher.task.cancel(true);
				MCLauncher.task = null;
			} else {
				return -1;
			}
		}

		CMapping mc_conf = MCLauncher.config.get("mc_conf").asMap();
		if (mc_conf.size() == 0) {
			CmdUtil.error("启动配置不存在，请重新setup");
			return -1;
		}

		File dest = new File(mc_conf.getString("root") + "/mods/");
		if (!dest.isDirectory() && !dest.mkdirs()) {
			CmdUtil.warning("无法创建mods文件夹");
			return -1;
		}

		Shared._lock();
		AutoCompile.beforeCompile();
		int v = -1;
		try {
			v = compile(args, project, BASE, 0);
		} finally {
			Shared._unlock();
			AutoCompile.afterCompile(v);
		}

		if (v < 0) return v;

		for (Project proj : project.getAllDependencies()) {
			copy4run(dest, proj);
		}
		copy4run(dest, project);

		if (CONFIG.getBool("启用热重载")) {
			launchHotReload();

			int port = 0xFFFF & CONFIG.getInteger("重载端口");
			if (port == 0) port = 4485;

			if (!mc_conf.containsKey("__hr_patched")) {
				String par = " -javaagent:" + new File(BASE, "util/FMD-agent.jar").getAbsolutePath() + "=" + port;
				CEntry jvm = mc_conf.get("jvmArg");
				((CString) jvm).value = ((CString) jvm).value.concat(par);
				mc_conf.put("__hr_patched", 1);
			}

			if (DEBUG) CmdUtil.info("重载工具已在端口 " + port + " 上启动");
		}

		MCLauncher.clearLogs(null);
		Task.pushTask(MCLauncher.task = new RunMinecraftTask(true));
		return 0;
	}

	private static void copy4run(File dest, Project p) throws IOException {
		File src = new File(BASE, p.name + '-' + p.version + ".jar");
		File dst = new File(dest, p.name + ".jar");
		IOUtil.copyFile(src, dst);
	}

	public static int build(Map<String, Object> args) throws IOException {
		if (hotReload != null) args.put("$$HR$$", new ArrayList<>());

		Shared._lock();
		AutoCompile.beforeCompile();
		int v = -1;
		try {
			v = compile(args, project, BASE, 0);
		} finally {
			Shared._unlock();
			AutoCompile.afterCompile(v);
		}

		List<ConstantData> modified = Helpers.cast(args.get("$$HR$$"));
		if (modified != null && !modified.isEmpty()) {
			hotReload.sendChanges(modified);
			if (DEBUG) CmdUtil.success("发送重载请求");
		}

		return v;
	}

	private static void executeCommand(Project proj, File base) throws IOException {
		String jarName = proj.name + '-' + proj.version + ".jar";
		String jarPath = base.getAbsolutePath() + '/' + jarName;

		CMapping cmd = CONFIG.get("编译成功后执行指令").asMap();
		CList list = cmd.get("**").asList();
		ex(list, jarPath);
		list = (cmd.containsKey(proj.name) ? cmd.get(proj.name) : cmd.get("*")).asList();
		ex(list, jarPath);
	}

	private static void ex(CList suc, String jarPath) throws IOException {
		for (int i = 0; i < suc.size(); i++) {
			Runtime.getRuntime().exec(suc.get(i).asString().replace("%jar", jarPath).replace("%name", project.name));
		}
	}

	/**
	 * @param flag Bit 1 : run (NoVersion) , Bit 2 : dependency mode
	 */
	private static int compile(Map<String, ?> args, Project p, File jarDest, int flag) throws IOException {
		if ((flag & 2) == 0) {
			for (Project proj : p.getAllDependencies()) {
				if (compile(args, proj, jarDest, flag | 2) < 0) {
					CmdUtil.info("前置编译失败");
					return -1;
				}
			}
		}

		boolean increment = args.containsKey("zl");
		long time = System.currentTimeMillis();

		// region 检测代码更改

		File source = p.srcPath;
		if (!source.isDirectory()) {
			CmdUtil.warning("源码目录 " + source.getAbsolutePath() + " 不存在");
			return -1;
		}

		List<File> files = null;
		if (increment) {
			MyHashSet<String> set = watcher.getModified(p, FileWatcher.ID_SRC);
			if (!set.contains(null)) {
				files = new ArrayList<>(set.size());
				synchronized (set) {
					for (String s : set) {
						files.add(new File(s));
					}
					if (DEBUG) System.out.println("FileWatcher.getSrc(): " + set);
					//set.clear();
				}
			}
		}

		long stamp = p.binJar.lastModified();

		if (files == null) {
			files = IOUtil.findAllFiles(source, FileFilter.INST.reset(stamp, increment ? FileFilter.F_SRC_TIME : FileFilter.F_SRC_ANNO));
			if (DEBUG) System.out.println("FileFilter.getSrc(): " + (files.size() < 100 ? files : files.subList(0, 100)));
		}

		if (DEBUG) System.out.println("检测更改 " + (System.currentTimeMillis() - time));

		// endregion
		// region 检测权限转换标记

		if (increment) {
			for (int i = 0; i < files.size(); i++) {
				File file = files.get(i);
				if (FileFilter.checkATComments(file)) {
					List<CmtATEntry> ent = FileFilter.cmtEntries;
					for (int j = 0; j < ent.size(); j++) {
						if (!p.atEntryCache.contains(ent.get(j))) {
							ent.clear();

							if (args.containsKey("forcezl")) {
								System.out.println("请手动全量编译");
								return -1;
							}

							CmdUtil.warning("找到AT注解, 使用全量编译");
							files = IOUtil.findAllFiles(source, FileFilter.INST.reset(0, FileFilter.F_SRC_ANNO));
							increment = false;
							break;
						}
					}
					ent.clear();
					if (!increment) break;
				}
			}
		}

		// endregion

		File jarFile = new File(jarDest, p.name + '-' + p.version + ".jar");
		if (!ensureWritable(jarFile)) return -1;

		// region 无代码变动的处理
		if (files.isEmpty()) {
			p.registerWatcher();
			if (increment) {
				if (!jarFile.isFile()) {
					CmdUtil.warning("请手动全量编译");
					return -1;
				}

				// bug: 如果是启动后第一次更新资源，并不会填充resources
				try (ZipOutput zo1 = updateDst(p, jarFile)) {
					zo1.begin(false);

					p.getResourceTask(stamp).execute();
				}

				if ((flag & 3) == 0) CmdUtil.info("更新了资源(若有)");
			} else {
				if ((flag & 3) == 0) CmdUtil.info("无源文件");
			}
			try {
				executeCommand(p, BASE);
			} catch (IOException e) {
				CmdUtil.warning("无法执行指令", e);
			}
			return 0;
		}
		// endregion
		// region 应用权限转换

		if (!FileFilter.cmtEntries.isEmpty()) {
			if (p.atName.isEmpty()) {
				CmdUtil.error(p.name + " 使用了AT注解,请设置AT配置的存放位置");
				return -1;
			}

			p.atEntryCache.clear();
			p.atEntryCache.addAll(FileFilter.cmtEntries);

			Map<String, Collection<String>> map = ATList.getMapping(); map.clear();
			loadATMap(CONFIG.getDot("预AT.编译期+运行期").asMap().get(p.name).asMap(), false);

			CharList atData = IOUtil.getSharedCharBuf();
			for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
				for (String val : entry.getValue()) {
					atData.append("public-f ").append(entry.getKey()).append(' ').append(val).append('\n');
				}
			}
			map.clear();

			List<FileFilter.CmtATEntry> cmt = FileFilter.cmtEntries;
			for (int i = 0; i < cmt.size(); i++) {
				FileFilter.CmtATEntry entry = cmt.get(i);
				if (!entry.compile) {
					for (String val : entry.value) {
						atData.append("public-f ").append(entry.clazz).append(' ').append(val).append('\n');
					}
				}
				map.computeIfAbsent(entry.clazz, Helpers.fnMyHashSet()).addAll(entry.value);
			}

			try (FileOutputStream fos = new FileOutputStream(p.atConfigPathStr)) {
				IOUtil.SharedCoder.get().encodeTo(fos);
			}

			ATHelper.makeATJar(p.name, map);
		}

		// endregion

		boolean canIncrementWrite = increment & jarFile.isFile() & p.state != null;

		ZipOutput zo1 = updateDst(p, jarFile);
		zo1.begin(!canIncrementWrite);

		// region 更新资源文件

		AsyncTask<Void> writeRes = p.getResourceTask(canIncrementWrite?stamp:-1);
		Task.pushTask(writeRes);

		// endregion
		// region 制备依赖

		CharList libBuf = new CharList(200);

		if (p.binJar.length() > 0 && increment) {
			libBuf.append(p.binJar.getAbsolutePath()).append(File.pathSeparatorChar);
		}

		libBuf.append(ATHelper.getJarName(p.name).getAbsolutePath()).append(File.pathSeparatorChar);

		List<Project> dependencies = p.getAllDependencies();
		for (int i = 0; i < dependencies.size(); i++) {
			libBuf.append(dependencies.get(i).binJar.getAbsolutePath()).append(File.pathSeparatorChar);
		}
		libBuf.append(getLibClasses());

		// endregion

		SimpleList<String> options = CONFIG.get("编译参数").asList().asStringList();
		options.addAll("-cp", libBuf.toString(), "-encoding", p.charset.name());

		Compiler.showErrorCode(args.containsKey("showErrorCode"));
		if (p.compiler.compile(options, files)) {
			if (CONFIG.getBool("自动编译")) AutoCompile.setEnabled(true);

			try {
				writeRes.get();
			} catch (Exception e) {
				CmdUtil.warning("资源写入失败", e);
			}

			if (DEBUG) System.out.println("编译完成 " + (System.currentTimeMillis() - time));

			MyHashMap<String, String> resources = p.resources;

			List<ByteListOutput> outputs = p.compiler.getCompiled();
			List<Context> list = Helpers.cast(outputs);
			for (int i = 0; i < outputs.size(); i++) {
				ByteListOutput out = outputs.get(i);
				if (resources.remove(out.getName()) != null) {
					CmdUtil.warning("资源与类重复 " + out.getName());
				}

				list.set(i, new Context(out.getName(), out.getOutput()));
			}

			int compiledCount = list.size();

			ZipOutput stampZip = p.binFile;
			try {
				stampZip.begin(!increment);
			} catch (Throwable e) {
				CmdUtil.warning("压缩文件有错误,请尝试全量", e);
				return -1;
			}

			try {
				for (int i = 0; i < list.size(); i++) {
					Context out = list.get(i);
					stampZip.set(out.getFileName(), out);
				}

				if (increment & !canIncrementWrite) {
					MyHashSet<String> changed = new MyHashSet<>(list.size());
					for (int i = 0; i < list.size(); i++) {
						changed.add(list.get(i).getFileName());
					}

					ZipArchive mzf = stampZip.getMZF();
					boolean isOpen = mzf.isOpen();
					if (!isOpen) mzf.reopen();
					ByteList buf = IOUtil.getSharedByteBuf();
					for (ZEntry file : mzf.getEntries().values()) {
						if (!changed.contains(file.getName())) {
							Context ctx = new Context(file.getName(), mzf.get(file, buf).toByteArray());
							list.add(ctx);
							buf.clear();
						}
					}
					if (!isOpen) mzf.closeFile();
				}
			} catch (Throwable e) {
				CmdUtil.warning("压缩文件有错误,请尝试全量", e);
			} finally {
				stampZip.end();
			}

			if (DEBUG) System.out.println("代码处理完成 " + (System.currentTimeMillis() - time));

			zo1.setComment("FMD " + VERSION + "\r\nBy Roj234 @ https://www.github.com/roj234/rojlib");

			PluginContext pc = new PluginContext();
			for (int i = 0; i < plugins.size(); i++) {
				plugins.get(i).afterCompile(list, false, pc);
			}

			// region 映射

			List<State> depStates = mapperFwd.getExtendedSuperList();
			depStates.clear();
			for (int i = 0; i < dependencies.size(); i++) {
				depStates.add(dependencies.get(i).state);
			}

			if (canIncrementWrite) {
				mapperFwd.state(p.state);
				mapperFwd.remapIncrement(list);
			} else {
				mapperFwd.remap(false, list);
			}
			p.state = mapperFwd.snapshot(p.state);

			if (doMapClassName) new CodeMapper(mapperFwd).remap(false, list);

			pc = new PluginContext();
			for (int i = 0; i < plugins.size(); i++) {
				plugins.get(i).afterCompile(list, true, pc);
			}

			if (DEBUG) System.out.println("映射完成 " + (System.currentTimeMillis() - time));

			// endregion
			// region 写入映射结果

			if (increment) {
				List<ConstantData> modified = Helpers.cast(args.get("$$HR$$"));
				if (modified != null) {
					for (int i = 0; i < compiledCount; i++) {
						modified.add(list.get(i).getData());
					}
				}
			} else {
				args.remove("$$HR$$");
			}

			try {
				for (int i = 0; i < list.size(); i++) {
					Context ctx = list.get(i);
					if (CONFIG.getBool("压缩输出")) {
						zo1.set(ctx.getFileName(), ctx::getCompressedShared);
					} else {
						zo1.set(ctx.getFileName(), ctx);
					}
				}
			} catch (Throwable e) {
				CmdUtil.error("write error", e);
				return -1;
			} finally {
				zo1.end();
			}

			// endregion

			CmdUtil.success("编译成功! " + (System.currentTimeMillis() - time) + "ms");

			if (!p.binJar.setLastModified(args.containsKey("dbg-nots") ? stamp : time)) {
				throw new IOException("设置时间戳失败!");
			}

			p.registerWatcher();

			try {
				executeCommand(p, BASE);
			} catch (IOException e) {
				CmdUtil.warning("无法执行指令", e);
			}

			return 1;
		} else if (!args.containsKey("zl")) {
			// todo test
			AutoCompile.setEnabled(false);
		}

		return -1;
	}

	// region Build.util

	private static ZipOutput updateDst(Project p, File jarFile) throws IOException {
		ZipOutput zo1 = p.dstFile;
		if (zo1 == null || !zo1.file.getAbsolutePath().equals(jarFile.getAbsolutePath())) {
			if (zo1 != null) {
				zo1.close();
			}
			p.dstFile = zo1 = new ZipOutput(jarFile);
			zo1.setCompress(true);
		}
		return zo1;
	}

	private static boolean ensureWritable(File jarFile) {
		int amount = 30 * 20;
		while (jarFile.isFile() && !IOUtil.checkTotalWritePermission(jarFile) && amount > 0) {
			if ((amount % 100) == 0) CmdUtil.warning("输出jar已被锁定, 请在30秒内解除对它的锁定，否则编译无法继续");
			LockSupport.parkNanos(50_000_000L);
			amount--;
		}
		return amount != 0;
	}

	private static long lastLibHash;
	private static CharList libClasses;

	private static CharList getLibClasses() {
		File lib = new File(BASE, "/class/");
		if (!lib.isDirectory()) return new CharList();

		List<File> fs = Arrays.asList(lib.listFiles());

		CharList sb = libClasses;
		if (sb == null) libClasses = sb = new CharList();
		else if (MapUtil.libHash(fs) == lastLibHash) return sb;
		sb.clear();

		for (int i = 0; i < fs.size(); i++) {
			File file = fs.get(i);
			if (!(file.getName().endsWith(".zip") || file.getName().endsWith(".jar")) || file.length() == 0) {
				continue;
			}
			try (ZipFile zf = new ZipFile(file)) {
				if (zf.size() == 0) CmdUtil.warning(file.getPath() + " 是空的");
			} catch (Throwable e) {
				CmdUtil.error(file.getPath() + " 不是ZIP压缩文件", e);
				if (!file.renameTo(new File(file.getAbsolutePath() + ".err"))) {
					throw new RuntimeException("未指定的I/O错误");
				} else {
					CmdUtil.info("文件已被自动重命名为.err");
				}
			}

			sb.append(file.getAbsolutePath()).append(File.pathSeparatorChar);
		}
		lastLibHash = MapUtil.libHash(fs);
		sb.setLength(sb.length()-1);
		return sb;
	}

	// endregion
	// region Common
	private static Map<String, Object> buildArgs(String[] args) {
		// args: dbg-nots showErrorCode zl
		MyHashMap<String, Object> ojbk = new MyHashMap<>(args.length);
		for (String arg : args) {
			ojbk.put(arg, null);
		}
		return ojbk;
	}

	public static void readTextList(Consumer<String> set, String key) {
		CEntry m = CONFIG.getDot(key);
		if (m.getType() == Type.LIST) {
			CList list = m.asList();
			List<CEntry> rl = list.raw();
			for (int i = 0; i < rl.size(); i++) {
				CEntry entry = rl.get(i);
				if (entry.getType() == Type.STRING) {
					String s = entry.asString().trim();
					if (s.length() > 0) set.accept(s);
				}
			}
		}
	}
	// endregion
}
