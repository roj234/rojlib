package roj.platform;

import roj.math.Version;
import roj.text.logging.Logger;
import roj.ui.CLIUtil;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandConsole;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.locks.LockSupport;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2023/12/25 0025 18:01
 */
public class DefaultPluginSystem {
	public static final CommandConsole CMD = new CommandConsole("RojLib> ");
	public static final PluginManager PM;
	static {
		Logger.getRootContext().destination(() -> System.out);
		File dataFolder = new File("plugins");
		dataFolder.mkdir();
		PM = new PluginManager(dataFolder);
	}

	public static void main(String[] args) {
		if (!CLIUtil.ANSI) {
			System.err.println("Not CLI environment");
			return;
		}

		CMD.register(literal("load").then(argument("name", Argument.file()).executes(ctx -> PM.loadPlugin(ctx.argument("name", File.class)))));
		CMD.register(literal("unload").then(argument("id", Argument.setOf(PM.plugins,  false)).executes(ctx -> PM.unloadPlugin(ctx.argument("id", PluginDescriptor.class).id))));
		CMD.register(literal("stop").executes(ctx -> {
			CLIUtil.setConsole(null);
			PM.LOGGER.info("正在关闭系统");
			PM.unloadPlugins();
			System.exit(0);
		}));
		CMD.register(literal("help").executes(ctx -> {
			System.out.println("已加载的插件:");
			for (PluginDescriptor value : PM.plugins.values()) {
				System.out.println("  "+value.getFullDesc().replace("\n", "\n  ")+"\n");
			}
			System.out.println("可用的指令:");
			CMD.dumpNodes();
		}));

		PM.LOGGER.info("欢迎使用Roj234可重载的插件系统1.1");

		PluginDescriptor pd = new PluginDescriptor();
		pd.id = "RojLib";
		pd.version = new Version("1.5.3-alpha");
		pd.authors = Collections.singletonList("Roj234");
		PM.registerPlugin(pd, null);

		pd = new PluginDescriptor();
		pd.id = "SimplePluginLoader";
		pd.version = new Version("1.0");
		pd.authors = Collections.singletonList("Roj234");
		pd.desc = "注解插件加载器";
		PM.registerPlugin(pd, new SimplePluginLoader());

		PM.readPlugins();

		CLIUtil.setConsole(CMD);
		LockSupport.park();
	}
}
