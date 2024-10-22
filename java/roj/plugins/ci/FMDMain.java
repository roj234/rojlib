package roj.plugins.ci;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipOutput;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.asmx.mapper.Mapper.State;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.config.data.Type;
import roj.io.IOUtil;
import roj.plugins.ci.plugin.PluginContext;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.TextWriter;
import roj.ui.Profiler;
import roj.ui.Terminal;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static roj.plugins.ci.Shared.*;
import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * FMD Main class
 *
 * @author Roj234
 * @since 2021/6/18 10:51
 */
public final class FMDMain {
	public static CommandConsole console = new CommandConsole("");
	static int exitCode;

	@SuppressWarnings("fallthrough")
	public static void main(String[] args) throws IOException, InterruptedException {
		var c = console;

		/*jvmArg
				String par = " -javaagent:" + new File(BASE, "util/FMD-agent.jar").getAbsolutePath() + "=" + port;
		 */

		c.register(literal("build").then(argument("flags", Argument.stringFlags("zl", "showErrorCode")).executes(ctx -> {
			List<String> flags = Helpers.cast(ctx.argument("flags", List.class));
			Map<String, Object> map = new MyHashMap<>();
			for (String flag : flags) map.put(flag, "");
			Profiler p = DEBUG ? new Profiler("build").begin() : null;
			try {
				exitCode = build(map);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (p != null) {
					p.end();
					p.popup();
				}
			}
		})));

		Argument.ArgSetOf<String> dynamicProject = new Argument.ArgSetOf<>(1, new MyHashMap<>()) {
			@Override
			protected void updateChoices() {
				choice.clear();
				File[] files = PROJECT_DIR.listFiles();
				if (files != null) {
					for (File file : files) {
						if (new File(file, "project.json").isFile()) {
							choice.put(file.getName(), file.getName());
						}
					}
				}
			}
		};
		c.register(literal("create").then(argument("name", Argument.string()).executes(ctx -> {
			String name = ctx.argument("name", String.class);
			ConfigEditWindow.open(Project.load(name), null);
		})));
		c.register(literal("edit").then(argument("name", dynamicProject).executes(ctx -> {
			var selected = ctx.argument("name", String.class);
			Project conf = Project.load(selected);
			ConfigEditWindow.open(conf, null);
		})));
		c.register(literal("select").then(argument("name", dynamicProject).executes(ctx -> {
			var selected = ctx.argument("name", String.class);
			Shared.setProject(selected);
			Terminal.success("配置文件已选择: "+selected);
			c.setPrompt("\u001b[33mFMD\u001b[97m[\u001b[96m"+project.name+"\u001b[97m]\u001b[33m > ");
		})));

		if (project == null) {
			Terminal.warning("未加载项目配置文件! 请使用create <名称>创建配置、和/或使用set <名称>选择配置");
		} else {
			c.setPrompt("\u001b[33mCI\u001b[97m[\u001b[96m"+project.name+"\u001b[97m]\u001b[33m > ");
		}

		if (args.length == 0) {
			String slogan = "Roj234-CI "+VERSION+" | 2019-2024";
			if (Terminal.ANSI_OUTPUT) {
				CharList sb1 = IOUtil.getSharedCharBuf();
				Terminal.MinecraftColor.rainbow(slogan, sb1);
				System.out.println(sb1);
				Terminal.info("使用Tab补全指令、或按下F1查看帮助");
				System.out.println();
			} else {
				System.out.println(slogan);
				System.out.println("使用支持ANSI转义的终端以获得更好的体验");
			}

			Terminal.setConsole(c);

			// only non-daemon thread
			HighResolutionTimer.activate();
		} else {
			c.executeSync(TextUtil.join(Arrays.asList(args), " "));
			System.exit(exitCode);
		}
	}

	public static int build(Map<String, Object> args) throws Exception {
		if (hotReload != null) args.put("$$HR$$", new ArrayList<>());

		Shared._lock();
		AutoCompile.beforeCompile();
		int v = -1;
		try {
			v = compile(args, project, BASE, 0);
		} finally {
			Shared._unlock();
			AutoCompile.afterCompile(v);
			if (project.dstFile != null) project.dstFile.end();
		}

		List<ConstantData> modified = Helpers.cast(args.get("$$HR$$"));
		if (modified != null && !modified.isEmpty()) {
			hotReload.sendChanges(modified);
			if (DEBUG) Terminal.success("发送重载请求");
		}

		return v;
	}

	private static void executeCommand(Project proj, File base) throws IOException {
		String jarName = proj.name+'-'+proj.version+".jar";
		String jarPath = base.getAbsolutePath()+'/'+jarName;

		CMap cmd = CONFIG.get("编译成功后执行指令").asMap();
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
	private static int compile(Map<String, ?> args, Project p, File dest, int flag) throws Exception {
		if ((flag & 2) == 0) {
			Profiler.startSection("dependProject");
			for (Project proj : p.getAllDependencies()) {
				Profiler.startSection(proj.name);
				if (compile(args, proj, dest, flag|2) < 0) {
					Terminal.info("前置编译失败");
					if (proj.dstFile != null) proj.dstFile.end();
					return -1;
				}
				Profiler.endSection();
			}
			Profiler.endSection();
		}

		File source = p.srcPath;
		if (!source.isDirectory()) {
			Terminal.warning("源码目录 "+source.getAbsolutePath()+" 不存在");
			return -1;
		}

		File jarFile = new File(dest, p.getConfig().getString("OVERRIDE_BIN_JAR", p.name+'-'+p.version+".jar"));
		boolean increment = jarFile.isFile() && args.containsKey("zl");

		Profiler.startSection("getSource");
		// region 检测代码更改
		long time = System.currentTimeMillis();

		List<File> files;
		block: {
			if (increment) {
				Set<String> set = watcher.getModified(p, FileWatcher.ID_SRC);
				if (!set.contains(null)) {
					files = new ArrayList<>(set.size());
					synchronized (set) {
						for (String path : set) files.add(new File(path));
						if (DEBUG) System.out.println("watcher.getSrc(): "+set);
					}
					break block;
				}
			}

			long stamp = increment ? p.binJar.lastModified() : -1;
			files = IOUtil.findAllFiles(source, file -> IOUtil.extensionName(file.getName()).equals("java") && file.lastModified() > stamp);
		}
		// endregion
		Profiler.endStartSection("ensureWritable <= getSource("+files.size()+")");
		// region 无代码变动的处理
		if (files.isEmpty()) {
			Profiler.endStartSection("updateResource <= ensureWritable");
			if (increment) {
				try (ZipOutput zo1 = updateDst(p, jarFile)) {
					zo1.begin(false);
					p.getResourceTask(true).call();
				}

				if ((flag & 3) == 0) Terminal.info("更新了资源(若有)");
			} else {
				if ((flag & 3) == 0) Terminal.info("无源文件");
			}

			try {
				executeCommand(p, BASE);
			} catch (IOException e) {
				Terminal.warning("无法执行指令", e);
			}

			p.registerWatcher();
			Profiler.endSection();
			return 0;
		}
		// endregion
		Profiler.endStartSection("openWriter <= ensureWritable");
		ZipOutput binWriter = updateDst(p, jarFile);
		binWriter.begin(!increment);
		binWriter.setComment("FMD "+VERSION+"\r\nBy Roj234 @ https://www.github.com/roj234/rojlib");

		var writeRes = Task.submit(p.getResourceTask(increment));
		Profiler.endStartSection("applyAT <= openWriter");
		// region 应用权限转换
		if (false) {
			if (p.atName.isEmpty()) {
				Terminal.error(p.name+" 使用了AT注解,请设置AT配置的存放位置");
				return -1;
			}

			Map<String, Collection<String>> map = new MyHashMap<>();

			CharList atData = IOUtil.getSharedCharBuf();
			for (Map.Entry<String, Collection<String>> entry : map.entrySet()) {
				for (String val : entry.getValue()) {
					atData.append("public-f ").append(entry.getKey()).append(' ').append(val).append('\n');
				}
			}
			map.clear();

			TextWriter.write(new File(p.atConfigPathStr), atData);
			// FIXME Use Lavac to resolve access transform
			// ATHelper.makeATJar(p.name, map);
		}
		// endregion
		Profiler.endStartSection("makeCompileParam <= applyAT");
		// region 制备编译参数
		CharList libBuf = new CharList(200);

		if (p.binJar.length() > 0 && increment) {
			libBuf.append(p.binJar.getAbsolutePath()).append(File.pathSeparatorChar);
		}

		//libBuf.append(ATHelper.getJarName(p.name).getAbsolutePath()).append(File.pathSeparatorChar);

		List<Project> dependencies = p.getAllDependencies();
		for (int i = 0; i < dependencies.size(); i++) {
			libBuf.append(dependencies.get(i).binJar.getAbsolutePath()).append(File.pathSeparatorChar);
		}
		getLibClasses(libBuf);

		SimpleList<String> options = CONFIG.getList("编译参数").toStringList();
		options.addAll("-cp", libBuf.toStringAndFree(), "-encoding", p.charset.name());

		p.compiler.showErrorCode(args.containsKey("showErrorCode"));
		// endregion

		Profiler.endStartSection("compile <= makeCompileParam");

		var pc = new PluginContext();
		pc.isPartialUpdate = increment;

		for (int i = 0; i < plugins.size(); i++) {
			plugins.get(i).beforeCompile(p.compiler, options, files, pc);
		}
		var outputs = p.compiler.compile(options, files);
		if (outputs == null) return -1;
		Profiler.endStartSection("writeDevJar <= compile");

		int compiledCount = outputs.size();
		List<Context> list = Helpers.cast(outputs);

		ZipOutput devJar = p.binFile;
		try {
			devJar.begin(!increment);

			for (int i = 0; i < outputs.size(); i++) {
				var out = outputs.get(i);
				devJar.set(out.getName(), out.getData());
				list.set(i, new Context(out.getName(), out.getData()));
			}

			if (increment && p.state == null) {
				increment = false;

				Profiler.startSection("reloadState");

				MyHashSet<String> changed = new MyHashSet<>(list.size());
				for (int i = 0; i < list.size(); i++) changed.add(list.get(i).getFileName());

				ZipArchive mzf = devJar.getMZF();
				for (ZEntry file : mzf.entries()) {
					if (!changed.contains(file.getName())) {
						Context ctx = new Context(file.getName(), mzf.get(file));
						list.add(ctx);
					}
				}

				Profiler.endSection();
			}
		} catch (Throwable e) {
			Terminal.warning("压缩文件有错误,请尝试全量", e);
			return -1;
		} finally {
			devJar.end();
		}

		Profiler.endStartSection("applyPlugin <= writeDevJar");

		pc.isPartialUpdate = increment;
		for (int i = 0; i < plugins.size(); i++) {
			plugins.get(i).afterCompile(list, pc);
		}

		Profiler.endStartSection("waitResource <= applyPlugin");

		try {
			writeRes.get();
		} catch (Exception e) {
			Terminal.warning("资源写入失败", e);
		}

		Profiler.endStartSection("mapClass <= waitResource");
		// region 映射

		List<State> libs = mapperFwd.getSeperatedLibraries(); libs.clear();
		for (int i = 0; i < dependencies.size(); i++) libs.add(dependencies.get(i).state);

		if (increment) {
			libs.add(p.state);
			mapperFwd.mapIncr(list);
		} else {
			mapperFwd.map(list);
		}
		p.state = mapperFwd.snapshot(p.state);

		pc = new PluginContext();
		for (int i = 0; i < plugins.size(); i++) {
			plugins.get(i).afterMap(list, pc);
		}

		// endregion
		Profiler.endStartSection("lockBinJar <= mapClass");
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

		// 锁定文件
		if (!ensureWritable(jarFile)) return -1;

		Profiler.endStartSection("writeBinJar <= lockBinJar");
		// region 写入映射结果
		Context ctx = Helpers.maybeNull();
		try {
			for (int i = 0; i < list.size(); i++) {
				ctx = list.get(i);
				binWriter.set(ctx.getFileName(), ctx::getCompressedShared);
			}
		} catch (Throwable e) {
			Terminal.error(ctx.getFileName()+" write error", e);
			return -1;
		} finally {
			binWriter.end();
		}
		// endregion

		Terminal.success("编译成功! "+(System.currentTimeMillis()-time)+"ms");
		Profiler.endStartSection("executeCommand <= writeBinJar");

		if (!p.binJar.setLastModified(time)) Terminal.warning("设置时间戳失败!");

		p.registerWatcher();

		try {
			executeCommand(p, BASE);
		} catch (IOException e) {
			Terminal.warning("无法执行指令", e);
		}

		return 0;
	}

	// region Build.util

	private static ZipOutput updateDst(Project p, File jarFile) throws IOException {
		ZipOutput zo1 = p.dstFile;
		if (zo1 == null || !zo1.file.getAbsolutePath().equals(jarFile.getAbsolutePath())) {
			if (zo1 != null) zo1.close();
			p.dstFile = zo1 = new ZipOutput(jarFile);
			zo1.setCompress(true);
		}
		return zo1;
	}

	private static boolean ensureWritable(File jarFile) {
		int amount = 30 * 20;
		while (jarFile.isFile() && !IOUtil.isReallyWritable(jarFile) && amount > 0) {
			if ((amount % 100) == 0) Terminal.warning("输出jar已被锁定, 请在30秒内解除对它的锁定，否则编译无法继续");
			LockSupport.parkNanos(50_000_000L);
			amount--;
		}
		return amount != 0;
	}

	private static CharList getLibClasses(CharList sb) {
		Predicate<File> callback = file -> {
			String ext = IOUtil.extensionName(file.getName());
			if ((ext.equals("zip") || ext.equals("jar")) && file.length() != 0) {
				sb.append(file.getAbsolutePath()).append(File.pathSeparatorChar);
			}
			return false;
		};

		IOUtil.findAllFiles(new File(BASE, "libs"), callback);
		IOUtil.findAllFiles(new File(project.basePath, "libs"), callback);

		if (sb.length() > 0) sb.setLength(sb.length()-1);
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