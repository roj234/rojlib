package roj.platform;

import roj.asm.tree.anno.Annotation;
import roj.asm.util.Context;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.asmx.ITransformer;
import roj.asmx.NodeFilter;
import roj.asmx.event.EventTransformer;
import roj.asmx.launcher.Bootstrap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.task.ITask;
import roj.concurrent.timing.LoopTaskWrapper;
import roj.concurrent.timing.Scheduler;
import roj.config.ConfigMaster;
import roj.config.Tokenizer;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.ch.ServerLaunch;
import roj.net.http.server.FileResponse;
import roj.net.http.server.HttpServer11;
import roj.net.http.server.auto.OKRouter;
import roj.reflect.ILSecurityManager;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.Template;
import roj.text.TextReader;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.CLIUtil;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiConsumer;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/25 0025 18:01
 */
public final class DefaultPluginSystem extends PluginManager {
	static final AnnotationRepo REPO = new AnnotationRepo();
	static final NodeFilter FILTER = new NodeFilter();

	static final CommandConsole CMD = new CommandConsole("Panger> ");

	private static final Scheduler ticker = new Scheduler(task -> {
		ITask _task = task.getTask();
		long nextRun = _task instanceof LoopTaskWrapper loop ? loop.getNextRun() : 0;
		try {
			_task.execute();
		} catch (Throwable e) {
			LOGGER.error("任务执行异常", e);
		}
		return nextRun;
	});
	static Scheduler getTicker() {return ticker;}
	static Thread mainThread;

	static DefaultPluginSystem PM;
	public static DefaultPluginSystem getInstance() { return PM; }

	private static CMap CONFIG;

	private DefaultPluginSystem(File dataFolder) { super(dataFolder); }

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		mainThread = Thread.currentThread();
		long time = System.currentTimeMillis();

		Bootstrap.classLoader.registerTransformer(FILTER);
		Bootstrap.classLoader.registerTransformer(new DPSAutoObfuscate());
		Bootstrap.classLoader.registerTransformer(EventTransformer.register(FILTER));

		transformers.add(new DPSSecurityManager());
		DPSSecurityManager.LOGGER.setLevel(Level.INFO);
		ILSecurityManager.setSecurityManager(new DPSSecurityManager.SecureClassDefineIL());

		Formatter f;
		if (CLIUtil.ANSI) {
			f = (env, sb) -> {
				((BiConsumer<Object, CharList>) env.get("0")).accept(env, sb.append('['));

				Level level = (Level) env.get("LEVEL");
				sb.append("]\u001b[").append(level.color).append("m[").append(env.get("NAME"));
				if (level.ordinal() > Level.WARN.ordinal())
					sb.append("][").append(env.get("THREAD"));

				return sb.append("]\u001b[0m: ");
			};
		} else {
			f = Template.compile("[${0}][${THREAD}][${NAME}/${LEVEL}]: ");
		}
		Logger.getRootContext().destination(() -> System.out).setPrefix(f);
		LOGGER.getContext().name("Panger");

		File altConfig = new File("DPSCore.yml");
		CONFIG = altConfig.isFile() ? ConfigMaster.fromExtension(altConfig).parse(altConfig).asMap() : new CMap();

		File dataFolder = new File(CONFIG.getString("plugin_dir", "plugins"));
		dataFolder.mkdirs();
		PM = new DefaultPluginSystem(dataFolder);

		CMD.register(literal("stop").executes(ctx -> System.exit(0)));
		Runtime.getRuntime().addShutdownHook(new Thread(DefaultPluginSystem::shutdown, "stop"));

		PM.onLoad();
		PM.readPlugins();
		LOGGER.info("启动耗时: {}ms", System.currentTimeMillis()-time);

		if (CLIUtil.ANSI) {
			CMD.sortCommands();
			CLIUtil.setConsole(CMD);
		} else {
			LOGGER.warn("在交互式终端中能获得更好的体验");
		}

		ticker.run();
	}

	private void onLoad() {
		String CORE_VERSION = "1.6.1";

		PluginDescriptor pd = new PluginDescriptor();
		pd.id = "Core";
		pd.version = new Version(CORE_VERSION);
		pd.authors = Collections.singletonList("Roj234");

		pd.skipCheck = true;
		pd.dynamicLoadClass = true;
		pd.accessUnsafe = true;

		pd.state = ENABLED;
		plugins.put(pd.id, pd);

		CMD.register(literal("load").then(argument("name", Argument.file()).executes(ctx -> {
			File plugin = ctx.argument("name", File.class);
			ticker.runAsync(() -> loadPlugin(plugin));
		})));
		CMD.register(literal("unload").then(argument("id", Argument.oneOf(plugins)).executes(ctx -> {
			String id = ctx.argument("id", PluginDescriptor.class).id;
			ticker.runAsync(() -> unloadPlugin(id));
		})));
		CMD.register(literal("help").executes(ctx -> {
			System.out.println("加载的插件:");
			for (PluginDescriptor value : plugins.values()) {
				System.out.println("  "+value.getFullDesc().replace("\n", "\n  ")+"\n");
			}
			System.out.println("注册的指令:");
			System.out.println(CMD.dumpNodes(IOUtil.getSharedCharBuf(), 2));
		}));

		CharList s = new CharList("""
			------------------------------------------------------------
			\u001b[38;2;46;137;255m
			  ██████╗   █████╗  ███╗   ██╗  ██████╗  ███████╗ ██████╗\s
			  ██╔══██╗ ██╔══██╗ ████╗  ██║ ██╔════╝  ██╔════╝ ██╔══██╗
			  ██████╔╝ ███████║ ██╔██╗ ██║ ██║  ███╗ █████╗   ██████╔╝
			  ██╔═══╝  ██╔══██║ ██║╚██╗██║ ██║   ██║ ██╔══╝   ██╔══██╗
			  ██║      ██║  ██║ ██║ ╚████║ ╚██████╔╝ ███████╗ ██║  ██║
			  ╚═╝      ╚═╝  ╚═╝ ╚═╝  ╚═══╝  ╚═════╝  ╚══════╝ ╚═╝  ╚═╝\s\u001b[38;2;128;255;255mv{V}
			\u001b[38;2;255;255;255m
			""").replace("{V}", CORE_VERSION);

		File file = new File(getPluginFolder(), "Core/splashes.txt");
		if (file.isFile()) try (TextReader tr = TextReader.from(file, StandardCharsets.UTF_8)) {
			int line = 863;
			line = (int)(System.currentTimeMillis()/86400000L)%line;

			CharList sb = IOUtil.getSharedCharBuf();
			while (line-- >= 0) {
				sb.clear();
				tr.readLine(sb);
			}
			String slogan = Tokenizer.removeSlashes(sb.replace("{user}", System.getProperty("user.name")));

			s.append(" + ").append(slogan).append('\n');
		} catch (Exception ignored) {}

		if (CLIUtil.ANSI) {
			System.out.print("\u001b[1;1H\u001b[0J\u001b]0;Panger VT\7");
			System.out.println(s.append(" + Copyright (c) 2019-2024 Roj234\n" +
				"\u001b[0m------------------------------------------------------------").toStringAndFree());
		}

		file = Helpers.getJarByClass(DefaultPluginSystem.class);
		if (file != null) {
			REPO.add(file);

			MyHashMap<String, PluginDescriptor> builtin = new MyHashMap<>();
			for (AnnotatedElement info : REPO.annotatedBy("roj/platform/BuiltinPlugin")) {
				pd = new PluginDescriptor();
				pd.fileName = "annotation:"+file.getName();
				pd.mainClass = info.owner().replace('/', '.');

				Annotation pin = info.annotations().get("roj/platform/BuiltinPlugin");
				pd.id = pin.getString("id");
				pd.version = new Version(pin.getString("version", CORE_VERSION));
				pd.desc = pin.getString("desc", "");

				if (pin.containsKey("depend"))
					pd.depend = Arrays.asList(pin.getStringArray("depend"));
				if (pin.containsKey("loadAfter"))
					pd.loadAfter = Arrays.asList(pin.getStringArray("loadAfter"));

				builtin.put(pd.id, pd);
			}
			if (builtin.size() > 0) {
				CMD.register(literal("load_builtin").then(argument("plugin", Argument.oneOf(builtin)).executes(ctx -> {
					PluginDescriptor pd1 = ctx.argument("plugin", PluginDescriptor.class);
					ticker.runAsync(() -> {
						plugins.putIfAbsent(pd1.id, pd1);
						loadPlugin(pd1);
					});
				})));
			}
			LOGGER.info("找到{}个内置插件", builtin.size());
		} else {
			LOGGER.warn("未从jar加载，无法加载内置插件");
		}
	}

	private static void shutdown() {
		CLIUtil.setConsole(null);
		LOGGER.info("正在关闭系统");
		PM.unloadPlugins();
		IOUtil.closeSilently(httpServer);
	}

	private static ServerLaunch httpServer;
	private static OKRouter router;
	static OKRouter initHttp() {
		if (router == null) {
			router = new OKRouter();
			try {
				httpServer = HttpServer11.simple(new InetSocketAddress(CONFIG.getInteger("http_port", 12345)), 512, router).launch();
				FileResponse.loadMimeMap(IOUtil.readUTF(new File(PM.getPluginFolder(), "Core/mime.ini")));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return router;
	}

	private static final SimpleList<ITransformer> transformers = new SimpleList<>();
	static void transform(String name, ByteList buf) {
		Context ctx = new Context(name, buf);
		boolean changed = false;
		for (int i = 0; i < transformers.size(); i++) {
			try {
				changed |= transformers.get(i).transform(name, ctx);
			} catch (Throwable e) {
				//LOGGER.fatal("转换类'{}'时发生异常", e, name);
				try {
					ctx.getData().dump();
				} catch (Throwable e1) {
					//LOGGER.fatal("保存'{}'的内容用于调试时发生异常", e1, name);
				}
				Helpers.athrow(e);
			}
		}
		if (changed) {
			buf.clear();
			buf.put(ctx.get());
		}
	}
}