package roj.plugins.ci;

import org.jetbrains.annotations.NotNull;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipOutput;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.attr.Attribute;
import roj.asm.attr.ModuleAttribute;
import roj.asm.attr.StringAttribute;
import roj.asm.util.Context;
import roj.asm.util.TransformUtil;
import roj.asmx.event.EventBus;
import roj.asmx.launcher.Bootstrap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.TrieTreeSet;
import roj.compiler.context.GlobalContext;
import roj.concurrent.ScheduleTask;
import roj.concurrent.Scheduler;
import roj.concurrent.TaskPool;
import roj.config.ConfigMaster;
import roj.config.Flags;
import roj.config.ParseException;
import roj.config.YAMLParser;
import roj.config.auto.Optional;
import roj.config.auto.SerializerFactory;
import roj.config.data.CMap;
import roj.crypt.jar.JarVerifier;
import roj.gui.Profiler;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.math.Version;
import roj.plugins.ci.annotation.ReplaceConstant;
import roj.plugins.ci.plugin.MAP;
import roj.plugins.ci.plugin.ProcessEnvironment;
import roj.plugins.ci.plugin.Processor;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.Argument;
import roj.ui.CommandConsole;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * FMD Main class
 * TODO file delete event
 * @author Roj234
 * @since 2021/6/18 10:51
 */
@ReplaceConstant
public final class FMD {
	static final String VERSION = "${ci_version}";

	//static final Pattern NAME_PATTERN = Pattern.compile("^[a-z_\\-][a-z0-9_\\-]*$");
	//static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+\\.?)+?([-_][a-zA-Z0-9]+)?$");
	//static final Pattern FILE_NAME_PATTERN = Pattern.compile("^[^<>|\"\\\\/:]+$");

	public static final File BASE, PROJECT_PATH, DATA_PATH;
	public static final Logger LOGGER = Logger.getLogger("FMD");

	private static boolean immediateExit;
	public static final Scheduler TIMER = Scheduler.getDefaultScheduler();
	public static final TaskPool EXECUTOR = TaskPool.Common();
	public static final EventBus EVENT_BUS = new EventBus();
	private static final AtomicInteger COMPILE_LOCK = new AtomicInteger();
	static IFileWatcher watcher;

	public static CMap config;
	public static final CommandConsole CommandManager = new CommandConsole("");

	public static Project defaultProject;
	public static final MyHashMap<String, Project> projects = new MyHashMap<>();
	static final MyHashMap<String, Compiler.Factory> compilerTypes = new MyHashMap<>();
	static final MyHashMap<String, Workspace.Factory> workspaceTypes = new MyHashMap<>();
	public static final MyHashMap<String, Workspace> workspaces = new MyHashMap<>();

	@Deprecated static List<Processor> processors;
	@Deprecated public static MAP MapPlugin;

	public static void _lock() {
		if (!COMPILE_LOCK.compareAndSet(0, 1)) {
			throw new FastFailException("其他线程正在编译");
		}
	}
	public static void _unlock() {COMPILE_LOCK.set(0);}


	private static ScheduleTask prelaunchTask;
	@SuppressWarnings({"fallthrough"})
	public static void main(String[] args) throws IOException, InterruptedException {
		if (Bootstrap.instance.getResource("java/awt/Color.class") == null) {
			Bootstrap.instance.registerTransformer((name, ctx) -> {
				if (name.equals("roj/gui/Profiler")) {
					TransformUtil.apiOnly(ctx.getData());
					return true;
				}
				return false;
			});
		}
		System.out.print("\u001b]0;MCMake v"+VERSION+" [Roj234]\7");

		var c = CommandManager;

		var buildProject = argument("选项", Argument.stringFlags("full", "diagnostic", "profile")).executes(ctx -> {
			Project project = ctx.argument("项目名称", Project.class, defaultProject);
			List<String> flags = Helpers.cast(ctx.argument("选项", List.class));
			Profiler p = flags.contains("profile") ? new Profiler("build").begin() : null;
			try {
				int build = build(new MyHashSet<>(flags), project);
				if (immediateExit) System.exit(build);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (p != null) {
					p.end();
					p.popup();
				}
			}
		});
		c.register(literal("build").then(argument("项目名称", Argument.oneOf(projects)).then(buildProject)).then(buildProject));

		c.register(literal("project")
			.then(literal("new").then(argument("项目名称", Argument.string()).then(argument("工作空间名称", Argument.oneOf(workspaces)).executes(ctx -> {
				var example = new EnvPojo.Project();
				example.name = ctx.argument("项目名称", String.class);
				example.compiler = compilerTypes.keySet().iterator().next();
				example.workspace = ctx.argument("工作空间名称", Workspace.class).id;

				var project = new Project(example);
				project.init();
				projects.put(project.name, project);
				saveConfig();
			}))))
			.then(literal("delete").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
				Workspace.addIDEAProject(defaultProject, true);
				System.out.println("IDEA项目已删除，请手动清理projects目录下的文件");
			})))
			.then(literal("setdefault").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
				defaultProject = ctx.argument("项目名称", Project.class);
				saveConfig();
				updatePrompt();
			})))
			.then(literal("auto").then(argument("项目名称", Argument.oneOf(projects)).then(argument("自动编译开关", Argument.bool()).executes(ctx -> {
				var proj = ctx.argument("项目名称", Project.class);
				proj.setAutoCompile(ctx.argument("自动编译开关", Boolean.class));
				saveConfig();
			}))))
		);

		c.register(literal("workspace")
			.then(literal("create").then(argument("工作空间类型", Argument.oneOf(workspaceTypes)).executes(ctx -> {
				Workspace space = ctx.argument("工作空间类型", Workspace.Factory.class).build(config.getMap("工作区.x"));
				if (space != null) {
					Terminal.success("工作空间 '"+space.id+"' 构建成功");
					System.out.println("是否需要修改它的名称方便记忆？按Ctrl+C以取消");
					try {
						space.id = Terminal.readString("新的名称：");
					} catch (NullPointerException ignored) {}

					workspaces.put(space.id, space);
					saveConfig();
				}
			})))
			.then(literal("delete").then(argument("工作空间名称", Argument.oneOf(workspaces)).executes(ctx -> {
				Workspace space = ctx.argument("工作空间名称", Workspace.class);
				char nn = Terminal.readChar("YyNn", "输入Y确定删除'"+space.id+"'");
				if (nn != 'Y' && nn != 'y') return;
				for (File dependency : space.unmappedDepend) {
					Files.deleteIfExists(dependency.toPath());
				}
				for (File dependency : space.mappedDepend) {
					Files.deleteIfExists(dependency.toPath());
				}
				for (File dependency : space.depend) {
					Files.deleteIfExists(dependency.toPath());
				}
				Files.deleteIfExists(space.mapping.toPath());

				workspaces.remove(space.id);
				saveConfig();
				System.out.println("工作空间'"+space.id+"'已删除");
			})))
			.then(literal("info").then(argument("工作空间名称", Argument.oneOf(workspaces)).executes(ctx -> {
				Workspace space = ctx.argument("工作空间名称", Workspace.class);

				System.out.println("名称: "+space.id);
				System.out.println("公共依赖: "+space.depend);
				System.out.println("正向依赖: "+space.mappedDepend);
				System.out.println("反向依赖: "+space.unmappedDepend);
				System.out.println("映射表: "+space.mapping);
				System.out.println("附加参数: "+space.variables);
			}))));

		c.register(literal("reload").executes(ctx -> loadEnv()));
		c.register(literal("zip").then(argument("路径", Argument.file()).executes(ctx -> {
			var file = ctx.argument("路径", File.class);
			try (ZipArchive za = new ZipArchive(file)) {
				for (ZEntry ze : za.entries()) {
					if (ze.getName().endsWith("/")) {
						za.put(ze.getName(), null);
                        if (ze.getSize() != 0) {
							LOGGER.warn("找到了有大小的文件夹{}", ze);
							za.put(ze.getName().substring(0, ze.getName().length()-1), DynByteBuf.wrap(za.get(ze)));
						}
                    }
				}

				za.save();
			}
		})));

		c.register(literal("modulize").then(argument("文件", Argument.file()).then(argument("模块名", Argument.string()).executes(ctx -> {
			File file = ctx.argument("文件", File.class);
			var moduleName = ctx.argument("模块名", String.class);

			MyHashSet<String> packages = new MyHashSet<>();

			boolean hasModule = false;
			for (Context context : Context.fromZip(file, null)) {
				String className = context.getClassName();
				if (className.startsWith("module-info")) {
					hasModule = true;
					continue;
				}
				int i = className.lastIndexOf('/');
				if (i < 0) throw new UnsupportedOperationException();
				String pack = className.substring(0, i);
				packages.add(pack);
			}

			if (hasModule) {
				char c1 = Terminal.readChar("YyNn", "该文件已包含模块信息，是否替换 [Yn]");
				if (c1 != 'y' && c1 != 'Y') return;
			}

			var moduleInfo = new ClassNode();
			var moduleAttr = new ModuleAttribute(moduleName, 0);

			moduleInfo.version = ClassNode.JavaVersion(17);
			moduleAttr.self.version = "1.0.0";
			moduleAttr.requires.add(new ModuleAttribute.Module("java.base", Opcodes.ACC_MANDATED));
			for (String pack : packages) {
				moduleAttr.exports.add(new ModuleAttribute.Export(pack));
			}

			moduleInfo.name("module-info");
			moduleInfo.parent(null);
			moduleInfo.modifier = Opcodes.ACC_MODULE;
			moduleInfo.addAttribute(new StringAttribute(Attribute.SourceFile, "module-info.java"));
			//moduleInfo.putAttr(new AttrString(Attribute.ModuleTarget, "windows-amd64"));
			//moduleInfo.putAttr(new AttrClassList(Attribute.ModulePackages, new SimpleList<>(packages)));
			moduleInfo.addAttribute(moduleAttr);

			System.out.println(moduleAttr);
			try (var za = new ZipArchive(file)) {
				za.put("module-info.class", DynByteBuf.wrap(Parser.toByteArray(moduleInfo)));
				za.save();
			}
			System.out.println("IL自动模块，应用成功！");
		}))));

		c.sortCommands();
		c.setAutoComplete(config.getBool("自动补全"));

		if (args.length != 0) {
			immediateExit = true;
			c.executeSync(TextUtil.join(Arrays.asList(args), " "));
			return;
		}

		if (projects.isEmpty()) {
			Terminal.warning("""
							欢迎使用MCMake，您的最后一个模组开发工具！
							使用前请先阅读维基及readme.txt
							如果你还没有读过，那么下面是快速使用指南：
								1. 准备Forge或Fabric的可以运行的客户端（必须）
								2. 准备相同版本和相同模组加载器的服务端（可选）
								3. 生成映射表 (/mapping create <映射类型>)
											* 此步需联网
								4. 生成工作空间 (/workspace create Minecraft)
									5. 生成项目 (/project create <项目名称>)
									6. 编辑项目：使用文本编辑器修改data/env.yml
									7. 加载项目 (/reload 和 /project setdefault <项目名称>)
									8. 构建 (/build)
							希望在接下来的日子里，它能陪伴您，并且为您节约数不胜数的时间
																—— Roj234
							""");
		}

		if (Terminal.ANSI_OUTPUT) {
			//prelaunchTask.cancel();
			Terminal.info("按F1查看快速帮助");
			System.out.println();
		} else {
			System.out.println("使用支持ANSI转义的终端以获得更好的体验");
		}

		Terminal.setConsole(c);
		HighResolutionTimer.runThis();
		//ansidebug();
	}

	private static void updatePrompt() {CommandManager.setPrompt("\u001b[33mMCMake"+(defaultProject == null ? "" : "\u001b[97m[\u001b[96m"+defaultProject.name+"\u001b[97m]")+"\u001b[33m > ");}

	private static final SerializerFactory POJO_FACTORY = SerializerFactory.getInstance();
	//region 初始化
	static {
		String slogan = "MCMake，您的最后一个模组开发工具！ "+VERSION+" | 2019-2025";
		System.out.println(slogan);
		if (Terminal.ANSI_OUTPUT) {
			setLogFormat();
			// 设置滚动区域
			// Terminal.directWrite("\033[" + 2 + ";" + Terminal.windowHeight + "r");
			// 向下滚动一行
			// Terminal.directWrite("\033[1T");
			// 恢复默认的滚动区域（整个屏幕）
			// Terminal.directWrite("\033[r");
			prelaunchTask = TIMER.loop(() -> {
				// 光标移动到第一行
				synchronized (System.out) {
					CharList sb1 = new CharList().append("\u001b7\u001b[?25l\u001b["+1+";1H");
					Terminal.Color.rainbow(slogan, sb1);
					Terminal.directWrite(sb1.append("\u001b[?25h\u001b8"));
					sb1._free();
				}
			}, 1000/60, 60 * 10);
		}

		BASE = new File("").getAbsoluteFile();
		DATA_PATH = new File(BASE, "data");
		PROJECT_PATH = new File(BASE, "projects");

		try {
			config = new YAMLParser().parse(new File(DATA_PATH, "config.yml"), Flags.NO_DUPLICATE_KEY|Flags.ORDERED_MAP).asMap();
			config.dot(true);
		} catch (Exception e) {
			LOGGER.fatal("系统配置config.yml解析失败!", e);
			LockSupport.parkNanos(5_000_000_000L);
			System.exit(-1);
		}

		LOGGER.setLevel(Level.valueOf(config.getString("日志级别", "DEBUG")));
		GlobalContext.cacheFolder = DATA_PATH;

		IFileWatcher w = null;
		if (config.getBool("文件修改监控")) {
			try {
				w = new FileWatcher();
			} catch (IOException e) {
				LOGGER.error("无法启动文件监控", e);
			}
		}
		if (w == null) w = new IFileWatcher();
		watcher = w;

		for (var entry : config.getMap("工作区").entrySet()) {
			var name = entry.getKey();
			var value = entry.getValue().asMap();
			try {
				var instance = Workspace.factory(Class.forName(value.getString("type")));
				workspaceTypes.put(name, instance);
			} catch (ClassNotFoundException e) {
				LOGGER.error("无法初始化编译器{}", e, name);
			}
		}

		for (var entry : config.getMap("编译器").entrySet()) {
			var name = entry.getKey();
			var value = entry.getValue().asMap();
			try {
				var instance = new Compiler.Factory(value);
				compilerTypes.put(name, instance);
			} catch (ClassNotFoundException e) {
				LOGGER.error("无法初始化编译器{}", e, name);
			}
		}

		processors = new SimpleList<>();
		for (var entry : config.getMap("插件").entrySet()) {
			try {
				Processor processor = (Processor) Class.forName(entry.getKey()).newInstance();
				processors.add(processor);
				processor.init(entry.getValue());
			} catch (Throwable e) {
				LOGGER.error("无法加载插件{}", e, entry.getKey());
			}
		}

		POJO_FACTORY.serializeCharsetToString().add(Version.class, CustomSerializer.class).add(File.class, CustomSerializer.class);
		try {
			loadEnv();
		} catch (Exception e) {
			LOGGER.fatal("环境配置env.yml解析失败!", e);
			LockSupport.parkNanos(5_000_000_000L);
			System.exit(-2);
		}
	}
	@SuppressWarnings("unchecked")
	private static void setLogFormat() {
		Logger.getRootContext().setPrefix((env, sb) -> {
			((BiConsumer<Object, CharList>) env.get("0")).accept(env, sb.append('['));

			Level level = (Level) env.get("LEVEL");
			sb.append("]\u001b[").append(level.color).append("m[").append(env.get("NAME"));
			if (level.ordinal() > Level.WARN.ordinal())
				sb.append("][").append(env.get("THREAD"));

			return sb.append("]\u001b[0m: ");
		});
	}

	@Optional
	static final class EnvPojo {
		List<Workspace> workspaces = Collections.emptyList();
		List<Project> projects = Collections.emptyList();

		String default_project;
		List<String> auto_compile = Collections.emptyList();

		static final class Project {
			static final Version DEFAULT_VERSION = new Version("1.0-rc1");

			String name;
			@Optional Version version = DEFAULT_VERSION;
			@Optional Charset charset = StandardCharsets.UTF_8;
			String compiler;
			@Optional List<String> compiler_options = Collections.emptyList();
			@Optional boolean compiler_options_overwrite;
			@Optional boolean no_compile_depend;
			String workspace;
			@Optional String name_format = "${name}-${version}.jar";
			@Optional List<String> dependency = Collections.emptyList();
			@Optional List<String> binary_depend = Collections.emptyList();
			@Optional List<String> source_depend = Collections.emptyList();
			@Optional Map<String, String> variables = new MyHashMap<>();
			@Optional TrieTreeSet variable_replace_in = new TrieTreeSet();
		}
	}
	private static void loadEnv() throws IOException, ParseException {
		var env = ConfigMaster.YAML.readObject(POJO_FACTORY.serializer(EnvPojo.class), new File(DATA_PATH, "env.yml"));

		workspaces.clear();
		projects.clear();

		for (var workspace : env.workspaces) {
			workspaces.put(workspace.id, workspace);
		}
		for (var config : env.projects) {
			try {
				var project = new Project(config);
				projects.put(project.name, project);
			} catch (IOException e) {
				LOGGER.error("无法加载项目{}", e, config.name);
			}
		}
		for (var name : env.auto_compile) {
			var project = projects.get(name);
			if (project != null) project.setAutoCompile(true);
		}
		defaultProject = projects.get(env.default_project);
		for (var value : projects.values()) value.init();
		updatePrompt();
	}
	private static void saveConfig() {
		var env = new EnvPojo();
		env.auto_compile = new SimpleList<>();
		env.projects = new SimpleList<>();
		env.workspaces = new SimpleList<>(workspaces.values());
		for (Project value : projects.values()) {
			env.projects.add(value.serialize());
			if (value.isAutoCompile()) {
				env.auto_compile.add(value.name);
			}
		}
		if (defaultProject != null) env.default_project = defaultProject.name;
		try {
			ConfigMaster.YAML.writeObject(POJO_FACTORY.serializer(EnvPojo.class), env, new File(DATA_PATH, "env.yml"));
		} catch (IOException e) {
			LOGGER.error("配置保存失败", e);
		}
	}

	private static class CustomSerializer {
		static String dataBase = DATA_PATH.getAbsolutePath();

		public static String toString(Version v) {return v.toString();}
		public static Version fromString(Object v) {return new Version(String.valueOf(v));}

		public static String toString(File v) {
			if (v == null) return null;
			String path = v.getAbsolutePath();
			return path.startsWith(dataBase) ? path.substring(dataBase.length() + 1) : path;
		}
		public static File fromString(String v) {return v == null ? null : IOUtil.relativePath(DATA_PATH, v);}
	}
	//endregion
	public static int build(Set<String> args, Project project) throws Exception {
		_lock();
		boolean v;
		try {
			v = build(args, project, BASE, 0);
			if (!v && args.contains("full")) {
				watcher.remove(project);
			}
		} finally {
			if (project.mappedWriter != null) try {
				project.mappedWriter.end();
			} catch (Throwable e) {
				new UnsupportedOperationException(project.mappedWriter.getClass()+"在关闭时抛出了异常！", e).printStackTrace();
			}

			_unlock();
		}

		return v ? 0 : 1;
	}
	// region Build
	/**
	 * @param flag Bit 1 : run (NoVersion) , Bit 2 : dependency mode
	 */
	private static boolean build(Set<String> args, Project p, File dest, int flag) throws IOException {
		if ((flag & 2) == 0 && !p.conf.no_compile_depend) {
			Profiler.startSection("depend");
			for (Project depend : p.getAllDependencies()) {
				depend.compiling = true;
				Profiler.startSection(depend.name);
				try {
					if (!build(args, depend, dest, flag|2)) {
						Terminal.info("前置编译失败");
						return false;
					}
				} finally {
					depend.compiling = false;
					if (depend.mappedWriter != null) depend.mappedWriter.end();
					if (depend.unmappedWriter != null) depend.unmappedWriter.end();
				}
				Profiler.endSection();
			}
			Profiler.endSection();
		}

		File jarFile = new File(dest, (flag&1) != 0 ? p.name+".jar" : p.getOutputFormat());
		boolean increment = jarFile.isFile() && p.unmappedJar.length() > 0 && !args.contains("full");

		Profiler.startSection("lockOutputFile");

		ZipOutput mappedWriter = lockOutput(p, jarFile);
		mappedWriter.begin(increment);
		mappedWriter.setComment("Build by MCMake "+VERSION+"\r\nBy Roj234 @ https://www.github.com/roj234/rojlib");

		Future<Integer> updateResource;

		Profiler.endStartSection("findModifiedSources");
		long time = System.currentTimeMillis();
		List<File> sources;
		findSourceDone: {
			long stamp;
			if (increment) {
				stamp = p.unmappedJar.lastModified();
				updateResource = EXECUTOR.submit(p.getResourceTask(stamp));
				Set<String> set = watcher.getModified(p, FileWatcher.ID_SRC);
				if (!set.contains(null)) {
					sources = new ArrayList<>(set.size());
					synchronized (set) {
						for (String path : set) sources.add(new File(path));
						LOGGER.trace("FileWatcher: {}", set);
					}
					break findSourceDone;
				}
			} else {
				stamp = -1;
				updateResource = EXECUTOR.submit(p.getResourceTask(stamp));
			}

			Predicate<File> incrFilter = file -> IOUtil.extensionName(file.getName()).equals("java") && file.lastModified() > stamp;
			sources = IOUtil.findAllFiles(p.srcPath, incrFilter);
			for (String s : p.conf.source_depend) IOUtil.findAllFiles(projects.get(s).srcPath, sources, incrFilter);
		}

		if (sources.isEmpty()) {
			int count;
			Profiler.endStartSection("waitResource");
			try {
				count = updateResource.get();
			} catch (Exception e) {
				Terminal.warning("资源写入失败["+p.getName()+"]", e);
				count = -1;
			}
			Profiler.endSection();

			if ((flag & 3) == 0) Terminal.info("更新了("+count+")个资源");
			mappedWriter.end();
			if (count == 0) return true;
		} else {
			Profiler.endStartSection("prepareCompileParam");

			var classpath = new CharList(200);

			var prefix = BASE.getAbsolutePath().length()+1;
			Predicate<File> callback = file1 -> {
				String ext = IOUtil.extensionName(file1.getName());
				if ((ext.equals("zip") || ext.equals("jar")) && file1.length() != 0) {
					classpath.append(file1.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
				}
				return false;
			};

			for (File file : p.workspace.depend) {
				classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
			}
			for (File file : p.workspace.mappedDepend) {
				classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
			}
			IOUtil.findAllFiles(new File(BASE, "libs"), callback);
			IOUtil.findAllFiles(new File(p.root, "libs"), callback);

			var dependencies = p.getAllDependencies();
			for (int i = 0; i < dependencies.size(); i++) {
				classpath.append(dependencies.get(i).unmappedJar.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
			}
			for (int i = 0; i < p.binaryDepend.size(); i++) {
				var dep = p.binaryDepend.get(i);
				if (IOUtil.extensionName(dep.getName()).equals("jar")) {
					classpath.append(dep.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
				}
			}

			if (increment) classpath.append(p.unmappedJar.getAbsolutePath().substring(prefix));
			else if (classpath.length() > 0) classpath.setLength(classpath.length()-1);

			SimpleList<String> options = p.conf.compiler_options_overwrite ? new SimpleList<>() : p.compiler.factory().getDefaultOptions();
			options.addAll(p.conf.compiler_options);
			String classPathStr = classpath.toStringAndFree();
			options.addAll("-cp", classPathStr, "-encoding", p.charset.name());
			if ("true".equals(p.conf.variables.get("javac:use_module")))
				options.addAll("--module-path", classPathStr);

			Profiler.endStartSection("Plugin.beforeCompile");

			var pc = new ProcessEnvironment();
			pc.project = p;

			int incrementLevel = increment ? 2 : 0;
			for (int i = 0; i < processors.size(); i++) {
				incrementLevel = Math.min(processors.get(i).beforeCompile(p.compiler, options, sources, pc), incrementLevel);
			}
			pc.increment = incrementLevel;

			Profiler.endStartSection("compile");
			var outputs = p.compiler.compile(options, sources, args.contains("diagnostic"));
			if (outputs == null) return false;

			Profiler.endStartSection("writeUnmappedJar");

			pc.changedClassIndex = outputs.size();
			List<Context> list = Helpers.cast(outputs);

			ZipOutput unmappedWriter = p.unmappedWriter;
			try {
				unmappedWriter.begin(increment);

				for (int i = 0; i < outputs.size(); i++) {
					var out = outputs.get(i);
					unmappedWriter.set(out.getFileName(), out.get());
					list.set(i, new Context(out.getFileName(), out.get()));
				}

				if (incrementLevel == 1) {
					Profiler.startSection("loadMapperState");

					MyHashSet<String> changed = new MyHashSet<>(list.size());
					for (int i = 0; i < list.size(); i++) changed.add(list.get(i).getFileName());

					ZipArchive mzf = unmappedWriter.getMZF();
					for (ZEntry file : mzf.entries()) {
						if (!changed.contains(file.getName())) {
							Context ctx = new Context(file.getName(), mzf.get(file));
							list.add(ctx);
						}
					}

					Profiler.endSection();
				}
			} catch (Throwable e) {
				Terminal.warning("增量更新失败["+p.getName()+"]", e);
				return false;
			} finally {
				unmappedWriter.end();
			}

			Profiler.endStartSection("Plugin.process");

			for (int i = 0; i < processors.size(); i++) {
				list = processors.get(i).process(list, pc);
			}

			Profiler.endStartSection("writeResource");
			try {
				updateResource.get();
			} catch (Exception e) {
				Terminal.warning("资源更新失败["+p.getName()+"]", e);
			}

			for (Map.Entry<String, ByteList> entry : pc.generatedFiles.entrySet()) {
				mappedWriter.set(entry.getKey(), entry.getValue());
			}

			Profiler.endStartSection("writeMappedJar");
			Context ctx = Helpers.maybeNull();
			try {
				for (int i = 0; i < list.size(); i++) {
					ctx = list.get(i);
					mappedWriter.set(ctx.getFileName(), ctx::getCompressedShared);
				}
			} catch (Throwable e) {
				Terminal.warning("代码更新失败["+p.getName()+"/"+ctx.getFileName()+"]", e);
				return false;
			}

			Terminal.success("编译成功["+p.getName()+"]! "+(System.currentTimeMillis()-time)+"ms");
			if (!p.unmappedJar.setLastModified(time)) Terminal.warning("设置时间戳失败!");
		}

		Profiler.endStartSection("signature");
		if ((flag&1) == 0 && p.variables.get("fmd:signature:keystore") != null) {
			mappedWriter.end();

			try {
				signatureJar(p, dest, jarFile);
				Terminal.success("签名成功");
			} catch (Exception e) {
				LOGGER.error("签名失败", e);
			}
		}
		Profiler.endSection();

		p.compileSuccess();
		return true;
	}
	private static ZipOutput lockOutput(Project p, File jarFile) {
		int amount = 30 * 20;
		while (jarFile.isFile() && !IOUtil.isReallyWritable(jarFile)) {
			if ((amount % 100) == 0) Terminal.warning("输出jar已被其它程序锁定, 等待解锁……");
			LockSupport.parkNanos(50_000_000L);
			if (--amount == 0) throw new IllegalStateException("输出被锁定");
		}

		var zo = p.mappedWriter;
		if (zo == null || !zo.file.getAbsolutePath().equals(jarFile.getAbsolutePath())) {
			IOUtil.closeSilently(zo);

			p.mappedWriter = zo = new ZipOutput(jarFile);
			zo.setCompress(true);
		}
		return zo;
	}
	private static void signatureJar(Project p, File dest, File jarFile) throws IOException, GeneralSecurityException {
		String name_format = p.variables.get("fmd:signature:name_format");
		if (name_format != null) {
			File newFile = new File(dest, Formatter.simple(name_format).format(p.variables, IOUtil.getSharedCharBuf()).toString());
			IOUtil.copyFile(jarFile, newFile);
			jarFile = newFile;
		}

		var keystore = new File(p.variables.get("fmd:signature:keystore"));
		var private_key_pass = p.variables.get("fmd:signature:keystore_pass").toCharArray();
		var key_alias = p.variables.get("fmd:signature:key_alias");

		var result = p.signatureCache;

		if (result == null) result = readKeys(keystore, private_key_pass, key_alias);
		var options = result.options;
		if (options.isEmpty()) {
			options.put("jarSigner:creator", "MCMake/"+VERSION);
			options.put("jarSigner:signatureFileName", p.variables.getOrDefault("fmd:signature:certificate_name", IOUtil.fileName(keystore.getName())));
			options.put("jarSigner:skipPerFileAttributes", p.variables.getOrDefault("fmd:signature:skip_per_file_attributes", "false"));
			options.put("jarSigner:manifestHashAlgorithm", p.variables.getOrDefault("fmd:signature:manifest_hash_algorithm", "SHA-256"));
			options.put("jarSigner:signatureHashAlgorithm", p.variables.getOrDefault("fmd:signature:signature_hash_algorithm", "SHA-256"));
			//options.put("jarSigner:cacheHash", "true");
		}

		try (var zf = name_format == null ? p.mappedWriter.getMZF() : new ZipArchive(jarFile)) {
			zf.reopen();
			JarVerifier.signJar(zf, result.certificate_chain, result.private_key, result.options);
			zf.save();
		}
	}

	@NotNull
	private static SignatureCache readKeys(File keystore, char[] keyPass, String keyAlias) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException {
		List<Certificate> certificate_chain;
		PrivateKey private_key;
		var ks = KeyStore.getInstance("PKCS12");
		try (var in = new FileInputStream(keystore)) {
			ks.load(in, keyPass);
			KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry) ks.getEntry(keyAlias, new KeyStore.PasswordProtection(keyPass));
			certificate_chain = Arrays.asList(entry.getCertificateChain());
			private_key = entry.getPrivateKey();
		}
        return new SignatureCache(certificate_chain, private_key);
	}

    public static final class SignatureCache {
		final List<Certificate> certificate_chain;
		final PrivateKey private_key;
		Map<String, String> options = new MyHashMap<>();

		public SignatureCache(List<Certificate> certificate_chain, PrivateKey private_key) {
            this.certificate_chain = certificate_chain;
            this.private_key = private_key;
        }
    }
	// endregion
}