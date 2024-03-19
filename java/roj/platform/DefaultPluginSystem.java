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
import roj.config.auto.Serializer;
import roj.config.auto.Serializers;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.http.server.HttpServer11;
import roj.net.http.server.auto.OKRouter;
import roj.reflect.ILSecurityManager;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.Template;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.AnsiString;
import roj.ui.CLIUtil;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collections;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/25 0025 18:01
 */
public final class DefaultPluginSystem extends PluginManager {
	static final AnnotationRepo REPO = new AnnotationRepo();
	static final NodeFilter FILTER = new NodeFilter();
	static final CommandConsole CMD = new CommandConsole("Pangoo> ");
	static final Scheduler MainThread = new Scheduler(task -> {
		ITask _task = task.getTask();
		long nextRun = _task instanceof LoopTaskWrapper loop ? loop.getNextRun() : 0;
		try {
			_task.execute();
		} catch (Throwable e) {
			LOGGER.error("任务执行异常", e);
		}
		return nextRun;
	});

	public static void runSync() {

	}

	static DefaultPluginSystem PM;
	public static DefaultPluginSystem getInstance() { return PM; }

	private static CMap CONFIG;

	private DefaultPluginSystem(File dataFolder) { super(dataFolder); }

	private static void initLogger() {
		Logger.getRootContext().destination(() -> System.out);
		Logger.getRootContext().setPrefix(Template.compile("[${0}][${THREAD}][${NAME}/${LEVEL}]: "));
		LOGGER.getContext().setPrefix(Logger.getRootContext().getPrefix());
	}
	public static void main(String[] args) throws Exception {
		Bootstrap.classLoader.registerTransformer(FILTER);
		Bootstrap.classLoader.registerTransformer(new DPSAutoObfuscate());
		Bootstrap.classLoader.registerTransformer(EventTransformer.register(FILTER));

		initLogger();

		File altConfig = new File("DPSCore.yml");
		CONFIG = altConfig.isFile() ? ConfigMaster.fromExtension(altConfig).parse(altConfig).asMap() : new CMap();

		File dataFolder = new File(CONFIG.getString("plugin_dir", "plugins"));
		dataFolder.mkdirs();
		PM = new DefaultPluginSystem(dataFolder);
		if (CLIUtil.ANSI) PM.fancyLoading();

		CMD.register(literal("stop").executes(ctx -> System.exit(0)));
		Runtime.getRuntime().addShutdownHook(new Thread(DefaultPluginSystem::shutdown, "stop"));

		long time = System.currentTimeMillis();
		PM.onLoad();

		transformers.add(new DPSSecurityManager());
		DPSSecurityManager.LOGGER.setLevel(Level.INFO);
		ILSecurityManager.setSecurityManager(new DPSSecurityManager.SecureClassDefineIL());

		PM.readPlugins();
		LOGGER.info("Started in {}ms", System.currentTimeMillis()-time);
		if (CLIUtil.ANSI) {
			CMD.sortCommands();
			CLIUtil.setConsole(CMD);
		} else {
			LOGGER.warn("在交互式终端中能获得更好的体验");
		}

		MainThread.run();
	}

	private void onLoad() {
		String CORE_VERSION = "1.6.0";

		PluginDescriptor pd = new PluginDescriptor();
		pd.id = "Core";
		pd.version = new Version(CORE_VERSION);
		pd.authors = Collections.singletonList("Roj234");

		pd.cl = DefaultPluginSystem.class.getClassLoader();
		pd.skipCheck = true;
		pd.dynamicLoadClass = true;
		pd.accessUnsafe = true;

		pd.state = ENABLED;
		plugins.put(pd.id, pd);

		CMD.register(literal("load").then(argument("name", Argument.file()).executes(ctx -> {
			File plugin = ctx.argument("name", File.class);
			MainThread.runAsync(() -> loadPlugin(plugin));
		})));
		CMD.register(literal("unload").then(argument("id", Argument.oneOf(plugins)).executes(ctx -> {
			String id = ctx.argument("id", PluginDescriptor.class).id;
			MainThread.runAsync(() -> unloadPlugin(id));
		})));
		CMD.register(literal("help").executes(ctx -> {
			System.out.println("加载的插件:");
			for (PluginDescriptor value : plugins.values()) {
				System.out.println("  "+value.getFullDesc().replace("\n", "\n  ")+"\n");
			}
			System.out.println("注册的指令:");
			System.out.println(CMD.dumpNodes(IOUtil.getSharedCharBuf(), 2));
		}));

		LOGGER.getContext().name("Panger");
		CharList s = new CharList("""
			\u001b[38;2;46;137;255m
			  ██████╗   █████╗  ███╗   ██╗  ██████╗  ███████╗ ██████╗\s
			  ██╔══██╗ ██╔══██╗ ████╗  ██║ ██╔════╝  ██╔════╝ ██╔══██╗
			  ██████╔╝ ███████║ ██╔██╗ ██║ ██║  ███╗ █████╗   ██████╔╝
			  ██╔═══╝  ██╔══██║ ██║╚██╗██║ ██║   ██║ ██╔══╝   ██╔══██╗
			  ██║      ██║  ██║ ██║ ╚████║ ╚██████╔╝ ███████╗ ██║  ██║
			  ╚═╝      ╚═╝  ╚═╝ ╚═╝  ╚═══╝  ╚═════╝  ╚══════╝ ╚═╝  ╚═╝\s\u001b[38;2;128;255;255mv{V}
			\u001b[38;2;255;255;255m""").replace("{V}", CORE_VERSION);

		try {
			// content from https://mcmod.cn, public license
			File file = new File(getPluginFolder(), "Core/splashes.txt");
			int line = 863;
			line = (int)(System.currentTimeMillis()/86400000L)%line;

			String slogan = LineReader.getLine(IOUtil.readUTF(file), line);
			slogan = Tokenizer.removeSlashes(slogan).replace("{user}", System.getProperty("user.name"));

			int width = 56 - slogan.length()*2;
			s.padEnd(' ', width).append("── ").append(slogan).append("\n\u001b[0m");
		} catch (Exception ignored) {}
		System.out.println(s.toStringAndFree());

		File file = Helpers.getJarByClass(DefaultPluginSystem.class);
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
					MainThread.runAsync(() -> {
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

	private void fancyLoading() throws Exception {
		File input = new File(getPluginFolder(), "Core/splash.png");
		if (!input.isFile()) return;

		BufferedImage image = ImageIO.read(input);
		System.out.println("\u001b[1;1H\u001b[0J┏"+"━".repeat(image.getWidth()*2+1)+'┓');
		for (int y = 0; y < image.getHeight(); y ++) {
			System.out.print("┃ ");
			for (int x = 0; x < image.getWidth(); x++) {
				System.out.print(new AnsiString(String.valueOf("咩啊".charAt((x+y)%2))).colorRGB(image.getRGB(x, y)).toAnsiString());
			}
			System.out.println('┃');
		}
		System.out.println('┗'+"━".repeat(image.getWidth()*2+1)+"┛\u001b[0m");
		Thread.sleep(500);
		System.out.print("\u001b[1;1H\u001b[0J");
	}

	private static void shutdown() {
		CLIUtil.setConsole(null);
		LOGGER.info("正在关闭系统");
		PM.unloadPlugins();
	}

	private static OKRouter router;
	static OKRouter initHttp() {
		if (router == null) {
			router = new OKRouter();
			try {
				HttpServer11.simple(new InetSocketAddress(CONFIG.getInteger("http_port", 12345)), 512, router).launch();
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