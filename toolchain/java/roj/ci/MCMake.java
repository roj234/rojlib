package roj.ci;

import org.jetbrains.annotations.NotNull;
import roj.archive.qz.*;
import roj.archive.qz.util.QZArchiver;
import roj.archive.xz.LZMA2Options;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.archive.zip.ZipOutput;
import roj.asm.ClassNode;
import roj.asmx.ClassResource;
import roj.asmx.Context;
import roj.asmx.event.EventBus;
import roj.ci.annotation.ReplaceConstant;
import roj.ci.plugin.BuildContext;
import roj.ci.plugin.Processor;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.compiler.*;
import roj.compiler.test.TestPlugin;
import roj.concurrent.TaskGroup;
import roj.concurrent.TaskPool;
import roj.concurrent.Timer;
import roj.concurrent.TimerTask;
import roj.config.ConfigMaster;
import roj.config.Parser;
import roj.config.YamlParser;
import roj.config.mapper.ObjectMapperFactory;
import roj.config.node.MapValue;
import roj.crypt.jar.JarVerifier;
import roj.gui.Profiler;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.reflect.Reflection;
import roj.text.Formatter;
import roj.text.*;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.*;
import roj.util.ArtifactVersion;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;
import roj.util.function.Flow;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static roj.ci.plugin.BuildContext.*;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * MCMake，你的最后一个Minecraft构建工具
 * @author Roj234
 * @since 2021/6/18 10:51
 */
@ReplaceConstant
public final class MCMake {
	static final String VERSION = "${project_version}";

	public static final Logger LOGGER = Logger.getLogger("MCMake");
	public static final File
			BASE = new File("").getAbsoluteFile(),
			BIN_PATH = IOUtil.getJar(MCMake.class).getParentFile(),
			CONF_PATH = new File(BASE, "conf"),
			CACHE_PATH = new File(BASE, "cache"),
			OUTPUT_PATH = new File(BASE, "build");

	public static final Timer TIMER = Timer.getDefault();
	public static final TaskPool EXECUTOR = TaskPool.common();
	public static final EventBus EVENT_BUS = new EventBus();

	public static final Shell COMMANDS = new Shell("");
	static final ObjectMapperFactory CONFIG = ObjectMapperFactory.getInstance()
			.serializeCharsetToString()
			.registerAdapter(ArtifactVersion.class, MySerializer.class)
			.registerAdapter(File.class, MySerializer.class);

	public static MapValue config;
	static IFileWatcher watcher;
	static final List<Processor> processors = new ArrayList<>();

	public static Project defaultProject;
	public static final HashMap<String, Project> projects = new HashMap<>();
	static final HashMap<String, Compiler.Factory> compilerTypes = new HashMap<>();
	static final HashMap<String, Workspace.Factory> workspaceTypes = new HashMap<>();
	public static final HashMap<String, Workspace> workspaces = new HashMap<>();

	private static boolean immediateExit;

	private static TimerTask rainbowTask;
	private static final KeyHandler cancelRainbowTask = (keyCode, isVirtualKey) -> {
		rainbowTask.cancel();
		Tty.setHandler(COMMANDS);
		COMMANDS.keyEnter(keyCode, isVirtualKey);
	};

	private static final ReentrantLock COMPILE_LOCK = new ReentrantLock();
	public static void _lock() {if (!COMPILE_LOCK.tryLock()) {
		Tty.warning("其他项目正在编译，您的操作已排队");
		COMPILE_LOCK.lock();
	}}
	public static void _unlock() {COMPILE_LOCK.unlock();}

	@SuppressWarnings({"fallthrough"})
	public static void main(String[] args) throws IOException, InterruptedException {
		if (Tty.IS_RICH) {
			Tty.write(Tty.Screen.clearScreen);
			Tty.write("\u001b]0;MCMake v"+VERSION+" [Roj234]\7");
			setupLogFormat();
		}

		String slogan = "MCMake，您的最后一个模组开发工具！ "+VERSION+" | 2019-2025";
		System.out.println(slogan);
		if (Tty.IS_RICH) {
			rainbowTask = TIMER.loop(() -> {
				synchronized (System.out) {
					CharList sb1 = new CharList().append("\u001b7\u001b[?25l\u001b["+1+";1H");
					Tty.TextEffect.rainbow(slogan, sb1);
					Tty.write(sb1.append("\u001b[?25h\u001b8"));
					sb1._free();
				}
				if (rainbowTask.isExpired()) {
					cancelRainbowTask.keyEnter('\b', true);
				}
			}, 1000/30, 30 * 10);
		}

		System.setProperty("roj.compiler.symbolCache", CACHE_PATH.getAbsolutePath());
		System.setProperty("roj.ui.eventDriven", "true");

		if (!loadConfig()) System.exit(-1);
		var loadEnv = false;
		try {
			loadEnv();
			loadEnv = true;
		} catch (Exception e) {
			LOGGER.fatal("环境配置env.yml解析失败! 请修复并输入reload", e);
			updatePrompt();
		}

		registerCommands();

		if (args.length != 0) {
			immediateExit = true;
			COMMANDS.executeSync(TextUtil.join(Arrays.asList(args), " "));
			return;
		}

		if (loadEnv && projects.isEmpty()) {
			Tty.warning("""
							欢迎使用MCMake，您的最后一个模组开发工具！
							使用前请先阅读readme.md
							把您的时间花在更有意义的事情，而不是等待编译上
																—— Roj234
							""");
		}

		if (Tty.IS_RICH) {
			COMMANDS.setAutoComplete(true);
			Tty.info("按F1查看快速帮助");
			System.out.println();
			Tty.pushHandler(cancelRainbowTask);
		} else {
			System.out.println("使用支持ANSI转义的终端以获得更好的体验");
			Tty.pushHandler(COMMANDS);
		}

		HighResolutionTimer.runThis();
	}

	private static void registerCommands() {
		var c = COMMANDS;

		var buildProject = argument("选项", Argument.stringFlags("full", "diagnostic", "profile", "full/inherit")).executes(ctx -> {
			Project project = ctx.argument("项目名称", Project.class, defaultProject);
			List<String> flags = Helpers.cast(ctx.argument("选项", List.class));
			Profiler p = flags.contains("profile") ? new Profiler("build").begin() : null;
			try {
				int exitcode;
				HashSet<String> options = new HashSet<>(flags);
				if (project == null) {
					exitcode = 0;
					for (Project value : Flow.of(projects.values()).filter(p1 -> p1.conf.type.canBuild()).toList()) {
						exitcode = build(options, value);
					}
				} else {
					exitcode = build(options, project);
				}

				if (immediateExit) System.exit(exitcode);
			} finally {
				if (p != null) {
					p.end();
					p.popup();
				}
			}
		});
		c.register(literal("build").then(argument("项目名称", Argument.oneOf(projects)).then(buildProject)).then(buildProject));

		var pr = literal("project")
			.then(literal("create").then(argument("项目名称", Argument.string()).then(argument("工作空间名称", Argument.oneOf(workspaces)).executes(ctx -> {
				var example = new Env.Project();
				example.name = ctx.argument("项目名称", String.class);
				example.workspace = ctx.argument("工作空间名称", Workspace.class).id;

				var project = new Project(example, true);
				project.init();
				projects.put(project.getName(), project);
				Workspace.registerModule(project, 0);
				saveEnv();
			}))))
			.then(literal("delete").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
				var proj = ctx.argument("项目名称", Project.class);
				Workspace.registerModule(proj, 1);
				IOUtil.deletePath(proj.cachePath);
				projects.remove(proj.getName());
				saveEnv();
				System.out.println("已从配置中删除，请手动删除projects目录下文件");
			})))
			.then(literal("setdefault").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
				defaultProject = ctx.argument("项目名称", Project.class);
				saveEnv();
				updatePrompt();
			})))
			.then(literal("auto").then(argument("项目名称", Argument.oneOf(projects)).then(argument("自动编译开关", Argument.bool()).executes(ctx -> {
				var proj = ctx.argument("项目名称", Project.class);
				proj.setAutoCompile(ctx.argument("自动编译开关", Boolean.class));
				saveEnv();
			}))))
			.then(literal("config").executes(ctx -> {
				System.out.println("配置模板: ");
				ClassNode project = ClassNode.fromType(Env.Project.class);
				project.methods.clear();
				System.out.println(project);
				System.out.println("MCMake命令已经很多了，你可以考虑通过脚本注册更多指令");
			}));
		importExportProject(pr);
		c.register(pr);

		var ws = literal("workspace")
			.then(literal("create").then(argument("工作空间类型", Argument.oneOf(workspaceTypes)).executes(ctx -> {
				Workspace space = ctx.argument("工作空间类型", Workspace.Factory.class).build(config.getMap("工作区").getMap("x"));
				if (space != null) {
					String id = TUI.inputOpt(TUI.text("是否需要修改工作空间'"+space.id+"'的名称？按Ctrl+C以取消"), Argument.string());
					if (id != null && !id.isBlank()) space.id = id;

					TUI.stepInfo(TUI.text("注册Library和配置"));
					space.registerLibrary();
					workspaces.put(space.id, space);
					saveEnv();

					TUI.end(TUI.text("工作空间'"+space.id+"'构建成功！"));
				}
			})))
			.then(literal("delete").then(argument("工作空间名称", Argument.oneOf(workspaces)).executes(ctx -> {
				Workspace space = ctx.argument("工作空间名称", Workspace.class);
				char nn = TUI.key("YyNn", "输入Y确定删除'"+space.id+"'及其所有文件");
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
				if (space.mapping != null)
					Files.deleteIfExists(space.mapping.toPath());

				space.unregisterLibrary();
				workspaces.remove(space.id);
				saveEnv();
				System.out.println("工作空间'"+space.id+"'已删除");
			})));
		importExportWorkspace(ws);
		c.register(ws);

		c.register(literal("reload").executes(ctx -> loadEnv())
			.then(literal("all").executes(ctx -> {
				if (loadConfig()) loadEnv();
			})));

		c.register(literal("zip").then(argument("路径", Argument.file()).executes(ctx -> {
			var file = ctx.argument("路径", File.class);
			if (file.getName().endsWith(".7z")) {
				try (var qz = new QZArchive(new FileSource(file))) {
					QZFileWriter qzfw = qz.append();

					ArrayList<QZEntry> emptyFiles = qzfw.getEmptyFiles();
					for (int i = emptyFiles.size() - 1; i >= 0; i--) {
						QZEntry emptyFile = emptyFiles.get(i);
						if (emptyFile.isDirectory() && emptyFile.getName().contains("/")) {
							qzfw.removeEmptyFile(i);
						}
					}

					qzfw.close();
				}
				return;
			}
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

		runScript();

		c.sortCommands();
		c.setAutoComplete(config.getBool("自动补全"));
	}

	private static void updatePrompt() {COMMANDS.setPrompt("\u001b[33mMCMake"+(defaultProject == null ? "" : "\u001b[97m[\u001b[96m"+defaultProject.getName()+"\u001b[97m]")+"\u001b[33m > ");}

	private static void setupLogFormat() {
		Logger.getRootContext().setPrefix((env, sb) -> {
			((Formatter) env.get("0")).format(env, sb.append('['));

			Level level = (Level) env.get("LEVEL");
			sb.append("]\u001b[").append(level.color).append("m[").append(env.get("NAME"));
			if (level.ordinal() > Level.WARN.ordinal())
				sb.append("][").append(env.get("THREAD"));

			return sb.append("]\u001b[0m: ");
		});
	}

	//region 导入导出&其它指令
	private static void runScript() {
		COMMANDS.register(literal("runscript").comment("执行脚本").then(argument("脚本名称", Argument.fileIn(new File(CONF_PATH, "scripts"))).executes(ctx -> {
			File scriptFile = ctx.argument("脚本名称", File.class);

			List<CompileUnit> files = new ArrayList<>();
			files.add(JavaCompileUnit.create(scriptFile.getName(), IOUtil.readString(scriptFile)));

			var compiler = new LavaCompiler();
			new TestPlugin().pluginInit(compiler);

			compiler.features.add(roj.compiler.api.Compiler.EMIT_INNER_CLASS);
			compiler.features.add(roj.compiler.api.Compiler.EMIT_LINE_NUMBERS);
			compiler.features.add(roj.compiler.api.Compiler.OPTIONAL_SEMICOLON);
			compiler.features.add(roj.compiler.api.Compiler.OMISSION_NEW);
			compiler.features.add(roj.compiler.api.Compiler.SHARED_STRING_CONCAT);
			compiler.features.add(roj.compiler.api.Compiler.OMIT_CHECKED_EXCEPTION);
			compiler.addLibrary(LambdaLinker.LIBRARY_SELF);

			CompileContext.set(compiler.createContext());
			block:
			try {
				files.get(0).S1parseStruct();
				if (compiler.hasError()) break block;
				compiler.getParsableUnits(files);
				for (int i = 0; i < files.size(); i++) {
					files.get(i).S2p1resolveName();
				}
				if (compiler.hasError()) break block;
				for (int i = 0; i < files.size(); i++) {
					files.get(i).S2p2resolveType();
				}
				if (compiler.hasError()) break block;
				for (int i = 0; i < files.size(); i++) {
					files.get(i).S2p3resolveMethod();
				}
				if (compiler.hasError()) break block;
				for (int i = 0; i < files.size(); i++) {
					files.get(i).S3processAnnotation();
				}
				if (compiler.hasError()) break block;
				for (int i = 0; i < files.size(); i++) {
					files.get(i).S4parseCode();
				}
				if (compiler.hasError()) break block;

				var cd = Reflection.newClassDefiner("fmdScript", MCMake.class.getClassLoader());
				for (CompileUnit file : files) Reflection.defineClass(cd, file);

				MethodHandle handle = MethodHandles.lookup().findStatic(cd.loadClass(files.get(0).name().replace('/', '.')), "main", MethodType.methodType(void.class, String[].class));
				handle.invoke(new String[0]);
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				CompileContext.set(null);
			}
		})));
	}
	public static void importExportWorkspace(CommandNode node) {
		node.then(literal("import").then(argument("工作空间归档", Argument.file()).executes(ctx -> {
			File archiveFile = ctx.argument("工作空间归档", File.class);
			try (var archive = new QZArchive(archiveFile);
				 var bar = new EasyProgressBar("导入工作空间", "B")) {
				archive.setMemoryLimitKb(26214400);

				InputStream in = archive.getStream("workspace.yml");
				if (in == null) {
					Tty.error("不是有效的归档文件：找不到workspace.yml");
					return;
				}

				Workspace workspace = ConfigMaster.YAML.readObject(MCMake.CONFIG.serializer(Workspace.class), in);
				workspace.id = IOUtil.safePath(workspace.id);

				if (MCMake.workspaces.containsKey(workspace.id)) {
					Tty.warning("同名工作空间("+workspace.id+")已存在");
					return;
				}

				bar.setName("导入"+workspace.id);
				for (QZEntry entry : archive.entries()) {
					if (entry.getName().equals("workspace.yml")) continue;
					bar.addTotal(entry.getSize());
				}

				Map<String, File> filenameMapping = new HashMap<>();
				List<File> depend = workspace.depend;
				for (int i = 0; i < depend.size(); i++) {
					File file = depend.get(i);
					File result;

					var path = file.getName();
					result = new File(CACHE_PATH, "ws-"+workspace.id+"-cd"+i+path.substring(path.lastIndexOf('.')));

					filenameMapping.putIfAbsent(file.getName(), result);
					depend.set(i, result);
				}
				List<File> mappedDepend = workspace.mappedDepend;
				for (int i = 0; i < mappedDepend.size(); i++) {
					File file = mappedDepend.get(i);
					File result;

					var path = file.getName();
					result = new File(CACHE_PATH, "ws-"+workspace.id+"-md"+i+path.substring(path.lastIndexOf('.')));

					filenameMapping.putIfAbsent(file.getName(), result);
					mappedDepend.set(i, result);
				}
				List<File> unmappedDepend = workspace.unmappedDepend;
				for (int i = 0; i < unmappedDepend.size(); i++) {
					File file = unmappedDepend.get(i);
					File result;

					var path = file.getName();
					result = new File(CACHE_PATH, "ws-"+workspace.id+"-ud"+i+path.substring(path.lastIndexOf('.')));

					filenameMapping.putIfAbsent(file.getName(), result);
					unmappedDepend.set(i, result);
				}

				for (var itr = filenameMapping.entrySet().iterator(); itr.hasNext(); ) {
					Map.Entry<String, Object> generalizedEntry = Helpers.cast(itr.next());
					generalizedEntry.setValue(new ZipFileWriter(((File) generalizedEntry.getValue()), Deflater.BEST_COMPRESSION, 0));
				}
				Map<String, ZipFileWriter> libraryWriter = Helpers.cast(filenameMapping);

				if (workspace.mapping != null) {
					workspace.mapping = new File(CACHE_PATH, "ws-"+workspace.id+"-mapping.lzma");
				}

				TaskGroup monitor = TaskPool.common().newGroup();
				archive.parallelDecompress(monitor, (entry, in1) -> {
					if (entry.isDirectory() || entry.getName().equals("workspace.yml")) return;

					String pathname = IOUtil.safePath(entry.getName());
					int i = pathname.indexOf('/');
					if (i < 0) {
						var file = workspace.mapping;
						if (file != null) try (var out = new FileOutputStream(file)) {
							QZArchiver.copyStreamWithProgress(in1, out, bar);
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {
						ZipFileWriter zfw = libraryWriter.get(pathname.substring(0, i));
						pathname = pathname.substring(i+1);
						synchronized (zfw) {
							try {
								zfw.beginEntry(new ZEntry(pathname));
								QZArchiver.copyStreamWithProgress(in1, zfw, bar);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				});
				monitor.await();
				for (ZipFileWriter zfw : libraryWriter.values()) zfw.close();

				if (!monitor.isCancelled()) {
					MCMake.workspaces.put(workspace.id, workspace);
					MCMake.saveEnv();

					bar.end("成功");
				}
			}
		}))).then(literal("export").then(argument("工作空间名称", Argument.oneOf(MCMake.workspaces)).executes(ctx -> {
			Workspace space = MCMake.CONFIG.serializer(Workspace.class).deepcopy(ctx.argument("工作空间名称", Workspace.class));

			try (var qzfw = new QZFileWriter("workspace-"+space.id+".7z");
				 var bar = new EasyProgressBar("导出"+space.id, "B")) {

				bar.addTotal(Integer.MAX_VALUE);

				var files = new ArrayList<>(space.depend);
				files.addAll(space.mappedDepend);
				files.addAll(space.unmappedDepend);

				TaskGroup monitor = TaskPool.common().newGroup();

				for (File file : files) {
					monitor.executeUnsafe(() -> {
						try (var in = new ZipInputStream(new FileInputStream(file));
							 var out = qzfw.newParallelWriter()) {
							out.setCodec(new LZMA2(new LZMA2Options(9).setDictSize(16777216)));

							while (true) {
								ZipEntry entry = in.getNextEntry();
								if (entry == null || entry.isDirectory()) break;

								out.beginEntry(QZEntry.of(file.getName()+"/"+entry));
								QZArchiver.copyStreamWithProgress(in, out, bar);
							}
						}
					});
				}

				monitor.await();

				qzfw.setCodec(Copy.INSTANCE);

				File mapping = space.mapping;
				if (mapping != null) {
					qzfw.beginEntry(QZEntry.of(mapping.getName()));
					try (var in = new FileInputStream(mapping)) {
						QZArchiver.copyStreamWithProgress(in, qzfw, bar);
					}
				}

				qzfw.setCodec(new LZMA2(new LZMA2Options(9).setDictSize(524288)));

				qzfw.beginEntry(QZEntry.of("workspace.yml"));
				ConfigMaster.YAML.writeObject(MCMake.CONFIG.serializer(Workspace.class), space, IOUtil.getSharedByteBuf()).writeToStream(qzfw);

				bar.end("成功");
			}
		})));
	}

	public static void importExportProject(CommandNode node) {
		node.then(literal("export").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
			Project proj = ctx.argument("项目名称", Project.class);
			File projectRoot = proj.root;

			QZArchiver arc = new QZArchiver();
			arc.inputDirectories = Collections.singletonList(projectRoot);
			//arc.threads = 0;
			arc.updateMode = QZArchiver.UM_REPLACE;
			arc.pathFormat = QZArchiver.PF_RELATIVE;
			arc.storeModifiedTime = true;
			arc.storeSymbolicLinks = true;
			arc.storeHardLinks = true;
			arc.solidSize = Long.MAX_VALUE;
			arc.options.setPreset(9).setDictSize(4194304);
			arc.useBCJ = true;
			arc.cacheDirectory = BASE;
			arc.outputDirectory = BASE;
			arc.outputFilename = "mcmake-project-"+proj.getSafeName()+"-"+ DateFormat.format("Ymd", System.currentTimeMillis())+".7z";

			arc.prepare();

			String ymldata = ConfigMaster.YAML.writeObject(CONFIG.serializer(Env.Project.class), proj.serialize(), IOUtil.getSharedCharBuf()).toString();
			arc.appendBinary(new QZCoder[]{Copy.INSTANCE}, Collections.singletonList(ymldata), Collections.singletonList(QZEntry.of("project.yml")));

			try (var bar = new EasyProgressBar("导出项目")) {
				arc.compress(TaskPool.common(), bar);
			}
		})))
		.then(literal("import").then(argument("项目归档", Argument.file()).executes(ctx -> {
			File archiveFile = ctx.argument("项目归档", File.class);

			try (var archive = new QZArchive(archiveFile);
				 var bar = new EasyProgressBar("导入项目", "B")) {
				InputStream in = archive.getStream("project.yml");
				if (in == null) {
					Tty.error("不是有效的归档文件：找不到project.yml");
					return;
				}

				Env.Project pojo = ConfigMaster.YAML.readObject(CONFIG.serializer(Env.Project.class), in);
				pojo.name = IOUtil.safePath(pojo.name);

				if (projects.containsKey(pojo.name)) {
					Tty.warning("同名项目("+pojo.name+")已存在");
					return;
				}

				Workspace workspace = workspaces.get(pojo.workspace);
				if (workspace == null) {
					Tty.warning("依赖的工作空间["+pojo.workspace+"]不存在");
					return;
				}

				var basePath = new File(workspace.getProjectPath(), pojo.name);
				if (basePath.exists()) {
					Tty.warning("项目目录'"+basePath+"'非空，是否清空目录\nY: 清空并导入\nN: 不清空并导入\nCtrl+C: 取消操作");
					char selection = TUI.key("YyNn", "您的选择: ");
					if (selection == 0) return;
					if (selection == 'Y' || selection == 'y') IOUtil.deletePath(basePath);
				}

				bar.setName("导入"+pojo.name);
				for (QZEntry entry : archive.entries()) {
					if (entry.getName().equals("project.yml")) continue;
					bar.addTotal(entry.getSize());
				}

				TaskGroup monitor = TaskPool.common().newGroup();
				archive.parallelDecompress(monitor, (entry, in1) -> {
					if (entry.isDirectory() || entry.getName().equals("project.yml")) return;
					var file = new File(basePath, IOUtil.safePath(entry.getName()));
					file.getParentFile().mkdirs();

					try (var out = new FileOutputStream(file)) {
						QZArchiver.copyStreamWithProgress(in1, out, bar);
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				monitor.await();
				if (!monitor.isCancelled()) {
					Project project = new Project(pojo, false);
					project.init();
					projects.put(project.getName(), project);
					Workspace.registerModule(project, 0);
					saveEnv();

					bar.end("成功");
				}
			}
		})));
	}
	//endregion
	//region 配置文件
	private static boolean loadConfig() {
		try {
			config = new YamlParser().parse(new File(CONF_PATH, "config.yml"), Parser.NO_DUPLICATE_KEY| Parser.ORDERED_MAP).asMap();
		} catch (Exception e) {
			LOGGER.fatal("系统配置config.yml解析失败!", e);
			return false;
		}

		Logger.getRootContext().level(Level.valueOf(config.getString("日志级别", "DEBUG")));

		if (watcher != null) watcher.removeAll();

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

		workspaceTypes.clear();
		for (var entry : config.getMap("工作区").entrySet()) {
			var name = entry.getKey();
			var value = entry.getValue().asMap();
			try {
				var instance = Workspace.factory(Class.forName(value.getString("type")));
				workspaceTypes.put(name, instance);
			} catch (ClassNotFoundException e) {
				LOGGER.error("无法初始化工作区{}", e, name);
			}
		}

		compilerTypes.clear();
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

		processors.clear();
		for (var entry : config.getMap("插件").entrySet()) {
			try {
				Processor processor = (Processor) Class.forName(entry.getKey()).newInstance();
				processors.add(processor);
				processor.init(entry.getValue());
			} catch (Throwable e) {
				LOGGER.error("无法加载插件{}", e, entry.getKey());
			}
		}

		return true;
	}
	private static void loadEnv() throws IOException, ParseException {
		watcher.removeAll();

		File file = new File(CONF_PATH, "env.yml");
		if (!file.isFile()) {
			workspaces.clear();
			projects.clear();
			return;
		}

		var env = ConfigMaster.YAML.readObject(CONFIG.serializer(Env.class), file);

		var deleteIml = !projects.isEmpty();

		workspaces.clear();
		projects.clear();

		for (var workspace : env.workspaces) {
			workspaces.put(workspace.id, workspace);
		}
		for (var config : env.projects) {
			try {
				var project = new Project(config, false);
				projects.put(project.getName(), project);
			} catch (IOException e) {
				LOGGER.error("无法加载项目{}", e, config.name);
			}
		}
		for (var name : env.auto_compile) {
			var project = projects.get(name);
			if (project != null) project.setAutoCompile(true);
		}
		defaultProject = projects.get(env.default_project);
		for (var value : projects.values()) {
			value.init();
			Workspace.registerModule(value, deleteIml ? 2 : 0);
		}
		for (var value : projects.values()) {
			value.init();
			Workspace.registerModule(value, deleteIml ? 2 : 0);
		}
		updatePrompt();
	}
	private static void saveEnv() {
		var env = getEnvPojo();
		try {
			ConfigMaster.YAML.writeObject(CONFIG.serializer(Env.class), env, new File(CONF_PATH, "env.yml"));
		} catch (IOException e) {
			LOGGER.error("配置保存失败", e);
		}
	}
	private static Env getEnvPojo() {
		var env = new Env();
		env.auto_compile = new ArrayList<>();
		env.projects = new ArrayList<>();
		env.workspaces = new ArrayList<>(workspaces.values());
		env.workspaces.sort((o1, o2) -> o1.id.compareTo(o2.id));
		for (Project value : projects.values()) {
			env.projects.add(value.serialize());
			if (value.isAutoCompile()) {
				env.auto_compile.add(value.getName());
			}
		}
		env.projects.sort((o1, o2) -> o1.name.compareTo(o2.name));
		if (defaultProject != null) env.default_project = defaultProject.getName();
		return env;
	}

	private static class MySerializer {
		static String dataBase = CACHE_PATH.getAbsolutePath();

		public static String toString(ArtifactVersion v) {return v.toString();}
		public static ArtifactVersion fromString(Object v) {return new ArtifactVersion(String.valueOf(v));}

		public static String toString(File v) {
			if (v == null) return null;
			String path = v.getAbsolutePath();
			return path.startsWith(dataBase) ? path.substring(dataBase.length() + 1) : path;
		}
		public static File fromString(String v) {return v == null ? null : IOUtil.relativePath(CACHE_PATH, v);}
	}
	//endregion

	public static int build(Set<String> args, Project project) throws Exception {
		if (!project.conf.type.canBuild()) {
			Tty.error("项目["+project.getName()+"]是"+project.conf.type+"，只能作为其它项目的依赖，而不能单独构建");
			return 1;
		}

		_lock();

		long startTime = System.currentTimeMillis();
		boolean success;
		block:
		try {
			if (!project.getProjectDependencies().isEmpty()) {
				Profiler.startSection("depend");
				try (var bar = new ProgressBar()) {
					var copyArgs = new HashSet<>(args);
					if (copyArgs.contains("full/inherit")) copyArgs.add("full");
					else copyArgs.remove("full");
					copyArgs.add("silent");

					List<Project> allDependencies = project.getProjectDependencies();
					for (int i = 0; i < allDependencies.size(); i++) {
						Project depend = allDependencies.get(i);

						depend.compiling = true;
						Profiler.startSection(depend.getName());

						bar.setName("构建依赖["+depend.getName()+"]");
						bar.setProgress((double)i / allDependencies.size());

						try {
							success = depend.conf.type.canBuild() ? build(copyArgs, depend, new File(OUTPUT_PATH, depend.getOutputFormat())) : compile(copyArgs, depend);
							if (!success) {
								bar.end("构建失败");
								break block;
							}
						} finally {
							depend.compiling = false;
							if (depend.mappedWriter != null) depend.mappedWriter.end();
						}
						Profiler.endSection();
					}
				}
				Profiler.endSection();
			}

			if (args.contains("full/inherit")) args.add("full");

			File jarFile = new File(OUTPUT_PATH, project.getOutputFormat());
			success = build(args, project, jarFile);
			Statistic.afterProjectBuild(project.getName(), System.currentTimeMillis() - startTime, args, success);
			if (!success && args.contains("full")) watcher.remove(project);
		} finally {
			if (project.mappedWriter != null) try {
				project.mappedWriter.end();
			} catch (Throwable e) {
				LOGGER.warn("mapperWriter", e);
			}

			_unlock();
		}

		return success ? 0 : 1;
	}
	// region Build
	private static boolean compile(Set<String> args, Project p) throws IOException {
		int increment = p.unmappedJar.length() != 0 && !args.contains("full") ? INC_UPDATE : INC_FULL;

		Profiler.startSection("environment");

		long listSourceTime = System.currentTimeMillis();
		long lastModified = p.unmappedJar.lastModified();
		var context = new BuildContext(p);
		context.increment = increment;

		Profiler.endStartSection("compile");
		if (!compile(args, p, increment > 0 ? lastModified : -1, context)) {
			Profiler.endSection();
			return false;
		}

		boolean wasUpdated = context.getClasses().size() != 0;

		Profiler.endStartSection("script");
		if (!p.unmappedJar.setLastModified(listSourceTime)) Tty.warning("设置时间戳失败!");
		if (!args.contains("silent") || wasUpdated) Tty.success("构建成功["+p.getName()+"]! "+(System.currentTimeMillis()-listSourceTime)+"ms");
		p.compileSuccess(increment > 0);

		Profiler.endSection();
		return true;
	}
	private static boolean build(Set<String> args, Project p, File jarFile) throws IOException {
		int increment = p.unmappedJar.length() != 0 && !args.contains("full") ? jarFile.isFile() ? INC_UPDATE : INC_REBUILD : INC_FULL;

		Profiler.startSection("lockOutput");

		ZipOutput mappedWriter = lockOutput(p, jarFile);
		mappedWriter.begin(increment == INC_UPDATE);
		mappedWriter.setComment("MCMake "+VERSION+" by Roj234\r\n" +
				"https://www.github.com/roj234/rojlib");

		Profiler.endStartSection("environment");

		long listSourceTime = System.currentTimeMillis();
		long prevCompile = p.unmappedJar.lastModified();
		var context = new BuildContext(p);
		context.increment = increment;

		Future<Integer> resources = EXECUTOR.submit(p.getResourceTask(increment == INC_UPDATE ? prevCompile : -1, context));
		context.initCache();

		Profiler.endStartSection("compile");
		if (!compile(args, p, increment == INC_FULL ? -1 : prevCompile, context)) {
			Profiler.endStartSection("writeResource [FAILURE]");
			resources.cancel(false);
			try {
				resources.get();
			} catch (Exception ignored) {}

			Profiler.endSection();
			return false;
		}

		increment = context.increment;
		var classes = context.getClasses();

		Profiler.endStartSection("getDependencyClasses");
		p.getDependencyClasses(context, prevCompile);

		Profiler.endStartSection("afterCompile");
		int oldSize = classes.size();
		classes = context.afterCompile();
		if (classes == null) {
			Profiler.endSection();
			Profiler.endSection();
			return false;
		}
		LOGGER.debug("GetDependencyClasses {}, {} => {}", increment, oldSize, classes.size());

		int resourceUpdated = 0;
		Profiler.endStartSection("writeResource");
		try {
			resourceUpdated = resources.get();
		} catch (Exception e) {
			Tty.warning("资源更新失败["+p.getName()+"]", e);
		}

		int extraWritten = context.writeExtra(mappedWriter);

		Profiler.endStartSection("writeClasses");
		Context ctx = Helpers.maybeNull();
		try {
			for (int i = 0; i < context.updateCount; i++) {
				ctx = classes.get(i);
				mappedWriter.set(ctx.getFileName(), ctx::getCompressedShared);
			}
		} catch (Throwable e) {
			Tty.error("代码更新失败["+p.getName()+"/"+ctx.getFileName()+"]", e);
			return false;
		}

		mappedWriter.end();

		boolean wasUpdated = (context.updateCount | extraWritten | resourceUpdated) != 0;
		if (wasUpdated && p.variables.get("fmd:signature:keystore") != null) {
			Profiler.endStartSection("signature");
			try {
				signatureJar(p);
				LOGGER.trace("签名成功");
			} catch (Exception e) {
				LOGGER.error("签名失败", e);
			}
		}

		Profiler.endStartSection("script");
		if (p.conf.type.hasFile() && !p.unmappedJar.setLastModified(listSourceTime)) Tty.warning("设置时间戳失败!");
		if (!args.contains("silent") || wasUpdated) Tty.success((wasUpdated?"构建成功":"无变更")+"["+p.getName()+"]! "+(System.currentTimeMillis()-listSourceTime)+"ms");
		p.compileSuccess(increment > 0);

		Profiler.endSection();
		return true;
	}
	private static ZipOutput lockOutput(Project p, File jarFile) {
		int amount = 30 * 20;
		while (jarFile.isFile() && !IOUtil.isReallyWritable(jarFile)) {
			if ((amount % 100) == 0) Tty.warning("输出jar已被其它程序锁定, 等待解锁……");
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
	private static List<File> listSourceFile(Project p, Set<String> classWasDeleted, long modifyAfter) throws IOException {
		List<File> sources;
		Set<String> sourceChanged;
		if (modifyAfter >= 0 && !(sourceChanged = watcher.getModified(p, FileWatcher.ID_SRC)).contains(null)) {
			// 初始化依赖图
			if (p.dependencyGraph.isEmpty()) {
				Profiler.startSection("dependencyGraph");
				ZipArchive za = p.unmappedWriter.getArchive();
				for (ZEntry ze : za.entries()) {
					p.dependencyGraph.add(ClassNode.parseSkeleton(za.get(ze)));
				}
				p.unmappedWriter.close();
				Profiler.endSection();
			}

			String srcPrefix = p.srcPath.getAbsolutePath();
			int srcPrefixLen = srcPrefix.length()+1;

			synchronized (sourceChanged) {
				for (String path : new ArrayList<>(sourceChanged)) {
					File file = new File(path);
					var className = path.substring(srcPrefixLen, path.length()-5).replace(File.separatorChar, '/'); // get roj/test/Asdf
					if (file.isFile()) {
						LOGGER.trace("Modified: {}", className);
						for (String referent : p.dependencyGraph.get(className)) {
							LOGGER.trace("Referenced by: {}", referent);
							referent = srcPrefix+File.separatorChar+referent+".java";
							sourceChanged.add(referent);
						}
					} else {
						LOGGER.trace("Deleted: {}", className);
						classWasDeleted.add(className);
					}
				}

				sources = new ArrayList<>(sourceChanged.size());

				for (String path : sourceChanged) {
					File file = new File(path);
					if (file.isFile())
						sources.add(file);
				}

				p.dependencyGraph.remove(sourceChanged);
				p.dependencyGraph.remove(classWasDeleted);
			}
			return sources;
		}

		p.dependencyGraph.clear();
		Predicate<File> incrFilter = file -> IOUtil.extensionName(file.getName()).equals("java") && file.lastModified() > modifyAfter;
		return IOUtil.listFiles(p.srcPath, incrFilter);
	}
	private static String getClassPath(Project p, boolean increment) {
		var classpath = new CharList(200);

		var prefix = BASE.getAbsolutePath().length()+1;
		Predicate<File> callback = Dependency.DirDep.jarFilter(classpath, prefix);

		for (File file : p.workspace.depend) {
			classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
		}
		for (File file : p.workspace.mappedDepend) {
			classpath.append(file.getAbsolutePath().substring(prefix)).append(File.pathSeparatorChar);
		}
		IOUtil.listFiles(new File(BASE, "lib"), callback);
		IOUtil.listFiles(p.libPath, callback);

		var dependencies = p.getCompileDependencies();
		for (int i = 0; i < dependencies.size(); i++) {
			dependencies.get(i).getClassPath(classpath, prefix);
		}

		if (increment) classpath.append(p.unmappedJar.getAbsolutePath().substring(prefix));
		else if (classpath.length() > 0) classpath.setLength(classpath.length()-1);

		return classpath.toStringAndFree();
	}
	private static boolean compile(Set<String> args, Project p, long modifyAfter, BuildContext context) throws IOException {
		int increment = context.increment;

		Set<String> removedSources = new HashSet<>();
		Profiler.startSection("listSources");
		List<File> sources = listSourceFile(p, removedSources, modifyAfter);
		if (sources.isEmpty() && removedSources.isEmpty() && increment != INC_LOAD && increment != INC_REBUILD) {
			context.setClasses(new ArrayList<>());
			Profiler.endSection();
			return true;
		}

		List<? extends ClassResource> outputs;
		if (sources.isEmpty()) {
			outputs = new ArrayList<>();
		} else {
			Profiler.endStartSection("parameters");

			String classPath = getClassPath(p, increment > 0);

			ArrayList<String> options = p.conf.compiler_options_overwrite ? new ArrayList<>() : p.compiler.factory().getDefaultOptions();
			options.addAll(p.conf.compiler_options);
			options.addAll("-cp", classPath, "-encoding", p.charset.name());
			p.compiler.modifyOptions(options, p);

			LOGGER.debug("Compile {}, argBefore={}", increment, options);
			Profiler.endStartSection("beforeCompile");
			increment = context.beforeCompile(options, sources, increment);
			LOGGER.debug("Compile {}, argAfter={}", increment, options);

			Profiler.endStartSection("compile");
			outputs = p.compiler.compile(options, sources, args.contains("diagnostic"));

			// 编译失败
			if (outputs == null) {
				Profiler.endSection();
				return false;
			}
		}

		Profiler.endStartSection("outputJar");

		List<Context> compilerOutput = Helpers.cast(outputs);
		context.setClasses(compilerOutput);

		ZipOutput unmappedWriter = p.unmappedWriter;
		try {
			unmappedWriter.begin(increment > 0);

			Profiler.startSection("update");
			// 更新
			for (int i = 0; i < outputs.size(); i++) {
				var out = outputs.get(i);
				unmappedWriter.set(out.getFileName(), out.getClassBytes());
				Context ctx = new Context(out.getFileName(), out.getClassBytes().slice());
				compilerOutput.set(i, ctx);

				p.dependencyGraph.add(ctx.getData());
			}

			// 删除
			if (increment > 0) {
				Profiler.endStartSection("remove");

				ZipArchive za = unmappedWriter.getArchive();
				for (ZEntry entry : za.entries()) {
					String className = entry.getName();
					if (className.endsWith(".class")) {
						int subClass = className.indexOf('$');
						className = className.substring(subClass < 0 ? 0 : subClass, className.length()-6);

						if (removedSources.contains(className)) {
							LOGGER.trace("Removing {}", className);
							za.put(entry.getName(), null);
						}
					}
				}
			}

			// mappedJar不存在时，需要复制所有class
			if (increment == INC_REBUILD || increment == INC_LOAD) {
				Profiler.endStartSection("load");

				HashSet<String> changed = new HashSet<>(compilerOutput.size());
				for (int i = 0; i < compilerOutput.size(); i++) changed.add(compilerOutput.get(i).getFileName());

				ZipArchive za = unmappedWriter.getArchive();
				for (ZEntry ze : za.entries()) {
					if (!changed.contains(ze.getName())) {
						compilerOutput.add(new Context(ze.getName(), za.get(ze)));
					}
				}

				LOGGER.debug("Load {} contexts", za.entries().size() - changed.size());
				Profiler.endSection();
			}
		} catch (Throwable e) {
			Tty.warning("增量更新失败["+p.getName()+"]", e);

			unmappedWriter.end();
			Profiler.endSection();
			Profiler.endSection();

			p.unmappedJar.setLastModified(0);
			return false;
		}

		unmappedWriter.end();
		Profiler.endSection();
		Profiler.endSection();
		return true;
	}
	private static void signatureJar(Project p) throws IOException, GeneralSecurityException {
		var keystore = new File(p.variables.get("fmd:signature:keystore"));
		var private_key_pass = p.variables.get("fmd:signature:keystore_pass").toCharArray();
		var key_alias = p.variables.get("fmd:signature:key_alias");

		var result = p.signatureCache;

		if (result == null) result = readKeys(keystore, private_key_pass, key_alias);
		var options = result.options;
		if (options.isEmpty()) {
			options.put("jarSigner:creator", "MCMake/"+VERSION);
			options.put("jarSigner:signatureFileName", p.variables.getOrDefault("fmd:signature:certificate_name", IOUtil.fileName(keystore.getName())));
			options.put("jarSigner:manifestHashAlgorithm", p.variables.getOrDefault("fmd:signature:manifest_hash_algorithm", "SHA-256"));
			options.put("jarSigner:signatureHashAlgorithm", p.variables.getOrDefault("fmd:signature:signature_hash_algorithm", "SHA-256"));
			options.put("jarSigner:cacheHash", "true");
		}

		try (var zf = p.mappedWriter.getArchive()) {
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
		Map<String, String> options = new HashMap<>();

		public SignatureCache(List<Certificate> certificate_chain, PrivateKey private_key) {
            this.certificate_chain = certificate_chain;
            this.private_key = private_key;
        }
    }
	// endregion
}