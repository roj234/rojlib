package roj.plugin;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.annotation.Annotation;
import roj.ci.annotation.ReplaceConstant;
import roj.collect.CollectionX;
import roj.collect.HashMap;
import roj.concurrent.TaskPool;
import roj.config.node.BoolValue;
import roj.config.node.ConfigValue;
import roj.config.node.Type;
import roj.http.server.HttpServer;
import roj.http.server.PathRouter;
import roj.http.server.ZipRouter;
import roj.http.server.auto.OKRouter;
import roj.io.IOUtil;
import roj.net.MyChannel;
import roj.net.ServerLaunch;
import roj.plugin.di.DIContext;
import roj.reflect.ILSecurityManager;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.TextReader;
import roj.text.Tokenizer;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.*;
import roj.util.ArtifactVersion;
import roj.util.Helpers;
import roj.util.JVM;

import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static roj.plugin.PanTweaker.CONFIG;
import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/25 18:01
 */
@ReplaceConstant
public final class Jocker extends PluginManager {
	static final Shell CMD = new Shell("Jocker>");
	static final boolean useModulePluginIfAvailable = true;

	static Jocker pm;
	public static PluginManager getPluginManager() {return pm;}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		long time = System.currentTimeMillis();

		Formatter f;
		if (Tty.IS_RICH) {
			f = (env, sb) -> {
				((Formatter) env.get("0")).format(env, sb.append('['));

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
		Tty.pushHandler(interrupter);

		File plugins = new File("plugins"); plugins.mkdir();
		pm = new Jocker(plugins);
		pm.registerSystemCommands();
		DIContext.dependencyLoad(PanTweaker.annotations);
		pm.loadBuiltinPlugins();
		ILSecurityManager.setSecurityManager(new PanSecurityManager.ILHook());
		pm.findPlugins();
		pm.loadPlugins();

		if (pm.stopping) return;

		if (CONFIG.containsKey("webui")) {
			boolean webTerminal = CONFIG.getBool("web_terminal");
			initHttp().register(new WebUI(webTerminal), "webui/");
			router.addPrefixDelegation("ui/", new PathRouter(new File(pm.getPluginFolder(), "Core/ui/")));
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

		if (httpServer != null) httpServer.launch();
		CMD.sortCommands();

		Tty.setHandler(CMD);

		LOGGER.info("启动耗时: {}ms", System.currentTimeMillis()-time);
		JVM.AccurateTimer.parkForMe();
	}

	private Jocker(File pluginFolder) {
		super(pluginFolder);

		var pd = systemPlugin;
		pd.id = "roj.core";
		pd.version = new ArtifactVersion("${project_version}");
		pd.authors = Collections.singletonList("Roj234");

		pd.skipCheck = true;
		pd.dynamicLoadClass = true;
		pd.accessUnsafe = true;

		pd.state = ENABLED;

		if (!Tty.IS_INTERACTIVE) LOGGER.error("使用支持ANSI转义的终端以获得更好的体验");
		else {
			if (CONFIG.getBool("clear_screen")) Tty.write("\u001b[1;1H\u001b[0J");
			Tty.write("\u001b]0;Jocker ${project_version} Terminal\7");
		}

		System.out.println("""
			------------------------------------------------------------
			\u001b[38;2;46;137;255m
			  █████▌╗   ████▌╗  ██▌╗   █▌╗  █████▌╗  ██████▌╗ █████▌╗\s
			  █▌╔══█▌╗ █▌╔══█▌╗ ███▌╗  █▌║ █▌╔════╝  █▌╔════╝ █▌╔══█▌╗
			  █████▌╔╝ ██████▌║ █▌╔█▌╗ █▌║ █▌║  ██▌╗ ████▌╗   █████▌╔╝
			  █▌╔═══╝  █▌╔══█▌║ █▌║╚█▌╗█▌║ █▌║   █▌║ █▌╔══╝   █▌╔══█▌╗
			  █▌║      █▌║  █▌║ █▌║ ╚███▌║ ╚█████▌╔╝ ██████▌╗ █▌║  █▌║
			  ╚═╝      ╚═╝  ╚═╝ ╚═╝  ╚═══╝  ╚═════╝  ╚══════╝ ╚═╝  ╚═╝\s\u001b[38;2;128;255;255mv${project_version}\u001b[38;2;255;255;255m
			
			""");

		readMOTDs();
		try{
			var motd = motds.get((int)(System.currentTimeMillis()/86400000L)%motds.size());
			motd = Tokenizer.unescape(motd.replace("{user}", System.getProperty("user.name")));

			System.out.println(" + "+motd);
		} catch (Exception ignored) {}

		System.out.println(" + Copyright (c) 2019-2025 Roj234\n\u001b[0m------------------------------------------------------------");
	}

	private void registerSystemCommands() {
		CMD.register(literal("load").then(argument("name", Argument.file()).executes(ctx -> {
			File plugin = ctx.argument("name", File.class);
			findAndLoadPlugin(plugin);
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
	}
	private void loadBuiltinPlugins() {
		PluginDescriptor pd;
		ConfigValue entry = CONFIG.get("load_builtin", BoolValue.TRUE);
		if (!entry.mayCastTo(Type.BOOL) || entry.asBool()) {
			HashMap<String, PluginDescriptor> builtin = new HashMap<>();
			for (var info : PanTweaker.annotations.annotatedBy("roj/plugin/SimplePlugin")) {
				Annotation pin = info.annotations().get("roj/plugin/SimplePlugin");

				pd = new PluginDescriptor();
				pd.fileName = pin.getBool("inheritConfig") ? "/builtin_inherit" : "/builtin";
				pd.mainClass = info.owner().replace('/', '.');

				pd.id = pin.getString("id");
				pd.version = new ArtifactVersion(pin.getString("version", "1.0-SNAPSHOT"));
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
						loadAndEnablePlugin(pd1);
					}
				})));
			}
		}
	}

	public static void addChannelInitializator(Consumer<MyChannel> ch) {
		var caller = Reflection.getCallerClass(2);
		if (caller.getClassLoader() instanceof PluginClassLoader) throw new IllegalStateException("not allowed for external plugin");
		httpServer.initializator(httpServer.initializator().andThen(ch));
	}

	private static ServerLaunch httpServer;
	static ZipFile resources;
	private static OKRouter router;
	static OKRouter initHttp() {
		if (router == null) {
			var level = Level.valueOf(CONFIG.getString("http_log", "INFO"));
			HttpServer.setLevel(level);
			router = new OKRouter(level.canLog(Level.DEBUG));
			try {
				httpServer = HttpServer.simple(new InetSocketAddress(CONFIG.getInt("http_port", 8080)), 512, router, "PangerHTTP");
				router.setInterceptor("PermissionManager", null);
				var resources = new ZipRouter(IOUtil.getJar(Jocker.class));
				resources.setPrefix("w/");
				router.addPrefixDelegation("", resources);
				Jocker.resources = resources.zip;

				var http = new PanHttp();
				var proxyToken = CONFIG.getString("http_reverse_proxy");
				if (!proxyToken.isEmpty()) HttpServer.proxySecret = proxyToken;
				if (CONFIG.getBool("http_status")) router.register(http);
			} catch (IOException e) {
				LOGGER.error("HTTP服务启动失败", e);
			}
		}

		return router;
	}

	static List<String> motds = Collections.singletonList("java与您！");
	private void readMOTDs() {
		try (var tr = TextReader.from(new File(getPluginFolder(), "Core/motd.txt"), StandardCharsets.UTF_8)) {
			motds = tr.lines();
		} catch (Exception ignored) {}
	}

	private static final class Interrupter extends Thread implements IntFunction<Boolean>, KeyHandler {
		private static void shutdown() {
			Tty.setHandler(null);
			LOGGER.info("正在卸载插件");
			pm.stopping = true;
			pm.unloadPlugins();
			IOUtil.closeSilently(httpServer);
			if (!JVM.isShutdownInProgress()) System.exit(0);
		}

		public Interrupter() {
			super(Interrupter::shutdown, "Panger Shutdown");
			Runtime.getRuntime().addShutdownHook(this);

			// preload class, only for development purpose
			JVM.isShutdownInProgress();
		}

		@Override
		public Boolean apply(int key) {
			if (key == (Tty.VK_CTRL | KeyEvent.VK_C)) {
				CMD.removeKeyHandler(this);
				Runtime.getRuntime().removeShutdownHook(this);
				LOGGER.warn("正在关闭系统，再次按下Ctrl+C以强制退出");
				TaskPool.common().executeUnsafe(Interrupter::shutdown);
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