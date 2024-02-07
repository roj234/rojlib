package roj.platform;

import roj.asm.util.Context;
import roj.asmx.ITransformer;
import roj.asmx.launcher.Bootstrap;
import roj.collect.SimpleList;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.math.Version;
import roj.net.http.srv.HttpServer11;
import roj.net.http.srv.autohandled.OKRouter;
import roj.reflect.ILSecurityManager;
import roj.text.Template;
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
import java.util.Collections;
import java.util.concurrent.locks.LockSupport;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/25 0025 18:01
 */
public final class DefaultPluginSystem extends PluginManager {
	public static final CommandConsole CMD = new CommandConsole("DPS> ");

	static DefaultPluginSystem PM;
	public static DefaultPluginSystem getInstance() { return PM; }
	private static CMapping CONFIG;

	private DefaultPluginSystem(File dataFolder) { super(dataFolder); }

	public static void main(String[] args) throws IOException, ParseException {
		if (!CLIUtil.ANSI) {
			System.err.println("Not CLI environment");
			return;
		}
		Bootstrap.classLoader.registerTransformer(new DPSAutoObfuscate());

		Logger.getRootContext().destination(() -> System.out);
		Logger.getRootContext().setPrefix(Template.compile("[${0}][${THREAD}][${NAME}/${LEVEL}]: "));

		File altConfig = new File("DPSCore.yml");
		CONFIG = altConfig.isFile() ? ConfigMaster.parse(altConfig).asMap() : new CMapping();

		File dataFolder = new File(CONFIG.getString("plugin_dir", "plugins"));
		dataFolder.mkdirs();
		PM = new DefaultPluginSystem(dataFolder);

		CMD.register(literal("load").then(argument("name", Argument.file()).executes(ctx -> PM.loadPlugin(ctx.argument("name", File.class)))));
		CMD.register(literal("unload").then(argument("id", Argument.oneOf(PM.plugins)).executes(ctx -> PM.unloadPlugin(ctx.argument("id", PluginDescriptor.class).id))));
		CMD.register(literal("stop").executes(ctx -> {
			shutdown();
			System.exit(0);
		}));
		CMD.register(literal("help").executes(ctx -> {
			System.out.println("加载的插件:");
			for (PluginDescriptor value : PM.plugins.values()) {
				System.out.println("  "+value.getFullDesc().replace("\n", "\n  ")+"\n");
			}
			System.out.println("注册的指令:");
			System.out.println(CMD.dumpNodes(IOUtil.getSharedCharBuf(), 2));
		}));

		PM.LOGGER.info("欢迎使用基础插件系统1.2");
		Runtime.getRuntime().addShutdownHook(new Thread(DefaultPluginSystem::shutdown));

		PluginDescriptor pd = new PluginDescriptor();
		pd.id = "Core";
		pd.version = new Version("1.5.3-alpha");
		pd.authors = Collections.singletonList("Roj234");

		pd.cl = DefaultPluginSystem.class.getClassLoader();
		pd.skipCheck = true;
		pd.dynamicLoadClass = true;
		pd.accessUnsafe = true;

		PM.registerPlugin(pd, null);

		pd = new PluginDescriptor();
		pd.id = "SimplePluginLoader";
		pd.version = new Version("1.0");
		pd.authors = Collections.singletonList("Roj234");
		pd.desc = "注解插件加载器";
		PM.registerPlugin(pd, new SimplePluginLoader());

		transformers.add(new DPSSecurityManager());
		DPSSecurityManager.LOGGER.setLevel(Level.INFO);
		ILSecurityManager.setSecurityManager(new DPSSecurityManager.SecureClassDefineIL());

		PM.readPlugins();

		CLIUtil.setConsole(CMD);
		LockSupport.park();
	}

	private static void shutdown() {
		CLIUtil.setConsole(null);
		PM.LOGGER.info("正在关闭系统");
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