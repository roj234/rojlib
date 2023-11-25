package roj.mod;

import roj.archive.zip.EntryMod;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipOutput;
import roj.asm.ATList;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.task.AsyncTask;
import roj.concurrent.timing.ScheduledTask;
import roj.config.data.*;
import roj.dev.ByteListOutput;
import roj.dev.Compiler;
import roj.io.IOUtil;
import roj.mapper.MapUtil;
import roj.mapper.Mapper.State;
import roj.mapper.MapperUI;
import roj.mod.FileFilter.CmtATEntry;
import roj.mod.MCLauncher.RunMinecraftTask;
import roj.mod.plugin.PluginContext;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.CLIConsole;
import roj.ui.CLIUtil;
import roj.ui.GUIUtil;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.ui.terminal.CommandImpl;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

import static roj.mod.Shared.*;
import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * FMD Main class
 *
 * @author Roj234
 * @since 2021/6/18 10:51
 */
public final class FMDMain {
	static boolean isCLI;
	static ScheduledTask shinyTask;
	public static CommandConsole console = new CommandConsole("") {
		@Override
		public boolean executeCommand(String cmd) {
			if (shinyTask != null) shinyTask.cancel();
			return super.executeCommand(cmd);
		}
	};

	@SuppressWarnings("fallthrough")
	public static void main(String[] args) throws IOException, InterruptedException {
		Shared.loadProject();
		CommandConsole c = console;

		c.register(literal("build"));
		c.register(literal("auto").then(argument("auto", Argument.bool()).executes(ctx -> AutoCompile.setEnabled(ctx.argument("auto", Boolean.class)))));
		c.register(literal("reflect").executes(ctx -> ReflectTool.start(!isCLI)));
		CommandImpl cDeobf = ctx -> {
			String mode = ctx.argument("reverse", String.class, "mcp2srg");

			GUIUtil.systemLook();
			MapperUI f = new MapperUI();
			f.setDefaultCloseOperation(isCLI ? JFrame.DISPOSE_ON_CLOSE : JFrame.EXIT_ON_CLOSE);
			loadMapper();
			f.setMapper(mode.equals("mcp2srg") ? mapperFwd : loadReverseMapper());
			f.show();
		};
		c.register(literal("deobf").executes(cDeobf).then(argument("reverse", Argument.string("mcp2srg", "srg2mcp")).executes(cDeobf)));
		c.register(literal("preAT").executes(ctx -> preAT()));
		c.register(literal("kill").executes(ctx -> {
			if (MCLauncher.task != null && !MCLauncher.task.isDone()) {
				MCLauncher.task.cancel(true);
			}
		}));
		c.register(literal("gc").executes(ctx -> {
			long used = Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
			ATHelper.close();
			System.runFinalization();
			System.gc();
			System.runFinalization();
			System.out.println("释放了 " + TextUtil.scaledNumber(used-(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())) + "B内存.");
		}));

		c.register(literal("build").then(argument("flags", Argument.stringFlags("zl", "showErrorCode", "noupdate")).executes(ctx -> {
			List<String> flags = Helpers.cast(ctx.argument("flags", List.class));
			Map<String, Object> map = new MyHashMap<>();
			for (String flag : flags) map.put(flag, "");
			try {
				build(map);
			} catch (IOException e) {
				e.printStackTrace();
			}
		})));
		c.register(literal("run").then(argument("flags", Argument.stringFlags("zl", "showErrorCode", "noupdate")).executes(ctx -> {
			List<String> flags = Helpers.cast(ctx.argument("flags", List.class));
			Map<String, Object> map = new MyHashMap<>();
			for (String flag : flags) map.put(flag, "");
			try {
				run(map);
			} catch (IOException e) {
				e.printStackTrace();
			}
		})));

		Argument.ArgSetOf<File> dynamicProject = new Argument.ArgSetOf<File>(false, new MyHashMap<>()) {
			@Override
			protected void updateChoices() {
				choice.clear();
				IOUtil.findAllFiles(CONFIG_DIR, (f) -> {
					String name = f.getName().toLowerCase();
					if (name.endsWith(".json")) choice.put(name.substring(0, name.length()-5), f);
					return false;
				});
			}
		};
		c.register(literal("create").then(argument("name", Argument.string()).executes(ctx -> {
			String name = ctx.argument("name", String.class);
			ConfigEditWindow.open(Project.load(name), null);
		})));
		c.register(literal("edit").then(argument("name", dynamicProject).executes(ctx -> {
			File selected = ctx.argument("name", File.class);
			Project conf = Project.load(selected.getName().substring(0, selected.getName().lastIndexOf('.')));
			ConfigEditWindow.open(conf, null);
		})));
		c.register(literal("project").then(argument("name", dynamicProject).executes(ctx -> {
			File selected = ctx.argument("name", File.class);
			String name = selected.getName().substring(0, selected.getName().lastIndexOf('.'));
			Shared.setProject(name);
			CLIUtil.success("配置文件已选择: " + name);
			c.setPrompt("\u001b[33mFMD\u001b[97m[\u001b[96m"+project.name+"\u001b[97m]\u001b[33m > ");
		})));
		c.register(literal("exit").executes(ctx -> System.exit(0)));

		if (project == null) {
			CLIUtil.warning("未加载项目配置文件! 请使用create <名称(modid)>创建配置、和/或使用project <名称(modid)>选择配置");
		} else {
			c.setPrompt("\u001b[33mFMD\u001b[97m[\u001b[96m"+project.name+"\u001b[97m]\u001b[33m > ");
		}

		if (args.length == 0) {
			isCLI = true;

			CharList sb = IOUtil.getSharedCharBuf();
			String slogan = "FMD 更快的mod开发环境 "+VERSION+" By Roj234";
			System.out.println(slogan);
			System.out.println("\u001b[96mhttps://www.github.com/roj234/rojlib");

			shinyTask = PeriodicTask.loop(() -> {
				CharList sb1 = IOUtil.getSharedCharBuf().append("\u001b7\u001b[?25l\u001b[1;1H\u001b[2K");
				CLIUtil.MinecraftColor.sonic(slogan, sb1);
				sb1.append("\u001b8\u001b[?25h");
				CLIUtil.sysOut.print(sb1);
			}, 1000 / 60, 9999);

			System.out.println();
			CLIUtil.info("使用Tab补全指令、或按下F1查看帮助");
			System.out.println();

			CLIConsole.setConsole(c);

			// only non-daemon thread
			LockSupport.park();
		} else {
			c.executeCommand(TextUtil.join(Arrays.asList(args), " "));
		}
	}

	public static void preAT() {
		// 关闭文件监控
		watcher.removeAll();

		Map<String, Collection<String>> map = ATList.getMapping(); map.clear();

		Shared.loadSrg2Mcp();

		readTextList((s) -> {
			int i = s.indexOf(' ');
			if (i < 0) {
				CLIUtil.warning("Unknown entry " + s);
				return;
			}
			ATList.add(s.substring(0,i), s.substring(i+1));
		}, "预AT.编译期");
		for (CEntry ce : CONFIG.getDot("预AT.编译期+运行期").asMap().values()) {
			if (ce.getType() == Type.MAP) loadATMap(ce.asMap(), true);
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
				atBackup.putStream(mod.getName(), mcBin.getInput(mod.getName()), true);
			}
			for (String name : removable) atBackup.put(name, null);
			atBackup.store();

			// 应用修改
			zo.end();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		CLIUtil.success("操作成功完成");
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

	public static int run(Map<String, Object> args) throws IOException {
		MCLauncher.load();
		if (MCLauncher.task != null && !MCLauncher.task.isDone()) {
			if (CLIUtil.readBoolean("是否结束游戏进程?")) {
				MCLauncher.task.cancel(true);
				MCLauncher.task = null;
			} else {
				return -1;
			}
		}

		CMapping mc_conf = MCLauncher.config.get("mc_conf").asMap();
		if (mc_conf.size() == 0) {
			CLIUtil.error("启动配置不存在，请重新setup");
			return -1;
		}

		File dest = new File(mc_conf.getString("root") + "/mods/");
		if (!dest.isDirectory() && !dest.mkdirs()) {
			CLIUtil.warning("无法创建mods文件夹");
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

			if (DEBUG) CLIUtil.info("重载工具已在端口 " + port + " 上启动");
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
			if (DEBUG) CLIUtil.success("发送重载请求");
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
					CLIUtil.info("前置编译失败");
					return -1;
				}
			}
		}

		boolean increment = args.containsKey("zl");
		long time = System.currentTimeMillis();

		// region 检测代码更改

		File source = p.srcPath;
		if (!source.isDirectory()) {
			CLIUtil.warning("源码目录 " + source.getAbsolutePath() + " 不存在");
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

							CLIUtil.warning("找到AT注解, 使用全量编译");
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
					CLIUtil.warning("请手动全量编译");
					return -1;
				}

				// bug: 如果是启动后第一次更新资源，并不会填充resources
				try (ZipOutput zo1 = updateDst(p, jarFile)) {
					zo1.begin(false);

					p.getResourceTask(stamp).execute();
				}

				if ((flag & 3) == 0) CLIUtil.info("更新了资源(若有)");
			} else {
				if ((flag & 3) == 0) CLIUtil.info("无源文件");
			}
			try {
				executeCommand(p, BASE);
			} catch (IOException e) {
				CLIUtil.warning("无法执行指令", e);
			}
			return 0;
		}
		// endregion
		// region 应用权限转换

		if (!FileFilter.cmtEntries.isEmpty()) {
			if (p.atName.isEmpty()) {
				CLIUtil.error(p.name + " 使用了AT注解,请设置AT配置的存放位置");
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
				CLIUtil.warning("资源写入失败", e);
			}

			if (DEBUG) System.out.println("编译完成 " + (System.currentTimeMillis() - time));

			MyHashMap<String, String> resources = p.resources;

			List<ByteListOutput> outputs = p.compiler.getCompiled();
			List<Context> list = Helpers.cast(outputs);
			for (int i = 0; i < outputs.size(); i++) {
				ByteListOutput out = outputs.get(i);
				if (resources.remove(out.getName()) != null) {
					CLIUtil.warning("资源与类重复 " + out.getName());
				}

				list.set(i, new Context(out.getName(), out.getOutput()));
			}

			int compiledCount = list.size();

			ZipOutput stampZip = p.binFile;
			try {
				stampZip.begin(!increment);
			} catch (Throwable e) {
				CLIUtil.warning("压缩文件有错误,请尝试全量", e);
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
				CLIUtil.warning("压缩文件有错误,请尝试全量", e);
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

			List<State> libs = mapperFwd.getSeperatedLibraries(); libs.clear();
			for (int i = 0; i < dependencies.size(); i++) libs.add(dependencies.get(i).state);

			if (canIncrementWrite) {
				libs.add(p.state);
				mapperFwd.mapIncr(list);
			} else {
				mapperFwd.map(list);
			}
			p.state = mapperFwd.snapshot(p.state);

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
				CLIUtil.error("write error", e);
				return -1;
			} finally {
				zo1.end();
			}

			// endregion

			CLIUtil.success("编译成功! " + (System.currentTimeMillis() - time) + "ms");

			if (!args.containsKey("noupdate") && !p.binJar.setLastModified(time)) {
				throw new IOException("设置时间戳失败!");
			}

			p.registerWatcher();

			try {
				executeCommand(p, BASE);
			} catch (IOException e) {
				CLIUtil.warning("无法执行指令", e);
			}

			return 1;
		} else if (!args.containsKey("zl")) {
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
			if ((amount % 100) == 0) CLIUtil.warning("输出jar已被锁定, 请在30秒内解除对它的锁定，否则编译无法继续");
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
				if (zf.size() == 0) CLIUtil.warning(file.getPath() + " 是空的");
			} catch (Throwable e) {
				CLIUtil.error(file.getPath() + " 不是ZIP压缩文件", e);
				if (!file.renameTo(new File(file.getAbsolutePath() + ".err"))) {
					throw new RuntimeException("未指定的I/O错误");
				} else {
					CLIUtil.info("文件已被自动重命名为.err");
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
