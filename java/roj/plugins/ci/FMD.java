package roj.plugins.ci;

import org.jetbrains.annotations.NotNull;
import roj.archive.qpak.QZArchiver;
import roj.archive.qz.*;
import roj.archive.xz.LZMA2Options;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.archive.zip.ZipOutput;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.ModuleAttribute;
import roj.asm.attr.StringAttribute;
import roj.asmx.Context;
import roj.asmx.TransformException;
import roj.asmx.TransformUtil;
import roj.asmx.event.EventBus;
import roj.asmx.launcher.Bootstrap;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.TrieTreeSet;
import roj.concurrent.Flow;
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
import roj.io.source.FileSource;
import roj.math.Version;
import roj.plugins.ci.annotation.ReplaceConstant;
import roj.plugins.ci.minecraft.MappingUI;
import roj.plugins.ci.plugin.MAP;
import roj.plugins.ci.plugin.ProcessEnvironment;
import roj.plugins.ci.plugin.Processor;
import roj.text.CharList;
import roj.text.DateTime;
import roj.text.Formatter;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.Argument;
import roj.ui.EasyProgressBar;
import roj.ui.Shell;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;

import javax.swing.*;
import java.io.*;
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
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * FMD Main class
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
	public static final TaskPool EXECUTOR = TaskPool.common();
	public static final EventBus EVENT_BUS = new EventBus();
	private static final AtomicInteger COMPILE_LOCK = new AtomicInteger();
	static IFileWatcher watcher;

	public static CMap config;
	public static final Shell CommandManager = new Shell("");

	public static Project defaultProject;
	public static final HashMap<String, Project> projects = new HashMap<>();
	static final HashMap<String, Compiler.Factory> compilerTypes = new HashMap<>();
	static final HashMap<String, Workspace.Factory> workspaceTypes = new HashMap<>();
	public static final HashMap<String, Workspace> workspaces = new HashMap<>();

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
				int build = build(new HashSet<>(flags), project);
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
				var proj = ctx.argument("项目名称", Project.class);
				Workspace.addIDEAProject(proj, true);
				proj.unmappedJar.delete();
				new File(DATA_PATH, "at-"+proj.name+"-.jar").delete();
				new File(PROJECT_PATH, proj.name+".iml").delete();
				projects.remove(proj.name);
				saveConfig();
				System.out.println("已从配置中删除，请手动删除projects目录下文件");
			})))
			.then(literal("setdefault").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
				defaultProject = ctx.argument("项目名称", Project.class);
				saveConfig();
				updatePrompt();
			})))
			.then(literal("export").then(argument("项目名称", Argument.oneOf(projects)).executes(ctx -> {
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
				arc.outputFilename = "mcmake-project-"+proj.name+"-"+DateTime.local().format("Ymd", System.currentTimeMillis())+".7z";

				arc.prepare();

				String ymldata = ConfigMaster.YAML.writeObject(POJO_FACTORY.serializer(EnvPojo.Project.class), proj.serialize(), IOUtil.getSharedCharBuf()).toString();
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
						Terminal.error("不是有效的归档文件：找不到project.yml");
						return;
					}

					EnvPojo.Project pojo = ConfigMaster.YAML.readObject(POJO_FACTORY.serializer(EnvPojo.Project.class), in);
					pojo.name = IOUtil.safePath(pojo.name);

					if (projects.containsKey(pojo.name)) {
						Terminal.warning("同名项目("+pojo.name+")已存在");
						return;
					}

					var basePath = new File(PROJECT_PATH, pojo.name);
					if (basePath.exists()) {
						Terminal.warning("项目目录'"+basePath+"'非空，是否清空目录\nY: 清空并导入\nN: 不清空并导入\nCtrl+C: 取消操作");
						char selection = Terminal.readChar("YyNn", "您的选择: ");
						if (selection == 0) return;
						if (selection == 'Y' || selection == 'y') IOUtil.deletePath(basePath);
					}

					bar.setName("导入"+pojo.name);
					for (QZEntry entry : archive.entries()) {
						if (entry.getName().equals("project.yml")) continue;
						bar.addTotal(entry.getSize());
					}

					AtomicInteger lock = archive.parallelDecompress(TaskPool.common(), (entry, in1) -> {
						if (entry.isDirectory()) return;
						var file = new File(basePath, IOUtil.safePath(entry.getName()));
						file.getParentFile().mkdirs();

						try (var out = new FileOutputStream(file)) {
							QZArchiver.copyStreamWithProgress(in1, out, bar);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
					QZArchive.awaitParallelComplete(lock);

					if (lock.get() == 0) {
						Project project = new Project(pojo);
						project.init();
						Workspace.addIDEAProject(project, false);

						projects.put(project.name, project);
						saveConfig();

						bar.end("成功");
					}
				}
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
				char nn = Terminal.readChar("YyNn", "输入Y确定删除'"+space.id+"'及其所有文件");
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

				workspaces.remove(space.id);
				saveConfig();
				System.out.println("工作空间'"+space.id+"'已删除");
			})))
			.then(literal("export").then(argument("工作空间名称", Argument.oneOf(workspaces)).executes(ctx -> {
				Workspace space = POJO_FACTORY.serializer(Workspace.class).deepcopy(ctx.argument("工作空间名称", Workspace.class));

				try (var qzfw = new QZFileWriter("workspace-"+space.id+".7z");
					var bar = new EasyProgressBar("导出"+space.id, "B")) {

					bar.addTotal(Integer.MAX_VALUE);

					var files = new ArrayList<>(space.depend);
					files.addAll(space.mappedDepend);
					files.addAll(space.unmappedDepend);

					List<Future<?>> tasks = new ArrayList<>();

					for (File file : files) {
						tasks.add(TaskPool.common().submit(() -> {
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
							return null;
						}));
					}

					for (Future<?> task : tasks) task.get();

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
					ConfigMaster.YAML.writeObject(POJO_FACTORY.serializer(Workspace.class), space, IOUtil.getSharedByteBuf()).writeToStream(qzfw);

					bar.end("成功");
				}
			})))
			.then(literal("import").then(argument("工作空间归档", Argument.file()).executes(ctx -> {
				File archiveFile = ctx.argument("工作空间归档", File.class);
				try (var archive = new QZArchive(archiveFile);
					var bar = new EasyProgressBar("导入工作空间", "B")) {
					archive.setMemoryLimitKb(26214400);

					InputStream in = archive.getStream("workspace.yml");
					if (in == null) {
						Terminal.error("不是有效的归档文件：找不到workspace.yml");
						return;
					}

					Workspace workspace = ConfigMaster.YAML.readObject(POJO_FACTORY.serializer(Workspace.class), in);
					workspace.id = IOUtil.safePath(workspace.id);

					if (workspaces.containsKey(workspace.id)) {
						Terminal.warning("同名工作空间("+workspace.id+")已存在");
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
						result = new File(DATA_PATH, "ws-"+workspace.id+"-cd"+i+path.substring(path.lastIndexOf('.')));

						filenameMapping.putIfAbsent(file.getName(), result);
						depend.set(i, result);
					}
					List<File> mappedDepend = workspace.mappedDepend;
					for (int i = 0; i < mappedDepend.size(); i++) {
						File file = mappedDepend.get(i);
						File result;

						var path = file.getName();
						result = new File(DATA_PATH, "ws-"+workspace.id+"-md"+i+path.substring(path.lastIndexOf('.')));

						filenameMapping.putIfAbsent(file.getName(), result);
						mappedDepend.set(i, result);
					}
					List<File> unmappedDepend = workspace.unmappedDepend;
					for (int i = 0; i < unmappedDepend.size(); i++) {
						File file = unmappedDepend.get(i);
						File result;

						var path = file.getName();
						result = new File(DATA_PATH, "ws-"+workspace.id+"-ud"+i+path.substring(path.lastIndexOf('.')));

						filenameMapping.putIfAbsent(file.getName(), result);
						unmappedDepend.set(i, result);
					}

					for (var itr = filenameMapping.entrySet().iterator(); itr.hasNext(); ) {
						Map.Entry<String, Object> generalizedEntry = Helpers.cast(itr.next());
						generalizedEntry.setValue(new ZipFileWriter(((File) generalizedEntry.getValue()), Deflater.BEST_COMPRESSION, 0));
					}
					Map<String, ZipFileWriter> libraryWriter = Helpers.cast(filenameMapping);

					if (workspace.mapping != null) {
						workspace.mapping = new File(DATA_PATH, "ws-"+workspace.id+"-mapping.lzma");
					}

					AtomicInteger lock = archive.parallelDecompress(TaskPool.common(), (entry, in1) -> {
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
					QZArchive.awaitParallelComplete(lock);
					for (ZipFileWriter zfw : libraryWriter.values()) zfw.close();

					if (lock.get() == 0) {
						workspaces.put(workspace.id, workspace);
						saveConfig();

						bar.end("成功");
					}
				}
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

		c.register(literal("modulize").then(argument("文件", Argument.file()).then(argument("模块名", Argument.string()).executes(ctx -> {
			File file = ctx.argument("文件", File.class);
			var moduleName = ctx.argument("模块名", String.class);

			HashSet<String> packages = new HashSet<>();

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
			//moduleInfo.putAttr(new AttrClassList(Attribute.ModulePackages, new ArrayList<>(packages)));
			moduleInfo.addAttribute(moduleAttr);

			System.out.println(moduleAttr);
			try (var za = new ZipArchive(file)) {
				za.put("module-info.class", DynByteBuf.wrap(AsmCache.toByteArray(moduleInfo)));
				za.save();
			}
			System.out.println("IL自动模块，应用成功！");
		}))));

		c.register(literal("backup").executes(ctx -> {
			File backups = new File(BASE, "backups");

			QZArchiver arc = new QZArchiver();
			arc.inputDirectories = Flow.of(PROJECT_PATH.listFiles()).filter(File::isDirectory).toList();
			//arc.threads = 0;
			arc.updateMode = QZArchiver.UM_REPLACE;
			arc.pathFormat = QZArchiver.PF_FULL;
			arc.storeModifiedTime = true;
			arc.storeSymbolicLinks = true;
			arc.storeHardLinks = true;
			arc.solidSize = Long.MAX_VALUE;
			arc.options.setPreset(9).setDictSize(4194304);
			arc.useBCJ = true;
			arc.cacheDirectory = BASE;
			arc.outputDirectory = backups;

			arc.outputFilename = "projects-"+DateTime.local().format("Ymd H_i_s", System.currentTimeMillis())+".7z";

			arc.prepare();

			String ymldata = ConfigMaster.YAML.writeObject(POJO_FACTORY.serializer(EnvPojo.class), getEnvPojo(), IOUtil.getSharedCharBuf()).toString();
			arc.appendBinary(new QZCoder[]{Copy.INSTANCE}, Collections.singletonList(ymldata), Collections.singletonList(QZEntry.of("project.yml")));

			try (var bar = new EasyProgressBar("备份")) {
				arc.compress(TaskPool.common(), bar);
			}
		}));

		c.register(literal("mapping").then(literal("create").executes(ctx -> {
			JFrame frame = new MappingUI();
			frame.pack();
			frame.setResizable(false);
			frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			frame.setVisible(true);
		})));

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
							使用前请先阅读readme.md
							把您的时间花在更有意义的事情，而不是等待编译上
																—— Roj234
							""");
			updatePrompt();
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
		System.setProperty("roj.compiler.symbolCache", DATA_PATH.getAbsolutePath()+"/symbolCache.zip");

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

		processors = new ArrayList<>();
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
			@Optional String name_format = "${project_name}-${project_version}.jar";
			@Optional List<String> dependency = Collections.emptyList();
			@Optional List<String> binary_depend = Collections.emptyList();
			@Optional List<String> source_depend = Collections.emptyList();
			@Optional Map<String, String> variables = new HashMap<>();
			@Optional TrieTreeSet variable_replace_in = new TrieTreeSet();
		}
	}
	private static void loadEnv() throws IOException, ParseException {
		File file = new File(DATA_PATH, "env.yml");
		if (!file.isFile()) {
			workspaces.clear();
			projects.clear();
			return;
		}

		var env = ConfigMaster.YAML.readObject(POJO_FACTORY.serializer(EnvPojo.class), file);

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
		var env = getEnvPojo();
		try {
			ConfigMaster.YAML.writeObject(POJO_FACTORY.serializer(EnvPojo.class), env, new File(DATA_PATH, "env.yml"));
		} catch (IOException e) {
			LOGGER.error("配置保存失败", e);
		}
	}
	private static EnvPojo getEnvPojo() {
		var env = new EnvPojo();
		env.auto_compile = new ArrayList<>();
		env.projects = new ArrayList<>();
		env.workspaces = new ArrayList<>(workspaces.values());
		for (Project value : projects.values()) {
			env.projects.add(value.serialize());
			if (value.isAutoCompile()) {
				env.auto_compile.add(value.name);
			}
		}
		if (defaultProject != null) env.default_project = defaultProject.name;
		return env;
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
		boolean success;
		try {
			success = build(args, project, BASE, 0);
			if (!success && args.contains("full")) {
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

		return success ? 0 : 1;
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
		Set<String> classWasDeleted;

		Profiler.endStartSection("findModifiedSources");
		long time = System.currentTimeMillis();
		List<File> sources;
		findSourceDone: {
			long stamp;
			if (increment) {
				stamp = p.unmappedJar.lastModified();
				updateResource = EXECUTOR.submit(p.getResourceTask(stamp));
				Set<String> sourceChanged = watcher.getModified(p, FileWatcher.ID_SRC);
				if (!sourceChanged.contains(null)) {
					if (p.dependencyGraph.isEmpty()) {
						ZipArchive mzf = p.unmappedWriter.getMZF();
						for (ZEntry file : mzf.entries()) {
							p.dependencyGraph.add(ClassNode.parseSkeleton(mzf.get(file)));
						}
						p.unmappedWriter.close();
					}

					String srcPrefix = p.srcPath.getAbsolutePath();
					int srcPrefixLen = srcPrefix.length()+1;

					synchronized (sourceChanged) {
						for (String path : new ArrayList<>(sourceChanged)) {
							var className = path.substring(srcPrefixLen, path.length()-5).replace(File.separatorChar, '/'); // get roj/test/Asdf

							LOGGER.trace("Modified: {}", className);
							for (String referent : p.dependencyGraph.get(className)) {
								LOGGER.trace("Referenced by: {}", referent);
								referent = srcPrefix+File.separatorChar+referent+".java";
								sourceChanged.add(referent);
							}
						}

						sources = new ArrayList<>(sourceChanged.size());

						for (String path : sourceChanged) {
							File file = new File(path);
							if (file.isFile())
								sources.add(file);
						}

						p.dependencyGraph.remove(sourceChanged);
					}
					break findSourceDone;
				}
			} else {
				stamp = -1;
				updateResource = EXECUTOR.submit(p.getResourceTask(stamp));
			}

			Predicate<File> incrFilter = file -> IOUtil.extensionName(file.getName()).equals("java") && file.lastModified() > stamp;
			sources = IOUtil.listFiles(p.srcPath, incrFilter);
			for (String s : p.conf.source_depend) IOUtil.listFiles(projects.get(s).srcPath, sources, incrFilter);
			p.dependencyGraph.clear();
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

			String classPath = getClassPath(p, increment);

			ArrayList<String> options = p.conf.compiler_options_overwrite ? new ArrayList<>() : p.compiler.factory().getDefaultOptions();
			options.addAll(p.conf.compiler_options);
			options.addAll("-cp", classPath, "-encoding", p.charset.name());
			if ("true".equals(p.conf.variables.get("javac:use_module")))
				options.addAll("--module-path", classPath);

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
			List<Context> compilerOutput = Helpers.cast(outputs);

			ZipOutput unmappedWriter = p.unmappedWriter;
			try {
				unmappedWriter.begin(increment);

				for (int i = 0; i < outputs.size(); i++) {
					var out = outputs.get(i);
					unmappedWriter.set(out.getFileName(), out.getClassBytes());
					Context ctx = new Context(out.getFileName(), out.getClassBytes().slice());
					compilerOutput.set(i, ctx);

					p.dependencyGraph.add(ctx.getData());
				}

				if (incrementLevel == 1) {
					Profiler.startSection("loadMapperState");

					HashSet<String> changed = new HashSet<>(compilerOutput.size());
					for (int i = 0; i < compilerOutput.size(); i++) changed.add(compilerOutput.get(i).getFileName());

					// copy all
					ZipArchive mzf = unmappedWriter.getMZF();
					for (ZEntry file : mzf.entries()) {
						if (!changed.contains(file.getName())) {
							Context ctx = new Context(file.getName(), mzf.get(file));
							compilerOutput.add(ctx);
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
				compilerOutput = processors.get(i).process(compilerOutput, pc);
			}

			try {
				pc.runTransformers(compilerOutput);
			} catch (TransformException e) {
				Terminal.error("类转换失败", e);
				return false;
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
				for (int i = 0; i < compilerOutput.size(); i++) {
					ctx = compilerOutput.get(i);
					mappedWriter.set(ctx.getFileName(), ctx::getCompressedShared);
				}
			} catch (Throwable e) {
				Terminal.error("代码更新失败["+p.getName()+"/"+ctx.getFileName()+"]", e);
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

		p.compileSuccess(increment);
		return true;
	}

	private static String getClassPath(Project p, boolean increment) {
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
		IOUtil.listFiles(new File(BASE, "libs"), callback);
		IOUtil.listFiles(new File(p.root, "libs"), callback);

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

		return classpath.toStringAndFree();
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
			options.put("jarSigner:manifestHashAlgorithm", p.variables.getOrDefault("fmd:signature:manifest_hash_algorithm", "SHA-256"));
			options.put("jarSigner:signatureHashAlgorithm", p.variables.getOrDefault("fmd:signature:signature_hash_algorithm", "SHA-256"));
			options.put("jarSigner:cacheHash", "true");
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
		Map<String, String> options = new HashMap<>();

		public SignatureCache(List<Certificate> certificate_chain, PrivateKey private_key) {
            this.certificate_chain = certificate_chain;
            this.private_key = private_key;
        }
    }
	// endregion
}