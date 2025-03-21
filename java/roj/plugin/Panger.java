package roj.plugin;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.annotation.Annotation;
import roj.collect.CollectionX;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.config.Tokenizer;
import roj.config.data.CBoolean;
import roj.config.data.CEntry;
import roj.config.data.Type;
import roj.http.server.*;
import roj.http.server.auto.OKRouter;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.MyChannel;
import roj.net.ServerLaunch;
import roj.plugins.ci.annotation.ReplaceConstant;
import roj.reflect.ILSecurityManager;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextReader;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.*;
import roj.util.Helpers;
import roj.util.HighResolutionTimer;
import roj.util.VMUtil;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static roj.plugin.PanTweaker.CONFIG;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/25 0025 18:01
 */
@ReplaceConstant
public final class Panger extends PluginManager {
	static final CommandConsole CMD = new CommandConsole("Panger>");
	static final boolean useModulePluginIfAvailable = true;

	public static Console console() {return CMD;}

	static Panger pm;
	public static PluginManager getInstance() {return pm;}
	private Panger(File pluginFolder) {super(pluginFolder);}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		long time = System.currentTimeMillis();

		Formatter f;
		if (Terminal.ANSI_OUTPUT) {
			f = (env, sb) -> {
				((BiConsumer<Object, CharList>) env.get("0")).accept(env, sb.append('['));

				Level level = (Level) env.get("LEVEL");
				sb.append("]\u001b[").append(level.color).append("m[").append(env.get("NAME"));
				if (level.ordinal() > Level.WARN.ordinal())
					sb.append("][").append(env.get("THREAD"));

				return sb.append("]\u001b[0m: ");
			};
		} else {
			f = Formatter.simple("[${0}][${THREAD}][${NAME}/${LEVEL}]: ");
		}
		Logger.getRootContext().setPrefix(f);
		LOGGER.setLevel(Level.valueOf(CONFIG.getString("plugin_log", "INFO")));
		PanSecurityManager.LOGGER.setLevel(Level.valueOf(CONFIG.getString("security_log", "INFO")));

		var interrupter = new Interrupter();
		CMD.onVirtualKey(interrupter);
		Terminal.setConsole(interrupter);

		File plugins = new File("plugins"); plugins.mkdir();
		pm = new Panger(plugins);
		pm.onLoad();
		ILSecurityManager.setSecurityManager(new PanSecurityManager.ILHook());
		pm.readPlugins();

		if (pm.stopping) return;

		if (CONFIG.containsKey("webui")) {
			boolean webTerminal = CONFIG.getBool("web_terminal");
			initHttp().register(new WebUI(webTerminal), "webui/");
			router.addPrefixDelegation("xui", new PathRouter(new File("plugins/Core/xui")));
			var noPerm = router.getInterceptor("PermissionManager") == null;
			if (noPerm) LOGGER.fatal("警告：您未安装任何权限管理插件，这会导致webui能被任何人访问！");
			if (webTerminal) {
				LOGGER.warn("""
				您已开启Web终端功能，远程终端的操作会与本地终端（若存在）同步。
				如果受到干扰，按下Ctrl+Q可临时关闭5分钟Web终端""");
			}
		} else if (router != null) {
			for (var itr = resources.entries().iterator(); itr.hasNext(); ) {
				ZEntry entry = itr.next();
				if (entry.getName().startsWith("webui/")) {
					itr.remove();
				}
			}
		}

		if (!Terminal.ANSI_INPUT) LOGGER.error("使用支持ANSI转义的终端以获得更好的体验");
		if (httpServer != null) httpServer.launch();
		CMD.sortCommands();
		Terminal.setConsole(CMD);

		LOGGER.info("启动耗时: {}ms", System.currentTimeMillis()-time);
		HighResolutionTimer.runThis();
	}

	private void onLoad() {
		String CORE_VERSION = "${panger_version}";
		splash(CORE_VERSION);

		var pd = new PluginDescriptor();
		pd.id = SYSTEM_NAME;
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
			unloadPlugin(ctx.argument("id", PluginDescriptor.class));
			System.gc();
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
		CMD.register(literal("cmdhelp").then(argument("指令名称", Argument.oneOf(CollectionX.stupidMapFromView(CMD.nodes(), CommandNode::getName))).executes(ctx -> {
			CommandNode node = ctx.argument("指令名称", CommandNode.class);
			System.out.println(node.dump(new CharList(), 2).toStringAndFree());
		})));
		CMD.register(literal("stop").executes(ctx -> System.exit(0)));

		loadBuiltin();
	}
	private void splash(String CORE_VERSION) {
		CharList s = new CharList("""
			------------------------------------------------------------
			\u001b[38;2;46;137;255m
			  █████▌╗   ████▌╗  ██▌╗   █▌╗  █████▌╗  ██████▌╗ █████▌╗\s
			  █▌╔══█▌╗ █▌╔══█▌╗ ███▌╗  █▌║ █▌╔════╝  █▌╔════╝ █▌╔══█▌╗
			  █████▌╔╝ ██████▌║ █▌╔█▌╗ █▌║ █▌║  ██▌╗ ████▌╗   █████▌╔╝
			  █▌╔═══╝  █▌╔══█▌║ █▌║╚█▌╗█▌║ █▌║   █▌║ █▌╔══╝   █▌╔══█▌╗
			  █▌║      █▌║  █▌║ █▌║ ╚███▌║ ╚█████▌╔╝ ██████▌╗ █▌║  █▌║
			  ╚═╝      ╚═╝  ╚═╝ ╚═╝  ╚═══╝  ╚═════╝  ╚══════╝ ╚═╝  ╚═╝\s\u001b[38;2;128;255;255mv{V}
			\u001b[38;2;255;255;255m
			""").replace("{V}", CORE_VERSION);

		readMOTD();
		if (!motds.isEmpty()) {
			try{
				var motd = motds.get((int)(System.currentTimeMillis()/86400000L)%motds.size());
				motd = Tokenizer.removeSlashes(motd.replace("{user}", System.getProperty("user.name")));

				s.append(" + ").append(motd).append('\n');
			} catch (Exception ignored) {}
		}

		if (CONFIG.getBool("clear_screen")) System.out.print("\u001b[1;1H\u001b[0J");
		System.out.print("\u001b]0;Panger VT\7");
		System.out.println(s.append(" + Copyright (c) 2019-2025 Roj234\n\u001b[0m------------------------------------------------------------").toStringAndFree());
	}
	private void loadBuiltin() {
		PluginDescriptor pd;
		CEntry entry = CONFIG.getOr("load_builtin", CBoolean.TRUE);
		if (!entry.mayCastTo(Type.BOOL) || entry.asBool()) {
			MyHashMap<String, PluginDescriptor> builtin = new MyHashMap<>();
			for (var info : PanTweaker.annotations.annotatedBy("roj/plugin/SimplePlugin")) {
				Annotation pin = info.annotations().get("roj/plugin/SimplePlugin");

				pd = new PluginDescriptor();
				pd.fileName = pin.getBool("inheritConfig") ? "/builtin_inherit" : "/builtin";
				pd.mainClass = info.owner().replace('/', '.');

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
			if (entry.mayCastTo(Type.BOOL) && entry.asBool()) {
				for (var name : CONFIG.getList("load_builtins").asList().raw()) {
					pd = builtin.remove(name.asString());
					if (pd != null) plugins.add(pd);
					else LOGGER.warn("找不到内置插件 {}", name.asString());
				}
			}
			if (builtin.size() > 0) {
				CMD.register(literal("load_builtin").then(argument("plugin", Argument.someOf(builtin)).executes(ctx -> {
					List<PluginDescriptor> lt = Helpers.cast(ctx.argument("plugin", List.class));
					for (int i = 0; i < lt.size(); i++) {
						var pd1 = lt.get(i);
						builtin.remove(pd1.id);
						plugins.add(pd1);
					}
					for (int i = 0; i < lt.size(); i++) {
						var pd1 = lt.get(i);
						loadPlugin(pd1);
						enablePlugin(pd1);
					}
				})));
			}
		}
	}

	private static void shutdown() {
		Terminal.setConsole(null);
		LOGGER.info("正在卸载插件");
		pm.stopping = true;
		pm.unloadPlugins();
		IOUtil.closeSilently(httpServer);
		if (!VMUtil.isShutdownInProgress()) System.exit(0);
	}

	public static void addChannelInitializator(Consumer<MyChannel> ch) {
		var caller = ReflectionUtils.getCallerClass(2);
		if (caller.getClassLoader() instanceof PluginClassLoader) throw new IllegalStateException("not allowed for external plugin");
		httpServer.initializator(httpServer.initializator().andThen(ch));
	}

	private static ServerLaunch httpServer;
	static ZipFile resources;
	private static OKRouter router;
	static OKRouter initHttp() {
		if (router == null) {
			var level = Level.valueOf(CONFIG.getString("http_log", "INFO"));
			HttpServer11.LOGGER.setLevel(level);
			router = new OKRouter(level.canLog(Level.DEBUG));
			try {
				httpServer = HttpServer11.simple("PangerHTTP", new InetSocketAddress(CONFIG.getInt("http_port", 8080)), 512, router);
				MimeType.loadMimeMap(IOUtil.readUTF(new File("plugins/Core/mime.ini")));

				router.setInterceptor("PermissionManager", null);
				var resources = new ZipRouter(new File("plugins/Core/resource.zip"));
				router.addPrefixDelegation("", resources);
				Panger.resources = resources.zip;

				var http = new PanHttp();
				var proxyToken = CONFIG.getString("http_reverse_proxy");
				if (!proxyToken.isEmpty()) HttpCache.proxySecret = proxyToken;
				if (CONFIG.getBool("http_status")) router.register(http);
			} catch (IOException e) {
				LOGGER.error("HTTP服务启动失败", e);
			}
		}

		return router;
	}

	static final SimpleList<String> motds = new SimpleList<>();
	private void readMOTD() {
		try (var tr = TextReader.from(new File(getPluginFolder(), "Core/splashes.txt"), StandardCharsets.UTF_8)) {
			for (;;) {
				var line = tr.readLine();
				if (line == null) break;
				motds.add(line);
			}
		} catch (Exception ignored) {}
	}

	private static final class Interrupter extends Thread implements IntFunction<Boolean>, Console {
		public Interrupter() {
			super(Panger::shutdown, "RojLib Interrupter");
			Runtime.getRuntime().addShutdownHook(this);
		}

		@Override
		public Boolean apply(int key) {
			if (key == (Terminal.VK_CTRL | KeyEvent.VK_C)) {
				CMD.removeKeyHandler(this);
				Runtime.getRuntime().removeShutdownHook(this);
				LOGGER.warn("正在关闭系统，再次按下Ctrl+C以强制退出");
				TaskPool.Common().submit(Panger::shutdown);
				return false;
			}

			return null;
		}

		@Override
		public void keyEnter(int keyCode, boolean isVirtualKey) {
			apply(keyCode);
		}
	}
}