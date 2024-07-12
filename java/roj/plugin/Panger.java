package roj.plugin;

import roj.asm.tree.anno.Annotation;
import roj.collect.CollectionX;
import roj.collect.MyHashMap;
import roj.config.Tokenizer;
import roj.config.data.CBoolean;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.ch.ServerLaunch;
import roj.net.http.server.HttpServer11;
import roj.net.http.server.MimeType;
import roj.net.http.server.ZipRouter;
import roj.net.http.server.auto.OKRouter;
import roj.reflect.ILSecurityManager;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.Template;
import roj.text.TextReader;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.CLIUtil;
import roj.ui.Console;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;
import roj.util.HighResolutionTimer;

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
public final class Panger extends PluginManager {
	static final CommandConsole CMD = new CommandConsole("Panger> ");
	public static Console console() {return CMD;}

	static Panger pm;
	public static PluginManager getInstance() {return pm;}
	private Panger(File pluginFolder) {super(pluginFolder);}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		long time = System.currentTimeMillis();

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
		Logger.getRootContext().setPrefix(f);
		boolean debugLoading = PanTweaker.CONFIG.getBool("debug_loading");
		LOGGER.setLevel(debugLoading ? Level.ALL : Level.INFO);
		if (!debugLoading) LOGGER.warn("有关插件加载顺序的详细信息，请开启debug_loading查看");
		boolean debugSecurity = PanTweaker.CONFIG.getBool("debug_security");
		PanSecurityManager.LOGGER.setLevel(debugSecurity ? Level.ALL : Level.INFO);
		if (!debugSecurity) LOGGER.warn("有关安全限制的详细信息，请开启debug_security查看");

		File plugins = new File("plugins"); plugins.mkdir();
		pm = new Panger(plugins);

		pm.onLoad();
		Runtime.getRuntime().addShutdownHook(new Thread(Panger::shutdown, "stop"));
		ILSecurityManager.setSecurityManager(new PanSecurityManager.ILHook());

		pm.readPlugins();

		if (CLIUtil.ANSI) {
			CMD.sortCommands();
			CLIUtil.setConsole(CMD);
		} else {
			LOGGER.warn("在交互式终端中能获得更好的体验");
		}

		LOGGER.info("启动耗时: {}ms", System.currentTimeMillis()-time);
		HighResolutionTimer.activate();
	}

	private void onLoad() {
		String CORE_VERSION = "1.7.5";
		splash(CORE_VERSION);

		var pd = new PluginDescriptor();
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
			loadPluginFromFile(plugin);
		})));
		CMD.register(literal("unload").then(argument("id", Argument.oneOf(CollectionX.toMap(plugins))).executes(ctx -> {
			String id = ctx.argument("id", PluginDescriptor.class).id;
			unloadPlugin(id);
		})));
		CMD.register(literal("help").executes(ctx -> {
			System.out.println("加载的插件:");
			for (var value : plugins) {
				if (value.state == ENABLED) System.out.println("  "+value);
			}
			System.out.println("\n使用help <插件名称>查看插件帮助\n使用cmdhelp <指令名称>查看(自动生成的)指令帮助\n按下F1查看所有指令的帮助(会很长)");
		}).then(argument("插件名称", Argument.oneOf(CollectionX.toMap(plugins))).executes(ctx -> {
			var pd1 = ctx.argument("插件名称", PluginDescriptor.class);
			System.out.println("  "+pd1.getFullDesc().replace("\n", "\n  ")+"\n\n");
		})));
		CMD.register(literal("cmdhelp").then(argument("指令名称", Argument.string()).executes(ctx -> {
			String name = ctx.argument("指令名称", String.class);
			for (var node : CMD.nodes()) {
				if (node.getName().equals(name)) {
					System.out.println(node.dump(new CharList(), 2).toStringAndFree());
				}
			}
		})));
		CMD.register(literal("stop").executes(ctx -> System.exit(0)));

		loadBuiltin();
	}
	private void splash(String CORE_VERSION) {
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

		System.out.print("\u001b[1;1H\u001b[0J\u001b]0;Panger VT\7");
		System.out.println(s.append(" + Copyright (c) 2019-2024 Roj234\n\u001b[0m------------------------------------------------------------").toStringAndFree());
	}
	private void loadBuiltin() {
		PluginDescriptor pd;
		CEntry entry = PanTweaker.CONFIG.getOr("load_builtin", CBoolean.TRUE);
		if (entry.getType() != Type.BOOL || entry.asBool()) {
			MyHashMap<String, PluginDescriptor> builtin = new MyHashMap<>();
			for (var info : PanTweaker.annotations.annotatedBy("roj/plugin/SimplePlugin")) {
				pd = new PluginDescriptor();
				pd.fileName = "annotation:"+info.node();
				pd.mainClass = info.owner().replace('/', '.');

				Annotation pin = info.annotations().get("roj/plugin/SimplePlugin");
				pd.id = pin.getString("id");
				pd.version = new Version(pin.getString("version", "1.0-SNAPSHOT"));
				pd.desc = pin.getString("desc", "");

				if (pin.containsKey("depend"))
					pd.depend = Arrays.asList(pin.getStringArray("depend"));
				if (pin.containsKey("loadAfter"))
					pd.loadAfter = Arrays.asList(pin.getStringArray("loadAfter"));

				builtin.put(pd.id, pd);
			}
			LOGGER.info("找到{}个内置插件", builtin.size());
			if (entry.getType() == Type.LIST) {
				for (var name : entry.asList().raw()) {
					pd = builtin.remove(name.asString());
					if (pd != null) plugins.add(pd);
					else LOGGER.warn("找不到内置插件 {}", name.asString());
				}
			}
			if (builtin.size() > 0) {
				CMD.register(literal("load_builtin").then(argument("plugin", Argument.oneOf(builtin)).executes(ctx -> {
					var pd1 = ctx.argument("plugin", PluginDescriptor.class);
					builtin.remove(pd1.id);
					loadAndEnablePlugin(pd1);
				})));
			}
		}
	}

	private static void shutdown() {
		CLIUtil.setConsole(null);
		LOGGER.info("正在关闭系统");
		pm.unloadPlugins();
		IOUtil.closeSilently(httpServer);
	}

	private static ServerLaunch httpServer;
	private static OKRouter router;
	static OKRouter initHttp() {
		if (router == null) {
			router = new OKRouter();
			try {
				router.addPrefixDelegation("", new ZipRouter(new File("plugins/Core/resource.zip")));
				httpServer = HttpServer11.simple(new InetSocketAddress(PanTweaker.CONFIG.getInteger("http_port", 8080)), 512, router).launch();
				MimeType.loadMimeMap(IOUtil.readUTF(new File("plugins/Core/mime.ini")));
			} catch (IOException e) {
				LOGGER.error("HTTP服务启动失败", e);
			}
		}
		return router;
	}
}